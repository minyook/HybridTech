/* 소스 안내
 * 일반/보충·자동/수동 길이 정책을 실제 출력 토큰 한도로 바꾼다. 표시 애니메이션이나 계정 질문 횟수 제한과 별개다.
 */
package com.minyook.sllm2.model

/**
 * LocalModelRuntime이 호출하는 출력 토큰 한도 정책. 토큰 사용량 요금제나 시간별 질문 횟수 제한이 아니다.
 * 수동은 설정값, 자동 일반 답변은 문맥별 예산, 명확한 짧은 요청과 선택적 문서 보충은 별도 작은 예산을 쓴다.
 * 표시 애니메이션은 이 한도를 바꾸지 않는다.
 */
object AnswerOutputPolicy {
    fun limit(
        settings: InferenceSettings,
        supplement: Boolean = false,
        general: Boolean = false,
        briefGeneral: Boolean = false,
    ): Int? = when {
        supplement -> minOf(settings.responseTokens, 192)
        !settings.automaticAnswerLength -> settings.responseTokens.coerceIn(ContextTokenPolicy.MIN_RESPONSE_TOKENS, ContextTokenPolicy.responseLimit(settings.contextTokens))
        general && briefGeneral -> minOf(ContextTokenPolicy.responseLimit(settings.contextTokens), 160)
        general -> ContextTokenPolicy.responseLimit(settings.contextTokens)
        else -> null
    }
}
