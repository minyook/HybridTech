package com.minyook.sllm2.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceResponseFormatterTest {
    @Test
    fun readsOnlyConciseAnswerWithoutMarkdownOrSources() {
        val answer = """
            ## 공기 측정
            산소 농도를 먼저 확인하세요. 20.9%라도 현장 절차에 따라 재측정하세요.
            > 출처: 밀폐공간 안전작업 가이드 12쪽
            추가 배경 설명입니다. 더 긴 배경 설명입니다.
        """.trimIndent()

        assertEquals(
            "산소 농도를 먼저 확인하세요. 20.9%라도 현장 절차에 따라 재측정하세요.",
            VoiceResponseFormatter.forSpeech(answer),
        )
    }

    @Test
    fun preservesShortSafetyInstruction() {
        assertEquals(
            "작업을 중지하고 대피하세요. 119와 현장 안전관리자에게 알리세요.",
            VoiceResponseFormatter.forSpeech("- **작업을 중지하고 대피하세요.**\n- 119와 현장 안전관리자에게 알리세요."),
        )
    }

    @Test
    fun keepsUrgentActionEvenWhenItComesAfterTwoSentences() {
        assertEquals(
            "산소값을 확인하세요. 기록을 남기세요. 이상 징후가 있으면 작업을 중지하고 대피하세요.",
            VoiceResponseFormatter.forSpeech("산소값을 확인하세요. 기록을 남기세요. 이상 징후가 있으면 작업을 중지하고 대피하세요. 배경 설명입니다."),
        )
    }
}
