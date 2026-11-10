/* 소스 안내
 * 기존 답변 경로의 제한된 모순 검사와 회귀 대상. 현재 ViewModel의 일반/보충 검증기는 다른 객체이므로 호출 범위를 구분한다.
 */
package com.minyook.sllm2.data

/**
 * 기존 답변 경로의 제한된 모순 검사 유틸리티. 선택한 CPR 근거 누락/문서 부재 단정 등을 검사한다.
 * 현재 ViewModel의 선택적 문서 보충은 DocumentAnswerPlan의 AnswerSupplementVerifier,
 * 일반 생성은 GeneralKnowledgeAnswer를 사용한다. 이 객체가 모든 앱 답변을 검증하는 것은 아니다.
 */
internal object QuestionAnswerVerifier {
    private val missingDocumentClaim = Regex(
        "(?:문서|자료)[^.!?\\n]{0,40}(?:찾을 수 없|확인할 수 없|확인되지 않|없습니다|없어요)|(?:찾을 수 없|확인할 수 없|확인되지 않)[^.!?\\n]{0,40}(?:문서|자료)",
    )
    private val cprProcedure = Regex("압박|가슴을|가슴 중앙|흉부")

    fun contradictsSources(question: String, answer: String, sources: List<RetrievedChunk>): Boolean {
        val aspects = QuestionEvidencePlanner.aspects(question)
        if (sources.isEmpty()) return false
        val asksForCprProcedure = question.contains("심폐소생술") &&
            listOf("알려", "방법", "어떻게", "설명").any(question::contains)
        val hasCprSource = sources.any { it.chunk.searchableText.contains("심폐소생술") }
        if (asksForCprProcedure && hasCprSource && !cprProcedure.containsMatchIn(answer)) return true
        return aspects.size > 1 && sources.size >= aspects.size && missingDocumentClaim.containsMatchIn(answer)
    }
}
