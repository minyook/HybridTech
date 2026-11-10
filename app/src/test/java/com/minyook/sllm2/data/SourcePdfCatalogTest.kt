/* 소스 안내
 * 문서 ID·PDF 판본·쪽 대응을 검증하는 JVM 회귀 검사다.
 * 검사 예: sourcePageNumbersMapToTheSamePhysicalPdfPages
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcePdfCatalogTest {
    @Test
    fun sourcePageNumbersMapToTheSamePhysicalPdfPages() {
        val guide = SourcePdfCatalog.find("confined_space_safety_guide_2022")!!
        val checklist = SourcePdfCatalog.find("confined_space_checklist_2026")!!

        assertEquals(84, guide.pageCount)
        assertEquals(44, guide.pageIndex(45))
        assertEquals(26, guide.pageIndex(27))
        assertEquals(1, checklist.pageIndex(2))
        assertNull(guide.pageIndex(85))
        assertNull(checklist.pageIndex(0))
    }
}
