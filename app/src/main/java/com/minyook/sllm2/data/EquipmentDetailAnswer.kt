/* 소스 안내
 * 명시한 장비와 수량/설명 속성에 맞는 원문 절을 선택한다. HybridRetrieval이 호출하며 미확인 수량을 모델 추측으로 채우지 않는다.
 */
package com.minyook.sllm2.data

/** Complete, source-backed answers for named equipment and count questions.
 * No model guesses a procurement quantity; missing attributes are not missing topics. */
internal class EquipmentDetailAnswer(chunks: List<KnowledgeChunk>) {
    private data class Section(val chunk: KnowledgeChunk, val units: List<EvidencePassage>) {
        val heading get() = units.first().section
        val text get() = units.joinToString(" ") { it.text }
    }
    private val sections by lazy {
        chunks.filterNot { Regex("┃사례|\\[답변|사망\\s*\\d+명|\\(\\s*\\)").containsMatchIn(it.body) }
            .flatMap { chunk -> FocusedEvidence.units(chunk).groupBy { it.section }.values.map { Section(chunk, it) } }
    }
    private val countValue = Regex("(?:\\d+(?:\\.\\d+)?|한|두|세|네|다섯|여섯)\\s*(?:개(?!소|월)|대(?!피|상)|벌|세트|본|가닥)(?:\\s*(?:이상|이하|씩|미만|초과))?")
    private val provision = Regex("준비|갖추|갖춰|확보|비치|지급|필요|설치|착용|사용|배치|구비|수량")
    private fun countFor(target: QuestionFocus.Entity, unit: EvidencePassage): Boolean {
        if (!provision.containsMatchIn(unit.text) || Regex("예시|예를|예컨대").containsMatchIn(unit.text)) return false
        return target.forms.findAll(unit.text).any { entity -> countValue.findAll(unit.text).any { count ->
            val between = when {
                entity.range.last < count.range.first -> unit.text.substring(entity.range.last + 1, count.range.first).takeIf { it.length <= 24 }
                count.range.last < entity.range.first -> unit.text.substring(count.range.last + 1, entity.range.first).takeIf { it.length <= 12 }
                else -> null
            }
            between != null && QuestionFocus.targets(between).isEmpty() && !Regex("[.;。]|예시|예를").containsMatchIn(between)
        } }
    }

    fun resolve(question: String): SearchResolution? {
        val targets = QuestionFocus.targets(question)
        if (targets.isEmpty()) return null
        val quantity = QuestionFocus.quantity(question)
        if (quantity && !QuestionFocus.simpleQuantity(question)) return null
        if (!quantity && !QuestionFocus.simpleDescription(question)) return null
        val selected = targets.associateWith { target ->
            val related = sections.filter { section -> section.units.any { target.matches(it.text) } }
            val matched = related.filter { target.matches(it.heading) }
            // Search all matching sections for quantity clauses, not just the top hit.
            val counted = related.filter { section -> section.units.any { countFor(target, it) } }
            (if (quantity && counted.isNotEmpty()) counted else matched.sortedWith(compareBy<Section> { "점검표" in it.chunk.sourceKind }.thenBy { it.text.length }).take(1))
        }
        if (selected.values.all { it.isEmpty() }) return null
        val missing = selected.filterValues { it.isEmpty() }.keys
        val sources = selected.values.flatten().groupBy { it.chunk.id }.values.map { group ->
            RetrievedChunk(group.first().chunk, 1.0, group.flatMap { it.units }.distinctBy { it.order }.sortedBy { it.order })
        }
        val unconfirmedQuantity = quantity && selected.any { (target, list) -> list.none { section -> section.units.any { countFor(target, it) } } }
        val body = if (quantity) {
            val withCount = selected.filter { (target, list) -> list.any { section -> section.units.any { countFor(target, it) } } }.keys
            buildString {
                if (withCount.isEmpty()) append("**관련 장비 항목에서는 ${targets.joinToString("·") { it.name }}의 고정 준비 수량을 확인하지 못했어요.**")
                else append("**${withCount.joinToString("·") { it.name }}의 수량과 관련된 조건이 문서에 있어요.** 아래 원문의 적용 조건을 함께 확인해 주세요.")
                append("\n\n")
                (if (withCount.isEmpty()) targets.filter { it in missing } else targets).forEach { target -> append("- **${target.name}**: " + when {
                    target in missing -> "해당 장비의 관련 항목을 이번 확인에서 찾지 못했어요."
                    target in withCount -> "아래 원문에 나온 수량·적용 조건을 확인해 주세요."
                    else -> "준비해야 할 고정 개수나 인원별 수량 기준은 관련 항목에서 확인되지 않아요."
                } + "\n") }
                val condition = QuestionFocus.people(question) ?: QuestionFocus.statedCount(question)
                if (condition != null) append("\n말씀하신 **$condition** 조건만으로 필요한 장비 수량을 계산하거나 충분하다고 단정할 수는 없어요.\n")
                append("\n장비 구비·착용 의무가 정해져 있다는 뜻과 고정 개수가 정해져 있다는 뜻은 달라요. 미확인 수량은 현장 작업·구조 계획과 해당 장비의 사용 조건을 확인해야 해요.")
                append("\n\n### 문서에서 확인한 내용\n\n")
                // Quote the actual provision paragraphs for this attribute,
                // not an unrelated fall-risk introduction on every follow-up.
                // Entire paragraphs and explicit exceptions remain together;
                // the citation still opens the complete original page.
                val quotes = sources.map { source ->
                    val units = FocusedEvidence.selected(source)
                    val actions = units.filter { unit -> targets.any { it.matches(unit.text) } && provision.containsMatchIn(unit.text) }
                    val groups = actions.map { it.group }.toSet()
                    val selectedUnits = if (units.any { Regex("다만|예외|않|금지|이 경우").containsMatchIn(it.text) }) units else units.filter { it.group in groups }.ifEmpty { units }
                    source.copy(evidence = selectedUnits.map { it.copy(section = "") })
                }
                append(FocusedEvidence.format(quotes))
            }
        } else {
            val purpose = selected.flatMap { (target, sections) ->
                sections.flatMap { it.units }.filter { target.matches(it.text) && Regex("위해|위한|대비|예방|착용|갖추|사용|금지").containsMatchIn(it.text) }
                    .distinctBy { it.text }.take(1).map { target.name to it.text }
            }
            val lead = purpose.groupBy { it.second }.map { (text, items) -> "- **${items.joinToString("·") { it.first }}**: ${AnswerMarkdown.evidenceLines(text).joinToString(" ").removePrefix("- ")}" }.joinToString("\n\n")
            val used = purpose.map { it.second }.toSet()
            val remaining = sources.map { source -> source.copy(evidence = FocusedEvidence.selected(source).filter { it.text !in used }.map { it.copy(section = "") }) }.filter { FocusedEvidence.selected(it).isNotEmpty() }
            val introduction = if (QuestionFocus.comparison(question) && purpose.map { it.second }.distinct().size == 1)
                "**확인한 항목에서는 ${targets.joinToString("·") { it.name }}를 함께 설명하고 있어요. 이 항목만으로 각각의 구조·작동 차이까지 구분하기는 어려워요.**"
                else if (QuestionFocus.comparison(question)) "문서에서 확인되는 각 장비의 역할을 비교하면 다음과 같아요."
                else "문서에서 설명하는 각 장비의 용도와 사용 조건이에요."
            introduction + "\n\n" + lead +
                missing.joinToString("") { "\n\n- **${it.name}**: 관련 항목을 이번 확인에서 찾지 못했어요." } +
                if (remaining.isEmpty()) "" else "\n\n### 사용 조건과 주의사항\n\n" + FocusedEvidence.format(remaining)
        }
        return SearchResolution(question, sources, if (missing.isEmpty()) "answered" else "partial", message = "", method = if (quantity) "equipment-quantity" else "equipment-detail", searchComplete = true, answerBody = body, generalQuestion = question.takeIf { unconfirmedQuantity })
    }
}
