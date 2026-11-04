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
}
