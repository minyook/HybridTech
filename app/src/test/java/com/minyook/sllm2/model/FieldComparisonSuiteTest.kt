/* 소스 안내
 * 공통 비교 문항/예산/근거 병합을 검증하는 JVM 회귀 검사다.
 * 검사 예: detailedScenariosKeepTheFullComparisonOutputBudget, subquestionsFromOnePageRetainBothConditions, oldQuickReportsKeepTheirActualSmallBudgetAndSuiteIdentity
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.model

import com.minyook.sllm2.data.EvidencePassage
import com.minyook.sllm2.data.GeneralKnowledgeAnswer
import com.minyook.sllm2.data.KnowledgeChunk
import com.minyook.sllm2.data.RetrievedChunk
import com.minyook.sllm2.data.SearchResolution
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FieldComparisonSuiteTest {
    @Test fun detailedScenariosKeepTheFullComparisonOutputBudget() {
        val settings = InferenceSettings(contextTokens = FieldComparisonSuite.CONTEXT_TOKENS,
            responseTokens = FieldComparisonSuite.OUTPUT_TOKENS, automaticAnswerLength = false)
        for (scenario in FieldComparisonSuite.cases) {
            val request = scenario.question + "\n\n" + FieldComparisonSuite.INSTRUCTION
            assertFalse(scenario.id, GeneralKnowledgeAnswer.prefersBrief(request, automaticLength = false))
            assertEquals(scenario.id, 2048, AnswerOutputPolicy.limit(settings, general = false))
            assertTrue(scenario.id, scenario.evidenceQueries.size >= 2)
        }
        assertTrue(LocalModel.entries.all { it.contextLimit >= FieldComparisonSuite.CONTEXT_TOKENS })
    }

    @Test fun subquestionsFromOnePageRetainBothConditions() {
        val page = KnowledgeChunk(id = 7, body = "원문")
        val first = EvidencePassage("측정 조건입니다.", "측정", 0, 0)
        val second = EvidencePassage("환기 조건입니다.", "환기", 1, 1)
        val sources = FieldComparisonSuite.mergeSources(listOf(
            SearchResolution("측정", sources = listOf(RetrievedChunk(page, 1.0, listOf(first)))),
            SearchResolution("환기", sources = listOf(RetrievedChunk(page, 1.0, listOf(first, second)))),
        ))
        assertEquals(1, sources.size)
        assertEquals(listOf(first, second), sources.single().evidence)
    }

    @Test fun oldQuickReportsKeepTheirActualSmallBudgetAndSuiteIdentity() {
        val legacy = JSONObject("""{"startedAt":1,"device":"test","requestedBackend":"CPU",
            "contextTokens":4096,"outputTokens":256,"rows":[],"phase":"complete"}""")
        val restored = ModelComparisonReport.parse(legacy)
        assertEquals("quick-v1", restored.suiteId)
        assertEquals(5, restored.questionCount)
        assertEquals(256, restored.outputTokens)
    }

    @Test fun longAnswersAndUnknownTerminationRemainIntactInJson() {
        val text = "현장 조건과 확인 절차를 설명합니다.\n".repeat(5000)
        val row = ModelComparisonRow("gemma3-1b", "현장 상세 질문", "공통 원문", text, text, 1500,
            120000, "CPU", 500, 2400, null, null, 2048, 0, caseId = "sampling",
            caseTitle = "측정 계획", reviewChecklist = listOf("측정 조건", "환기 조건"),
            missingEvidenceQueries = listOf("미확인 항목"), requestedOutputTokens = 2048, outputLimitReached = null)
        val report = ModelComparisonReport(1, "test", "CPU", rows = listOf(row), phase = "complete")
        assertEquals(report, ModelComparisonReport.parse(JSONObject(report.json().toString())))
        assertTrue(row.json().isNull("outputLimitReached"))
        assertEquals(text.length, row.json().getInt("outputChars"))
    }
}
