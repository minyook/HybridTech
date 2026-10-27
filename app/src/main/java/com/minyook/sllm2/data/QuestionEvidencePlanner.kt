package com.minyook.sllm2.data

/** Shared general query scope for page retrieval and source validation. */
internal object QuestionEvidencePlanner {
    fun aspects(question: String) = FocusedRetriever.aspects(question)
    fun rank(aspect: String, chunks: List<KnowledgeChunk>) = FocusedRetriever(chunks).rank(aspect)
}
