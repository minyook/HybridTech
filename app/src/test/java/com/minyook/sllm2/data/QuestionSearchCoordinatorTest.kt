/* 소스 안내
 * 오류/모호성/일반 생성 허용과 복합 질문 검색 순서을 검증하는 JVM 회귀 검사다.
 * 검사 예: domainNormalizationIsIdempotentAndDoesNotRewriteAccidentOrMeasurementHoses, nounConjunctionsStayInOneQuestionWhileExplicitQuestionsSplit, vector
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class QuestionSearchCoordinatorTest {
    @Test fun domainNormalizationIsIdempotentAndDoesNotRewriteAccidentOrMeasurementHoses() {
        for(q in listOf("산소 가스를 잴 때 위아래랑 가로로 어디까지 재야 돼요","송기마스크로 숨 쉬는 중 전기가 나가거나 호스가 꺾이면 어떻게 대비해","점심 먹느라 맨홀 밖에 있다 다시 내려가요 가스는 다시 재나요")){
            val normalized=QuestionUnderstanding.normalize(q)
            assertEquals(normalized,QuestionUnderstanding.normalize(normalized))
        }
        assertEquals("재해 예방과 측정 호스",QuestionUnderstanding.normalize("재해 예방과 측정 호스"))
        assertTrue(QuestionUnderstanding.normalize("산소 가스를 잴 때 위아래랑 가로로 측정").contains("수직"))
    }
    @Test fun nounConjunctionsStayInOneQuestionWhileExplicitQuestionsSplit() {
        for(q in listOf("급기구와 배기구 배치는 어떻게 해","산소 및 유해가스 측정방법","센서 정확도와 신뢰성은 어떻게 확보해","구조훈련 대상과 주기"))assertEquals(listOf(q),QuestionSearchCoordinator.parts(q))
        assertEquals(listOf("측정 위치와 개소","깊은 탱크 호스 눈금"),QuestionSearchCoordinator.parts("측정 위치와 개소 그리고 깊은 탱크 호스 눈금"))
    }
    private fun vector(axis:Int)=FloatArray(768){if(it==axis)1f else 0f}
    private val chunks=listOf(
        KnowledgeChunk(id=1,documentId="guide",pageNumber=1,body="대상설비은 첫 번째 후보입니다. 이 후보의 조건을 확인합니다."),
        KnowledgeChunk(id=2,documentId="guide",pageNumber=2,body="두 번째 문단이 의미에 맞는 실제 설명입니다. 조건도 함께 확인합니다.")
    )
    private fun index(sourceChunks:List<KnowledgeChunk> = chunks, passageChunks:List<KnowledgeChunk> = chunks):HybridRetrieval {
        val passages=JSONArray()
        passageChunks.forEachIndexed{index,c->
            val units=FocusedEvidence.units(c)
            passages.put(JSONObject().put("id","p$index").put("documentId",c.documentId).put("page",c.pageNumber).put("chunkIndex",c.chunkIndex).put("section","").put("text",c.body).put("sourceBody",c.body).put("searchText",c.body).put("orders",JSONArray(units.map{it.order})).put("units",JSONArray(units.map{JSONObject().put("text",it.text).put("section",it.section).put("order",it.order).put("group",it.group)})).put("vector",JSONArray(vector(index).map{it.toDouble()})))
        }
        return HybridRetrieval(sourceChunks,JSONObject().put("modelRevision","5090578d9565bb06545b4552f76e6bc2c93e4a66").put("dimensions",768).put("passages",passages).toString())
    }
    @Test fun lexicalCandidateIsReviewedAndCanBeReplacedByDirectSemanticEvidence() {
        val index=index()
        val fast=index.resolve("대상설비 알려줘",null)
        assertEquals("answered",fast.status);assertEquals(1,fast.sources.first().chunk.pageNumber)
        var calls=0
        val result=QuestionSearchCoordinator(index){calls++;vector(1)}.search("대상설비 알려줘")
        assertEquals(1,calls);assertEquals("answered",result.status);assertEquals(2,result.sources.first().chunk.pageNumber)
        assertTrue(result.searchComplete)
    }
    @Test fun anActuallyAmbiguousQuestionIsReviewedButDoesNotInventItsSubject() {
        var calls=0
        val result=QuestionSearchCoordinator(index()){calls++;vector(1)}.search("방법")
        assertEquals(1,calls);assertEquals("clarify",result.status);assertNull(result.generalQuestion)
    }
    @Test fun aCompleteSearchWithNoSupportCanRequestGeneralKnowledge() {
        val result=QuestionSearchCoordinator(index()){vector(3)}.search("커피 추출 원리를 설명해줘")
        assertEquals("no-evidence",result.status);assertTrue(result.searchComplete)
        assertEquals(result.question,result.generalQuestion);assertTrue(result.sources.isEmpty())
    }
    @Test fun encoderOrIndexFailureCannotBecomeGeneralKnowledgeAbsence() {
        val failed=QuestionSearchCoordinator(index()){throw IllegalStateException("failed")}.search("대상설비")
        assertEquals("search-error",failed.status);assertFalse(failed.searchComplete);assertNull(failed.generalQuestion);assertTrue(failed.sources.isEmpty())
        val stale=QuestionSearchCoordinator(index(emptyList())){vector(3)}.search("커피 원리")
        assertEquals("search-error",stale.status);assertFalse(stale.searchComplete);assertNull(stale.generalQuestion)
        val invalid=QuestionSearchCoordinator(index()){FloatArray(768)}.search("새 질문")
        assertEquals("search-error",invalid.status)
    }
    @Test fun compoundQuestionsSearchEveryPartAndKeepSupportedAndGeneralPortions() {
        val asked=mutableListOf<String>()
        val result=QuestionSearchCoordinator(index()){q->asked+=q;if("대상설비" in q)vector(0) else vector(3)}.search("대상설비 그리고 커피 추출")
        assertEquals(listOf("대상설비","커피 추출"),asked)
        assertEquals("partial",result.status);assertEquals(1,result.sources.first().chunk.pageNumber)
        assertTrue(result.searchComplete);assertEquals("커피 추출",result.generalQuestion)
    }
    @Test fun explicitInventoriesAndLiveStateDoNotAllocateTheEncoder() {
        val root=if(File("src/main/assets").exists())File(".") else File("app")
        val rows=JSONObject(File(root,"src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
        val source=(0 until rows.length()).map{i->val c=rows.getJSONObject(i);KnowledgeChunk(id=i+1L,documentId=c.getString("documentId"),documentTitle=c.getString("documentTitle"),sourceKind=c.getString("sourceKind"),pageNumber=c.getInt("pageNumber"),chunkIndex=c.getInt("chunkIndex"),body=c.getString("body"))}
        val search=QuestionSearchCoordinator(HybridRetrieval(source,File(root,"src/main/assets/knowledge/semantic-index-v1.json").readText())){error("encoder must not run")}
        assertEquals("answered",search.search("작업장비").status)
        assertEquals("live-state",search.search("우리 현장 승인이 지금 완료됐어?").status)
        assertEquals("heading",search.search("산소 유해가스 농도 측정자의 자격").method)
    }
    @Test fun unresolvedReferenceAndTruncatedQuestionsCannotClaimAbsence() {
        val search=QuestionSearchCoordinator(index()){error("must not encode")}
        for(q in listOf("그거 어떻게 하는데?",(1..9).joinToString(" 그리고 "){"대상$it"})){
            val result=search.search(q)
            assertEquals("clarify",result.status);assertFalse(result.searchComplete);assertNull(result.generalQuestion)
        }
    }
    @Test fun oldChoiceFeedbackSearchesTheOriginalTopicWithoutOfferingChoicesAgain() {
        val options = listOf(SearchSuggestion("첫 항목", "대상설비"), SearchSuggestion("두 번째 항목", "다른 대상"))
        val history = listOf(ConversationTurn(true, "대상설비"), ConversationTurn(false, "선택해 주세요.", suggestions = options))
        val request = ConversationContext.resolve("항목이 안보이는데?", history)
        val result = QuestionSearchCoordinator(index()) { vector(0) }.search(request)
        assertEquals("answered", result.status)
        assertNull(result.generalQuestion)
        assertTrue(result.suggestions.isEmpty())
        assertFalse(result.message.contains("첫 항목"))
    }
    @Test fun tiedCandidatesContinueWithTheOriginalQuestionInsteadOfAThreeTopicMenu() {
        val question = "밀폐공간 작업을 중단했다가 다시 시작할 때 감독관이 확인할 사항을 핵심 5개로 정리해줘. 각 항목은 한 문장으로 설명해줘."
        val tied = FloatArray(768) { if(it < 2) (1.0 / kotlin.math.sqrt(2.0)).toFloat() else 0f }
        val result = QuestionSearchCoordinator(index()) { tied }.search(question)
        assertEquals("no-evidence", result.status)
        assertEquals(question, result.generalQuestion)
        assertTrue(result.searchComplete)
        assertTrue(result.sources.isEmpty())
        assertTrue(result.suggestions.isEmpty())
        assertFalse(result.message.contains("선택"))
    }
    @Test fun moreWithoutNewSupportedEvidenceDoesNotClaimTheOriginalTopicIsAbsent() {
        val history = listOf(ConversationTurn(true, "대상설비"), ConversationTurn(false, chunks[0].body, topicQuestion = "대상설비"))
        val result = QuestionSearchCoordinator(index()) { vector(0) }.search(ConversationContext.resolve("또 다른 건 없어?", history))
        assertEquals("no-more", result.status); assertNull(result.generalQuestion)
        assertTrue(result.message.contains("이미 설명한 내용이 문서에 없다는 뜻은 아니에요"))
    }
    @Test fun recallUsesTheLocalUserTranscriptInsteadOfModelKnowledge() {
        val request = ConversationContext.resolve("내가 아까 뭐라고 말했지?", listOf(ConversationTurn(true, "저는 S24를 사용해요.")))
        val result = QuestionSearchCoordinator(index()) { error("recall must not search") }.search(request)
        assertEquals("conversation-recall", result.status); assertTrue(result.message.contains("S24를 사용해요")); assertNull(result.generalQuestion)
    }
    @Test fun moreReturnsNovelEvidenceWithinTheSameTopicWhenItExists() {
        val pages = listOf(chunks[0], chunks[1].copy(body = "대상설비에는 추가로 점검할 부품이 있습니다. 연결 상태도 확인합니다."))
        val q = "대상설비"
        val request = ConversationContext.resolve("또 다른 건 없어?", listOf(ConversationTurn(true, q), ConversationTurn(false, pages[0].body, topicQuestion = q)))
        val together = FloatArray(768) { if(it < 2) (1.0 / kotlin.math.sqrt(2.0)).toFloat() else 0f }
        val result = QuestionSearchCoordinator(index(pages, pages)) { together }.search(request)
        assertEquals("answered", result.status); assertEquals("conversation-more", result.method)
        assertTrue(result.sources.any { it.chunk.id == 2L }); assertNull(result.generalQuestion)
        assertTrue(DocumentAnswerPlan.create(q, result.sources).body.contains("연결 상태도 확인"))
    }
    @Test fun moreAfterGeneralKnowledgeCanContinueWithTheModelInsteadOfClaimingNoMoreDocuments() {
        val topic = "커피 추출 원리"
        val history = listOf(ConversationTurn(true, topic), ConversationTurn(false, GeneralKnowledgeAnswer.NOTICE + "\n\n추출 설명입니다.", topicQuestion = topic))
        val result = QuestionSearchCoordinator(index()) { vector(3) }.search(ConversationContext.resolve("다른 것도 알려줘", history))
        assertEquals("conversation-general", result.method); assertEquals(topic, result.generalQuestion)
        assertTrue(result.sources.isEmpty()); assertTrue(result.searchComplete)
    }
}
