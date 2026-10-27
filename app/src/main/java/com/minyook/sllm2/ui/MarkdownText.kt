package com.minyook.sllm2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small offline Markdown renderer for model and RAG answers. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        markdownBlocks(markdown).forEach { block ->
            if(block is MarkdownBlock.Code) {
                Surface(color=Color(0xFF202B3E),shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth().padding(vertical=15.dp)) {
                    Text(block.text,fontFamily=FontFamily.Monospace,fontSize=12.sp,lineHeight=21.sp,color=Color(0xFFE2EAF8),modifier=Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=19.dp,vertical=17.dp))
                }
                return@forEach
            }
            if(block is MarkdownBlock.Table) {
                Surface(color=Color.White,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Color(0xFFE1E8F2)),modifier=Modifier.fillMaxWidth().padding(vertical=15.dp)) {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        block.rows.forEachIndexed { rowIndex,row ->
                            Row(Modifier.background(if(rowIndex==0)Color(0xFFEDF4FF) else if(rowIndex%2==0)Color(0xFFFAFCFF) else Color.White)) {
                                row.forEach { cell -> Text(inlineMarkdown(cell),fontSize=13.sp,lineHeight=22.sp,fontWeight=if(rowIndex==0)FontWeight.Bold else FontWeight.Normal,color=if(rowIndex==0)Color(0xFF3E5E88) else Color(0xFF4E5968),modifier=Modifier.width(160.dp).padding(horizontal=15.dp,vertical=12.dp)) }
                            }
                            if(rowIndex<block.rows.lastIndex)HorizontalDivider(color=Color(0xFFE8EDF4))
                        }
                    }
                }
                return@forEach
            }
            val original=(block as MarkdownBlock.Line).text
            val line = original.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.padding(vertical = 3.dp))
                line.startsWith(">") || line.startsWith("※") -> Text(
                    inlineMarkdown(if (line.startsWith(">")) line.removePrefix(">").trim() else line),
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFEEF5FF)).padding(horizontal = 17.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF5A7294)),
                )
                line.startsWith("### ") -> Text(inlineMarkdown(line.removePrefix("### ")), fontSize=15.sp,lineHeight=24.sp,fontWeight=FontWeight.Bold,color=Color(0xFF24344B), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                line.startsWith("## ") -> Text(inlineMarkdown(line.removePrefix("## ")),fontSize=18.sp,lineHeight=28.sp,fontWeight=FontWeight.Bold,color=Color(0xFF24344B), modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                line.startsWith("# ") -> Text(inlineMarkdown(line.removePrefix("# ")),fontSize=23.sp,lineHeight=35.sp,fontWeight=FontWeight.Bold,color=Color(0xFF24344B), modifier = Modifier.padding(top = 24.dp, bottom = 13.dp))
                line.startsWith("- ") || line.startsWith("* ") -> MarkdownListItem("•", line.drop(2))
                line.matches(Regex("^\\d+[.)]\\s+.*")) -> MarkdownListItem(line.takeWhile { !it.isWhitespace() }, line.substringAfter(' '))
                line.isNotEmpty() && line[0] in "•□○●❶❷❸❹❺❻❼❽❾❿①②③④⑤⑥⑦⑧⑨⑩" -> MarkdownListItem(line.take(1), line.drop(1).trim())
                else -> Text(inlineMarkdown(line), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 1.dp))
            }
        }
    }
}

@Composable private fun MarkdownListItem(marker: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(marker, fontSize=13.sp,lineHeight=24.sp,color=Color(0xFF6B7F98),modifier=Modifier.width(22.dp))
        Text(inlineMarkdown(body), fontSize=14.sp,lineHeight=24.sp,color=Color(0xFF38485B),modifier=Modifier.weight(1f))
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
                addStyle(SpanStyle(fontWeight = FontWeight.Bold,color=Color(0xFF243E62)), start, length)
            }
            else -> {
                append(value.removePrefix("`").removeSuffix("`"))
                addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFEDF2F9),color=Color(0xFF315C91)), start, length)
            }
        }
        cursor = match.range.last + 1
    }
    append(source.substring(cursor))
}
