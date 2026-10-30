package com.minyook.sllm2.ui

/** Stable lazy-list entries let a long answer render a viewport of blocks,
 * instead of composing the entire answer as one oversized list item. */
internal sealed interface ChatTimelineRow {
    val key: String
    val contentType: String

    data class Question(val message: ChatMessageUi) : ChatTimelineRow {
        override val key = "${message.id}:question"
        override val contentType = "question"
    }
    data class Header(val message: ChatMessageUi) : ChatTimelineRow {
        override val key = "${message.id}:header"
        override val contentType = "answerHeader"
    }
    data class Body(val messageId: String, val index: Int, val block: MarkdownBlock) : ChatTimelineRow {
        override val key = "$messageId:body:$index"
        override val contentType = when (block) {
            is MarkdownBlock.Code -> "answerCode"
            is MarkdownBlock.Table -> "answerTable"
            is MarkdownBlock.Line -> "answerLine"
        }
    }
    data class Footer(val message: ChatMessageUi) : ChatTimelineRow {
        override val key = "${message.id}:footer"
        override val contentType = "answerFooter"
    }
}

internal class ChatTimelineBuilder(private val parse: (String) -> List<MarkdownBlock> = ::markdownBlocks) {
    private data class Parsed(val text: String, val blocks: List<MarkdownBlock>)
    private val parsed = mutableMapOf<String, Parsed>()

    fun build(messages: List<ChatMessageUi>): List<ChatTimelineRow> {
        val active = messages.filterNot { it.fromWorker }.mapTo(mutableSetOf()) { it.id }
        parsed.keys.retainAll(active)
        return buildList {
            messages.forEach { message ->
                if (message.fromWorker) add(ChatTimelineRow.Question(message))
                else {
                    add(ChatTimelineRow.Header(message))
                    if (message.text.isNotBlank()) {
                        val cached = parsed[message.id]?.takeIf { it.text == message.text }
                            ?: Parsed(message.text, parse(message.text)).also { parsed[message.id] = it }
                        cached.blocks.forEachIndexed { index, block -> add(ChatTimelineRow.Body(message.id, index, block)) }
                    } else parsed.remove(message.id)
                    add(ChatTimelineRow.Footer(message))
                }
            }
        }
    }
}
