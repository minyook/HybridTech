package com.minyook.sllm2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionEvidencePlannerTest {
    @Test
    fun separatesCprAndOxygenMeasurement() {
        assertEquals(
            listOf("심폐소생술", "산소측정법 알려줘"),
            QuestionEvidencePlanner.aspects("심폐소생술과 산소측정법 알려줘"),
        )
        assertEquals(listOf("측정 결과 알려줘"), QuestionEvidencePlanner.aspects("측정 결과 알려줘"))
    }

    @Test
    fun ranksTheProcedurePagesAheadOfTheTableOfContentsAndGeneralLimits() {
        val pages = listOf(
            page(43, "재해자 구조 2. 심폐소생술"),
            page(45, "심폐소생술: 반응 확인, 119 신고, 호흡 확인, 흉부 압박 30회, 인공호흡 2회. 심폐소생술 절차"),
            page(46, "심폐소생술: 흉부 압박과 인공호흡을 반복"),
            page(2, "산소 및 유해가스 농도 측정 결과를 기록합니다. 안전한 작업 방법을 교육합니다. 산소 측정 후 결과를 확인합니다."),
            page(24, "산소·유해가스 농도 측정. 적정 공기 기준"),
            page(27, "산소·유해가스 농도 측정방법. 사전준비와 측정방법, 주의사항"),
        )

        assertEquals(45, QuestionEvidencePlanner.rank("심폐소생술", pages).first().chunk.pageNumber)
        assertEquals(27, QuestionEvidencePlanner.rank("산소측정법 알려줘", pages).first().chunk.pageNumber)
        assertTrue(QuestionEvidencePlanner.rank("심폐소생술", pages).any { it.chunk.pageNumber == 46 })
    }

    private fun page(number: Int, body: String) = KnowledgeChunk(
        id = number.toLong(),
        documentId = "guide",
        documentTitle = "안전작업 가이드",
        pageNumber = number,
        body = body,
        searchableText = body.lowercase(),
    )
}
