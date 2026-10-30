package com.minyook.sllm2.ui

import org.junit.Assert.*
import org.junit.Test

class ChatTimelineTest {
    @Test fun unchangedAnswersAreNotReparsedWhenPendingStatusOrAnotherTurnChanges() {
        var parses = 0
        val builder = ChatTimelineBuilder { parses++; markdownBlocks(it) }
        val answer = ChatMessageUi(id = "answer", text = "## 장비\n\n- 방폭 **전등**", fromWorker = false, pending = true)
        val first = builder.build(listOf(answer))
        builder.build(listOf(answer.copy(pending = false)))
        builder.build(listOf(answer, ChatMessageUi(id = "question", text = "다음 질문", fromWorker = true)))
        assertEquals(1, parses)
        assertEquals(first.map { it.key }, builder.build(listOf(answer)).map { it.key })
        builder.build(listOf(answer.copy(text = answer.text + "\n\n- 송기마스크")))
        assertEquals(2, parses)
    }

    @Test fun markdownOrderTablesAndCodeSurviveLazyBlockSeparation() {
        val text = "## 측정\n\n- 산소 **기준**\n\n|항목|값|\n|---|---|\n|가스|확인|\n\n```text\na | b\n```\n> 조건을 확인하세요."
        val answer = ChatMessageUi(id = "answer", text = text, fromWorker = false, sourceIds = listOf(27L))
        val rows = ChatTimelineBuilder().build(listOf(answer))
        assertEquals(markdownBlocks(text), rows.filterIsInstance<ChatTimelineRow.Body>().map { it.block })
        assertEquals(1, rows.count { it is ChatTimelineRow.Body && it.block is MarkdownBlock.Table })
        assertEquals(1, rows.count { it is ChatTimelineRow.Body && it.block is MarkdownBlock.Code })
        assertEquals(listOf(27L), (rows.last() as ChatTimelineRow.Footer).message.sourceIds)
    }

    @Test fun appendingAnAnswerKeepsPreviouslyVisibleKeysAndDoesNotDuplicateFooter() {
        val question = ChatMessageUi(id = "q", text = "작업장비", fromWorker = true)
        val answer = ChatMessageUi(id = "a", text = "첫 문단\n\n두 번째 문단", fromWorker = false, pending = true)
        val builder = ChatTimelineBuilder()
        val before = builder.build(listOf(question, answer))
        val after = builder.build(listOf(question, answer.copy(text = answer.text + "\n\n추가 설명", pending = false)))
        val oldBodies = before.filterIsInstance<ChatTimelineRow.Body>()
        assertEquals(oldBodies, after.filterIsInstance<ChatTimelineRow.Body>().take(oldBodies.size))
        assertEquals(after.size, after.map { it.key }.distinct().size)
        assertEquals(1, after.count { it is ChatTimelineRow.Footer })
        assertEquals("q:question", after.first().key)
    }

    @Test fun aLongAnswerIsNotRepresentedAsOneHugeLazyItem() {
        val text = (1..1000).joinToString("\n\n") { "- 항목 $it: 해당 문서의 조건을 확인합니다." }
        val rows = ChatTimelineBuilder().build(listOf(ChatMessageUi(id = "a", text = text, fromWorker = false)))
        val body = rows.filterIsInstance<ChatTimelineRow.Body>()
        assertEquals(1000, body.count { (it.block as? MarkdownBlock.Line)?.text?.startsWith("- ") == true })
        assertTrue(body.all { (it.block as MarkdownBlock.Line).text.length < 100 })
        assertTrue(rows.first() is ChatTimelineRow.Header)
        assertTrue(rows.last() is ChatTimelineRow.Footer)
    }

    @Test fun removedConversationsAndClearedAnswersReleaseTheirParsedBlocks() {
        var parses = 0
        val builder = ChatTimelineBuilder { parses++; markdownBlocks(it) }
        val answer = ChatMessageUi(id = "old", text = "원문 답변", fromWorker = false)
        builder.build(listOf(answer))
        assertTrue(builder.build(emptyList()).isEmpty())
        builder.build(listOf(answer))
        assertEquals(2, parses)
        val pending = builder.build(listOf(answer.copy(text = "", pending = true)))
        assertTrue(pending.none { it is ChatTimelineRow.Body })
        builder.build(listOf(answer))
        assertEquals(3, parses)
    }
}
