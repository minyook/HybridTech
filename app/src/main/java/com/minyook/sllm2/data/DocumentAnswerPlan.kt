/* 소스 안내
 * 검색 결과를 본문·근거 ID·출처로 묶는다. 화면에 먼저 표시한 뒤 선택적 보충을 근거 계약으로 검사한다.
 */
package com.minyook.sllm2.data

import org.json.JSONObject

/** 화면 본문과 선택적 AI 보충이 공유하는 근거 단위. id는 보충 검증에, sourceId는 실제 PDF 출처 연결에 사용한다. */
data class PlannedPassage(val id: String, val sourceId: Long, val text: String, val section: String)

/**
 * 검색 결과에서 만들어 화면·음성·선택적 모델 보충이 공유하는 답변 계획.
 * create는 선택한 원문의 표시 본문과 근거 ID/출처를 함께 구성하고 context는 같은 근거를 모델용으로 직렬화한다.
 * ViewModel이 본문을 먼저 표시한 뒤 supplementRequested인 경우에만 보충 실행으로 넘어간다.
 * 근거 선택과 일반 생성 허용은 앞단 SearchResolution의 책임이며 이 계획 자체가 현장 승인 판단은 아니다.
 */
data class DocumentAnswerPlan(val question: String, val body: String, val evidence: List<PlannedPassage>, val sources: List<RetrievedChunk>, val directAnswer: Boolean = false) {
    // Source answers finish immediately. A reason/comparison question already
    // answered by the document must not spend seconds regenerating the same text.
    val supplementRequested = !directAnswer && evidence.isNotEmpty() && Regex("쉽게|풀어|AI\\s*(?:로|가)\\s*(?:설명|보충)",RegexOption.IGNORE_CASE).containsMatchIn(question) && !Regex("승인|작업\\s*가능|들어가도|출입해도").containsMatchIn(question)
    val sourceLabels = sources.associate { it.chunk.id to "${it.chunk.sourceKind} · ${it.chunk.pageNumber}쪽" }
    val sourceIds = sources.map { it.chunk.id }.distinct()
    /** 선택한 근거만 ID와 함께 모델에 보낸다. 페이지 전체나 다른 절의 내용은 새로 붙이지 않는다. */
    fun context(): String = evidence.joinToString("\n\n") { passage ->
        val title=sources.firstOrNull { it.chunk.id==passage.sourceId }?.chunk?.documentTitle.orEmpty()
        "[${passage.id}] $title · ${passage.section}\n${passage.text}"
    }
    companion object {
        fun create(question: String, resolution: SearchResolution, knowledgeReady: Boolean = true): DocumentAnswerPlan {
            val plan = create(question, resolution.sources, knowledgeReady)
            return if (knowledgeReady && resolution.answerBody != null) plan.copy(body = resolution.answerBody, directAnswer = true) else plan
        }
        fun create(question: String, sources: List<RetrievedChunk>, knowledgeReady: Boolean = true): DocumentAnswerPlan {
            val evidence = sources.flatMap { source -> FocusedEvidence.selected(source).map { unit -> PlannedPassage("e${source.chunk.id}-${unit.order}", source.chunk.id, unit.text, unit.section) } }
            val body = if (!knowledgeReady) "문서 검색 데이터를 준비하고 있어요. 준비가 끝난 뒤 다시 질문해 주세요." else FocusedEvidence.format(sources)
            val figure = evidence.any { Regex("①~③|\\bX\\b|도식|그림").containsMatchIn(it.text) }
            return DocumentAnswerPlan(question, body + if (figure) "\n\n> 도식의 위치·기호가 포함된 절차는 아래 출처의 원문 그림과 함께 확인해 주세요." else "", evidence, sources)
        }
    }
}

/**
 * LocalModelRuntime의 선택적 문서 보충 JSON을 검사하는 경계.
 * 모델이 반환한 evidenceIds가 현재 계획에 존재하는지, 숫자·조건·중복/작업 허용 단정이 없는지 확인한다.
 * 통과하면 짧은 보충 문자열, 실패하면 빈 문자열을 반환한다. ViewModel은 원문 본문을 유지한 채 뒤에만 추가한다.
 */
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
