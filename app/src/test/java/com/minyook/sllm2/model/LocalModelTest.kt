/* 소스 안내
 * 모델 ID·파일·크기·문맥 카탈로그을 검증하는 JVM 회귀 검사다.
 * 검사 예: existingPreferencesAndModelFileKeepTheirDefault, aPartialOrDifferentModelCannotBecomeReadyByFileSize, comparisonExportPreservesMissingMetricsAndManualQualityReview
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.model

import org.junit.Assert.*
import org.junit.Test

class LocalModelTest {
    @Test fun existingPreferencesAndModelFileKeepTheirDefault() {
        assertEquals(LocalModel.GEMMA4_E2B, LocalModel.fromId(null))
        assertEquals(LocalModel.GEMMA4_E2B, LocalModel.fromId("removed-model"))
        assertEquals(ModelFiles.MODEL_FILE_NAME, LocalModel.GEMMA4_E2B.fileName)
        assertEquals(LocalModel.entries.size, LocalModel.entries.map { it.fileName }.distinct().size)
    }

    @Test fun aPartialOrDifferentModelCannotBecomeReadyByFileSize() {
        val small = LocalModel.GEMMA3_270M
        val medium = LocalModel.GEMMA3_1B
        assertTrue(small.acceptsBytes(304_005_120))
        assertFalse(small.acceptsBytes(304_005_119))
        assertFalse(small.acceptsBytes(medium.downloadBytes))
        assertFalse(medium.acceptsBytes(small.downloadBytes))
        assertTrue(medium.acceptsBytes(584_417_280))
        assertFalse(LocalModel.GEMMA4_E2B.acceptsBytes(medium.downloadBytes))
    }

    @Test fun comparisonExportPreservesMissingMetricsAndManualQualityReview() {
        val original = ModelComparisonRow("gemma3-270m", "질문", "기준", "답변", "원시 답변",
            null, 1234, "CPU", 200, 900, null, null, 2048, 0, correct = false, reusedEngine = true)
        assertEquals(original, ModelComparisonRow.parse(original.json()))
        assertTrue(original.json().isNull("tokensPerSecond"))
        assertFalse(original.json().getBoolean("correct"))
        assertEquals(original.copy(correct = null), ModelComparisonRow.parse(original.copy(correct = null).json()))
    }
}
