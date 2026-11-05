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
    @Test fun reachedLimitIsVisibleInsteadOfPretendingTheAnswerFinished() {
        val answer = GeneralKnowledgeAnswer.finish("핵심 설명입니다.\n\n- **아직 이어지는 조건", "일반 원리", outputLimitReached = true)
        assertTrue(answer.contains("**아직 이어지는 조건**"))
        assertTrue(answer.endsWith(GeneralKnowledgeAnswer.LIMIT_NOTICE))
        assertFalse(GeneralKnowledgeAnswer.finish("완결된 설명입니다.", "일반 원리").contains(GeneralKnowledgeAnswer.LIMIT_NOTICE))
    }
    @Test fun equipmentCountsWithoutDocumentEvidenceAreNotInventedByTheModel() {
        assertNull(GeneralKnowledgeAnswer.partial("삼각대는 1대만 준비하면 됩니다.","삼각대 몇 대 필요해?"))
        assertNull(GeneralKnowledgeAnswer.partial("안전대는 두 개씩 준비하세요.","안전대 개수"))
        assertNotNull(GeneralKnowledgeAnswer.partial("현장 조건을 확인하지 않아 수량을 확정할 수 없어요.","안전대 개수"))
        val prompt = GeneralKnowledgeAnswer.prompt("구명줄 개수")
        assertTrue(prompt.contains("이번 질문의 초점")); assertTrue(prompt.contains("안전 수량"))
    }
    @Test fun usefulGeneralExplanationSurvivesAnUnverifiedCountParagraph() {
        val raw="삼각대 준비 수량을 정할 때는 동시에 사용하는 작업 구역과 인양 구성을 함께 살펴야 해요.\n\n삼각대는 1대만 준비하면 됩니다.\n\n장비의 호환성과 사용 설명서의 설치 조건도 확인해야 해요."
        val answer=GeneralKnowledgeAnswer.finish(raw,"삼각대 갯수")
        assertTrue(answer.contains("동시에 사용하는 작업 구역"));assertTrue(answer.contains("호환성과 사용 설명서"))
        assertFalse(answer.contains("1대만"));assertFalse(answer.contains("구체적인 수치나 작업 가능 여부를 문서 근거 없이"))
        assertNotNull(GeneralKnowledgeAnswer.partial(raw,"삼각대 갯수"))
    }
    @Test fun safeNumberedExplanationsAreNotRejectedAsProductSpecifications() {
        val raw="1. 전압은 제품의 명판에서 확인할 수 있어요.\n\n2. 연결하려는 전원과의 호환성을 확인하세요."
        assertTrue(GeneralKnowledgeAnswer.acceptable(raw,"이 전등 전압은?"))
        assertTrue(GeneralKnowledgeAnswer.finish(raw,"이 전등 전압은?").contains("명판"))
        val prompt=GeneralKnowledgeAnswer.prompt("삼각대 수량","구조용 삼각대 등을 갖추어 두어야 합니다.")
        assertTrue(prompt.contains("문서에 없다는 이유만으로 답변을 거절하지 마세요"));assertTrue(prompt.contains("이미 표시한 문서"))
    }
    @Test fun anExplicitShortRequestDoesNotForceExtraListsWhileDetailedAnswersKeepTheirScope() {
        assertTrue(GeneralKnowledgeAnswer.prompt("구름이 생기는 원리를 세 문장으로 설명해줘").contains("1~3개의 완결 문장만"))
        assertTrue(GeneralKnowledgeAnswer.prompt("구름이 생기는 원리를 자세히 설명해줘").contains("Markdown 답변 본문"))
        assertFalse(GeneralKnowledgeAnswer.prompt("구름이 생기는 원리를 자세히 설명해줘").contains("1~3개의 완결 문장만"))
    }
}
