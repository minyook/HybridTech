package com.minyook.sllm2.data

import kotlin.math.sqrt

data class RetrievedChunk(
    val chunk: KnowledgeChunk,
    val score: Double,
)

/**
 * Original local RAG retrieval path.
 *
 * The full PDF chunks remain in ObjectBox. A question is ranked with both
 * local-vector similarity and lexical overlap, then the highest-ranked source
 * passages are passed intact to the local model. Deliberately do not apply a
 * second sentence filter here: PDF procedures and tables often carry their
 * necessary condition in a neighbouring line of the same chunk.
 */
class KnowledgeRepository {
    private val box = ObjectBoxStore.store.boxFor(KnowledgeChunk::class.java)

    fun retrieve(question: String, limit: Int = 4): List<RetrievedChunk> {
        if (question.isBlank() || box.count() == 0L) return emptyList()
        val safeLimit = limit.coerceAtLeast(1)
        val queryVector = OfflineHashEmbedding.embed(question)
        val vectorScores = mutableMapOf<Long, Double>()
        box.query(KnowledgeChunk_.embedding.nearestNeighbors(queryVector, safeLimit * 4)).build().use { query ->
            query.findWithScores().forEach { result ->
                // ObjectBox cosine distance is 0.0 (same direction) to 2.0 (opposite).
                vectorScores[result.get().id] = 1.0 - (result.score / 2.0)
            }
        }

        val terms = question.lowercase()
            .split(Regex("[^0-9a-z가-힣]+"))
            .filter { it.length > 1 }
            .toSet()
        return box.all
            .map { chunk ->
                val semantic = vectorScores[chunk.id] ?: 0.0
                val lexical = lexicalScore(chunk.searchableText, terms)
                RetrievedChunk(chunk, semantic * 0.72 + lexical * 0.28)
            }
            .sortedByDescending { it.score }
            .take(safeLimit)
    }

    /** Show the actual retrieved source passages when the local model is unavailable. */
    fun answerWithoutModel(
        question: String,
        maximumSources: Int = 2,
        bodyCharacterLimit: Int = 360,
    ): String {
        val sources = retrieve(question, limit = maximumSources.coerceIn(1, 4))
        if (sources.isEmpty()) {
            return "## 지식베이스 준비 중\n\n제공 문서를 기기에 정리하고 있습니다. 잠시 후 다시 질문해 주세요."
        }
        return buildString {
            append("## 관련 문서 근거\n\n")
            sources.forEachIndexed { index, item ->
                append("### ${index + 1}. ${item.chunk.heading}\n")
                append(item.chunk.body.take(bodyCharacterLimit.coerceIn(120, 600)).trim())
                append("\n\n> 출처: ${item.chunk.documentTitle} ${item.chunk.pageNumber}쪽\n\n")
            }
        }.trim()
    }

    /** The same intact evidence blocks used by the original Gemma RAG prompt. */
    fun contextForModel(question: String): String = retrieve(question, limit = 2).joinToString("\n\n") { item ->
        "[${item.chunk.documentTitle} ${item.chunk.pageNumber}쪽]\n${item.chunk.body.take(700)}"
    }

    fun documentCount(): Int = box.count().toInt()

    private fun lexicalScore(text: String, terms: Set<String>): Double {
        if (terms.isEmpty()) return 0.0
        val hits = terms.count { term -> text.contains(term) }
        return hits / sqrt(terms.size.toDouble())
    }
}
