package com.minyook.sllm2.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionAnswerVerifierTest {
    private val sources = listOf(
        source(45, "심폐소생술 절차와 흉부 압박"),
        source(27, "산소·유해가스 농도 측정방법"),
    )

    @Test
    fun rejectsAClaimThatOneOfTwoSourcedTopicsIsMissing() {
        val question = "심폐소생술과 산소측정법 알려줘"
        assertTrue(QuestionAnswerVerifier.contradictsSources(
            question,
            "산소 측정 방법은 다음과 같습니다. 심폐소생술은 문서에서 찾을 수 없습니다.",
            sources,
        ))
        assertTrue(QuestionAnswerVerifier.contradictsSources(
            question,
            "산소 측정 방법은 다음과 같습니다.",
            sources,
        ))
        assertFalse(QuestionAnswerVerifier.contradictsSources(
            question,
            "심폐소생술은 흉부 압박을 설명합니다. 산소 측정은 장비를 확인합니다.",
            sources,
        ))
    }

    private fun source(page: Int, body: String) = RetrievedChunk(
        KnowledgeChunk(id = page.toLong(), pageNumber = page, body = body, searchableText = body.lowercase()),
        1.0,
    )
}
