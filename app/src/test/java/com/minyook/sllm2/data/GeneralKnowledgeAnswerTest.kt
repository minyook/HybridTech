/* 소스 안내
 * 일반 설명의 프롬프트·규칙 검사·부분 공개·항목별 길이을 검증하는 JVM 회귀 검사다.
 * 검사 예: generalMarkdownIsExplicitlyLabelledAndPreservesFormatting, inventedDocumentReferencesAndUnsafeUnverifiedClaimsAreNotDisplayed, searchFailureAndModelAbsenceAreNotPresentedAsSourcedAnswers
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import org.junit.Assert.*
import org.junit.Test

class GeneralKnowledgeAnswerTest {
    @Test fun explicitFiveItemRequestStartsWithTheRequestedListWithoutChangingSafetyOrLengthLimits() {
        val question = "밀폐공간 작업을 중단했다가 다시 시작할 때 감독관이 확인할 사항을 핵심 5개로 정리해줘. 각 항목은 한 문장으로 설명해줘."
        assertEquals(5, GeneralKnowledgeAnswer.numberedItemCount(question))
        val prompt = GeneralKnowledgeAnswer.prompt(question)
        assertTrue(prompt.contains("정확히 5개 번호 항목"))
        assertTrue(prompt.contains("필요한 조건·예외"))
        assertTrue(prompt.contains("추측하거나 확정하지 마세요"))
        assertFalse(prompt.contains("첫 문장은 짧은 직답"))
        assertFalse(GeneralKnowledgeAnswer.prefersBrief(question, automaticLength=true))
    }

    @Test fun detailedOrAmbiguousListRequestsAreNotSilentlyShortened() {
        assertNull(GeneralKnowledgeAnswer.numberedItemCount("주의사항 5개를 자세하게 설명해줘"))
        assertNull(GeneralKnowledgeAnswer.numberedItemCount("각 항목을 한 문장으로 설명해줘"))
        assertNull(GeneralKnowledgeAnswer.numberedItemCount("장비 2개와 조건 5개를 각 항목 한 문장으로 설명해줘"))
        assertNull(GeneralKnowledgeAnswer.numberedItemCount("확인할 사항 15개를 각 항목 한 문장으로 설명해줘"))
        assertEquals(4, GeneralKnowledgeAnswer.numberedItemCount("확인 조건을 4가지로 설명해줘. 조건별 한 문장으로 설명해줘."))
        assertTrue(GeneralKnowledgeAnswer.prompt("확인할 사항 5개를 자세히 설명해줘").contains("필요한 설명·조건·예외"))
    }

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
    @Test fun streamingPreviewPublishesOnlyNewValidatedText() {
        val question = "삼각대 갯수"
        val preview = GeneralKnowledgeAnswer.StreamingPreview(question)
        val first = "장비 배치를 먼저 확인하세요."
        assertNull(preview.next("장비 배치를 먼저"))
        assertEquals(GeneralKnowledgeAnswer.partial(first, question), preview.next(first))
        assertNull(preview.next("$first 다음 문장이 이어지는 중"))

        val unverified = "$first\n\n삼각대는 1대만 준비하면 됩니다."
        assertNull(preview.next(unverified))
        val next = "$unverified\n\n제조사의 설치 조건도 확인하세요."
        val published = preview.next(next)
        assertNotNull(published)
        assertEquals(GeneralKnowledgeAnswer.partial(next, question), published)
        assertFalse(published.orEmpty().contains("1대만"))
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

    @Test fun automaticBriefAnswersRequireOneFocusedQuestion() {
        assertTrue(GeneralKnowledgeAnswer.prefersBrief("구조용 삼각대 준비 수량", automaticLength = true))
        assertTrue(GeneralKnowledgeAnswer.prefersBrief("응결의 뜻이 뭐야?", automaticLength = true))
        assertFalse(GeneralKnowledgeAnswer.prefersBrief("구조용 삼각대 준비 수량", automaticLength = false))
        assertFalse(GeneralKnowledgeAnswer.prefersBrief("삼각대 개수랑 설치 방법을 알려줘", automaticLength = true))
        assertFalse(GeneralKnowledgeAnswer.prefersBrief("삼각대와 구명줄은 각각 몇 개 필요해?", automaticLength = true))
        assertFalse(GeneralKnowledgeAnswer.prefersBrief("삼각대 개수를 13문장으로 설명해줘", automaticLength = true))
        assertFalse(GeneralKnowledgeAnswer.prefersBrief("구조용 삼각대 준비 수량", automaticLength = true, latestQuestion = "얼마나 필요한지 자세히 알려줘"))
        assertTrue(GeneralKnowledgeAnswer.prefersBrief("과정을 세 문장으로 설명해줘", automaticLength = false))
        assertFalse(GeneralKnowledgeAnswer.prefersBrief("작업 재개 전 확인할 사항 5개를 각 항목 한 문장으로 정리해줘", automaticLength = true))
        assertFalse(GeneralKnowledgeAnswer.prefersBrief("확인 조건을 항목별 한 문장으로 설명해줘", automaticLength = false))
        assertTrue(GeneralKnowledgeAnswer.prompt("확인 사항 5개를 각 항목 한 문장으로 정리해줘").contains("한국어 Markdown 답변"))
    }

    @Test fun numberedAnswersKeepCompleteValidRowsAndIdentifyMissingItems() {
        val question = "밀폐공간 확인 사항 5개를 각 항목 한 문장으로 알려줘"
        val raw = "1. 산소를 측정합니다.\n2. 산소는 16%면 됩니다.\n3. 연락 수단을 확인합니다.\n4. 보호구를"
        val final = GeneralKnowledgeAnswer.finish(raw, question, outputLimitReached = true)
        assertTrue(final.contains("1. 산소를 측정합니다.\n3. 연락 수단을 확인합니다."))
        assertFalse(final.contains("16%"))
        assertFalse(final.contains("4. 보호구를"))
        assertTrue(final.contains("5개 항목 중 2개"))
        assertTrue(final.contains(GeneralKnowledgeAnswer.LIMIT_NOTICE))
        assertTrue(GeneralKnowledgeAnswer.partial(raw, question).orEmpty().contains("3. 연락 수단을 확인합니다."))
    }
}
