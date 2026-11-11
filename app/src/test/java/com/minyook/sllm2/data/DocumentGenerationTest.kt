package com.minyook.sllm2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentGenerationTest {
    @Test fun numberedAnswerMustBeCompleteAndPreserveReviewedConditions() {
        val body = """
            1. 공기 상태가 확인되지 않으면 작업 재개를 보류합니다.
            2. 환기설비가 정상 작동하는지 확인합니다.
        """.trimIndent()
        val plan = DocumentAnswerPlan(
            "점검 항목 2개를 각 항목 한 문장으로 써줘",
            body, listOf(PlannedPassage("e1", 1, body, "점검")), emptyList(), directAnswer = true,
        )
        assertEquals(body, DocumentGeneration.accept(body, plan, limitReached = false))
        assertNull(DocumentGeneration.accept(body, plan, limitReached = true))
        assertNull(DocumentGeneration.accept(body.substringBefore('\n'), plan, limitReached = false))
        assertNull(DocumentGeneration.accept(body.replace("2.", "1."), plan, limitReached = false))
        assertNull(DocumentGeneration.accept(body.replace("정상 작동하는지 확인합니다.", "정상입니다. 계속 확인합니다."), plan, limitReached = false))
        assertNull(DocumentGeneration.accept(body.replace("않으면 작업 재개를 보류합니다.", "않아도 작업 재개가 가능합니다."), plan, limitReached = false))
    }

    @Test fun numericClaimsMustMatchTheDocumentIncludingTheirQualifier() {
        val body = "시험 장비의 대기 시간은 10분 이상입니다."
        val plan = DocumentAnswerPlan(
            "시험 장비의 대기 시간은?",
            body, listOf(PlannedPassage("e1", 1, body, "대기 시간")), emptyList(),
        )
        assertEquals(body, DocumentGeneration.accept(body, plan, limitReached = false))
        for (unsupported in listOf("12분 이상", "10분 이하", "10분")) {
            assertNull(DocumentGeneration.accept(body.replace("10분 이상", unsupported), plan, limitReached = false))
        }
        val timingPlan = plan.copy(question = "작업 재개 전에 확인하나요?", body = "$body 작업 재개 전에 확인해야 합니다.",
            evidence = listOf(PlannedPassage("e1", 1, "$body 작업 재개 전에 확인해야 합니다.", "점검")))
        assertEquals("작업 재개 전에 확인해야 합니다.",
            DocumentGeneration.accept("작업 재개 전에 확인해야 합니다.", timingPlan, limitReached = false))
        assertNull(DocumentGeneration.accept("작업 재개 전에 확인해야 합니다. 작업 전·중·후에 확인합니다.",
            timingPlan, limitReached = false))
        assertNull(DocumentGeneration.accept("작업 재개 전에 확인해야 합니다. [e1-0]", timingPlan, limitReached = false))
        assertNull(DocumentGeneration.accept("문서 근거\n측정 시기 작업을 일시 중단했다가 다시 시작하기 전",
            timingPlan, limitReached = false))
        assertNull(DocumentGeneration.accept("작업 재개 전에 허가서 갱신 여부를 확인해야 합니다.",
            timingPlan, limitReached = false))
    }
}
