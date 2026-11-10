/* 소스 안내
 * 기본 프로세스의 NPU IPC 요청/응답·시간 제한·종료 확인을 담당한다. 실패는 LocalModelRuntime의 전환 정책으로 전달한다.
 */
package com.minyook.sllm2.model

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

internal data class NpuGenerationResult(
    val text: String,
    val bindingMs: Long,
    val initializationMs: Long,
    val firstChunkMs: Long?,
    val reusedEngine: Boolean,
    val outputTokens: Int?,
    val tokensPerSecond: Double?,
    val contextTokens: Int,
)

/**
 * 기본 프로세스의 LocalModelRuntime과 :npu 서비스 사이의 Messenger IPC 어댑터.
 * 요청 ID로 늦게 도착한 응답을 구분하고 TEXT/DONE/ERROR를 중간 본문·완료 지표·예외로 바꾼다.
 * bind/첫 응답/다음 응답에 제한 시간을 두며 호출자가 실패 시 abortAndRelease로 프로세스 종료를 확인한다.
 * 범용 GPU/CPU 전환 정책은 여기서 결정하지 않고 LocalModelRuntime에 오류를 전달한다.
 */
internal class NpuInferenceClient(private val context: Context) {
    @Volatile private var connection: ServiceConnection? = null
    @Volatile private var remote: Messenger? = null
    @Volatile private var replies: Channel<Message>? = null
    private val sequence = AtomicInteger()

    suspend fun generate(model: LocalModel, prompt: String, contextTokens: Int, outputTokens: Int,
        onPartial: ((String) -> Unit)?): NpuGenerationResult {
        val id = sequence.incrementAndGet()
        val channel = Channel<Message>(Channel.UNLIMITED)
        replies = channel
        val responseHandler = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.arg1 == id) channel.trySend(Message.obtain(message))
            true
        })
        val bindingStarted = SystemClock.elapsedRealtime()
        val service = withTimeout(15_000) { bind() }
        val bindingMs = SystemClock.elapsedRealtime() - bindingStarted
        try {
            service.send(Message.obtain(null, NpuProtocol.GENERATE).apply {
                arg1 = id
                replyTo = responseHandler
                data = Bundle().apply {
                    putString("modelId", model.id)
                    putString("prompt", prompt)
                    putInt("contextTokens", contextTokens)
                    putInt("outputTokens", outputTokens)
                }
            })
            var startedStreaming = false
            while (true) {
                val message = withTimeout(if (startedStreaming) 30_000L else 60_000L) { channel.receive() }
                when (message.what) {
                    NpuProtocol.TEXT -> {
                        startedStreaming = true
                        onPartial?.invoke(message.data.getString("text").orEmpty())
                    }
                    NpuProtocol.DONE -> {
                        val result = message.data
                        return NpuGenerationResult(result.getString("text").orEmpty(), bindingMs,
                            result.getLong("initializationMs"), result.getLong("firstChunkMs").takeIf { it >= 0 },
                            result.getBoolean("reusedEngine"), result.getInt("outputTokens").takeIf { it >= 0 },
                            result.getDouble("tokensPerSecond").takeIf { it >= 0 }, result.getInt("contextTokens"))
                    }
                    NpuProtocol.ERROR -> throw IOException(message.data.getString("error") ?: "NPU 실행에 실패했어요.")
                }
            }
        } finally {
            replies = null
            channel.close()
        }
    }

    private suspend fun bind(): Messenger {
        remote?.let { return it }
        val ready = CompletableDeferred<Messenger>()
        withContext(Dispatchers.Main) {
            val callback = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (connection !== this) return
                    Messenger(binder).also { remote = it; ready.complete(it) }
                }
                override fun onServiceDisconnected(name: ComponentName) = disconnected()
                override fun onBindingDied(name: ComponentName) = disconnected()
                override fun onNullBinding(name: ComponentName) = disconnected()
                private fun disconnected() {
                    if (connection !== this) return
                    remote = null
                    val failure = IOException("NPU 실행 프로세스가 종료됐어요.")
                    ready.completeExceptionally(failure)
                    replies?.close(failure)
                }
            }
            connection = callback
            if (!context.bindService(Intent(context, NpuInferenceService::class.java), callback, Context.BIND_AUTO_CREATE)) {
                connection = null
                ready.completeExceptionally(IOException("NPU 실행 서비스에 연결하지 못했어요."))
            }
        }
        return ready.await()
    }

    fun release(abort: Boolean = false) {
        if (abort) runCatching { remote?.send(Message.obtain(null, NpuProtocol.ABORT)) }
        val previous = connection
        connection = null
        remote = null
        if (previous != null) runCatching { context.unbindService(previous) }
        replies?.close(IOException("NPU 연결을 정리했어요."))
        replies = null
    }

    /** native 호출이 막혔을 수 있으므로 ABORT 후 binder 사망을 확인한다. 정리 불확실성을 호출자에게 전달한다. */
    suspend fun abortAndRelease() {
        val binder = remote?.binder
        if (binder == null || !binder.isBinderAlive) {
            release(abort = true)
            return
        }
        val died = CompletableDeferred<Unit>()
        val recipient = IBinder.DeathRecipient { died.complete(Unit) }
        runCatching { binder.linkToDeath(recipient, 0) }.onFailure { died.complete(Unit) }
        release(abort = true)
        val confirmed = withTimeoutOrNull(3_000) { died.await(); true } == true
        runCatching { binder.unlinkToDeath(recipient, 0) }
        check(confirmed || !binder.isBinderAlive) { "NPU 메모리 정리를 확인하지 못했어요. 앱을 다시 열어 주세요." }
    }
}
