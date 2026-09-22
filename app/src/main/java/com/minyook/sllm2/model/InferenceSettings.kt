package com.minyook.sllm2.model

import android.content.Context

/** A small, persistent execution profile for the app's one reviewed Gemma model. */
enum class InferenceBackend(val label: String) {
    AUTO("자동 · GPU 우선"),
    GPU("GPU 우선 · 실패 시 CPU"),
    CPU("CPU 안전 모드"),
}

data class InferenceSettings(
    val backend: InferenceBackend = InferenceBackend.AUTO,
    val contextTokens: Int = 4_096,
    val responseTokens: Int = 512,
)

/**
 * The model supports a much larger theoretical window, but a mobile device has
 * to reserve memory for Android, the GPU driver and the model itself.  This
 * keeps the setting flexible without advertising an unsafe “unlimited” value.
 */
object ContextTokenPolicy {
    const val MODEL_CONTEXT_LIMIT = 128_000
    const val MIN_CONTEXT_TOKENS = 2_048
    const val MIN_RESPONSE_TOKENS = 128

    fun deviceLimit(context: Context): Int = deviceLimit(DeviceProfile.read(context))

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

    fun load(): InferenceSettings = InferenceSettings(
        backend = runCatching {
            InferenceBackend.valueOf(preferences.getString(KEY_BACKEND, InferenceBackend.AUTO.name).orEmpty())
        }.getOrDefault(InferenceBackend.AUTO),
        contextTokens = preferences.getInt(KEY_CONTEXT_TOKENS, 4_096)
            .coerceIn(ContextTokenPolicy.MIN_CONTEXT_TOKENS, ContextTokenPolicy.deviceLimit(appContext)),
        responseTokens = preferences.getInt(KEY_RESPONSE_TOKENS, 512)
            .coerceIn(ContextTokenPolicy.MIN_RESPONSE_TOKENS, ContextTokenPolicy.responseLimit(
                preferences.getInt(KEY_CONTEXT_TOKENS, 4_096)
                    .coerceIn(ContextTokenPolicy.MIN_CONTEXT_TOKENS, ContextTokenPolicy.deviceLimit(appContext)),
            )),
    )

    fun save(settings: InferenceSettings) {
        preferences.edit()
            .putString(KEY_BACKEND, settings.backend.name)
            .putInt(KEY_CONTEXT_TOKENS, settings.contextTokens)
            .putInt(KEY_RESPONSE_TOKENS, settings.responseTokens)
            .apply()
    }

    private companion object {
        const val KEY_BACKEND = "backend"
        const val KEY_CONTEXT_TOKENS = "context_tokens"
        const val KEY_RESPONSE_TOKENS = "response_tokens"
    }
}
