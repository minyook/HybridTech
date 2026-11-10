/* 소스 안내
 * 표 셀·중첩 목록·강조/코드 파싱을 검증하는 JVM 회귀 검사다.
 * 검사 예: cellBreaksAreRenderedWithoutSplittingRowsOrChangingInlineCode, phoneTablesFitAndWideTablesKeepReadableMinimumWidths, tableKeepsUnitsAndEscapedPipes
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlocksTest {
    @Test fun cellBreaksAreRenderedWithoutSplittingRowsOrChangingInlineCode() {
        val table=markdownBlocks("|가스|위험|\n|---|---|\n|메탄|화재<br>상부 축적<BR />무취|\n|코드|`<br>`|").single() as MarkdownBlock.Table
        assertEquals("화재\n상부 축적\n무취",table.rows[1][1])
        assertEquals("`<br>`",table.rows[2][1])
    }
    @Test fun phoneTablesFitAndWideTablesKeepReadableMinimumWidths() {
        assertEquals(370f,markdownTableWidths(3,370f).sum()+2,.01f)
        assertTrue(markdownTableWidths(3,280f).all{it>=96f})
        assertTrue(markdownTableWidths(12,370f).all{it>=104f})
        assertEquals(emptyList<Float>(),markdownTableWidths(0,370f))
    }
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
    @Test fun raggedTableRowsNeverLoseExtraCells() {
        val table = markdownBlocks("|A|B|\n|---|---|\n|1|2|기존에는 사라진 내용|").single() as MarkdownBlock.Table
        assertEquals(3, table.rows[0].size)
        assertEquals("기존에는 사라진 내용", table.rows[1][2])
    }
    @Test fun literalPipesInsideCodeStayInOneCell() {
        val table = markdownBlocks("|항목|값|\n|---|---|\n|원문|`A|B`|").single() as MarkdownBlock.Table
        assertEquals(listOf("원문", "`A|B`"), table.rows[1])
    }
    @Test fun nestedListsAndLongNumbersKeepTheirBody() {
        assertEquals(MarkdownListEntry("•", "조건을 확인합니다.", 2), markdownListEntry("    *   조건을 확인합니다."))
        assertEquals("100.", markdownListEntry("100. 마지막 항목")!!.marker)
        assertNull(markdownListEntry("23.5% 미만"))
        assertNull(markdownListEntry("-12도"))
    }
    @Test fun inlineEmphasisCodeLinksAndDividersAreParsedWithoutRawSyntax() {
        val runs = markdownInline("**강조**와 *설명*, `A|B`, [원문](https://example.invalid)")
        assertTrue(runs.any { it.bold && it.text == "강조" })
        assertTrue(runs.any { it.italic && it.text == "설명" })
        assertTrue(runs.any { it.code && it.text == "A|B" })
        assertEquals("강조와 설명, A|B, 원문 (https://example.invalid)", runs.joinToString("") { it.text })
        assertEquals(MarkdownBlock.Divider, markdownBlocks("---").single())
    }
}
