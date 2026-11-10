/* 소스 안내
 * 검색 인코더를 함께 사용할 메모리 여유가 있을 때 준비된 생성 엔진을 유지할지 결정하는 순수 정책이다.
 */
package com.minyook.sllm2.model

/** Keep a ready GPU engine only when a separate query encoder has ample RAM headroom. */
object EngineReusePolicy {
    fun retainForSearch(backend:String,totalRamGb:Int,availableBytes:Long,lowMemory:Boolean) =
        backend in setOf("GPU", "NPU") && totalRamGb >= 8 && !lowMemory && availableBytes >= 2L * 1024 * 1024 * 1024

    /** E2B 채팅의 첫 적재만 앞당긴다. NPU/CPU 선택과 메모리가 부족한 기기는 요청 시 적재한다. */
    fun prepareWhileIdle(settings: InferenceSettings, totalRamGb: Int, availableBytes: Long, lowMemory: Boolean, arm64: Boolean) =
        settings.model == LocalModel.GEMMA4_E2B && settings.backend in setOf(InferenceBackend.AUTO, InferenceBackend.GPU) &&
            arm64 && totalRamGb >= 8 && !lowMemory && availableBytes >= 4L * 1024 * 1024 * 1024

    /** 출력 길이만 바뀐 경우 같은 엔진을 유지한다. 현재 생성 중인 엔진의 강제 종료에는 사용하지 않는다. */
    fun requiresReload(before: InferenceSettings, after: InferenceSettings) =
        before.model != after.model || before.backend != after.backend || before.contextTokens != after.contextTokens
}
