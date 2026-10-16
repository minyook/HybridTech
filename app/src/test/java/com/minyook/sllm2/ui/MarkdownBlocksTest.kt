package com.minyook.sllm2.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlocksTest {
    @Test fun tableKeepsUnitsAndEscapedPipes() {
        val table=markdownBlocks("| 항목 | 값 |\n| --- | --- |\n| O₂ | 18~23.5% |\n| 방법 | A\\|B |").single() as MarkdownBlock.Table
        assertEquals(listOf("O₂","18~23.5%"),table.rows[1])
        assertEquals("A|B",table.rows[2][1])
    }
    @Test fun fencedCodeKeepsLiteralMarkdown() {
        val block=markdownBlocks("```text\n**원문**\n| A | B |\n```").single() as MarkdownBlock.Code
        assertEquals("**원문**\n| A | B |",block.text)
    }
    @Test fun incompleteStreamingFenceKeepsItsContent() {
        assertEquals("18~23.5",(markdownBlocks("```\n18~23.5").single() as MarkdownBlock.Code).text)
    }
    @Test fun plainPipesAndHtmlRemainText() {
        val blocks=markdownBlocks("A | B\n18~23.5%\n<img src='https://example.invalid/a'>")
        assertTrue(blocks.all{it is MarkdownBlock.Line})
        assertEquals("18~23.5%",(blocks[1] as MarkdownBlock.Line).text)
    }
}
