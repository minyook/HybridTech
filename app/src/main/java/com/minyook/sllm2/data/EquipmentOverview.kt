package com.minyook.sllm2.data

/** An inventory question has several complementary answers, not competing
 * interpretations. These are equipment concepts, never question/page answers.
 * Every displayed item and condition still comes from the current corpus. */
internal class EquipmentOverview(private val chunks: List<KnowledgeChunk>) {
    private enum class Scope { ALL, PROTECTION, RESCUE }
    private data class Family(
        val label: String,
        val equipment: Regex,
        val heading: Regex? = null,
        val preferred: Regex? = null,
        val requiredContext: Regex? = null,
        val sectionsTogether: Boolean = false,
    )
    private data class Section(val chunk: KnowledgeChunk, val units: List<EvidencePassage>) {
        val heading = units.first().section
        val text = units.joinToString(" ") { it.text }
    }
    private val sections by lazy {
        chunks.filterNot { chunk ->
            Regex("┃사례|\\[답변|사망\\s*\\d+명").containsMatchIn(chunk.body) ||
                Regex("\\(\\s*\\)").findAll(chunk.body).count() >= 4
        }.flatMap { chunk ->
            FocusedEvidence.units(chunk).groupBy { it.section }.values.map { Section(chunk, it) }
        }
    }

    fun resolve(question: String): SearchResolution? {
        val scope = scope(question) ?: return null
        val families = when (scope) {
            Scope.ALL -> listOf(MEASUREMENT, LIGHTING, VENTILATION, RESPIRATORY, FALL_RESCUE, COMMUNICATION)
            Scope.PROTECTION -> listOf(RESPIRATORY, FALL_RESCUE)
            Scope.RESCUE -> listOf(FALL_RESCUE, RESCUE_ENTRY)
        }
        val selected = families.mapNotNull(::select)
        if (selected.isEmpty()) return null
        val sources = selected.groupBy { it.chunk.id }.values.map { group ->
            group.first().copy(evidence = group.flatMap { it.evidence.orEmpty() }.distinctBy { it.order })
        }
        return SearchResolution(
            question = question,
            sources = sources,
            status = "answered",
            message = "문서에서 확인되는 장비를 용도별로 정리했어요. 각 장비의 사용 조건도 함께 확인해 주세요.",
        )
    }

    private fun scope(question: String): Scope? {
        val text = EvidenceLanguage.aliases(question)
        val compact = EvidenceLanguage.compact(question)
        // A requested specification, method, exception or real situation must
        // never be replaced with a generic list of equipment.
        if (Regex("\\d|가격|구매|전압|볼트|방수|등급|배터리|무게|제조사|모델명|충전|주기|기간|언제|시기|점검|검사|사용법|사용방법|사용 방법|고장|차이|비교|대신|없이|없어|없으면|없는데|않|안\\s|아니|가능|해도|해야만|중단|멈추|꼬이|끊어|왜|이유|원리").containsMatchIn(text)) return null
        if (Regex("공기호흡기|송기마스크|측정기|측정장비|환기팬|덕트|채기관|전등|삼각대|안전대|구명줄|구명밧줄|호흡용|호흡기").containsMatchIn(compact)) return null
        val subject = Regex("장비|장구|보호구|준비물|준비품|비품|도구|기기").containsMatchIn(compact)
        val packing = Regex("챙|가져가|갖춰|준비").containsMatchIn(text) && Regex("밀폐공간|작업|현장").containsMatchIn(text)
        if (!subject && !packing) return null
        val inventory = Regex("종류|목록|리스트|뭐|무엇|무슨|어떤|필요|준비|챙|갖춰|가져가|알려|정리|설명|전체|기본|필수").containsMatchIn(text)
        if (!inventory && EvidenceLanguage.terms(question).size > 3) return null
        // Unknown domain modifiers are meaningful (e.g. diving/radiation
        // equipment). Do not discard them just to produce a confined-space list.
        if (!inventoryLanguage.matches(compact)) return null
        return when {
            "구조" in compact -> Scope.RESCUE
            Regex("보호구|보호장구|보호장비").containsMatchIn(compact) -> Scope.PROTECTION
            else -> Scope.ALL
        }
    }

    private fun select(family: Family): RetrievedChunk? {
        val section = sections.filter { section ->
            family.equipment.containsMatchIn(section.heading + " " + section.text) &&
                (family.heading == null || family.heading.containsMatchIn(section.heading)) &&
                (family.requiredContext == null || family.requiredContext.containsMatchIn(section.text))
        }.maxByOrNull { section ->
            (if (family.heading?.containsMatchIn(section.heading) == true) 8.0 else 0.0) +
                (if (family.preferred?.containsMatchIn(section.heading + " " + section.text) == true) 5.0 else 0.0) +
                (if (section.heading == "사전준비") 3.0 else 0.0) +
                (if ("점검표" in section.chunk.sourceKind) -4.0 else 0.0) - section.text.length / 4000.0
        } ?: return null
        val units = FocusedEvidence.units(section.chunk)
        val groups = units.groupBy { it.group }.values
        val chosen = groups.filter { group ->
            val text = group.joinToString(" ") { it.text }
            when {
                family.sectionsTogether -> group.any { family.heading?.containsMatchIn(it.section) == true }
                family == RESCUE_ENTRY -> group.any { family.heading!!.containsMatchIn(it.section) } && family.equipment.containsMatchIn(text)
                family == COMMUNICATION -> family.equipment.containsMatchIn(text) && family.requiredContext!!.containsMatchIn(text)
                else -> family.equipment.containsMatchIn(text)
            }
        }.flatten().toMutableList()
        if (family == RESPIRATORY) {
            chosen += groups.filter { group -> group.any {
                "보호장구의 종류" in it.section || Regex("즉시\\s*사용\\s*가능").containsMatchIn(it.text)
            } }.flatten()
        }
        if (family == VENTILATION) {
            chosen += section.units
            chosen += groups.filter { group -> group.any { Regex("금지|사용하지|대피|중단되|말아|해서는").containsMatchIn(it.text) } }.flatten()
        }
        if (chosen.isEmpty()) return null
        val heading = if (family == RESPIRATORY) {
            units.firstOrNull { "공기호흡기" in it.section && "송기마스크" in it.section }?.section ?: family.label
        } else family.label
        return RetrievedChunk(section.chunk, 1.0, chosen.distinctBy { it.order }.sortedBy { it.order }
            .filterNot { Regex("^공기호흡기\\(SCBA\\)\\s*\\d+\\s*보호구$|^산업안전보건법\\s*제\\d+조.*").matches(it.text) }
            .map { it.copy(section = if ("보호장구의 종류" in it.section) "보호장구 종류" else heading) })
    }

    companion object {
        // Match the whole request, including common Korean request endings.
        // An unknown modifier cannot disappear through substring/particle removal.
        private val inventoryLanguage = Regex("(?:" + listOf(
            "밀폐공간", "작업장비", "작업", "현장", "안전장비", "안전", "보호장구", "보호구", "보호장비", "구조장비", "구조용", "구조", "장비", "장구", "준비물", "준비품", "비품", "도구", "기기",
            "종류", "목록", "리스트", "기본", "필수", "전체", "필요한", "필요해", "필요", "설명", "정리", "알려주세요", "알려줘", "알려", "말해줘", "가르쳐줘", "챙겨야", "챙길", "챙겨", "챙기는", "갖춰야", "갖출", "갖춰", "가져가야", "가져갈", "준비해야", "준비할", "준비",
            "어떤", "무엇인가요", "무엇", "무슨", "뭐뭐", "뭐야", "뭐가", "뭐", "뭔지", "있는지", "있나요", "있어요", "있어", "있음", "있을까", "사용하는", "하는", "하기", "할때", "할", "쓰는", "들어갈때", "출입", "들어가기전", "에대해서", "에대해", "대해서", "대해", "관련해서", "관련", "궁금해", "해주세요", "해줘", "주세요", "좀", "되나요", "돼", "하나요", "해야", "합니까", "입니까", "인가요", "에서", "전에", "은", "는", "이", "가", "을", "를", "의", "에", "로", "도", "만", "들", "요", "전", "시", "때"
        ).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")+")
        private val MEASUREMENT = Family("측정 장비", Regex("측정기|측정장비|채기관"), requiredContext = Regex("검교정|교정|센서교체"))
        private val LIGHTING = Family("조명 장비", Regex("전등|조명|랜턴"), requiredContext = Regex("방폭"))
        private val VENTILATION = Family("환기 장비", Regex("환기팬|송풍관|덕트|송풍기|환기장치"), requiredContext = Regex("정압|용량|제조사"))
        private val RESPIRATORY = Family("호흡용 보호구", Regex("공기호흡기|송기마스크"), heading = Regex("호흡용\\s*보호구"), sectionsTogether = true)
        private val FALL_RESCUE = Family("추락 방지·구조 장비", Regex("안전대|구명\\s*(?:밧)?줄|구명\\s*밧줄|삼각대|섬유로프|사다리"), heading = Regex("안전대|구명|삼각대|추락"), sectionsTogether = true)
        private val COMMUNICATION = Family("연락용 설비", Regex("연락|통신수단"), preferred = Regex("항상.*연락"), requiredContext = Regex("감시인|작업근로자|작업자"))
        private val RESCUE_ENTRY = Family("구조 진입 시 보호장비와 제한", Regex("공기호흡기|송기마스크|119|호흡용보호구|기다리"), heading = Regex("구조|쓰러"), requiredContext = Regex("119|구조대"))
    }
}
