/* 소스 안내
 * 검증된 생성 접두부를 프레임별 작은 묶음으로 표시한다. 원문/기록과 모델 출력 한도는 바꾸지 않는다.
 * 연결 모듈: com.minyook.sllm2.data.AnswerMarkdown, com.minyook.sllm2.data.GeneralKnowledgeAnswer.
 */
package com.minyook.sllm2.ui

import androidx.compose.runtime.*
import com.minyook.sllm2.data.AnswerMarkdown
import com.minyook.sllm2.data.GeneralKnowledgeAnswer
import kotlin.math.max

/**
 * 검사기가 승인한 생성 본문만 작은 글자 묶음으로 표시하는 UI 버퍼.
 * ViewModel이 갱신한 전체 본문인 target과 화면에서 보이는 접두부인 visible을 분리한다.
 * 문서 본문·복원 기록·정정은 즉시 반영하고 생성 완료 시 남은 표시를 짧은 시간 안에 따라잡는다.
 * 모델의 미검증 토큰을 공개하거나 저장 기록·최종 복사 본문을 바꾸지 않는다.
 */
internal class StreamingTextReveal(initial: String) {
    var visible: String = initial
        private set
    private var target = initial
    private var generating = false
    private var lastFrame = 0L
    private var finishingAt = 0L
    private var credit = 0.0

    fun begin(text: String) {
        val notice=text.indexOf(GeneralKnowledgeAnswer.NOTICE)
        visible=if(notice<0) "" else text.take((notice+GeneralKnowledgeAnswer.NOTICE.length+2).coerceAtMost(text.length))
        credit=0.0
    }

    fun update(text: String, pending: Boolean, animate: Boolean, now: Long) {
        target = text
        generating = pending
        if (!animate || !text.startsWith(visible)) {
            // Corrections/rejections replace stale text immediately, never animate unsafe remnants.
            visible = text
            credit = 0.0
        } else {
            val notice = text.indexOf(GeneralKnowledgeAnswer.NOTICE)
            if (notice >= 0) {
                val fixedEnd = (notice + GeneralKnowledgeAnswer.NOTICE.length + 2).coerceAtMost(text.length)
                if (visible.length < fixedEnd) visible = text.take(fixedEnd)
            }
        }
        if (pending) finishingAt = 0L
        else if (visible != target && finishingAt == 0L) finishingAt = now + 160L
        lastFrame = now
    }

    val running: Boolean get() = visible != target

    fun frame(now: Long): Boolean {
        if (!running) { lastFrame = now; return false }
        val elapsed = (now - lastFrame).coerceIn(0L, 50L)
        lastFrame = now
        val remaining = target.length - visible.length
        val rate = if (generating) max(64.0, remaining / 2.0).coerceAtMost(180.0)
            else remaining * 1000.0 / (finishingAt - now).coerceAtLeast(16L)
        credit += elapsed * rate / 1000.0
        val count = if (!generating && now >= finishingAt) remaining else credit.toInt().coerceAtMost(remaining)
        if (count <= 0) return false
        credit -= count
        var end = (visible.length + count).coerceAtMost(target.length)
        // Keep UTF-16 pairs, combining marks, Markdown delimiters and table/code blocks whole.
        if (end < target.length && Character.isHighSurrogate(target[end - 1]) && Character.isLowSurrogate(target[end])) end++
        while (end < target.length && (target[end] in "\uFE0F\uFE0E\u200D" || target.getOrNull(end - 1) == '\u200D' ||
                    Character.getType(target[end]) in setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt()))) end++
        if (end < target.length && Character.isHighSurrogate(target[end - 1]) && Character.isLowSurrogate(target[end])) end++
        while (end < target.length && target[end] in "*_`" && target[end] == target[end - 1]) end++
        if (end < target.length && target[end - 1] in "*_`" && !target[end].isWhitespace()) end++
        val startOfLine = target.lastIndexOf('\n', (end - 1).coerceAtLeast(0)) + 1
        val endOfLine = target.indexOf('\n', startOfLine).let { if (it < 0) target.length else it }
        val line = target.substring(startOfLine, endOfLine)
        if (line.contains('|') || AnswerMarkdown.fenceMarker(line) != null) {
            // Source tables and literal code should appear as complete structures, not flickering cells.
            val blocks = target.substring(startOfLine).lines()
            var blockEnd = startOfLine
            val fence = AnswerMarkdown.fenceMarker(line)
            for ((index, value) in blocks.withIndex()) {
                if (fence == null && index > 0 && !value.contains('|')) break
                blockEnd = (blockEnd + value.length + 1).coerceAtMost(target.length)
                if (fence != null && index > 0 && AnswerMarkdown.fenceMarker(value)?.first() == fence.first()) break
            }
            end = max(end, blockEnd)
        } else {
            val marker = Regex("^\\s*(?:#{1,6}\\s+|[-*+]\\s+|\\d+[.)]\\s+|>\\s*)").find(line)
            if (marker != null && end <= startOfLine + marker.value.length)
                end = (startOfLine + marker.value.length + 1).coerceAtMost(target.length)
        }
        if (end < target.length && Character.isHighSurrogate(target[end - 1]) && Character.isLowSurrogate(target[end])) end++
        val changed = end > visible.length
        visible = target.take(end)
        return changed
    }
}

@Composable internal fun rememberRevealedMessage(message: ChatMessageUi): ChatMessageUi {
    val reveal = remember(message.id) { StreamingTextReveal(message.text) }
    var shown by remember(message.id) { mutableStateOf(message.text) }
    var running by remember(message.id) { mutableStateOf(false) }
    var observedAnimation by remember(message.id) { mutableStateOf(message.animateAnswer) }
    // A restored/completed message starts fully visible; revisiting a chat never replays old answers.
    LaunchedEffect(message.id, message.text, message.pending, message.animateAnswer) {
        val now = withFrameNanos { it / 1_000_000L }
        if(message.animateAnswer && !observedAnimation && message.pending)reveal.begin(message.text)
        observedAnimation=message.animateAnswer
        reveal.update(message.text, message.pending, message.animateAnswer, now)
        shown = reveal.visible
        running = reveal.running
        var lastPublishedFrame = now
        while (reveal.running) {
            withFrameNanos { frame ->
                val milliseconds = frame / 1_000_000L
                // Batch reveal updates so Markdown and the lazy list lay out less often.
                if (milliseconds - lastPublishedFrame >= 64L) {
                    if (reveal.frame(milliseconds)) shown = reveal.visible
                    running = reveal.running
                    lastPublishedFrame = milliseconds
                }
            }
        }
    }
    if (shown == message.text && !running) return message
    val formatted = remember(shown) { AnswerMarkdown.normalize(shown) }
    return message.copy(text = formatted, pending = message.pending || running)
}
