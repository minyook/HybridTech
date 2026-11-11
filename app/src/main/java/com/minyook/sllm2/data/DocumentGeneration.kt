package com.minyook.sllm2.data

/** Checks the generated answer before showing it as supported by the linked documents. */
internal object DocumentGeneration {
    private val numbered = Regex("""^(\d+)\.\s+(.+)$""")
    private val sentenceEnd = Regex("""\.(?=\s|$)|[!?。！？]""")
    private val quantities = Regex(
        """\d+(?:[,.]\d+)*(?:\s*[~～–-]\s*\d+(?:[,.]\d+)*)?\s*(?:%|ppm|ppb|mg/m[³3]|m[³3]/min|시간|분|초|미터|미리|mm|cm|m\b|회|개소)(?:\s*(?:이상|이하|미만|초과|이내))?""",
        RegexOption.IGNORE_CASE,
    )
    private val unsafeConclusion = Regex(
        """(?:작업|출입|진입|재개)(?:은|는|이|가|을|를)?\s*(?:가능합니다|허용됩니다|해도 됩니다|할 수 있습니다)|안전합니다|문제없습니다|생략해도 됩니다""",
    )
    private val asksForQuantity = Regex(
        """(?:농도|수치|노출|허용)\s*(?:기준|범위|상한|하한|한계)|(?:기준|범위|상한|하한|한계)(?:은|는|이|가)?\s*(?:얼마|몇)|몇\s*(?:%|ppm|분|초|시간|회|개|m|미터)|얼마나\s*(?:높|낮|오래|멀)|수량|개수""",
        RegexOption.IGNORE_CASE,
    )
    private val condition = Regex("""않|금지|경우|다면|(?:일|할|될|었을|졌을)\s*때|이상|이하|미만|초과|보류""")
    private val internalCitation = Regex("""\be\d+-\d+\b""")
    private val afterWork = Regex("""작업\s*(?:전\s*[·ㆍ/,]\s*중\s*[·ㆍ/,]\s*후|(?:완료|종료)\s*후|후)(?!속)""")
    private val permitChanges = Regex("""갱신|재발급|재승인""")
    private val answerEnding = Regex("""(?:합니다|됩니다|입니다|습니다|세요|십시오|해야\s*해요|해야\s*돼요)(?=[.!?。！？\s*]|$)""")

    fun accept(raw: String, plan: DocumentAnswerPlan, limitReached: Boolean): String? {
        val answer = raw.trim()
        if (limitReached || answer.isBlank() || unsafeConclusion.containsMatchIn(answer) || internalCitation.containsMatchIn(answer) ||
            QuestionAnswerVerifier.contradictsSources(plan.question, answer, plan.sources)
        ) return null

        val reference = plan.evidence.joinToString("\n") { it.text }
        if (afterWork.containsMatchIn(answer) && !afterWork.containsMatchIn(reference)) return null
        if (permitChanges.findAll(answer).any { it.value !in reference }) return null
        val answerQuantities = quantitiesIn(answer)
        if (!quantitiesIn(reference).containsAll(answerQuantities)) return null
        if (asksForQuantity.containsMatchIn(plan.question) && !answerQuantities.containsAll(quantitiesIn(plan.body))) return null

        val count = GeneralKnowledgeAnswer.numberedItemCount(plan.question)
        if (count == null) {
            return answer.takeIf { answerEnding.containsMatchIn(answer) && overlaps(answer, reference) }
        }

        val rows = answer.lines().map(String::trim).filter(String::isNotEmpty)
        if (rows.size != count) return null
        val reviewedRows = if (plan.directAnswer) plan.body.lines().mapNotNull { numbered.matchEntire(it)?.groupValues?.get(2) } else emptyList()
        val seen = mutableSetOf<String>()
        for ((index, row) in rows.withIndex()) {
            val match = numbered.matchEntire(row) ?: return null
            val sentence = match.groupValues[2]
            if (match.groupValues[1].toIntOrNull() != index + 1 || sentenceEnd.findAll(sentence).count() != 1 ||
                sentence.last() !in ".!?。！？" || !seen.add(EvidenceLanguage.compact(sentence))
            ) return null
            val expected = reviewedRows.getOrNull(index)
            if (!overlaps(sentence, expected ?: reference) ||
                (expected != null && condition.containsMatchIn(expected) && !condition.containsMatchIn(sentence))
            ) return null
        }
        return rows.joinToString("\n")
    }

    private fun quantitiesIn(text: String): Set<String> = quantities.findAll(text.replace("*", "")).map { match ->
        match.value.lowercase().replace(Regex("""[\s,]"""), "").replace('～', '~').replace('–', '~').replace('-', '~')
    }.toSet()

    private fun overlaps(answer: String, reference: String): Boolean {
        val grams = EvidenceLanguage.compact(answer).windowed(2).toSet()
        val source = EvidenceLanguage.compact(reference)
        return grams.isNotEmpty() && grams.count { it in source } >= grams.size * .45
    }
}
