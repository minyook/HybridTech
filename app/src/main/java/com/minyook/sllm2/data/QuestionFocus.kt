package com.minyook.sllm2.data

/** Domain entities and requested attributes, never a question-to-answer/page table. */
internal object QuestionFocus {
    data class Entity(val name: String, val forms: Regex) {
        fun matches(text: String) = forms.containsMatchIn(text)
    }
    val entities = listOf(
        Entity("안전대", Regex("안전\\s*대|하네스")),
        Entity("구명줄", Regex("구명\\s*(?:밧)?줄|구명\\s*로프")),
        Entity("구조용 삼각대", Regex("(?:구조용\\s*)?삼각대")),
        Entity("사다리", Regex("사다리")),
        Entity("섬유로프", Regex("섬유\\s*로프")),
        Entity("공기호흡기", Regex("공기\\s*호흡기")),
        Entity("송기마스크", Regex("송기\\s*마스크")),
        Entity("가스측정기", Regex("(?:복합\\s*)?(?:가스\\s*)?측정기")),
        Entity("전등", Regex("전등|손전등|랜턴|조명")),
        Entity("환기팬", Regex("환기팬|송풍기")),
    )
    fun targets(text: String) = entities.filter { it.matches(text) }
    fun requestText(text: String): String = text.trim()
        .replace(Regex("^(?:아니요?|그게\\s*아니라|내\\s*말은)[,，\\s]*"), "")
        .replace(Regex("(?:갯수|개수|수량)(?:를|을|가|은|는)?\\s*(?:물어보잖아|물었잖아|물어봤잖아|묻잖아|물어본거야|묻는거야)"), "수량 ")
    private val count = Regex("개수|갯수|수량|몇\\s*(?:개(?!소|월)|대|벌|세트|본|가닥)|각각\\s*몇|(?:\\d+|한|두|세|네)\\s*(?:개(?!소|월)|대|벌|세트|본|가닥)(?:면|씩|만|로|가|는|를|야|도|\\s|$)")
    private val otherMeasure = Regex("길이|미터|몇\\s*m|하중|높이|전압|간격|농도|몇\\s*회|몇\\s*시간", RegexOption.IGNORE_CASE)
    fun quantity(text: String) = (count.containsMatchIn(text) || Regex("얼마나\\s*(?:필요|준비|챙)").containsMatchIn(text)) && !otherMeasure.containsMatchIn(text)
    fun comparison(text: String) = Regex("차이|비교|어떻게\\s*달라|뭐가\\s*달라").containsMatchIn(text)
    fun people(text: String): String? = Regex("(?:\\d+|한|두|세|네|다섯|여섯)\\s*명|(?:일|이|삼|사)\\s*인(?:이|일|이면|분)").find(text)?.value
    fun statedCount(text: String): String? = Regex("(?:\\d+|한|두|세|네|다섯|여섯)\\s*(?:개|대|벌|세트|본|가닥)").find(text)?.value

    /** Unknown modifiers ('실린더', '배터리') stay meaningful for semantic search. */
    fun simpleQuantity(question: String): Boolean {
        if (!quantity(question) || targets(question).isEmpty()) return false
        return quantityRemainder(question).isBlank()
    }
    fun bareQuantity(question: String) = quantity(question) && targets(question).isEmpty() && quantityRemainder(question).isBlank()
    private fun quantityRemainder(question: String): String {
        var rest = requestText(question)
        targets(question).forEach { rest = rest.replace(it.forms, " ") }
        rest = rest.replace(Regex("\\d+|한|두|세|네|다섯|여섯"), " ")
        rest = rest.replace(Regex("그럼|그러면|그거|그건|각각|개수|갯수|수량|몇|인원|명|정해진|정해져|정해|정도|어느|얼마나|챙겨야|챙겨|챙기|준비해야|준비|필요|고정|기준|조건|있나요|있나|있어|없어|돼요|되나|될까|가능|되는|이면|면|하나|해야|해줘|해요|돼|해|게|것|건|지|요|는|은|이|가|을|를|의|과|와|랑|하고|등등|등|에|로|도|만|씩|개|대|벌|세트|본|가닥|좀|\\s|[?？,.·()]"), "")
        return rest
    }

    /** Covers an omitted subject and a narrowed named subset; explicit new nouns use normal search. */
    fun followUp(asked: String, topic: String): String? {
        val cleaned = requestText(asked)
        if (cleaned != asked) return followUp(cleaned, topic)
        val prior = targets(topic)
        if (prior.isEmpty()) return null
        val named = targets(asked)
        if (named.any { it !in prior }) return null
        val quantity = quantity(asked)
        val countCondition = people(asked) != null && quantity(topic)
        val compare = comparison(asked)
        val description = Regex("용도|역할|왜\\s*필요|언제\\s*(?:써|쓰|사용|필요)|어디에?\\s*(?:써|쓰|사용)").containsMatchIn(asked)
        val rephrase = Regex("쉽게|자세히|설명").containsMatchIn(asked)
        val selected = named.ifEmpty { prior }
        val subject = selected.joinToString(" · ") { it.name }
        if (Regex("^(?:그럼|그러면|그거|그건)?(?:가격|길이|하중|점검주기|점검시기|사용법|사용방법|설치방법|사용기한)").containsMatchIn(EvidenceLanguage.compact(asked))) return "$subject $asked"
        if (rephrase && !description && !compare && !quantity && named.isEmpty() && Regex("^(?:그럼|그러면|그거|그건|그것)?(?:좀|다시)?(?:쉽게|자세히|자세하게)?(?:풀어)?설명(?:해줘|해주세요|해|해줄래)?$").matches(EvidenceLanguage.compact(asked))) return topic
        if (!quantity && !countCondition && !compare && !description && !(rephrase && named.isNotEmpty())) return null
        // After removing only known domain entities and attribute language, an
        // unknown noun is still meaningful: '커피 개수' cannot inherit safety gear.
        var rest = asked
        named.forEach { rest = rest.replace(it.forms, " ") }
        rest = rest.replace(Regex("\\d+|한|두|세|네|다섯|여섯"), " ")
        rest = rest.replace(Regex("그럼|그러면|그거|그건|그것|이거|이건|각각|둘|셋|차이점|차이|비교|어떻게|달라|다른|개수|갯수|수량|몇|명|인원|정해진|정해져|정해|정도|어느|얼마나|챙겨야|챙겨|챙기|준비해야|준비|왜|언제|어디|필요|고정|기준|사용|역할|용도|설명|쉽게|자세히|알려줘|알려|때는|일때|이면|나요|있나|있어|없어|돼요|되나|될까|가능|되는|쓰면|쓰는|써야|써도|쓰|써|하나|해야|해줘|해요|돼|해|게|것|건|지|요|는|은|이|가|을|를|의|과|와|랑|하고|등등|등|에|로|도|만|씩|개|대|벌|세트|본|가닥|좀|\\s|[?？,.·]"), "")
        if (rest.isNotBlank()) return null
        return when {
            quantity || countCondition -> "$subject 준비 수량" + ((people(asked) ?: statedCount(asked))?.let { " ($it 조건)" } ?: "")
            compare -> "$subject 역할 차이"
            else -> "$subject 용도와 사용 조건"
        }
    }

    /** Restrict the deterministic description path to actual description questions. */
    fun simpleDescription(question: String): Boolean {
        if (targets(question).size < 2 || quantity(question)) return false
        var rest = question
        targets(question).forEach { rest = rest.replace(it.forms, " ") }
        rest = rest.replace(Regex("에\\s*대해서|에\\s*대해|대해서|대해|사용\\s*조건|역할|용도|차이점|차이|비교|설명해줘|설명해주세요|설명|알려주세요|알려줘|알려|정리해줘|정리|무엇|뭐야|뭔지|각각|쉽게|자세히|구조용|등등|등|하고|이랑|랑|및|와|과|은|는|이|가|을|를|의|좀|\\s|[?？,.·]"), "")
        return rest.isBlank()
    }
}
