package com.minyook.sllm2.data

import org.junit.Assert.*
import org.junit.Test

class AnswerMarkdownTest {
    @Test fun joinedCircledItemsBecomeSeparateRowsWithoutBreakingRanges() {
        val rows = AnswerMarkdown.evidenceLines("① 산소 18% 이상② 황화수소 10 ppm 미만③ 이산화탄소 1.5% 미만")
        assertEquals(3, rows.size); assertTrue(rows[0].startsWith("1. ")); assertTrue(rows[2].contains("1.5% 미만"))
        val range = AnswerMarkdown.evidenceLines("①~③을 측정하고 X는 장비를 착용하고 측정합니다.").single()
        assertTrue(range.contains("①~③을")); assertTrue(range.contains("장비를 착용"))
    }
    @Test fun fullContentAndConditionalTailSurvivePresentation() {
        val raw = "호흡이 회복되었는지 확인하고, 회복되었다면 옆으로 눕힙니다. 회복되지 않았다면 반복합니다."
        val text = AnswerMarkdown.evidenceLines(raw).joinToString("\n")
        assertEquals(raw, text.removePrefix("- "))
        assertTrue(AnswerMarkdown.evidenceLines("압박 5cm(소아는 4~5cm), 분당 100~120회").single().contains("소아는 **4~5cm**"))
    }
    @Test fun wrapperBulletsAndSpacingAreCleanButLiteralCodeIsUntouched() {
        val raw = "```markdown\r\n## 설명\r\n\r\n\r\n*   본문입니다.\r\n    *   조건입니다.\r\n```"
        assertEquals("## 설명\n\n- 본문입니다.\n    - 조건입니다.", AnswerMarkdown.normalize(raw))
        val code = "```text\n*   literal\n\n\n**data**\n```"
        assertEquals(code, AnswerMarkdown.normalize(code))
    }
    @Test fun previewDoesNotPublishAnIncompleteSentenceOrMarkdownSpan() {
        assertNull(AnswerMarkdown.preview("농도는 18."))
        assertNull(AnswerMarkdown.preview("1."))
        assertNull(AnswerMarkdown.preview("## 제목\n\n문장이 아직"))
        assertNull(AnswerMarkdown.preview("**아직 강조가 끝나지 않았습니다."))
        assertEquals("첫 문장입니다.", AnswerMarkdown.preview("첫 문장입니다. 다음 문장의 중"))
        assertEquals("첫 문장입니다.", AnswerMarkdown.preview("첫 문장입니다.\n\n```json\n{\"unfinished\":"))
        assertEquals("첫 문장입니다.", AnswerMarkdown.preview("```markdown\n첫 문장입니다. 다음"))
    }
    @Test fun incompleteFormattingIsRepairedWithoutDeletingAnyWords() {
        assertEquals("- **조건을 확인합니다.**", AnswerMarkdown.normalize("- **조건을 확인합니다."))
        assertEquals("```text\n전체 내용을 유지\n```", AnswerMarkdown.normalize("```text\n전체 내용을 유지"))
        assertEquals("`코드`", AnswerMarkdown.normalize("`코드"))
        assertEquals("2 * 3입니다.", AnswerMarkdown.normalize("2 * 3입니다."))
    }
    @Test fun previewLiteralCodeMayContainUnpairedBackticksAndStars() {
        val text = "```text\nprint(\"`**\")\n```"
        assertEquals(text, AnswerMarkdown.preview(text))
    }
    @Test fun pdfColumnLabelsAndBrokenEndingsAreCleanedWithoutDroppingActions() {
        val raw = "밀폐공간 작업 시 준수사항(산업안전보건기준에 관한 규칙)항 목○×위 반 사 항 및 조 치호흡용 보호구를 착용하십 시오. 환기를 유지해야 합니 다."
        val answer = AnswerMarkdown.evidenceLines(raw).single()
        assertEquals("- 호흡용 보호구를 착용하십시오. 환기를 유지해야 합니다.", answer)
    }
}
