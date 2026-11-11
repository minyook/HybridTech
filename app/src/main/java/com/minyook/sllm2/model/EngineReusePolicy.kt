package com.minyook.sllm2.model

/** Leaves memory available for the separate ONNX retrieval encoder. */
object EngineReusePolicy {
    fun retainForSearch(totalRamGb: Int, availableBytes: Long, lowMemory: Boolean) =
        totalRamGb >= 8 && !lowMemory && availableBytes >= 2L * 1024 * 1024 * 1024

    fun prepareWhileIdle(totalRamGb: Int, availableBytes: Long, lowMemory: Boolean, arm64: Boolean) =
        arm64 && totalRamGb >= 8 && !lowMemory && availableBytes >= 3L * 1024 * 1024 * 1024

    fun requiresReload(before: InferenceSettings, after: InferenceSettings) =
        before.contextTokens != after.contextTokens || before.backend != after.backend
}
