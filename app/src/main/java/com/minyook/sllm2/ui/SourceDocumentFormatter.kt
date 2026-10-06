package com.minyook.sllm2.ui

/** Adds display-only paragraph breaks to PDF text without rewriting source words or numbers. */
internal object SourceDocumentFormatter {
    private val sourceMarkers = Regex("(?<=\\S)\\s*(?=[※□○●•❶-❿①-⑳])")
    private val listMarkers = Regex("(?<=\\S)\\s+(?=[-*]\\s)")
    private val sentenceBreaks = Regex("(?<=[.!?])\\s+(?=[가-힣“‘(])")
    private val extraNewlines = Regex("\\n{3,}")

    fun format(body: String): String = body
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(sourceMarkers, "\n\n")
        .replace(listMarkers, "\n\n")
        .replace(sentenceBreaks, "\n\n")
        .replace(extraNewlines, "\n\n")
        .trim()
}
