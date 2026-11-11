package com.minyook.sllm2.model

import org.junit.Assert.*
import org.junit.Test

class EngineReusePolicyTest {
    private val gb = 1024L * 1024 * 1024

    @Test fun idlePreparationRequiresArm64AndMemoryHeadroom() {
        assertTrue(EngineReusePolicy.prepareWhileIdle(10, 3 * gb, false, true))
        assertFalse(EngineReusePolicy.prepareWhileIdle(6, 4 * gb, false, true))
        assertFalse(EngineReusePolicy.prepareWhileIdle(10, 2 * gb, false, true))
        assertFalse(EngineReusePolicy.prepareWhileIdle(10, 4 * gb, true, true))
        assertFalse(EngineReusePolicy.prepareWhileIdle(10, 4 * gb, false, false))
    }

    @Test fun changingOutputLengthKeepsTheEngine() {
        val settings = InferenceSettings()
        assertFalse(EngineReusePolicy.requiresReload(settings, settings.copy(responseTokens = 1024, automaticAnswerLength = false)))
        assertTrue(EngineReusePolicy.requiresReload(settings, settings.copy(contextTokens = 8192)))
        assertTrue(EngineReusePolicy.requiresReload(settings, settings.copy(backend = InferenceBackend.GPU)))
    }

    @Test fun autoTriesHexagonThenAdrenoThenCpu() {
        assertEquals(InferenceBackend.AUTO, InferenceSettings().backend)
        assertEquals(listOf(LlamaBackend.NPU, LlamaBackend.GPU, LlamaBackend.CPU), InferenceBackend.AUTO.candidates)
        assertEquals(listOf(LlamaBackend.GPU, LlamaBackend.CPU), InferenceBackend.GPU.candidates)
        assertEquals(listOf(LlamaBackend.CPU), InferenceBackend.CPU.candidates)
    }

    @Test fun nativePluginsRequireSupportedSnapdragonAndAndroidVersion() {
        assertTrue(SnapdragonAcceleration.supports(34, true, "SM8650-AC"))
        assertTrue(SnapdragonAcceleration.supports(35, true, "Qualcomm sm8750"))
        assertFalse(SnapdragonAcceleration.supports(33, true, "SM8650"))
        assertFalse(SnapdragonAcceleration.supports(34, false, "SM8650"))
        assertFalse(SnapdragonAcceleration.supports(35, true, "Exynos 2400"))
        assertFalse(SnapdragonAcceleration.supports(35, true, "SM86500"))
    }

    @Test fun retentionRequiresMemoryForSearch() {
        assertTrue(EngineReusePolicy.retainForSearch(10, 2 * gb, false))
        assertFalse(EngineReusePolicy.retainForSearch(6, 3 * gb, false))
        assertFalse(EngineReusePolicy.retainForSearch(10, gb, false))
        assertFalse(EngineReusePolicy.retainForSearch(10, 3 * gb, true))
    }
}
