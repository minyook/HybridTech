/* 소스 안내
 * ObjectBox 조회와 재사용 검색 인덱스를 조정한다. ViewModel의 질문을 QuestionSearchCoordinator로 전달한다.
 */
package com.minyook.sllm2.data


data class RetrievedChunk(
    val chunk: KnowledgeChunk,
    val score: Double,
    val evidence: List<EvidencePassage>? = null,
)

data class KnowledgeDocument(
    val id: String,
    val title: String,
    val chunkCount: Int,
)

/**
 * ObjectBox 문서와 질문 검색 계층을 연결하는 저장소 어댑터.
 * ViewModel의 searchQuestion 호출을 받아 HybridRetrieval 캐시를 준비하고 QuestionSearchCoordinator로 넘긴다.
 * 결과의 원본 문서 ID/쪽/선택 문단은 DocumentAnswerPlan과 PDF 출처 열기에 사용한다.
 * 문서 내용이 바뀌면 인덱스를 재구성하며 표시용 정리로 DB의 원문을 덮어쓰지 않는다.
 */
class KnowledgeRepository {
    private val box = ObjectBoxStore.store.boxFor(KnowledgeChunk::class.java)

    private var indexedChunks = emptyList<KnowledgeChunk>()
    private var index: FocusedRetriever? = null
    private var hybrid: HybridRetrieval? = null
    private var hybridJson: String? = null
    @Synchronized private fun retriever(): FocusedRetriever {
        val chunks = box.all
        val unchanged = chunks.size == indexedChunks.size && chunks.zip(indexedChunks).all { (fresh, old) -> fresh.id == old.id && fresh.body == old.body && fresh.heading == old.heading && fresh.documentId == old.documentId && fresh.documentTitle == old.documentTitle && fresh.sourceKind == old.sourceKind && fresh.pageNumber == old.pageNumber }
        if (index == null || !unchanged) { indexedChunks = chunks; index = FocusedRetriever(chunks); hybrid=null }
        return index!!
    }
    fun retrieveForQuestion(question: String): List<RetrievedChunk> = if (question.isBlank()) emptyList() else retriever().retrieve(question)
    @Synchronized private fun questionIndex(json: String): HybridRetrieval {
        retriever()
        if(hybrid==null||hybridJson!=json){hybrid=HybridRetrieval(indexedChunks,json);hybridJson=json}
        return hybrid!!
    }
    fun prepareQuestionIndex(semanticIndex:String) { questionIndex(semanticIndex) }
    fun resolveQuestion(question: String, semanticIndex: String, vector: FloatArray?): SearchResolution = questionIndex(semanticIndex).resolve(question, vector)
    /** 실제 앱 검색 진입점. retrieve와 달리 의미 재검토·모호성·검색 오류·일반 생성 허용을 함께 결정한다. */
    fun searchQuestion(question:String,semanticIndex:String,encode:(String)->FloatArray):SearchResolution = QuestionSearchCoordinator(questionIndex(semanticIndex),encode).search(question)
    fun searchQuestion(request:ConversationRequest,semanticIndex:String,encode:(String)->FloatArray):SearchResolution = QuestionSearchCoordinator(questionIndex(semanticIndex),encode).search(request)
    fun retrieve(question: String, limit: Int = 4): List<RetrievedChunk> = if (question.isBlank()) emptyList() else retriever().rank(question).take(limit.coerceAtLeast(1))
    fun answerWithoutModel(sources: List<RetrievedChunk>): String = FocusedEvidence.format(sources)
    fun contextForModel(sources: List<RetrievedChunk>): String = sources.joinToString("\n\n") { source ->
        "["+source.chunk.documentTitle+" "+source.chunk.pageNumber+"쪽]\n"+FocusedEvidence.selected(source).joinToString("\n") { it.text }
    }

    fun documentCount(): Int = box.count().toInt()

    fun documents(): List<KnowledgeDocument> = box.all
        .groupBy { it.documentId }
        .map { (id, chunks) -> KnowledgeDocument(id, chunks.first().documentTitle, chunks.size) }
        .sortedByDescending { it.chunkCount }

    fun chunk(id: Long): KnowledgeChunk? = box.get(id)

    fun chunksForDocument(documentId: String): List<KnowledgeChunk> = box.all
        .filter { it.documentId == documentId }
        .sortedWith(compareBy(KnowledgeChunk::pageNumber, KnowledgeChunk::chunkIndex))

}
