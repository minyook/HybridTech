/* 소스 안내
 * :npu 보조 프로세스에서 칩셋 전용 엔진을 실행한다. Messenger TEXT/DONE/ERROR 응답과 ABORT를 처리한다.
 */
package com.minyook.sllm2.model

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SystemClock
import android.system.Os
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors

internal object NpuProtocol {
    const val GENERATE = 1
    const val TEXT = 2
    const val DONE = 3
    const val ERROR = 4
    const val ABORT = 5
}

/**
 * Manifest의 android:process=":npu"로 실행되는 추론 전용 서비스.
 * NpuInferenceClient가 보낸 prompt/설정을 받아 칩셋 전용 LiteRT-LM 엔진을 열고 Messenger로 응답한다.
 * 무거운 생성은 executor에서 실행해 main looper가 ABORT를 받을 수 있게 한다.
 * ABORT는 이 보조 프로세스만 종료한다. 기본 UI/문서 DB는 이 서비스가 소유하지 않는다.
 * 프로세스 격리는 드라이버 오류의 영향 범위를 줄이는 장치이며 NPU 드라이버 호환성을 보장하지 않는다.
 */
class NpuInferenceService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var busy = false
    private var engine: Engine? = null
    private var engineKey: String? = null
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            NpuProtocol.GENERATE -> {
                val data = Bundle(message.data)
                val reply = message.replyTo
                val id = message.arg1
                executor.execute { generate(data, reply, id) }
                true
            }
            NpuProtocol.ABORT -> {
                Process.killProcess(Process.myPid())
                true
            }
            else -> false
        }
    })

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun send(reply: Messenger, id: Int, kind: Int, data: Bundle) {
        reply.send(Message.obtain(null, kind).apply { arg1 = id; this.data = data })
    }

    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private fun generate(data: Bundle, reply: Messenger, id: Int) = runBlocking {
        busy = true
        val started = SystemClock.elapsedRealtime()
        try {
            val socModel = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL
                else error("이 Android 버전에서는 NPU 칩셋을 확인할 수 없어요.")
            val model = LocalModel.fromId(data.getString("modelId"))
            val artifact = NpuModel.forDevice(model, socModel) ?: error("지원하는 NPU 모델 파일이 없어요.")
            val path = ModelFiles.finalFile(this@NpuInferenceService, model, artifact)
            check(path.length() == artifact.bytes) { "NPU 모델 다운로드를 확인해 주세요." }
            val contextTokens = data.getInt("contextTokens").coerceAtMost(artifact.contextLimit)
            val outputTokens = data.getInt("outputTokens")
            check(outputTokens in 1 until contextTokens) { "요청한 답변 길이가 NPU 문맥을 초과해요." }
            val key = "${artifact.id}-$contextTokens"
            val reused = engine != null && engineKey == key
            if (!reused) {
                engine?.close()
                engine = null
                engineKey = null
                val libraries = applicationInfo.nativeLibraryDir
                Os.setenv("ADSP_LIBRARY_PATH", libraries, true)
                val cache = File(noBackupFilesDir, "litert-cache/${artifact.id}").apply { mkdirs() }
                val candidate = Engine(EngineConfig(path.absolutePath,
                    backend = Backend.NPU(nativeLibraryDir = libraries), cacheDir = cache.absolutePath,
                    maxNumTokens = contextTokens))
                try {
                    candidate.initialize()
                    engine = candidate
                    engineKey = key
                } catch (error: Throwable) {
                    runCatching { candidate.close() }
                    throw error
                }
            }
            val ready = SystemClock.elapsedRealtime()
            val response = StringBuilder()
            var firstText: Long? = null
            var lastPublish = 0L
            var outputTokenCount: Int? = null
            var tokensPerSecond: Double? = null
            engine!!.createConversation().use { conversation ->
                conversation.sendMessageAsync(data.getString("prompt").orEmpty(), maxOutputToken = outputTokens).collect { partial ->
                    val previous = response.length
                    partial.contents.contents.forEach { if (it is Content.Text) response.append(it.text) }
                    if (response.length != previous) {
                        val now = SystemClock.elapsedRealtime()
                        if (firstText == null) firstText = now - started
                        if (lastPublish == 0L || now - lastPublish >= 80) {
                            send(reply, id, NpuProtocol.TEXT, Bundle().apply { putString("text", response.toString()) })
                            lastPublish = now
                        }
                    }
                }
                runCatching { conversation.getBenchmarkInfo() }.getOrNull()?.let {
                    outputTokenCount = it.lastDecodeTokenCount
                    tokensPerSecond = it.lastDecodeTokensPerSecond
                }
            }
            send(reply, id, NpuProtocol.DONE, Bundle().apply {
                putString("text", response.toString())
                putLong("initializationMs", ready - started)
                putLong("firstChunkMs", firstText ?: -1)
                putBoolean("reusedEngine", reused)
                putInt("outputTokens", outputTokenCount ?: -1)
                putDouble("tokensPerSecond", tokensPerSecond ?: -1.0)
                putInt("contextTokens", contextTokens)
            })
        } catch (error: Throwable) {
            runCatching { engine?.close() }
            engine = null
            engineKey = null
            runCatching { send(reply, id, NpuProtocol.ERROR, Bundle().apply {
                putString("error", error.message?.take(200) ?: "NPU 실행을 완료하지 못했어요.")
            }) }
        } finally { busy = false }
    }

    override fun onDestroy() {
        if (busy) {
            Process.killProcess(Process.myPid())
            return
        }
        executor.execute { engine?.close(); engine = null }
        executor.shutdown()
        super.onDestroy()
    }
}
