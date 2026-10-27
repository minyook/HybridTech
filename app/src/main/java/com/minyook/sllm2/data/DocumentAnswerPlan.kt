package com.minyook.sllm2.data

import org.json.JSONObject

data class PlannedPassage(val id: String, val sourceId: Long, val text: String, val section: String)
data class DocumentAnswerPlan(val question: String, val body: String, val evidence: List<PlannedPassage>, val sources: List<RetrievedChunk>) {
    val supplementRequested = evidence.isNotEmpty() && Regex("왜|이유|차이|비교|뜻|의미|쉽게|풀어").containsMatchIn(question) && !Regex("승인|작업\\s*가능|들어가도|출입해도").containsMatchIn(question)
    val sourceLabels = sources.associate { it.chunk.id to "${it.chunk.sourceKind} · ${it.chunk.pageNumber}쪽" }
    val sourceIds = sources.map { it.chunk.id }.distinct()
    fun context(): String = evidence.joinToString("\n\n") { "[${it.id}] ${it.section}\n${it.text}" }
    companion object {
        fun create(question: String, sources: List<RetrievedChunk>, knowledgeReady: Boolean = true): DocumentAnswerPlan {
            val evidence = sources.flatMap { source -> FocusedEvidence.selected(source).map { unit -> PlannedPassage("e${source.chunk.id}-${unit.order}", source.chunk.id, unit.text, unit.section) } }
            val body = if (!knowledgeReady) "문서 검색 데이터를 준비하고 있어요. 준비가 끝난 뒤 다시 질문해 주세요." else FocusedEvidence.format(sources)
            val figure = evidence.any { Regex("①~③|\\bX\\b|도식|그림").containsMatchIn(it.text) }
            return DocumentAnswerPlan(question, body + if (figure) "\n\n> 도식의 위치·기호가 포함된 절차는 아래 출처의 원문 그림과 함께 확인해 주세요." else "", evidence, sources)
        }
    }
}

/** Optional additions never replace, rewrite or stream over the selected source answer. */
object AnswerSupplementVerifier {
    fun fromJson(raw: String, plan: DocumentAnswerPlan): String = try {
        val json = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val ids = json.getJSONArray("evidenceIds")
        accept(json.getString("text"), (0 until ids.length()).map { ids.getString(it) }, plan)
    } catch (_: Exception) { "" }
    fun accept(text: String, evidenceIds: List<String>, plan: DocumentAnswerPlan): String {
        val value = text.trim()
        if (value.isBlank() || value.length > 600 || evidenceIds.isEmpty()) return ""
        val units = evidenceIds.map { id -> plan.evidence.find { it.id == id } ?: return "" }
        if (Regex("[\\n#<>]|(?:작업|출입|진입)[^.!?\\n]{0,12}(?:가능|허용|해도|할 수|할수|하셔도)|승인|안전합니다|문제없|괜찮|생략해도|만\\s*(?:하면|지키면|충족하면)|무조건|항상\\s*안전|(?:문서|자료).{0,30}(?:없습니다|없어요|찾을 수 없)").containsMatchIn(value)) return ""
        val reference = units.joinToString(" ") { it.text }
        val source = EvidenceLanguage.compact(reference); val normalized = EvidenceLanguage.compact(value)
        if (normalized.isBlank() || normalized in EvidenceLanguage.compact(plan.body)) return ""
        val numbers = Regex("\\d+(?:\\.\\d+)?(?:\\s*[~～]\\s*\\d+(?:\\.\\d+)?)?\\s*(?:%|ppm|cm|mmAq|m3/min|m\\b|초|분|시간|회|개소)?(?:\\s*(?:이상|미만|이하|초과))?", RegexOption.IGNORE_CASE)
        val known = numbers.findAll(reference).map { EvidenceLanguage.compact(it.value) }.toSet()
        if (numbers.findAll(value).any { EvidenceLanguage.compact(it.value) !in known }) return ""
        if (units.any { Regex("경우|때|다면|없이|않|금지|미만|이상|이하|초과").containsMatchIn(it.text) } && !Regex("경우|때|다면|않|금지|미만|이상|이하|초과|까지").containsMatchIn(value)) return ""
        val grams = normalized.windowed(2).toSet()
        val ratio = grams.count { it in source }.toDouble() / grams.size.coerceAtLeast(1)
        return if (ratio < .6 || ratio > .95) "" else value
    }
    fun compose(plan: DocumentAnswerPlan, supplement: String) = if (supplement.isBlank()) plan.body else plan.body + "\n\n" + supplement
}
