package com.minyook.sllm2.model

/** Automatic output uses the context budget; manual length is honored for general answers too. */
object AnswerOutputPolicy {
    fun limit(settings: InferenceSettings, supplement: Boolean = false, general: Boolean = false): Int? = when {
        supplement -> minOf(settings.responseTokens, 192)
        !settings.automaticAnswerLength -> settings.responseTokens.coerceIn(ContextTokenPolicy.MIN_RESPONSE_TOKENS, ContextTokenPolicy.responseLimit(settings.contextTokens))
        general -> ContextTokenPolicy.responseLimit(settings.contextTokens)
        else -> null
    }
}
