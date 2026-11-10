/* 소스 안내
 * 원문 단어/수치를 보존한 표시용 문단 분리을 검증하는 JVM 회귀 검사다.
 * 검사 예: separatesSourceMarkersWithoutChangingTheEvidence
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDocumentFormatterTest {
    @Test
    fun separatesSourceMarkersWithoutChangingTheEvidence() {
        val original = "적정 공기 ① 산소농도 18.0% 이상 23.5% 미만② 황화수소 10 ppm 미만 ※ 측정 결과를 기록합니다."

        val formatted = SourceDocumentFormatter.format(original)

        assertTrue(formatted.contains("\n\n①"))
        assertTrue(formatted.contains("\n\n②"))
        assertTrue(formatted.contains("\n\n※"))
        assertEquals(original.filterNot(Char::isWhitespace), formatted.filterNot(Char::isWhitespace))
    }
}
