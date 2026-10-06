package com.minyook.sllm2.data

/** Rejects a generated answer when it omits a documented CPR procedure or denies selected evidence. */
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
