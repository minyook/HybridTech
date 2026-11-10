/* 소스 안내
 * 질문/헤더/본문/푸터를 안정 키의 lazy 항목으로 나누고 본문 파싱을 캐시한다. WorkspaceChat이 사용한다.
 */
package com.minyook.sllm2.ui

/**
 * WorkspaceChat이 사용하는 질문·답변 헤더·Markdown 본문·푸터의 lazy 목록 모델.
 * 메시지 ID와 블록 인덱스의 안정적인 키로 뒤쪽 글자/문단 추가가 앞쪽 항목 재생성으로 번지지 않게 한다.
 * 실제 파싱/본문별 캐시는 아래 ChatTimelineBuilder가 맡고 각 블록 표시는 MarkdownBlockText로 이어진다.
 */
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
            MarkdownBlock.Divider -> "answerDivider"
        }
    }
    data class Footer(val message: ChatMessageUi) : ChatTimelineRow {
        override val key = "${message.id}:footer"
        override val contentType = "answerFooter"
    }
}

internal class ChatTimelineBuilder(private val parse: (String) -> List<MarkdownBlock> = ::markdownBlocks) {
    private data class Parsed(val text: String, val rows: List<ChatTimelineRow.Body>)
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
                        val previous = parsed[message.id]
                        val cached = previous?.takeIf { it.text == message.text } ?: run {
                            val rows = parse(message.text).mapIndexed { index, block ->
                                previous?.rows?.getOrNull(index)?.takeIf { it.block == block }
                                    ?: ChatTimelineRow.Body(message.id, index, block)
                            }
                            Parsed(message.text, rows).also { parsed[message.id] = it }
                        }
                        addAll(cached.rows)
                    } else parsed.remove(message.id)
                    add(ChatTimelineRow.Footer(message))
                }
            }
        }
    }
}
