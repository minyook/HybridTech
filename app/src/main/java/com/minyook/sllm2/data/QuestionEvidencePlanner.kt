/* 소스 안내
 * FocusedRetriever의 주제 분리·페이지 순위를 노출하는 기존 어댑터. 현재 전체 검색 진입점은 QuestionSearchCoordinator다.
 */
package com.minyook.sllm2.data

/**
 * 기존 페이지 검색/검증용의 얇은 순수 어댑터. FocusedRetriever의 주제 분리와 순위를 노출한다.
 * 현재 ViewModel의 실제 검색 진입점은 KnowledgeRepository.searchQuestion → QuestionSearchCoordinator다.
 * 이 이름을 DocumentAnswerPlan이나 전체 앱 검색 조정기로 혼동하지 않는다.
 */
internal object QuestionEvidencePlanner {
    fun aspects(question: String) = FocusedRetriever.aspects(question)
    fun rank(aspect: String, chunks: List<KnowledgeChunk>) = FocusedRetriever(chunks).rank(aspect)
}
