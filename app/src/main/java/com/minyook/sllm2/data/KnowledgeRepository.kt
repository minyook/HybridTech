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

/** ObjectBox pages stay unchanged; questions use only focused source-linked passages. */
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
    fun searchQuestion(question:String,semanticIndex:String,encode:(String)->FloatArray):SearchResolution = QuestionSearchCoordinator(questionIndex(semanticIndex),encode).search(question)
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
