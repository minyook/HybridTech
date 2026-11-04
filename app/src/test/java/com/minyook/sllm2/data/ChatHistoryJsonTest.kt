package com.minyook.sllm2.data

import org.junit.Assert.*
import org.junit.Test

class ChatHistoryJsonTest {
    @Test fun contextChoicesAndSourcesSurviveStorageRoundTrip() {
        val topic = "산소 측정 방법"
        val choices = listOf(SearchSuggestion("측정 시기", "산소 측정 시기"))
        val session = ChatHistoryStore.Session("chat", "제목", 10L, listOf(
            ChatHistoryStore.Turn(ChatHistoryStore.Role.USER, "또 다른 건 없어?", resolvedQuestion = topic, topicQuestion = topic),
            ChatHistoryStore.Turn(ChatHistoryStore.Role.ASSISTANT, "추가 항목입니다.", listOf(25L, 26L), choices, topicQuestion = topic, answerStatus = "conversation-choices"),
        ))
        val restored = ChatHistoryJson.decode(ChatHistoryJson.encode(listOf(session)))
        assertEquals(listOf(session), restored)
        val request = ConversationContext.resolve("항목이 안보이는데?", restored.single().turns.map { ConversationTurn(it.role == ChatHistoryStore.Role.USER, it.text, it.resolvedQuestion, it.topicQuestion, it.answerStatus, it.sourceIds, it.suggestions) })
        assertEquals(topic, request.topic); assertEquals(choices, request.previousAnswer!!.suggestions)
    }
    @Test fun legacyChatsWithoutContextFieldsRemainReadableAndCanRecoverTheTopic() {
        val legacy = """[{"id":"old","title":"기존 대화","updatedAt":1,"turns":[{"role":"USER","text":"산소 측정 방법"},{"role":"ASSISTANT","text":"기존 답변","sourceIds":[27]},{"role":"USER","text":"또 다른 건 없어?"}]}]"""
        val restored = ChatHistoryJson.decode(legacy).single()
        assertEquals(3, restored.turns.size); assertNull(restored.turns.first().topicQuestion)
        val request = ConversationContext.resolve("그거 자세히", restored.turns.map { ConversationTurn(it.role == ChatHistoryStore.Role.USER, it.text, it.resolvedQuestion, it.topicQuestion, it.answerStatus, it.sourceIds, it.suggestions) })
        assertEquals("산소 측정 방법", request.topic)
    }
    @Test fun invalidJsonKeepsTheExistingEmptyFallbackAndChatsAreLimited() {
        assertTrue(ChatHistoryJson.decode("not-json").isEmpty())
        val sessions = (1..25).map { ChatHistoryStore.Session("c$it", "제목", it.toLong(), emptyList()) }
        val restored = ChatHistoryJson.decode(ChatHistoryJson.encode(sessions))
        assertEquals(20, restored.size); assertEquals("c25", restored.first().id)
    }
}
