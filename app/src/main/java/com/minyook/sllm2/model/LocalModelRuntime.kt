package com.minyook.sllm2.model

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.minyook.sllm2.data.KnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/** Owns one native engine for the visible process; the downloaded model itself stays persistent on disk. */
class LocalModelRuntime(private val context: Context) {
    private val lock = Any()
    @Volatile private var engine: Engine? = null

    fun generate(
        question: String,
        repository: KnowledgeRepository,
        compactVoiceAnswer: Boolean = false,
    ): String = runBlocking {
        withContext(Dispatchers.Default) {
            val settings = InferenceSettingsPreferences(context).load()
            val activeEngine = initializeIfNeeded()
            val prompt = buildPrompt(
                question = question,
                context = repository.contextForModel(question),
                responseTokens = settings.responseTokens,
                compactVoiceAnswer = compactVoiceAnswer,
            )
            val response = StringBuilder()
            activeEngine.createConversation().use { conversation ->
                conversation.sendMessageAsync(prompt).collect { partial ->
                    partial.contents.contents
                        .filterIsInstance<Content.Text>()
                        .forEach { text -> response.append(text.text) }
                }
            }
            response.toString().trim()
        }
    }

    fun release() = synchronized(lock) {
        engine?.close()
        engine = null
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
        responseTokens: Int,
        compactVoiceAnswer: Boolean,
    ): String = """
        당신은 밀폐공간 작업 안전을 돕는 오프라인 안내 도우미입니다.
        아래 제공 문서만 근거로 한국어로 간결하게 답하세요.
        근거가 부족하면 추측하지 말고 문서에서 확인되지 않는다고 말하세요.
        인명 위험 또는 공기 상태 미확인 상황에서는 작업 중지, 대피, 119 및 현장 안전관리 체계를 우선 안내하세요.
        질문에 직접 답하는 내용만 쓰고, 관련 없는 점검 항목·절차·배경 설명은 넣지 마세요.
        답이 명확하면 2~5문장 또는 짧은 목록으로 끝내세요. $responseTokens 토큰은 최대치일 뿐 채울 목표가 아닙니다.
        ${if (compactVoiceAnswer) "음성 인식 질문입니다. 호칭·추임새는 무시하고 질문의 핵심 한 가지에만 1~3문장으로 답하세요. 질문에 명시되지 않은 관련 지식·일반 절차·추가 점검 항목은 넣지 마세요." else ""}
        답변은 읽기 쉬운 Markdown으로 작성하세요. 필요한 경우에만 짧은 제목(`###`)·목록(`-`)·출처(`> 출처:`)를 사용하세요.

        [검색된 문서 근거]
        $context

        [질문]
        $question
    """.trimIndent()
}
