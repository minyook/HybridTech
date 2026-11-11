/* 소스 안내
 * 모델/실행 장치/문맥/출력 설정과 기기별 상한·저장을 정의한다. 설정 UI와 Runtime이 같은 값을 사용한다.
 */
package com.minyook.sllm2.model

import android.content.Context

/** Persistent model choice and execution settings. */
enum class InferenceBackend(val label: String) {
    AUTO("자동 · NPU → GPU → CPU"),
    GPU("GPU 우선 · GPU → CPU"),
    CPU("CPU · llama.cpp"),

    ;

    internal val candidates: List<LlamaBackend> get() = when (this) {
        AUTO -> listOf(LlamaBackend.NPU, LlamaBackend.GPU, LlamaBackend.CPU)
        GPU -> listOf(LlamaBackend.GPU, LlamaBackend.CPU)
        CPU -> listOf(LlamaBackend.CPU)
    }
}

/** 화면·저장·Runtime이 공유하는 실행 설정 값. 문맥/출력 상한은 ContextTokenPolicy와 AnswerOutputPolicy가 적용한다. */
data class InferenceSettings(
    val backend: InferenceBackend = InferenceBackend.AUTO,
    val contextTokens: Int = 4_096,
    val responseTokens: Int = 512,
    val automaticAnswerLength: Boolean = true,
)

/**
 * The model supports a much larger theoretical window, but a mobile device has
 * to reserve memory for Android, the search encoder and the model itself.  This
 * keeps the setting flexible without advertising an unsafe “unlimited” value.
 */
object ContextTokenPolicy {
    const val MODEL_CONTEXT_LIMIT = 65_536
    const val MIN_CONTEXT_TOKENS = 2_048
    const val MIN_RESPONSE_TOKENS = 128

    fun deviceLimit(context: Context): Int =
        minOf(deviceLimit(DeviceProfile.read(context)), LocalModel.contextLimit)

    fun deviceLimit(profile: DeviceProfile): Int = when {
        profile.lowRamDevice || profile.totalRamGb < 6 -> 2_048
        profile.totalRamGb < 8 -> 4_096
        profile.totalRamGb < 12 -> 8_192
        profile.totalRamGb < 16 -> 16_384
        profile.totalRamGb < 24 -> 24_576
        else -> 32_768
    }.coerceAtMost(MODEL_CONTEXT_LIMIT)

    fun responseLimit(contextTokens: Int): Int = (contextTokens / 2)
        .coerceAtLeast(MIN_RESPONSE_TOKENS)
        .coerceAtMost(8_192)
}

/** Keeps settings across app restarts without storing model input, document text, or answers. */
class InferenceSettingsPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences("inference_settings", Context.MODE_PRIVATE)

    fun load(): InferenceSettings {
        val context = preferences.getInt(KEY_CONTEXT_TOKENS, 4_096)
            .coerceIn(ContextTokenPolicy.MIN_CONTEXT_TOKENS, ContextTokenPolicy.deviceLimit(appContext))
        return InferenceSettings(
            backend = runCatching {
                InferenceBackend.valueOf(preferences.getString(KEY_BACKEND, InferenceBackend.AUTO.name).orEmpty())
            }.getOrDefault(InferenceBackend.AUTO),
            contextTokens = context,
            responseTokens = preferences.getInt(KEY_RESPONSE_TOKENS, 512)
                .coerceIn(ContextTokenPolicy.MIN_RESPONSE_TOKENS, ContextTokenPolicy.responseLimit(context)),
            automaticAnswerLength = preferences.getBoolean(KEY_AUTOMATIC_ANSWER_LENGTH, true),
        )
    }

    fun save(settings: InferenceSettings) {
        preferences.edit()
            .putString(KEY_BACKEND, settings.backend.name)
            .putInt(KEY_CONTEXT_TOKENS, settings.contextTokens)
            .putInt(KEY_RESPONSE_TOKENS, settings.responseTokens)
            .putBoolean(KEY_AUTOMATIC_ANSWER_LENGTH, settings.automaticAnswerLength)
            .apply()
    }

    private companion object {
        const val KEY_BACKEND = "execution_order"
        const val KEY_CONTEXT_TOKENS = "context_tokens"
        const val KEY_RESPONSE_TOKENS = "response_tokens"
        const val KEY_AUTOMATIC_ANSWER_LENGTH = "automatic_answer_length"
    }
}
