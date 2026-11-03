package com.minyook.sllm2.data

/** Conservative domain language normalization, not a question-to-page/answer table.
 * Preserve the user's question in UI/history and use this only for retrieval. */
internal object QuestionUnderstanding {
    fun normalize(question:String):String {
        var q=question
            .replace(Regex("(?<![가-힣])(?:잴|잰|재는|재야|재나요|재면|재고|재볼|재지)(?:\\s|[?？]|$)")," 측정 ")
            .replace(Regex("위아래(?:랑|로|를)?"),"수직")
            .replace(Regex("가로(?:로|를)?"),"수평")
            .replace(Regex("전기(?:가|는)?\\s*(?:나가(?:거나|면|서)?|끊(?:기면|어지면|기거나))"),"정전")
            .replace(Regex("점심\\s*먹(?:느라|고|으러|은\\s*후)"),"점심시간")
            .replace(Regex("더러워진|더러운"),"오염된")
            .replace(Regex("뜯어|뜯는"),"분해")
            .replace(Regex("멈추면|멈췄|멈춘|멈추는"),"중단")
        if("측정" in q&&"산소" in q)q=q.replace(Regex("(?<!유해)가스"),"유해가스")
        if("송기마스크" in q)q=q.replace("호스","송기라인")
        if(Regex("질소|아르곤|불활성").containsMatchIn(q))q=q.replace(Regex("실수로\\s*열지\\s*않도록"),"잘못된 조작 방지")
        if(Regex("심폐소생술|CPR|cpr|가슴압박|흉부압박").containsMatchIn(q))q=q.replace(Regex("계속\\s*해야|계속\\s*해요|계속\\s*할"),"반복")
        return q.replace(Regex("\\s+")," ").trim()
    }
}
