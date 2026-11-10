/* 소스 안내
 * 일반/보충 및 자동/수동 토큰 예산을 검증하는 JVM 회귀 검사다.
 * 검사 예: automaticGeneralOutputUsesContextBudgetInsteadOfHidden768Limit, manualOutputIsHonoredAndOptionalSupplementsStaySmall, briefAutomaticGeneralAnswersUseASmallerBudget
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.model

import org.junit.Assert.*
import org.junit.Test

class AnswerOutputPolicyTest {
    @Test fun automaticGeneralOutputUsesContextBudgetInsteadOfHidden768Limit() {
        assertEquals(2048, AnswerOutputPolicy.limit(InferenceSettings(contextTokens = 4096, responseTokens = 128), general = true))
        assertEquals(4096, AnswerOutputPolicy.limit(InferenceSettings(contextTokens = 8192), general = true))
        assertEquals(8192, AnswerOutputPolicy.limit(InferenceSettings(contextTokens = 32768), general = true))
    }
    @Test fun manualOutputIsHonoredAndOptionalSupplementsStaySmall() {
        val settings = InferenceSettings(contextTokens = 8192, responseTokens = 3072, automaticAnswerLength = false)
        assertEquals(3072, AnswerOutputPolicy.limit(settings, general = true))
        assertEquals(192, AnswerOutputPolicy.limit(settings, supplement = true))
        assertNull(AnswerOutputPolicy.limit(InferenceSettings()))
    }

    @Test fun briefAutomaticGeneralAnswersUseASmallerBudget() {
        val settings = InferenceSettings(contextTokens = 8192, responseTokens = 3072)
        assertEquals(160, AnswerOutputPolicy.limit(settings, general = true, briefGeneral = true))
        assertEquals(4096, AnswerOutputPolicy.limit(settings, general = true))
        assertEquals(3072, AnswerOutputPolicy.limit(settings.copy(automaticAnswerLength = false), general = true, briefGeneral = true))
        assertEquals(192, AnswerOutputPolicy.limit(settings, supplement = true, briefGeneral = true))
    }
}
