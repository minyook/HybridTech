package com.minyook.sllm2.data

import java.util.Locale
import kotlin.math.min

/** Keeps separate subjects in a question from competing for the same source slots. */
internal object QuestionEvidencePlanner {
    private val separator = Regex("(?<![결효성경교학분])(?<=[0-9a-z가-힣])과\\s+|(?<=[0-9a-z가-힣])와\\s+|\\s+(?:그리고|및)\\s+|[,，]")
    private val nonWord = Regex("[^0-9a-z가-힣]+")
    private val trailingParticle = Regex("(에서|으로|은|는|이|가|을|를|의|에|로|도|만)$")
    private val requestWords = setOf("알려줘", "알려주세요", "설명해줘", "설명해주세요", "말해줘", "말해주세요", "방법은", "어떻게")

    fun aspects(question: String): List<String> = separator.split(question.trim())
        .map(String::trim)
        .filter(String::isNotBlank)
        .ifEmpty { listOf(question.trim()) }

    fun rank(aspect: String, chunks: List<KnowledgeChunk>): List<RetrievedChunk> {
        val terms = terms(aspect)
        if (terms.isEmpty()) return emptyList()
        return chunks.mapNotNull { chunk ->
            val text = chunk.searchableText.lowercase(Locale.KOREAN)
            val matched = terms.filter { text.contains(it) }
            if (matched.isEmpty()) return@mapNotNull null
            val coverage = matched.size.toDouble() / terms.size
            val repetitions = matched.sumOf { min(occurrences(text, it), 3) }
            val compactText = text.replace(nonWord, "")
            val phraseBonus = terms.windowed(2).sumOf { pair ->
                if (compactText.contains(pair[0] + pair[1])) {
                    if (pair[1] == "방법") 4.0 else 2.0
                } else 0.0
            }
            RetrievedChunk(chunk, coverage * 10.0 + repetitions * 0.2 + phraseBonus)
        }.sortedWith(compareByDescending<RetrievedChunk> { it.score }
            .thenByDescending { it.chunk.body.length }
            .thenBy { it.chunk.pageNumber })
    }

    private fun terms(aspect: String): List<String> = aspect.lowercase(Locale.KOREAN)
        // "산소측정법" and "산소·유해가스 농도 측정방법" should share the words
        // 산소, 측정, 방법 even when the question writes them as one compound.
        .replace("측정법", " 측정 방법")
        .split(nonWord)
        .map { it.replace(trailingParticle, "") }
        .filter { it.length >= 2 && it !in requestWords }
        .distinct()

    private fun occurrences(text: String, term: String): Int {
        var count = 0
        var offset = 0
        while (true) {
            val found = text.indexOf(term, offset)
            if (found < 0) return count
            count++
            offset = found + term.length
        }
    }
}
