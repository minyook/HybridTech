package com.minyook.sllm2

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.widget.TextView

/** Deliberately small offline Markdown renderer for RAG and model answers. */
internal object MarkdownText {
    private val heading = Regex("^(#{1,3})\\s+(.+?)\\s*#*\\s*$")
    private val unorderedItem = Regex("^\\s*[-*+]\\s+(.+)$")
    private val orderedItem = Regex("^\\s*(\\d+)[.)]\\s+(.+)$")
    private val horizontalRule = Regex("^\\s{0,3}([-*_])(?:\\s*\\1){2,}\\s*$")
    private const val CODE_BACKGROUND = 0x16000000

    fun applyTo(view: TextView, markdown: String) {
        view.text = render(markdown)
        view.linksClickable = true
        view.movementMethod = LinkMovementMethod.getInstance()
        view.highlightColor = Color.TRANSPARENT
    }

    fun render(markdown: String): CharSequence {
        val result = SpannableStringBuilder()
        var inCodeFence = false
        markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { rawLine ->
            if (rawLine.trimStart().startsWith("```")) {
                inCodeFence = !inCodeFence
                return@forEach
            }
            if (result.isNotEmpty()) result.append('\n')
            val start = result.length
            if (inCodeFence) {
                result.append(rawLine)
                applySpan(result, TypefaceSpan("monospace"), start, result.length)
                applySpan(result, BackgroundColorSpan(CODE_BACKGROUND), start, result.length)
                return@forEach
            }
            when {
                horizontalRule.matches(rawLine) -> result.append("────────")
                heading.matches(rawLine) -> {
                    val match = heading.matchEntire(rawLine) ?: return@forEach
                    appendInline(result, match.groupValues[2])
                    applySpan(result, StyleSpan(Typeface.BOLD), start, result.length)
                    applySpan(result, RelativeSizeSpan(when (match.groupValues[1].length) {
                        1 -> 1.35f
                        2 -> 1.20f
                        else -> 1.10f
                    }), start, result.length)
                }
                rawLine.trimStart().startsWith(">") -> {
                    result.append("│ ")
                    appendInline(result, rawLine.trimStart().removePrefix(">").trimStart())
                    applySpan(result, StyleSpan(Typeface.ITALIC), start, result.length)
                }
                unorderedItem.matches(rawLine) -> {
                    result.append("• ")
                    appendInline(result, unorderedItem.matchEntire(rawLine)?.groupValues?.get(1).orEmpty())
                }
                orderedItem.matches(rawLine) -> {
                    val match = orderedItem.matchEntire(rawLine) ?: return@forEach
                    result.append(match.groupValues[1]).append(". ")
                    appendInline(result, match.groupValues[2])
                }
                else -> appendInline(result, rawLine)
            }
        }
        return result
    }

    private fun appendInline(output: SpannableStringBuilder, value: String) {
        var index = 0
        while (index < value.length) {
            if (value[index] == '\\' && index + 1 < value.length) {
                output.append(value[index + 1])
                index += 2
                continue
            }
            if (value[index] == '[') {
                val closeBracket = value.indexOf(']', index + 1)
                val openParenthesis = closeBracket + 1
                val closeParenthesis = if (openParenthesis in value.indices && value[openParenthesis] == '(') {
                    value.indexOf(')', openParenthesis + 1)
                } else -1
                if (closeBracket > index && closeParenthesis > openParenthesis) {
                    val target = value.substring(openParenthesis + 1, closeParenthesis)
                    if (target.startsWith("https://", true) || target.startsWith("http://", true)) {
                        val start = output.length
                        appendInline(output, value.substring(index + 1, closeBracket))
                        applySpan(output, URLSpan(target), start, output.length)
                        index = closeParenthesis + 1
                        continue
                    }
                }
            }
            val next = delimiterSpan(output, value, index, "**", StyleSpan(Typeface.BOLD))
                ?: delimiterSpan(output, value, index, "__", StyleSpan(Typeface.BOLD))
                ?: delimiterSpan(output, value, index, "~~", android.text.style.StrikethroughSpan())
                ?: delimiterSpan(output, value, index, "`", TypefaceSpan("monospace"), BackgroundColorSpan(CODE_BACKGROUND))
                ?: if (value[index] == '*' || value[index] == '_') {
                    delimiterSpan(output, value, index, value[index].toString(), StyleSpan(Typeface.ITALIC))
                } else null
            if (next != null) {
                index = next
            } else {
                output.append(value[index])
                index++
            }
        }
    }

    private fun delimiterSpan(
        output: SpannableStringBuilder,
        value: String,
        startIndex: Int,
        marker: String,
        vararg spans: Any,
    ): Int? {
        if (!value.startsWith(marker, startIndex)) return null
        val closeIndex = value.indexOf(marker, startIndex + marker.length)
        if (closeIndex <= startIndex + marker.length) return null
        val start = output.length
        appendInline(output, value.substring(startIndex + marker.length, closeIndex))
        spans.forEach { applySpan(output, it, start, output.length) }
        return closeIndex + marker.length
    }

    private fun applySpan(output: SpannableStringBuilder, span: Any, start: Int, end: Int) {
        if (end > start) output.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
