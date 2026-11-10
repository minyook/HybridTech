/* 소스 안내
 * 장비 대상·질문 초점과 상세 응답의 경계을 검증하는 JVM 회귀 검사다.
 * 검사 예: transcript, corpus, omittedCountsRetainAllPreviouslyNamedEquipment
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EquipmentDialogueTest {
    private val topic = "안전대와 구명줄 , 구조용 삼각대에 대해 알려줘"
    private fun transcript(topic: String = this.topic) = listOf(ConversationTurn(true, topic), ConversationTurn(false, "추락위험에 대비하기 위해 안전대와 구명 밧줄을 착용하여야 합니다.", topicQuestion = topic))
    private fun corpus(): List<KnowledgeChunk> {
        val app = if (File("src/main/assets").exists()) File(".") else File("app")
        val rows = JSONObject(File(app, "src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
        return (0 until rows.length()).map { i -> val c = rows.getJSONObject(i); KnowledgeChunk(id=i+1L,documentId=c.getString("documentId"),documentTitle=c.getString("documentTitle"),sourceKind=c.getString("sourceKind"),pageNumber=c.getInt("pageNumber"),body=c.getString("body")) }
    }
    @Test fun omittedCountsRetainAllPreviouslyNamedEquipment() {
        for (asked in listOf("갯수는 어느정도로 챙겨?", "개수는?", "수량은 얼마나 준비해야 해?", "그럼 몇 개씩?", "각각 몇 개 필요해?")) {
            val request = ConversationContext.resolve(asked, transcript())
            assertEquals(asked, FollowUpKind.FOLLOW_UP, request.kind)
            assertEquals(asked, listOf("안전대", "구명줄", "구조용 삼각대"), QuestionFocus.targets(request.query).map { it.name })
            assertTrue(asked, QuestionFocus.quantity(request.query))
        }
    }
    @Test fun aNamedSubsetReplacesPriorTargetsAndHeadcountDoesNotAccumulateOldQuestions() {
        val count = ConversationContext.resolve("구조용 삼각대랑 구명줄 등등 갯수 정해진 게 있나?", transcript())
        assertEquals(listOf("구명줄", "구조용 삼각대"), QuestionFocus.targets(count.query).map { it.name })
        val history = transcript() + ConversationTurn(true, count.asked, count.query, count.topic) + ConversationTurn(false, "고정 수량 미확인", topicQuestion=count.topic)
        val people = ConversationContext.resolve("그럼 두 명이면?", history)
        assertEquals(FollowUpKind.FOLLOW_UP, people.kind); assertTrue(people.query.contains("두 명")); assertTrue(QuestionFocus.quantity(people.query))
        assertFalse(people.query.contains("알려줘")); assertFalse(people.query.contains("안전대"))
        val narrowed = ConversationContext.resolve("삼각대만 몇 대?", history)
        assertEquals(listOf("구조용 삼각대"), QuestionFocus.targets(narrowed.query).map { it.name })
    }
    @Test fun newAndUnknownSubjectsDoNotInheritTheEquipmentTopic() {
        for (asked in listOf("커피 개수는?", "가스 측정 시기는?", "그럼 갤럭시 배터리 수명은?")) {
            val request = ConversationContext.resolve(asked, transcript())
            assertEquals(asked, FollowUpKind.NEW, request.kind); assertFalse(request.query.contains("삼각대"))
        }
        assertNull(QuestionFocus.followUp("구명줄 길이 몇 m야?", topic))
        assertFalse(QuestionFocus.quantity("가스 측정은 몇 개소야?"))
        assertFalse(QuestionFocus.quantity("구명줄 길이는 몇 미터야?"))
    }
    @Test fun screenshotUsesBothEquipmentSectionsAndAnswersTheCountAttributeWithoutGenerating() {
        val resolver = EquipmentDetailAnswer(corpus())
        val overview = resolver.resolve(topic)!!
        val plan = DocumentAnswerPlan.create(topic, overview)
        assertTrue(plan.body.contains("삼각대, 사다리, 섬유로프")); assertTrue(plan.body.contains("착용하여야"))
        assertFalse(plan.supplementRequested)
        assertTrue(plan.body.indexOf("착용하여야") < plan.body.indexOf("습기"))
        val request = ConversationContext.resolve("갯수는 어느정도로 챙겨?", transcript())
        val quantity = resolver.resolve(request.query)!!
        val answer = DocumentAnswerPlan.create(request.answerQuestion, quantity)
        assertEquals("equipment-quantity", quantity.method); assertEquals(request.query,quantity.generalQuestion)
        assertTrue(answer.body.take(160).contains("고정 준비 수량을 확인하지 못했어요"))
        assertTrue(answer.body.contains("안전대")); assertTrue(answer.body.contains("구명줄")); assertTrue(answer.body.contains("구조용 삼각대"))
        assertEquals(1,Regex("고정 준비 수량").findAll(answer.body).count()); assertFalse(answer.body.contains("습기 찬 환경"))
        assertTrue(answer.sourceIds.isNotEmpty()); assertFalse(answer.supplementRequested)
        assertFalse(answer.body.contains("작업의 종류")); assertFalse(answer.body.contains("어떤 종류"))
        assertTrue(quantity.sources.all { it.chunk.pageNumber == 37 })
    }
    @Test fun quantitiesInFutureDocumentsAreQuotedWithConditionsAndOtherAttributeQuestionsStayInSearch() {
        val chunks = listOf(KnowledgeChunk(id=1,body="▶ 구조용 삼각대\n• 작업구가 둘인 경우 구조용 삼각대 2대를 비치하고 출입구의 조건을 확인합니다."))
        val answer = EquipmentDetailAnswer(chunks).resolve("삼각대 몇 대 준비해?")!!
        assertTrue(answer.answerBody!!.contains("수량과 관련된 조건이 문서에 있어요"))
        assertTrue(answer.answerBody!!.contains("작업구가 둘인 경우")); assertTrue(answer.answerBody!!.contains("2대"))
        assertFalse(answer.answerBody!!.contains("고정 준비 수량을 확인하지 못했어요"))
        val resolver = EquipmentDetailAnswer(corpus())
        for (asked in listOf("구명줄 길이 몇 m야?", "안전대 없이 삼각대를 써도 돼?", "안전대와 구명줄 점검 시기", "삼각대 가격은?", "방사선 장비와 안전대 설명해줘", "공기호흡기 실린더 개수는?", "측정기 배터리는 몇 개야?")) assertNull(asked,resolver.resolve(asked))
    }
    @Test fun comparisonReusesSubjectsAndQuotedConditionsNeverBecomeACountApproval() {
        val request = ConversationContext.resolve("둘의 차이는?", transcript("안전대와 구명줄 알려줘"))
        assertEquals(FollowUpKind.FOLLOW_UP, request.kind); assertTrue(request.query.contains("역할 차이"))
        val comparison = EquipmentDetailAnswer(corpus()).resolve(request.query)!!
        assertTrue(comparison.answerBody!!.take(180).contains("각각의 구조·작동 차이까지 구분하기는 어려워요"))
        val people = EquipmentDetailAnswer(corpus()).resolve("안전대와 구명줄 2명 준비 수량")!!
        assertTrue(people.answerBody!!.contains("**2명**")); assertTrue(people.answerBody!!.contains("충분하다고 단정할 수는 없어요"))
        assertFalse(people.answerBody!!.contains("2개씩"))
    }
    @Test fun changingAttributeAfterCountDropsTheOldCountIntentButRephrasingKeepsIt() {
        val countTopic = "안전대 · 구명줄 · 구조용 삼각대 준비 수량"
        val history = transcript(countTopic)
        for (asked in listOf("그럼 언제 써?", "그럼 용도는?", "둘의 차이는?", "길이는?", "가격은?")) {
            val request = ConversationContext.resolve(asked, history)
            assertEquals(asked, FollowUpKind.FOLLOW_UP, request.kind)
            assertFalse(asked, QuestionFocus.quantity(request.query))
            assertTrue(request.query.contains("삼각대"))
        }
        val rephrase = ConversationContext.resolve("쉽게 설명해줘", history)
        assertEquals(FollowUpKind.EXPLAIN, rephrase.kind); assertTrue(QuestionFocus.quantity(rephrase.query))
    }
    @Test fun explicitMissingTargetsArePartialAndAnUnrelatedCountDoesNotSupplyTheAnswer() {
        val source = KnowledgeChunk(id=1,body="▶ 안전대와 구명줄\n• 안전대와 구명줄을 착용합니다.\n• 근처에 전등 2개를 비치합니다.")
        val answer = EquipmentDetailAnswer(listOf(source)).resolve("안전대와 삼각대 수량")!!
        assertEquals("partial",answer.status); assertTrue(answer.answerBody!!.contains("고정 준비 수량을 확인하지 못했어요"))
        assertTrue(answer.answerBody!!.contains("관련 항목을 이번 확인에서 찾지 못했어요"))
        assertNull(EquipmentDetailAnswer(listOf(source)).resolve("밀폐공간 장비가 몇 개야?"))
    }
    @Test fun mixedCountSupportIsReportedForEveryRequestedTarget() {
        val source = KnowledgeChunk(id=1,body="▶ 구조용 삼각대\n• 작업구가 둘인 경우 삼각대 2대를 비치합니다.\n▶ 안전대\n• 추락위험이 있는 경우 안전대를 착용합니다.")
        val body = EquipmentDetailAnswer(listOf(source)).resolve("안전대와 삼각대 준비 수량")!!.answerBody!!
        assertTrue(body.contains("**안전대**: 준비해야 할 고정 개수나 인원별 수량 기준은 관련 항목에서 확인되지 않아요."))
        assertTrue(body.contains("**구조용 삼각대**: 아래 원문에 나온 수량·적용 조건을 확인해 주세요."))
        assertTrue(body.contains("작업구가 둘인 경우")); assertTrue(body.contains("2대"))
    }
    @Test fun anUnlistedNewSubjectWithCountLanguageRemainsTheTopicOnTheNextTurn() {
        val fresh = ConversationContext.resolve("커피 개수는?", transcript())
        assertEquals(FollowUpKind.NEW, fresh.kind)
        val continued = ConversationContext.resolve("그거 쉽게 설명해줘", transcript() + ConversationTurn(true,fresh.asked,fresh.query,fresh.topic) + ConversationTurn(false,"커피 설명입니다.",topicQuestion=fresh.topic))
        assertEquals(FollowUpKind.EXPLAIN,continued.kind); assertTrue(continued.query.contains("커피")); assertFalse(continued.query.contains("삼각대"))
    }
    @Test fun screenshotAmountAndCorrectionKeepTheActualEquipmentInsteadOfOfferingUnrelatedChoices() {
        val history = listOf(ConversationTurn(true,"필요한 도구는 먼데?"),ConversationTurn(false,"### 구조용 삼각대 등\n\n- 응급상황에 대비해 구조용 삼각대 등을 갖춥니다.",topicQuestion="필요한 도구는 먼데?",sourceIds=listOf(37)))
        val amount = ConversationContext.resolve("얼마나 필요해?",history)
        assertEquals(FollowUpKind.FOLLOW_UP,amount.kind); assertEquals(listOf("구조용 삼각대"),QuestionFocus.targets(amount.query).map { it.name }); assertTrue(QuestionFocus.quantity(amount.query))
        val correction = ConversationContext.resolve("아니 갯수를 물어보잖아 삼각대 갯수",history)
        val result = EquipmentDetailAnswer(corpus()).resolve(QuestionUnderstanding.normalize(correction.query))!!
        assertEquals("equipment-quantity",result.method);assertNotNull(result.generalQuestion)
        assertFalse(result.answerBody!!.contains("관리감독자"));assertTrue(result.answerBody!!.contains("고정 준비 수량"))
    }
    @Test fun aColloquialToolListIsAnInventoryRatherThanOneTripodParagraph() {
        val q = QuestionUnderstanding.normalize("필요한 도구는 먼데?")
        val result = EquipmentOverview(corpus()).resolve(q)!!
        assertEquals("inventory",result.method)
        val body = DocumentAnswerPlan.create(q,result).body
        assertTrue(body.contains("측정기"));assertTrue(body.contains("전등"));assertTrue(body.contains("환기"));assertTrue(body.contains("삼각대"))
    }
}
