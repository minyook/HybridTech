package com.minyook.sllm2.data

import kotlin.math.ln
import kotlin.math.sqrt

data class EvidencePassage(val text: String, val section: String, val order: Int, val group: Int)

/** Source layout is cleaned for display only. ObjectBox pages and PDF citations stay unchanged. */
object FocusedEvidence {
    fun pageBody(chunk: KnowledgeChunk): String = chunk.body
        .replace(Regex("^제1장\\s*질식과\\s*밀폐공간제2장[\\s\\S]*?안전작업\\s*가이드\\s*\\d+\\s*"), "")
        .replace(Regex("^\\d+\\s+"), "").replace(Regex("<[^<>\\n]{1,80}>"), "\n")
        .replace(Regex("심\\s+폐\\s+소\\s+생\\s+술"), "심폐소생술")
        .replace(Regex("\\s+\\d+\\s*작업시\\s*안전보건\\s*조치\\s*$"), "")
        .replace(Regex("\\s+심폐소생술\\s+\\d+\\s*$"), "").trim()

    fun units(chunk: KnowledgeChunk): List<EvidencePassage> {
        val body = pageBody(chunk)
        val structured = if (Regex("^순\\s*서\\s*실\\s*시\\s*방\\s*법").containsMatchIn(body)) body.replace(Regex("([.!?])\\s+([^.!?•▶\\n]{1,32})\\s*(?=•)"), "$1\n$2\n") else body
        val lines = structured.replace(Regex("([▶•◆※☞])"), "\n$1")
            .replace(Regex("\\s+([①-⑳])(?=\\s)"), "\n$1").replace(Regex("\\s+-\\s+"), "\n- ")
            .replace(Regex("\\s+(?=\\([가-힣]{2,8}\\))"), "\n")
            .split(Regex("\\n+")).flatMapIndexed { group, block ->
                block.split(Regex("(?<!ex\\.)(?<!e\\.g\\.)(?<=[.!?])\\s+")).map { it.trim() to group }
            }.filter { it.first.isNotBlank() }
        val units = mutableListOf<EvidencePassage>(); var section = ""
        lines.forEachIndexed { index, (line, group) ->
            val marked = Regex("^[•◆※☞①-⑳*\\-]").containsMatchIn(line)
            val text = line.replace(Regex("^[▶•◆※☞*\\-]\\s*"), "")
                .replace(Regex("^산업안전보건기준에\\s*관한\\s*규칙\\s*제\\d+조(?:의\\d+)?(?:\\s*제\\d+항)?\\s*"), "").trim()
            if (text.isBlank()) return@forEachIndexed
            val followedByItem = Regex("^[•◆※☞①-⑳*\\-]").containsMatchIn(lines.getOrNull(index + 1)?.first.orEmpty())
            if (followedByItem && text.length < 55 && !Regex("[:：>]").containsMatchIn(text) &&
                (line.startsWith("▶") || (!marked && !Regex("[.!?。]").containsMatchIn(text) && !Regex("이상|미만|이하|초과").containsMatchIn(text)))) {
                section = text.replace(Regex("^순\\s*서\\s*실\\s*시\\s*방\\s*법\\s*"), "")
            } else units.add(EvidencePassage(text, section, units.size, group))
        }
        return units.ifEmpty { if (body.isBlank()) emptyList() else listOf(EvidencePassage(body, "", 0, 0)) }
    }

    fun selected(source: RetrievedChunk) = source.evidence ?: units(source.chunk)
    fun format(sources: List<RetrievedChunk>): String {
        if (sources.isEmpty()) return "제공 문서에서 질문에 직접 답할 근거를 찾지 못했어요. 문서에 있는 용어나 확인할 조건을 조금 더 구체적으로 말씀해 주세요."
        val seen = mutableSetOf<String>()
        return sources.map { source ->
            var section = ""
            buildList {
                selected(source).forEach { unit ->
                    val key = unit.text.replace(Regex("\\s+"), " ")
                    if (!seen.add(key)) return@forEach
                    if (unit.section.isNotBlank() && unit.section != section) { section = unit.section; add("**${unit.section}**") }
                    val visible = unit.text.replace(Regex("\\d+(?:\\.\\d+)?(?:\\s*[~～]\\s*\\d+(?:\\.\\d+)?)?\\s*(?:%|ppm|cm|mmAq|m3/min|미터|센티미터|초|분|시간|회|개소)(?:\\s*(?:이상|미만|이하|초과))?", RegexOption.IGNORE_CASE)) { "**${it.value}**" }
                    add("- $visible")
                }
            }.joinToString("\n\n")
        }.filter(String::isNotBlank).joinToString("\n\n")
    }
}

/** IDF + lexical coverage + local co-occurrence; hash-vector collisions cannot create citations. */
internal class FocusedRetriever(chunks: List<KnowledgeChunk>) {
    private data class Page(val chunk: KnowledgeChunk, val body: String, val text: String, val opening: String, val units: List<EvidencePassage>) {
        val groups = units.groupBy { it.group }.values.toList()
        val procedure = Regex("^순\\s*서\\s*실\\s*시\\s*방\\s*법").containsMatchIn(body)
    }
    private val pages = chunks.map { val body = FocusedEvidence.pageBody(it); Page(it, body, EvidenceLanguage.searchable(body), EvidenceLanguage.searchable(body.take(180)), FocusedEvidence.units(it)) }
    private data class Profile(val tokens: List<String>, val weights: List<Double>, val focus: List<String>) { val total = weights.sum() }
    private fun profile(question: String): Profile {
        val tokens = EvidenceLanguage.terms(question).map { token ->
            if (token in EvidenceLanguage.vocabulary) token else (2 until token.length).firstOrNull { length ->
                Regex("^(?:하|해|했|할|되|돼|이|은|는|을|를|의|에|로|과|와|만|마다|여야|까지|처럼|인가|야|어|었|졌|용|기)").containsMatchIn(token.drop(length)) && pages.any { token.take(length) in it.text }
            }?.let { token.take(it) } ?: token
        }.distinct()
        val frequencies = tokens.map { term -> pages.count { term in it.text } }
        val weights = tokens.mapIndexed { i, term -> ln(1 + pages.size.toDouble() / (1 + frequencies[i])) * if (term in EvidenceLanguage.generic) .25 else 1.0 }
        val specific = tokens.filterIndexed { i, term -> frequencies[i] > 0 && term !in EvidenceLanguage.generic }
        val rarest = specific.minOfOrNull { frequencies[tokens.indexOf(it)] } ?: 0
        return Profile(tokens, weights, specific.filter { frequencies[tokens.indexOf(it)] <= maxOf(5.0, rarest * 1.5) })
    }
    private fun passageScore(units: List<EvidencePassage>, profile: Profile): Double {
        val body = units.joinToString(" ") { it.text }; val local = EvidenceLanguage.searchable(body)
        val heading = EvidenceLanguage.compact(units.firstOrNull()?.section.orEmpty())
        return profile.tokens.indices.sumOf { i -> profile.weights[i] * when { profile.tokens[i] in local -> 1.0; profile.tokens[i] in heading -> .35; else -> 0.0 } } / sqrt(1 + EvidenceLanguage.compact(body).length / 350.0)
    }
    fun rank(question: String): List<RetrievedChunk> {
        val profile = profile(question); if (profile.tokens.isEmpty() || profile.total == 0.0) return emptyList()
        val subjects = profile.tokens.filter { it !in EvidenceLanguage.generic }
        return pages.mapNotNull { page ->
            val text = page.text
            if (profile.tokens.none { it in text } || (subjects.isNotEmpty() && subjects.none { it in text }) || (profile.focus.isNotEmpty() && profile.focus.none { it in text })) return@mapNotNull null
            val coverage = profile.tokens.indices.sumOf { if (profile.tokens[it] in text) profile.weights[it] else 0.0 } / profile.total
            if (coverage < .52) return@mapNotNull null
            var score = 0.0
            profile.tokens.forEachIndexed { i, token ->
                val occurrences = text.split(token).size - 1
                if (occurrences > 0) {
                    score += profile.weights[i] * (occurrences / (occurrences + .8 * (.3 + .7 * text.length / 700.0)) + if (token in page.opening) .7 else 0.0)
                    if (i > 0 && Regex(Regex.escape(profile.tokens[i - 1]) + "[가-힣]{0,8}" + Regex.escape(token)).containsMatchIn(text)) score += (profile.weights[i - 1] + profile.weights[i]) * .7
                }
            }
            score = score * .8 + (page.groups.maxOfOrNull { passageScore(it, profile) } ?: 0.0) * .8
            if (Regex("기준|범위|수치|몇|얼마").containsMatchIn(question) && Regex("적정\\s*공기").containsMatchIn(page.body) && Regex("\\d+(?:\\.\\d+)?\\s*(?:%|ppm)\\s*(?:이상|이하|미만)", RegexOption.IGNORE_CASE).containsMatchIn(page.body)) score *= 1.8
            if (Regex("제\\d장").findAll(page.body).count() >= 3 && "목차" !in question) score *= .15
            if (Regex("\\(\\s*\\)").findAll(page.body).count() >= 4 && !Regex("허가|양식|신청|작성").containsMatchIn(question)) score *= .6
            if ("점검표" in page.chunk.sourceKind && !Regex("점검표|체크리스트|준수사항").containsMatchIn(question)) score *= .65
            if (Regex("확인\\s*부탁|(?:^|\\s)질의(?:\\s|[:：])|문의드립니다").containsMatchIn(page.body) && !Regex("사례|질의|해석|여부|반드시|항상|무조건").containsMatchIn(question)) score *= .65
            if (Regex("┃사례|\\[답변|\\(\\d+\\)[^()]{0,60}사망").containsMatchIn(page.body) && !Regex("사례|질의|해석|여부").containsMatchIn(question)) score *= .4
            RetrievedChunk(page.chunk, score * coverage * if (text.length < 100) .3 else 1.0)
        }.sortedWith(compareByDescending<RetrievedChunk> { it.score }.thenBy { it.chunk.pageNumber })
    }
    fun retrieve(question: String, limit: Int = 3): List<RetrievedChunk> {
        if (Regex("(?:우리|현재|지금).*(?:승인|허가).*(?:됐|되었|받았|상태)").containsMatchIn(question)) return emptyList()
        var subjects = aspects(question).take(8)
        if (subjects.size > 1 && subjects.dropLast(1).all { Regex("^(?:산소|유해가스|일산화탄소|이산화탄소|황화수소)$").matches(EvidenceLanguage.compact(it)) } && EvidenceLanguage.terms(subjects.last()).any { it in setOf("측정", "농도", "기준") }) subjects = listOf(question)
        val rankings = subjects.map(::rank); val selected = linkedMapOf<Long, RetrievedChunk>()
        fun page(source: RetrievedChunk) = pages.first { it.chunk === source.chunk }
        rankings.forEach { ranking -> ranking.firstOrNull()?.let { selected[it.chunk.id] = it } }
        for (ranking in rankings) {
            val first = ranking.firstOrNull() ?: continue
            if (!page(first).procedure) continue
            val continuation = ranking.firstOrNull { it.chunk.documentId == first.chunk.documentId && kotlin.math.abs(it.chunk.pageNumber - first.chunk.pageNumber) == 1 && page(it).procedure }
            if (continuation != null && selected.size < maxOf(limit, subjects.size)) selected[continuation.chunk.id] = continuation
        }
        for ((subjectIndex, ranking) in rankings.withIndex()) {
            val alternative = ranking.getOrNull(1) ?: continue
            if (alternative.score >= ranking[0].score * .98 && selected.size < maxOf(limit, subjects.size) && sameAttribute(subjects[subjectIndex], ranking[0], alternative)) selected[alternative.chunk.id] = alternative
        }
        val alternatives = rankings.flatMapIndexed { index, ranking -> ranking.drop(1).map { Triple(it, it.score / ranking[0].score, index) } }.sortedByDescending { it.second }
        for ((source, ratio, index) in alternatives) {
            if (selected.size >= maxOf(limit, subjects.size)) break
            if (rankings.any { ranking -> ranking.firstOrNull()?.let { page(it).procedure && it.chunk.documentId == source.chunk.documentId } == true } && !page(source).procedure) continue
            if (ratio < .65 || page(source).text.length < 100) continue
            if (!sameAttribute(subjects[index], rankings[index][0], source)) continue
            selected[source.chunk.id] = source
        }
        val result = selected.values.toMutableList()
        for (i in result.indices) if (page(result[i]).procedure) {
            val earlier = result.indices.firstOrNull { j -> j > i && page(result[j]).procedure && result[j].chunk.documentId == result[i].chunk.documentId && result[j].chunk.pageNumber == result[i].chunk.pageNumber - 1 }
            if (earlier != null) { val old = result[i]; result[i] = result[earlier]; result[earlier] = old }
        }
        return result.map { source ->
            val primary = subjects.filterIndexed { i, _ -> rankings[i].firstOrNull()?.chunk?.id == source.chunk.id }
            val relevant = primary.ifEmpty { listOf(subjects[rankings.indexOfFirst { ranking -> ranking.any { it.chunk.id == source.chunk.id } }]) }
            run {
                val scopes = relevant.map { scope(it, source) }
                if (scopes.any { it.evidence == null }) source else source.copy(evidence = scopes.flatMap { it.evidence.orEmpty() }.distinctBy { it.order })
            }
        }
    }
    private fun scope(question: String, source: RetrievedChunk): RetrievedChunk {
        val page = pages.first { it.chunk === source.chunk }; if (page.procedure) return source
        val profile = profile(question); if (profile.focus.isEmpty() || page.groups.isEmpty()) return source
        val best = page.groups.sortedWith(compareByDescending<List<EvidencePassage>> { passageScore(it, profile) }.thenBy { it.first().order }).first()
        val headingMatch = profile.focus.any { it in EvidenceLanguage.compact(best.first().section) }
        if (!headingMatch && profile.focus.none { it in EvidenceLanguage.compact(best.joinToString(" ") { unit -> unit.text }) }) return source
        val section = best.first().section
        fun root(heading: String) = EvidenceLanguage.compact(heading.replace(Regex("\\([^)]*\\)"), ""))
        val key = root(section)
        val sameSection: (EvidencePassage) -> Boolean = { section.isBlank() || it.section == section || (it.section.isNotBlank() && (root(it.section).startsWith(key) || key.startsWith(root(it.section)))) }
        // Keep a whole safety section: rare-word-only truncation can drop the
        // next bullet's exception, shutdown rule or protective equipment.
        val units = page.units.filter { sameSection(it) || (it.section.isBlank() && it.text.length > 60) || Regex("반드시|금지|없다면").containsMatchIn(it.text) }
        val lead = best.toMutableList()
        if (Regex("^(?:다만|이 경우|이 때|이때|그 밖|또한|그러|예를 들어)").containsMatchIn(best.first().text)) {
            val previous = page.groups.getOrNull(page.groups.indexOf(best) - 1)
            if (previous?.firstOrNull()?.section == section) lead.addAll(0, previous)
        }
        return source.copy(evidence = (lead + units).distinctBy { it.order })
    }
    private fun sameAttribute(question: String, primary: RetrievedChunk, alternative: RetrievedChunk): Boolean {
        val first = pages.first { it.chunk === primary.chunk }.opening
        val next = pages.first { it.chunk === alternative.chunk }.opening
        val attributes = EvidenceLanguage.terms(question).filter { it in setOf("방법", "시기", "자격", "직무", "절차", "주의사항") }
        return attributes.none { it in first && it !in next }
    }
    companion object {
        fun aspects(question: String): List<String> {
            val parts = question.trim().split(Regex("(?<![결효성경교학분안밖전후])(?<=[0-9a-z가-힣])과\\s+|(?<![안밖전후])(?<=[0-9a-z가-힣])와\\s+|\\s+(?:그리고|및)\\s+|[,，]")).map(String::trim).filter(String::isNotBlank)
            return if (parts.size > 1 && parts.drop(1).any { part -> EvidenceLanguage.terms(part).all { it in EvidenceLanguage.generic || it in setOf("체적", "길이", "시간", "수치", "깊이", "횟수") } }) listOf(question.trim()) else parts
        }
    }
}
