/* 소스 안내
 * 대화의 대상·후속 초점·선택지와 프롬프트 예산을 검증하는 JVM 회귀 검사다.
 * 검사 예: user, answer, screenshotMoreAndMissingChoicesStayInTheOriginalTopic
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ConversationContextTest {
    private val choices = listOf(SearchSuggestion("측정 위치", "산소 측정 위치와 개소"), SearchSuggestion("측정 시기", "산소 유해가스 측정 시기"))
    private fun user(text: String, topic: String? = null, resolved: String? = null) = ConversationTurn(true, text, resolved, topic)
    private fun answer(text: String = "앞선 답변입니다.", topic: String? = "산소 측정 방법", options: List<SearchSuggestion> = emptyList()) = ConversationTurn(false, text, topicQuestion = topic, answerStatus = if(options.isEmpty())"answered" else "clarify", sourceIds = listOf(27L), suggestions = options)
    @Test fun screenshotMoreAndMissingChoicesStayInTheOriginalTopic() {
        val history = listOf(user("산소 측정 방법"), answer())
        val more = ConversationContext.resolve("또 다른 건 없어?", history)
        assertEquals(FollowUpKind.MORE, more.kind); assertEquals("산소 측정 방법", more.query)
        val repair = ConversationContext.resolve("항목이 안보이는데?", history + user(more.asked, more.topic, more.query) + answer("아래 항목을 선택해 주세요.", more.topic, choices))
        assertEquals(FollowUpKind.SHOW_CHOICES, repair.kind); assertEquals("산소 측정 방법", repair.topic)
        assertEquals(choices, repair.previousAnswer!!.suggestions)
    }
    @Test fun oldSavedChatsCanRecoverPastUnresolvedFollowups() {
        val request = ConversationContext.resolve("항목이 안보이는데?", listOf(user("산소 측정 방법"), answer(topic = null), user("또 다른 건 없어?", resolved = "또 다른 건 없어?"), answer("아래 항목을 선택해 주세요.", topic = null)))
        assertEquals("산소 측정 방법", request.topic)
    }
    @Test fun spokenOrdinalsAndLabelsSelectAnActualOfferedChoice() {
        val history = listOf(user("산소 측정 방법"), answer(options = choices))
        for (q in listOf("두 번째 알려줘", "2번", "그럼 두번째 자세히", "측정 시기")) {
            val request = ConversationContext.resolve(q, history)
            assertEquals(q, FollowUpKind.CHOICE, request.kind)
            assertEquals("산소 유해가스 측정 시기", request.query)
        }
        assertEquals(FollowUpKind.SHOW_CHOICES, ConversationContext.resolve("5번", history).kind)
    }
    @Test fun followupFocusChangesWithoutGrowingRecursiveQuestionChains() {
        val whenRequest = ConversationContext.resolve("그럼 언제 해야 해?", listOf(user("산소 측정 방법"), answer()))
        assertEquals(FollowUpKind.FOLLOW_UP, whenRequest.kind); assertTrue(whenRequest.query.contains("시기")); assertFalse(whenRequest.query.contains("방법"))
        val why = ConversationContext.resolve("왜?", listOf(user("산소 측정 방법"), answer(), user(whenRequest.asked, whenRequest.topic, whenRequest.query), answer(topic = whenRequest.topic)))
        assertEquals(FollowUpKind.EXPLAIN, why.kind); assertEquals(whenRequest.query, why.query)
        assertFalse(why.query.contains("후속 질문:"))
        assertEquals(FollowUpKind.EXPLAIN, ConversationContext.resolve("그럼 왜 위험해?", listOf(user("산소 측정 방법"), answer())).kind)
    }
    @Test fun explicitNewSubjectDoesNotInheritGasTopicAndResetDropsPromptHistory() {
        val history = listOf(user("산소 측정 방법"), answer())
        assertEquals("심폐소생술 알려줘", ConversationContext.resolve("심폐소생술 알려줘", history).query)
        val explicit = ConversationContext.resolve("그럼 심폐소생술 알려줘", history)
        assertEquals(FollowUpKind.NEW, explicit.kind); assertFalse(explicit.query.contains("산소"))
        assertTrue(ConversationContext.resolve("다른 얘기: 커피 추출 원리", history).turns.isEmpty())
    }
    @Test fun emptyChatCannotInventAReferentOrAChoice() {
        assertEquals(FollowUpKind.UNRESOLVED, ConversationContext.resolve("또 다른 건 없어?", emptyList()).kind)
        assertEquals(FollowUpKind.UNRESOLVED, ConversationContext.resolve("그거 어떻게 해?", emptyList()).kind)
        assertEquals(FollowUpKind.NEW, ConversationContext.resolve("커피 추출", emptyList()).kind)
    }
    @Test fun recentUserStatementsCanBeRecalledWithoutDocumentSearch() {
        val request = ConversationContext.resolve("내가 아까 뭐라고 말했지?", listOf(user("갤럭시 S24를 사용하고 있어요"), answer("확인했어요.", topic = null)))
        assertEquals(FollowUpKind.RECALL, request.kind)
    }
    @Test fun promptHistoryKeepsRolesAndWholeTurnsInsideByteBudget() {
        val history = listOf(user("저는 S24를 써요."), answer("기기 정보를 확인했어요.", topic = null), user("다른 질문"), answer("조건을 제외하지 않는 긴 문장입니다.".repeat(300), topic = null))
        val prompt = ConversationContext.promptHistory(history, 600)
        assertTrue(prompt.toByteArray(Charsets.UTF_8).size <= 600)
        val turns = JSONArray(prompt)
        assertTrue(prompt.contains("S24")); assertTrue(prompt.contains("assistantOmitted"))
        assertEquals("user", turns.getJSONObject(0).getString("role"))
        assertFalse(prompt.contains("조건을 제외하지"))
        assertEquals("", ConversationContext.promptHistory(history, 10))
    }
    @Test fun explicitSupervisorQuestionRetainsItsRequestedItemCountAndWording() {
        val question = "밀폐공간 작업을 중단했다가 다시 시작할 때 감독관이 확인할 사항을 핵심 5개로 정리해줘. 각 항목은 한 문장으로 설명해줘."
        val request = ConversationContext.resolve(question, emptyList())
        assertEquals(FollowUpKind.NEW, request.kind)
        assertEquals(question, request.asked)
        assertEquals(question, request.answerQuestion)
        assertFalse(GeneralKnowledgeAnswer.prefersBrief(request.query, automaticLength = true))
    }
    @Test fun actualDomainQuestionsAreNotMistakenForUiFeedbackOrMemoryRecall() {
        assertEquals(FollowUpKind.NEW, ConversationContext.resolve("작업 점검 항목이 없으면 어떻게 해?", emptyList()).kind)
        assertEquals(FollowUpKind.NEW, ConversationContext.resolve("아까 상사가 공기호흡기가 필요없다고 말했는데 맞아?", emptyList()).kind)
        assertEquals(FollowUpKind.ACK, ConversationContext.resolve("고마워", listOf(user("산소 측정 방법"), answer())).kind)
    }
    @Test fun explicitTopicResetAlsoBoundsTheNextTurnsMemory() {
        val history = listOf(user("산소 측정 방법"), answer(), user("다른 얘기: 커피 추출 원리", "커피 추출 원리"), answer("커피 설명입니다.", "커피 추출 원리"))
        val follow = ConversationContext.resolve("그거 쉽게 설명해줘", history)
        assertEquals("커피 추출 원리", follow.query)
        assertFalse(ConversationContext.promptHistory(follow.turns, 4096).contains("산소"))
        val newSubject = ConversationContext.resolve("그럼 갤럭시 배터리 수명은?", listOf(user("산소 측정 방법"), answer()))
        assertEquals(FollowUpKind.NEW, newSubject.kind)
        assertFalse(newSubject.query.contains("산소"))
    }
    @Test fun questionWordsAreNotMistakenForNewTopicNouns() {
        val request = ConversationContext.resolve("그럼 언제까지 반복해?", listOf(user("심폐소생술 알려줘"), answer("호흡을 확인합니다.", "심폐소생술 알려줘")))
        assertEquals(FollowUpKind.FOLLOW_UP, request.kind); assertTrue(request.query.contains("심폐소생술"))
    }
    @Test fun generationPromptContainsRolesAndTheLatestUtteranceLastWithinBudget() {
        val base = "현재 문서와 질문에 답하세요."
        val prompt = ConversationContext.generationPrompt(base, listOf(user("커피 추출 원리"), answer("추출은 성분이 물에 녹는 과정입니다.", "커피 추출 원리")), "그거 쉽게 설명해줘", 4096, 2048)
        assertTrue(prompt.contains("\"role\":\"assistant\"")); assertTrue(prompt.contains("성분이 물에 녹는"))
        assertTrue(prompt.endsWith("그거 쉽게 설명해줘")); assertTrue(prompt.toByteArray(Charsets.UTF_8).size + 2048 <= 4096)
        assertEquals(base, ConversationContext.generationPrompt(base, listOf(user("질문")), "질문", 200, 128))
    }
    @Test fun longAnswerCanContributeItsActualHeadingsWithoutCuttingSafetySentences() {
        val history = listOf(user("커피 추출"), answer("## 원두 선택\n\n" + "긴 설명과 조건입니다.".repeat(300) + "\n\n## 물 관리\n\n끝 조건입니다.", "커피 추출"))
        val memory = ConversationContext.promptHistory(history, 500)
        assertTrue(memory.contains("discussedHeadings")); assertTrue(memory.contains("원두 선택"))
        assertFalse(memory.contains("긴 설명과 조건")); assertTrue(memory.contains("fullAnswerOmitted"))
        assertTrue(memory.toByteArray(Charsets.UTF_8).size <= 500)
    }

    @Test fun briefHistoryBudgetKeepsLatestQuestionAndDocumentContextIntact() {
        val documentContext = "문서 근거와 필수 조건입니다."
        val latest = "그럼 얼마나 필요해?"
        val history = listOf(user("삼각대 알려줘"), answer("이전 장문 설명입니다. ".repeat(40)))
        val brief = ConversationContext.generationPrompt(documentContext, history, latest, 8192, 160, maxHistoryBytes = 768)
        val full = ConversationContext.generationPrompt(documentContext, history, latest, 8192, 160)
        assertTrue(brief.startsWith(documentContext))
        assertTrue(brief.endsWith(latest))
        assertTrue(brief.contains("삼각대 알려줘"))
        assertFalse(brief.contains("이전 장문 설명"))
        assertTrue(full.contains("이전 장문 설명"))
    }
}
