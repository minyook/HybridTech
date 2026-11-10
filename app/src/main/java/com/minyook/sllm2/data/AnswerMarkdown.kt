/* 소스 안내
 * 모델/문서 Markdown의 표시 정리와 완결 경계를 관리한다. ViewModel의 부분 공개와 UI 표시가 사용하며 원문 수치나 조건을 요약하지 않는다.
 */
package com.minyook.sllm2.data

/** Display-only cleanup. Does not shorten evidence, remove conditions, or rewrite measurements. */
object AnswerMarkdown {
    private val wrapper = Regex("^```(?:markdown|md)\\s*\\n", RegexOption.IGNORE_CASE)
    private val bullet = Regex("^(\\s*)[*+]\\s+")
    private val ordered = Regex("^(\\s*)(\\d+)[)]\\s+")
    private val sentenceEnd = Regex("(?<!\\d)[.!?。！？](?:[’”\\\"')\\]]|\\*\\*|`)*(?=\\s|$)")
    private val listLine = Regex("(?:[-*+] |\\d+[.)] ).+")
    private val dividerLine = Regex("[-*_ ]+")
    private val numbered = Regex("(?<![~～①-⑳])([①-⑳])\\s+(?=[가-힣A-Za-z])")
    private val checklistHeader = Regex("^밀폐공간\\s*작업\\s*시\\s*준수사항\\s*\\(산업안전보건기준에\\s*관한\\s*규칙\\)\\s*항\\s*목\\s*[○×]*\\s*위\\s*반\\s*사\\s*항\\s*및\\s*조\\s*치\\s*")
    private val brokenEnding = Regex("(하십시|하십|있습|없습|습니|합니|됩니)\\s+(오|시오|니다|다)(?=[.?!,\\s]|$)")
    private val measurement = Regex("\\d+(?:\\.\\d+)?(?:\\s*[~～]\\s*\\d+(?:\\.\\d+)?)?\\s*(?:%|ppm|cm|mmAq|m3/min|미터|센티미터|초|분|시간|회|개소)(?:\\s*(?:이상|미만|이하|초과))?", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ').replace("\u200b", "").trim()
        if (wrapper.containsMatchIn(text)) text = text.replaceFirst(wrapper, "").removeSuffix("```").trim()
        var fence: String? = null
        val lines = text.lines().map { original ->
            val line = original.trimEnd()
            val marker = fenceMarker(line)
            if (marker != null) {
                if (fence == null) fence = marker else if (marker.first() == fence!!.first() && marker.length >= fence!!.length) fence = null
                line
            } else if (fence != null) line
            else line.replace(bullet, "$1- ").replace(ordered, "$1$2. ")
        }
        // Empty lines inside literal code are intentional.
        val output = mutableListOf<String>(); fence = null
        for (line in lines) {
            val marker = fenceMarker(line)
            if (marker != null) { if (fence == null) fence = marker else if (marker.first() == fence!!.first() && marker.length >= fence!!.length) fence = null }
            if (line.isBlank() && fence == null && output.lastOrNull()?.isBlank() == true) continue
            output += line
        }
        // A stopped generation must not leave all following UI text inside a code block.
        if (fence != null) output += fence!!
        val formatted = output.joinToString("\n").trim()
        return formatted + closingInlineMarkers(formatted)
    }

    /** Only completed blocks/sentences are published while generation is still in progress. */
    fun preview(raw: String): String? = previewPrefix(raw)?.let(::formatPreview)

    internal fun previewPrefix(raw: String): String? {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
        if (wrapper.containsMatchIn(text)) text = text.replaceFirst(wrapper, "").removeSuffix("```")
        var fence: String? = null; var offset = 0; var boundary = 0
        for (line in text.split('\n')) {
            val marker = fenceMarker(line)
            if (marker != null) {
                if (fence == null) fence = marker else if (marker.first() == fence!!.first() && marker.length >= fence!!.length) { fence = null; boundary = offset + line.length }
            } else if (fence == null) {
                if (line.isBlank()) boundary = offset
                else if (!line.trimStart().startsWith('#') && !line.contains('|')) {
                    sentenceEnd.findAll(line).lastOrNull()?.let { boundary = offset + it.range.last + 1 }
                    if (offset + line.length < text.length && line.trimStart().matches(listLine)) boundary = offset + line.length
                }
            }
            offset += line.length + 1
        }
        return if (boundary == 0) null else text.take(boundary).trim()
    }

    internal fun formatPreview(prefix: String): String? {
        // Do not show a half-open bold/code span or an isolated title as the first answer.
        if (closingInlineMarkers(prefix).isNotEmpty()) return null
        val body = normalize(prefix)
        if (body.lineSequence().none { it.isNotBlank() && !it.trimStart().startsWith('#') && !it.matches(dividerLine) }) return null
        return body
    }

    fun evidenceLines(raw: String): List<String> {
        val clean = raw.replace('\u00a0', ' ').replace("\u200b", "").replace(Regex("[ \\t]+"), " ").trim()
            .replace(checklistHeader, "").replace(brokenEnding, "$1$2")
            .replace(Regex("산소결\\s+핍증"),"산소결핍증").replace(Regex("일\\s+산화탄소"),"일산화탄소")
            .replace(Regex("호\\s+흡"),"호흡").replace(Regex("유기\\s+물"),"유기물").replace(Regex("일\\s+으킵니다"),"일으킵니다")
        if(clean.startsWith("| ") && clean.lines().getOrNull(1)?.contains("---")==true)return listOf(clean)
        return clean.replace(numbered) { match ->
            val prefix = if (match.range.first == 0) "" else "\n"
            "$prefix${match.groupValues[1]} "
        }.lines().map(String::trim).filter(String::isNotBlank).map { line ->
            val marker = Regex("^([①-⑳])\\s+").find(line)?.groupValues?.get(1)?.first()
            val text = if (marker != null) line.drop(1).trim() else line.replace(Regex("^[•◆*\\-]\\s*"), "")
            val visible = text.replace(measurement) { "**${it.value}**" }
            if (marker == null) "- $visible" else "${marker.code - '①'.code + 1}. $visible"
        }
    }

    private fun closingInlineMarkers(text: String): String {
        val stack = mutableListOf<String>(); var fence: String? = null
        for (line in text.lines()) {
            val marker = fenceMarker(line)
            if (marker != null) { if (fence == null) fence = marker else if (marker.first() == fence!!.first() && marker.length >= fence!!.length) fence = null; continue }
            if (fence != null) continue
            var index = 0
            while (index < line.length) {
                val char = line[index]
                if (char == '\\') { index += 2; continue }
                val code = stack.lastOrNull()?.takeIf { it.startsWith('`') }
                if (code != null && char != '`') { index++; continue }
                if (char in "`*_") {
                    val run = line.drop(index).takeWhile { it == char }
                    if (char == '`') {
                        if (code == run) stack.removeAt(stack.lastIndex) else if (code == null) stack += run
                    } else if (code == null && (char != '_' || run.length >= 2)) {
                        val before = line.getOrNull(index - 1)?.isWhitespace() == false
                        val after = line.getOrNull(index + run.length)?.isWhitespace() == false
                        if (stack.lastOrNull() == run && before) stack.removeAt(stack.lastIndex)
                        else if (after && run.length <= 3) stack += run
                    }
                    index += run.length
                } else index++
            }
        }
        return stack.asReversed().joinToString("")
    }

    fun fenceMarker(line: String): String? {
        val trimmed = line.trimStart()
        val first = trimmed.firstOrNull() ?: return null
        if (first != '`' && first != '~') return null
        val run = trimmed.takeWhile { it == first }
        return run.takeIf { it.length >= 3 }
    }
}
