package com.minyook.sllm2.ui

import com.minyook.sllm2.data.AnswerMarkdown

internal sealed interface MarkdownBlock {
    data class Line(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data class Table(val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

internal fun markdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    val result = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val fence = AnswerMarkdown.fenceMarker(line)
        if (fence != null) {
            val body = mutableListOf<String>(); index++
            while (index < lines.size) {
                val closing = AnswerMarkdown.fenceMarker(lines[index])
                if (closing != null && closing.first() == fence.first() && closing.length >= fence.length) break
                body += lines[index++]
            }
            if (index < lines.size) index++
            result += MarkdownBlock.Code(body.joinToString("\n")); continue
        }
        if (line.trim().matches(Regex("(?:(?:-\\s*){3,}|(?:\\*\\s*){3,}|(?:_\\s*){3,})"))) {
            result += MarkdownBlock.Divider; index++; continue
        }
        if (index + 1 < lines.size && line.contains('|')) {
            val header = markdownCells(line); val separator = markdownCells(lines[index + 1])
            if (header.size > 1 && separator.size == header.size && separator.all { it.matches(Regex(":?-{3,}:?")) }) {
                val rows = mutableListOf(header); index += 2
                while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) rows += markdownCells(lines[index++])
                // A malformed/generated row may contain extra cells: never silently discard their text.
                val columns = rows.maxOf { it.size }
                result += MarkdownBlock.Table(rows.map { row -> List(columns) { row.getOrElse(it) { "" } } }); continue
            }
        }
        result += MarkdownBlock.Line(line); index++
    }
    return result
}

private fun markdownCells(raw: String): List<String> {
    var line = raw.trim().removePrefix("|")
    if (line.endsWith('|') && !line.endsWith("\\|")) line = line.dropLast(1)
    val cells = mutableListOf<String>(); val cell = StringBuilder(); var index = 0; var ticks = 0
    while (index < line.length) {
        val char = line[index]
        if(ticks==0 && char=='<') {
            val br=Regex("^<br\\s*/?>",RegexOption.IGNORE_CASE).find(line.substring(index))
            if(br!=null) { cell.append('\n');index+=br.value.length;continue }
        }
        if (char == '\\' && index + 1 < line.length && line[index + 1] == '|') { cell.append('|'); index += 2; continue }
        if (char == '`') {
            val run = line.drop(index).takeWhile { it == '`' }.length
            ticks = if (ticks == 0) run else if (ticks == run) 0 else ticks
            cell.append("`".repeat(run)); index += run; continue
        }
        if (char == '|' && ticks == 0) { cells += cell.toString().trim(); cell.clear() } else cell.append(char)
        index++
    }
    cells += cell.toString().trim()
    return cells
}

/** Compact three-column source tables fit phones; wide data tables keep readable cells. */
internal fun markdownTableWidths(columns:Int,available:Float):List<Float> {
    if(columns<=0)return emptyList()
    val minimum=when(columns) { 1->listOf(160f);2->listOf(104f,190f);3->listOf(96f,124f,112f);else->List(columns){104f} }
    val weights=when(columns) { 1->listOf(1f);2->listOf(.35f,.65f);3->listOf(.25f,.45f,.30f);else->List(columns){1f/columns} }
    val extra=(available-minimum.sum()-(columns-1)).coerceAtLeast(0f)
    return minimum.mapIndexed { i,width -> width+extra*weights[i] }
}

internal data class MarkdownListEntry(val marker: String, val body: String, val depth: Int)
internal fun markdownListEntry(line: String): MarkdownListEntry? {
    val indent = line.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') 4 else 1 }
    val body = line.trimStart()
    val match = Regex("^([-*+]|\\d+[.)])\\s+(.*)$").find(body) ?: Regex("^([•□○●❶-❿①-⑳])\\s*(.*)$").find(body)
    match?.let {
        val marker = it.groupValues[1]
        // A bare minus followed by a number is prose, not necessarily a Markdown list.
        if (marker in listOf("-", "*", "+") && !body.startsWith("$marker ") && !body.startsWith("$marker\t")) return null
        return MarkdownListEntry(if (marker in listOf("-", "*", "+")) "•" else marker, it.groupValues[2], (indent / 2).coerceAtMost(4))
    }
    return null
}

internal data class InlineRun(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)
internal fun markdownInline(source: String, bold: Boolean = false, italic: Boolean = false): List<InlineRun> {
    val result = mutableListOf<InlineRun>(); val plain = StringBuilder(); var index = 0
    fun flush() { if (plain.isNotEmpty()) { result += InlineRun(plain.toString(), bold, italic); plain.clear() } }
    while (index < source.length) {
        if (source[index] == '\\' && index + 1 < source.length && source[index + 1] in "*_`[]\\|") { plain.append(source[index + 1]); index += 2; continue }
        if (source[index] == '`') {
            val marker = source.drop(index).takeWhile { it == '`' }
            val end = source.indexOf(marker, index + marker.length)
            if (end >= 0) { flush(); result += InlineRun(source.substring(index + marker.length, end), bold, italic, true); index = end + marker.length; continue }
        }
        val delimiter = when { source.startsWith("***", index) -> "***"; source.startsWith("**", index) -> "**"; source.startsWith("__", index) -> "__"; source[index] == '*' -> "*"; else -> null }
        if (delimiter != null) {
            val end = source.indexOf(delimiter, index + delimiter.length)
            if (end > index + delimiter.length) {
                flush(); result += markdownInline(source.substring(index + delimiter.length, end), bold || delimiter.length >= 2, italic || delimiter.length == 1 || delimiter.length == 3)
                index = end + delimiter.length; continue
            }
        }
        // Keep link destinations visible; displaying model text must not open a browser implicitly.
        if (source[index] == '[') {
            val endLabel = source.indexOf("](", index + 1)
            val endUrl = if (endLabel >= 0) source.indexOf(')', endLabel + 2) else -1
            if (endUrl > endLabel + 2) {
                flush(); result += markdownInline(source.substring(index + 1, endLabel), bold, italic)
                result += InlineRun(" (${source.substring(endLabel + 2, endUrl)})", bold, italic)
                index = endUrl + 1; continue
            }
        }
        plain.append(source[index++])
    }
    flush(); return result
}
