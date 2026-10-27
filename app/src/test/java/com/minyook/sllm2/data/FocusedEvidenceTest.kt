package com.minyook.sllm2.data

import org.junit.Assert.*
import org.junit.Test

class FocusedEvidenceTest {
    private fun page(id: Long, body: String) = KnowledgeChunk(id = id, documentId = "test", documentTitle = "테스트 원문", sourceKind = "가이드", pageNumber = id.toInt(), body = body, searchableText = body)
    @Test fun directNounBeatsGenericMeasurementsAndTheStoredPageIsUntouched() {
        val source = page(1, "▶ 사전준비 • 산소 농도를 확인합니다. ▶ 측정방법 • 수직 및 수평 방향으로 측정합니다. ▶ 사용시 주의사항 • 어두운 공간에서 전등을 사용하는 경우 가연성가스 때문에 방폭구조의 전등을 사용합니다.")
        val sources = FocusedRetriever(listOf(source, page(2, "산소 측정 방법과 장비의 이상 유무를 확인합니다."))).retrieve("어두운 공간에서 전등 종류 알려줘")
        val answer = FocusedEvidence.format(sources)
        assertTrue(answer.contains("방폭구조의 전등")); assertFalse(answer.contains("수직 및 수평")); assertFalse(answer.contains("산소 농도를 확인")); assertTrue(source.body.contains("수직 및 수평"))
    }
    @Test fun previouslyUnlistedNounsWorkInNewDocumentsAndConditionsRemainTogether() {
        val sources = FocusedRetriever(listOf(page(1, "▶ 사용 주의사항 • 배수구를 막는 마개는 균열이 없는 것을 사용하십시오. 손상된 마개는 사용하지 마십시오."), page(2, "산소 농도 측정은 수직 및 수평 방향으로 하십시오."))).retrieve("배수구 마개는 어떤 걸 사용해야 해?")
        val answer = FocusedEvidence.format(sources)
        assertTrue(answer.contains("균열이 없는")); assertTrue(answer.contains("손상된 마개는 사용하지 마십시오")); assertFalse(answer.contains("수직 및 수평"))
    }
    @Test fun unknownPropertiesCannotBeAnsweredByAnUnrelatedMatchingTopic() {
        val retriever = FocusedRetriever(listOf(page(1, "전등은 방폭구조의 전등을 사용하십시오.")))
        assertTrue(retriever.retrieve("전등의 방수 IP 등급 알려줘").isEmpty())
        assertTrue(retriever.retrieve("전등 전압은 몇 볼트야?").isEmpty())
    }
    @Test fun spacesCannotTurnPowerOutageEtcIntoALamp() {
        val retriever = FocusedRetriever(listOf(page(1, "정전 등으로 환기가 중단되면 대피하십시오. 작업장 등 급기로 공기를 공급합니다."), page(2, "전등은 방폭구조의 전등을 사용하십시오.")))
        assertEquals(listOf(2L), retriever.retrieve("전등 종류 알려줘").map { it.chunk.id })
    }
    @Test fun retrievingOtherQuestionsCannotAffectTheNextResult() {
        val retriever = FocusedRetriever(listOf(page(1, "전등은 방폭구조의 전등을 사용하십시오."), page(2, "심폐소생술 흉부 압박을 실시합니다.")))
        val before = retriever.retrieve("전등 종류 알려줘")
        retriever.retrieve("심폐소생술"); retriever.retrieve("없는 문서 질문")
        assertEquals(before, retriever.retrieve("전등 종류 알려줘"))
    }
    @Test fun aConditionAndItsExampleStayInOneSourceGroup() {
        val source = page(1, "▶ 사용 • 압력이 낮은 경우 작업을 중지합니다. 예외 없이 원인을 확인합니다.")
        val units = FocusedEvidence.units(source)
        assertEquals(2, units.size); assertEquals(units[0].group, units[1].group)
    }
    @Test fun formattingRemovesRunningHeadersAndHighlightsCriteriaWithoutChangingNumbers() {
        val source = page(1, "제1장 질식과 밀폐공간제2장 조치사항 안전작업 가이드 1 ▶ 기준 • 산소농도는 18% 이상 23.5% 미만입니다.")
        val body = FocusedEvidence.format(listOf(RetrievedChunk(source, 1.0)))
        assertFalse(body.contains("제1장")); assertTrue(body.contains("**18% 이상**")); assertTrue(body.contains("**23.5% 미만**"))
    }
    @Test fun procedureHeadingsDoNotTrailThePreviousAction() {
        val units = FocusedEvidence.units(page(1, "순 서 실 시 방 법 반응확인 • 반응을 확인합니다. 신고 • 도움을 요청합니다. 압박 • 흉부압박을 실시합니다."))
        assertEquals(listOf("반응확인", "신고", "압박"), units.map { it.section })
        assertEquals("반응을 확인합니다.", units[0].text)
    }
    @Test fun answerPlanAndModelContextUseExactlyTheSelectedEvidence() {
        val source = page(1, "사전준비는 나중의 다른 문단입니다. 직접 답하는 근거입니다.")
        val selected = RetrievedChunk(source, 1.0, listOf(EvidencePassage("직접 답하는 근거입니다.", "조건", 1, 1)))
        val plan = DocumentAnswerPlan.create("쉽게 설명해줘", listOf(selected))
        assertFalse(plan.body.contains("사전준비")); assertFalse(plan.context().contains("사전준비")); assertTrue(plan.context().contains("[e1-1]"))
        assertTrue(plan.supplementRequested); assertFalse(DocumentAnswerPlan.create("측정방법 알려줘", listOf(selected)).supplementRequested)
    }
    @Test fun supplementCannotAddNumbersApprovalOrWrongEvidenceAndDoesNotReplaceTheBody() {
        val source = page(1, "산소는 18% 이상 23.5% 미만이어야 합니다.")
        val plan = DocumentAnswerPlan.create("쉽게 설명해줘", listOf(RetrievedChunk(source, 1.0)))
        assertEquals("", AnswerSupplementVerifier.accept("산소는 18% 이상 23.5% 미만이면 작업 가능", listOf("e1-0"), plan))
        assertEquals("", AnswerSupplementVerifier.accept("산소는 19% 이상이어야 합니다.", listOf("e1-0"), plan))
        assertEquals("", AnswerSupplementVerifier.accept("산소는 18% 이상 23.5% 이하인 경우입니다.", listOf("e1-0"), plan))
        assertEquals("", AnswerSupplementVerifier.accept("새 설명", listOf("forged"), plan))
        assertEquals("", AnswerSupplementVerifier.accept(source.body, listOf("e1-0"), plan))
        assertEquals(plan.body, AnswerSupplementVerifier.compose(plan, ""))
        assertTrue(AnswerSupplementVerifier.compose(plan, "테스트 보충").startsWith(plan.body + "\n\n"))
    }
    @Test fun structuredSupplementCanExplainAConditionButCannotUseMalformedJson() {
        val plan = DocumentAnswerPlan.create("왜 전등이 필요한지 쉽게 설명해줘", listOf(RetrievedChunk(page(1, "가연성가스가 차 있을 수 있는 경우에는 방폭구조의 전등을 사용하십시오."), 1.0)))
        val text = "가연성가스가 차 있을 수 있는 경우에는 사용하는 전등도 방폭구조여야 합니다."
        assertEquals(text, AnswerSupplementVerifier.fromJson("{\"evidenceIds\":[\"e1-0\"],\"text\":\"$text\"}", plan))
        assertEquals("", AnswerSupplementVerifier.fromJson("근거 없는 자유 형식 답변", plan))
        assertEquals("", AnswerSupplementVerifier.fromJson("{\"evidenceIds\":[\"forged\"],\"text\":\"$text\"}", plan))
    }
    @Test fun figuresPointToTheOriginalAndMissingDataIsDifferentFromNoEvidence() {
        val plan = DocumentAnswerPlan.create("측정방법", listOf(RetrievedChunk(page(1, "맨홀의 ①~③을 측정합니다. X는 보호구를 장착하고 측정합니다."), 1.0)))
        assertTrue(plan.body.contains("원문 그림"))
        assertTrue(DocumentAnswerPlan.create("질문", emptyList(), false).body.contains("준비하고"))
        assertFalse(DocumentAnswerPlan.create("질문", emptyList()).body.contains("준비하고"))
    }
    @Test fun latinGasAbbreviationsNormalizeWithKoreanParticlesAndSubscripts() {
        val terms = EvidenceLanguage.terms("O₂는 CO₂는 CO는 H₂S는 CPR은 알려줘")
        assertTrue(terms.containsAll(listOf("산소", "이산화탄소", "일산화탄소", "황화수소", "심폐소생술")))
        assertFalse(EvidenceLanguage.aliases("micro company").contains("일산화탄소"))
    }
}
