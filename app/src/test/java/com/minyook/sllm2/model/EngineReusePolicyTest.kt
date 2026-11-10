/* 소스 안내
 * 메모리 조건별 생성 엔진 유지을 검증하는 JVM 회귀 검사다.
 * 검사 예: retentionRequiresGpuAndRamHeadroom
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.model

import org.junit.Assert.*
import org.junit.Test

class EngineReusePolicyTest {
    @Test fun idlePreparationRequiresSupportedModelBackendAndMemoryHeadroom() {
        val gb = 1024L * 1024 * 1024
        val settings = InferenceSettings()
        assertTrue(EngineReusePolicy.prepareWhileIdle(settings, 10, 4*gb, false, true))
        assertTrue(EngineReusePolicy.prepareWhileIdle(settings.copy(backend=InferenceBackend.GPU), 10, 5*gb, false, true))
        for (backend in listOf(InferenceBackend.CPU, InferenceBackend.NPU))
            assertFalse(EngineReusePolicy.prepareWhileIdle(settings.copy(backend=backend), 10, 5*gb, false, true))
        assertFalse(EngineReusePolicy.prepareWhileIdle(settings.copy(model=LocalModel.GEMMA3_1B), 10, 5*gb, false, true))
        assertFalse(EngineReusePolicy.prepareWhileIdle(settings, 6, 5*gb, false, true))
        assertFalse(EngineReusePolicy.prepareWhileIdle(settings, 10, 3*gb, false, true))
        assertFalse(EngineReusePolicy.prepareWhileIdle(settings, 10, 5*gb, true, true))
        assertFalse(EngineReusePolicy.prepareWhileIdle(settings, 10, 5*gb, false, false))
    }

    @Test fun savingOnlyAnswerLengthDoesNotDiscardAReadyEngine() {
        val settings = InferenceSettings()
        assertFalse(EngineReusePolicy.requiresReload(settings, settings))
        assertFalse(EngineReusePolicy.requiresReload(settings, settings.copy(responseTokens=1024, automaticAnswerLength=false)))
        assertTrue(EngineReusePolicy.requiresReload(settings, settings.copy(contextTokens=8192)))
        assertTrue(EngineReusePolicy.requiresReload(settings, settings.copy(backend=InferenceBackend.CPU)))
        assertTrue(EngineReusePolicy.requiresReload(settings, settings.copy(model=LocalModel.GEMMA3_1B)))
    }

    @Test fun retentionRequiresGpuAndRamHeadroom() {
        val gb=1024L*1024*1024
        assertTrue(EngineReusePolicy.retainForSearch("GPU",10,3*gb,false))
        assertTrue(EngineReusePolicy.retainForSearch("NPU",10,3*gb,false))
        assertFalse(EngineReusePolicy.retainForSearch("NPU",10,gb,false))
        assertFalse(EngineReusePolicy.retainForSearch("CPU",10,3*gb,false))
        assertFalse(EngineReusePolicy.retainForSearch("GPU",6,3*gb,false))
        assertFalse(EngineReusePolicy.retainForSearch("GPU",10,gb,false))
        assertFalse(EngineReusePolicy.retainForSearch("GPU",10,3*gb,true))
    }
}
