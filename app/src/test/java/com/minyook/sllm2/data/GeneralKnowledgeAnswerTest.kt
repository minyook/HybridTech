package com.minyook.sllm2.data

import org.junit.Assert.*
import org.junit.Test

class GeneralKnowledgeAnswerTest {
    @Test fun generalMarkdownIsExplicitlyLabelledAndPreservesFormatting() {
        val raw="## 커피 추출\n\n- 물이 커피의 성분을 녹여냅니다.\n\n```text\n추출\n```"
        val answer=GeneralKnowledgeAnswer.finish(raw,"커피 추출 원리")
        assertTrue(GeneralKnowledgeAnswer.isGeneral(answer));assertTrue(answer.endsWith(raw))
        assertTrue(GeneralKnowledgeAnswer.finish("```markdown\n## 설명\n본문\n```","원리").endsWith("## 설명\n본문"))
    }
    @Test fun inventedDocumentReferencesAndUnsafeUnverifiedClaimsAreNotDisplayed() {
        for(raw in listOf("안전 가이드 27쪽에 따르면 가능합니다.","제공 문서에 따르면 이 제품은 적합합니다.","밀폐공간 출입이 가능합니다.","산소는 15% 이상이면 됩니다.")) {
            assertNull(raw,GeneralKnowledgeAnswer.partial(raw,"밀폐공간 산소 조건"))
            assertFalse(GeneralKnowledgeAnswer.finish(raw,"밀폐공간 산소 조건").contains(raw))
        }
    }
    @Test fun searchFailureAndModelAbsenceAreNotPresentedAsSourcedAnswers() {
        val empty=GeneralKnowledgeAnswer.finish("","일반 질문")
        assertTrue(empty.startsWith(GeneralKnowledgeAnswer.NOTICE));assertFalse(empty.contains("문서에 없"))
        assertTrue(GeneralKnowledgeAnswer.prompt("질문").contains("Markdown"))
        assertTrue(GeneralKnowledgeAnswer.prompt("질문").contains("쪽수"))
    }
    @Test fun unknownProductSpecificationsAreNotFilledWithInventedNumbers() {
        assertNull(GeneralKnowledgeAnswer.partial("이 제품은 12V입니다.","이 전등 전압 알려줘"))
        assertNull(GeneralKnowledgeAnswer.partial("전등은 12V입니다.","이 전등 전압이 뭐야?"))
        assertNull(GeneralKnowledgeAnswer.partial("제공된 문서에는 그 내용이 없습니다.","새로운 질문"))
        assertNotNull(GeneralKnowledgeAnswer.partial("전압은 전기적 전위 차이를 뜻합니다.","전압의 의미가 뭐야?"))
    }
}
