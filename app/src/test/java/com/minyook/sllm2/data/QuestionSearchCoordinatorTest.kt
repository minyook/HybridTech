package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class QuestionSearchCoordinatorTest {
    private fun vector(axis:Int)=FloatArray(768){if(it==axis)1f else 0f}
    private val chunks=listOf(
        KnowledgeChunk(id=1,documentId="guide",pageNumber=1,body="대상설비은 첫 번째 후보입니다. 이 후보의 조건을 확인합니다."),
        KnowledgeChunk(id=2,documentId="guide",pageNumber=2,body="두 번째 문단이 의미에 맞는 실제 설명입니다. 조건도 함께 확인합니다.")
    )
    private fun index(sourceChunks:List<KnowledgeChunk> = chunks):HybridRetrieval {
        val passages=JSONArray()
        chunks.forEachIndexed{index,c->
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
}
