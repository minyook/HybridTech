package com.minyook.sllm2.model

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File

/** Owns one native engine for the visible process; the downloaded model itself stays persistent on disk. */
class LocalModelRuntime(private val context: Context) {
    private val lock = Any()
    @Volatile private var engine: Engine? = null
    @Volatile private var activeBackendName = "unknown"

    suspend fun generate(
        question: String,
        evidence: String,
        compactVoiceAnswer: Boolean = false,
        onPartialAnswer: (String) -> Unit = {},
    ): String = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.elapsedRealtime()
        val settings = InferenceSettingsPreferences(context).load()
        val activeEngine = initializeIfNeeded()
        val engineReadyAt = SystemClock.elapsedRealtime()
        val outputLimit = settings.responseTokens.takeUnless { settings.automaticAnswerLength }
        val prompt = buildPrompt(
            question = question,
            context = evidence,
            compactVoiceAnswer = compactVoiceAnswer,
        )
        val response = StringBuilder()
        var firstChunkAt = 0L
        var lastChunkAt = 0L
        var chunks = 0
        var lastPartialUpdateAt = 0L
        activeEngine.createConversation().use { conversation ->
            conversation.sendMessageAsync(prompt, maxOutputToken = outputLimit).collect { partial ->
                partial.contents.contents
                    .filterIsInstance<Content.Text>()
                    .forEach { text -> response.append(text.text) }
                val now = SystemClock.elapsedRealtime()
                if (firstChunkAt == 0L) firstChunkAt = now
                lastChunkAt = now
                chunks++
                if (response.isNotEmpty() && now - lastPartialUpdateAt >= PARTIAL_UPDATE_INTERVAL_MS) {
                    onPartialAnswer(response.toString())
                    lastPartialUpdateAt = now
                }
            }
        }
        val finishedAt = SystemClock.elapsedRealtime()
        Log.i(
            TAG,
            "backend=$activeBackendName initMs=${engineReadyAt - startedAt} firstChunkMs=${if (firstChunkAt == 0L) -1 else firstChunkAt - engineReadyAt} " +
                "streamMs=${if (firstChunkAt == 0L) -1 else lastChunkAt - firstChunkAt} " +
                "tailMs=${if (lastChunkAt == 0L) -1 else finishedAt - lastChunkAt} " +
                "chunks=$chunks outputChars=${response.length} outputLimit=${outputLimit ?: "model-default"} promptChars=${prompt.length}",
        )
        response.toString().trim()
    }

    fun release() = synchronized(lock) {
        engine?.close()
        engine = null
        activeBackendName = "unknown"
    }

    private fun initializeIfNeeded(): Engine = synchronized(lock) {
        engine?.let { return@synchronized it }
        val modelPath = ModelFiles.finalFile(context).takeIf { it.exists() }
            ?: throw IllegalStateException("다운로드된 Gemma 4 E2B 모델이 없습니다.")
        val cacheDirectory = File(context.noBackupFilesDir, "litert-cache").apply { mkdirs() }
        val profile = DeviceProfile.read(context)
        val settings = InferenceSettingsPreferences(context).load()
        val candidates = when (settings.backend) {
            InferenceBackend.CPU -> listOf(Backend.CPU())
            InferenceBackend.GPU -> listOf(Backend.GPU(), Backend.CPU())
            InferenceBackend.AUTO -> if (profile.recommendedForGemma4E2b) {
                listOf(Backend.GPU(), Backend.CPU())
            } else {
                listOf(Backend.CPU())
            }
        }
        var lastError: Throwable? = null
        for (backend in candidates) {
            var candidate: Engine? = null
            try {
                val initializedEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath.absolutePath,
                        backend = backend,
                        cacheDir = cacheDirectory.absolutePath,
                        maxNumTokens = settings.contextTokens,
                    ),
                )
                candidate = initializedEngine
                initializedEngine.initialize()
                engine = initializedEngine
                activeBackendName = backend.javaClass.simpleName
                return@synchronized initializedEngine
            } catch (error: Exception) {
                candidate?.let { runCatching { it.close() } }
                lastError = error
            }
        }
        throw IllegalStateException("기기에서 모델을 초기화할 수 없습니다.", lastError)
    }

    private fun buildPrompt(
        question: String,
        context: String,
        compactVoiceAnswer: Boolean,
    ): String {
        val answerStyle = if (compactVoiceAnswer) {
            "음성 질문입니다. 직접적인 답을 먼저 말하고, 질문에 필요한 조건·절차·주의사항을 빠짐없이 설명하세요."
        } else {
            "질문에 필요한 조건·절차·주의사항을 빠짐없이 설명하세요. 같은 내용을 반복하지 마세요."
        }
        return """
        당신은 밀폐공간 작업 안전을 돕는 오프라인 안내 도우미입니다.
        아래 제공 문서만 근거로 한국어로 답하세요.
        근거가 부족하면 추측하지 말고 문서에서 확인되지 않는다고 말하세요.
        인명 위험 또는 공기 상태 미확인 상황에서는 작업 중지, 대피, 119 및 현장 안전관리 체계를 우선 안내하세요.
        질문과 관련된 기준 수치·단위·조건·안전 조치는 생략하지 마세요. 관련 없는 배경 설명은 제외하세요.
        $answerStyle
        필요한 경우에만 Markdown 목록을 사용하세요. 문서명·쪽수는 앱에서 별도로 표시합니다.

        [검색된 문서 근거]
        $context

        [질문]
        $question
    """.trimIndent()
    }

    private companion object {
        const val TAG = "FieldGuardInference"
        const val PARTIAL_UPDATE_INTERVAL_MS = 200L
    }
}
