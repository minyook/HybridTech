package com.minyook.sllm2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small offline Markdown renderer for model and RAG answers. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        markdown.lines().forEach { original ->
            val line = original.trimEnd()
            when {
                line.isBlank() -> Text("", modifier = Modifier.padding(vertical = 2.dp))
                line.startsWith(">") || line.startsWith("※") -> Text(
                    inlineMarkdown(if (line.startsWith(">")) line.removePrefix(">").trim() else line),
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F3FF)).padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF4E5968), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                )
                line.startsWith("### ") -> Text(inlineMarkdown(line.removePrefix("### ")), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 5.dp, bottom = 2.dp))
                line.startsWith("## ") -> Text(inlineMarkdown(line.removePrefix("## ")), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
                line.startsWith("# ") -> Text(inlineMarkdown(line.removePrefix("# ")), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    inlineMarkdown("• " + line.replaceFirst(Regex("^(?:[-*]|\\d+[.)])\\s+"), "")),
                    style = MaterialTheme.typography.bodyLarge.copy(textIndent = TextIndent(firstLine = 0.sp, restLine = 14.sp)),
                    modifier = Modifier.padding(vertical = 1.dp),
                )
                line.matches(Regex("^\\d+[.)]\\s+.*")) || (line.isNotEmpty() && line[0] in "•□○●❶❷❸❹❺❻❼❽❾❿①②③④⑤⑥⑦⑧⑨⑩") -> Text(
                    inlineMarkdown(line),
                    style = MaterialTheme.typography.bodyLarge.copy(textIndent = TextIndent(firstLine = 0.sp, restLine = 14.sp)),
                    modifier = Modifier.padding(vertical = 1.dp),
                )
                else -> Text(inlineMarkdown(line), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 1.dp))
            }
        }
    }
}

private fun inlineMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    val token = Regex("(\\*\\*.+?\\*\\*)|(`.+?`)")
    var cursor = 0
    token.findAll(source).forEach { match ->
        append(source.substring(cursor, match.range.first))
        val value = match.value
        val start = length
        when {
            value.startsWith("**") -> {
                append(value.removePrefix("**").removeSuffix("**"))
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
            }
            else -> {
                append(value.removePrefix("`").removeSuffix("`"))
                addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFF2F4F6)), start, length)
            }
        }
        cursor = match.range.last + 1
    }
    append(source.substring(cursor))
}
