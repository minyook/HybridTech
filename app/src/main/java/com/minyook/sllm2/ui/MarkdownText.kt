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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small offline Markdown renderer for model and RAG answers. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { markdownBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEach { MarkdownBlockText(it) }
    }
}

@Composable internal fun MarkdownBlockText(block: MarkdownBlock) {
            if(block is MarkdownBlock.Code) {
                Surface(color=Color(0xFF202B3E),shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth().padding(vertical=15.dp)) {
                    Text(block.text,fontFamily=FontFamily.Monospace,fontSize=12.sp,lineHeight=21.sp,color=Color(0xFFE2EAF8),modifier=Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=19.dp,vertical=17.dp))
                }
                return
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
                return
            }
            if (block == MarkdownBlock.Divider) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE8EDF4)); return
            }
            val original=(block as MarkdownBlock.Line).text
            val line = original.trim()
            val list = markdownListEntry(original)
            val heading = Regex("^(#{1,6})\\s+(.+?)(?:\\s+#+)?$").find(line)
            when {
                line.isBlank() -> Spacer(Modifier.padding(vertical = 3.dp))
                line.startsWith(">") || line.startsWith("※") -> Text(
                    inlineMarkdown(if (line.startsWith(">")) line.removePrefix(">").trim() else line),
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFEEF5FF)).padding(horizontal = 17.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF5A7294)),
                )
                heading != null -> {
                    val level = heading.groupValues[1].length
                    Text(inlineMarkdown(heading.groupValues[2]), fontSize = (if(level == 1) 21 else if(level == 2) 18 else 15).sp,
                        lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF24344B),
                        modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
                }
                list != null -> MarkdownListItem(list)
                else -> Text(inlineMarkdown(line), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 26.sp), modifier = Modifier.padding(vertical = 1.dp))
            }
}

@Composable private fun MarkdownListItem(item: MarkdownListEntry) {
    Row(Modifier.fillMaxWidth().padding(start = (item.depth * 12).dp).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item.marker, fontSize = 13.sp, lineHeight = 26.sp, color = Color(0xFF6B7F98), modifier = Modifier.width(maxOf(22, item.marker.length * 8).dp))
        Text(inlineMarkdown(item.body), fontSize = 15.sp, lineHeight = 26.sp, color = Color(0xFF38485B), modifier = Modifier.weight(1f))
    }
}

@Composable private fun inlineMarkdown(source: String): AnnotatedString = remember(source) { buildAnnotatedString {
    markdownInline(source).forEach { run ->
        val start = length
        append(run.text)
        addStyle(SpanStyle(
            fontWeight = if(run.bold) FontWeight.Bold else null,
            fontStyle = if(run.italic) FontStyle.Italic else null,
            fontFamily = if(run.code) FontFamily.Monospace else null,
            background = if(run.code) Color(0xFFEDF2F9) else Color.Unspecified,
            color = if(run.code) Color(0xFF315C91) else if(run.bold) Color(0xFF243E62) else Color.Unspecified,
        ), start, length)
    }
} }
