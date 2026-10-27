package com.minyook.sllm2.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceResponseFormatterTest {
    @Test
    fun preservesDetailedAnswerWithoutMarkdownOrSourceMetadata() {
        val answer = """
            ## 공기 측정
            산소 농도를 먼저 확인하세요. 20.9%라도 현장 절차에 따라 재측정하세요.
            > 출처: 밀폐공간 안전작업 가이드 12쪽
            추가 배경 설명입니다. 더 긴 배경 설명입니다.
        """.trimIndent()

        assertEquals(
            "공기 측정 산소 농도를 먼저 확인하세요. 20.9%라도 현장 절차에 따라 재측정하세요. 추가 배경 설명입니다. 더 긴 배경 설명입니다.",
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
            "산소값을 확인하세요. 기록을 남기세요. 이상 징후가 있으면 작업을 중지하고 대피하세요. 배경 설명입니다.",
            VoiceResponseFormatter.forSpeech("산소값을 확인하세요. 기록을 남기세요. 이상 징후가 있으면 작업을 중지하고 대피하세요. 배경 설명입니다."),
        )
    }
    @Test fun tableValuesAndLateConditionsAreNotDropped() {
        val text = VoiceResponseFormatter.forSpeech("## 기준\n| 가스 | 농도 |\n|---|---|\n| CO | 30 ppm 미만 |\n**단, 확인되지 않으면 출입하지 않습니다.**")
        org.junit.Assert.assertTrue(text.contains("CO 30 ppm 미만"))
        org.junit.Assert.assertTrue(text.contains("확인되지 않으면 출입하지 않습니다."))
    }
    @Test fun chunksPreserveEveryWordAndFitTheNativeTtsLimit() {
        val text = (1..100).joinToString(" ") { "조건 $it: 산소 18% 이상 23.5% 미만을 확인하세요." }
        val chunks = VoiceResponseFormatter.chunks(text, 100)
        org.junit.Assert.assertTrue(chunks.all { it.length <= 100 })
        assertEquals(VoiceResponseFormatter.forSpeech(text), chunks.joinToString(" "))
    }
}
