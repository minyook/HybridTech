package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class HybridRetrievalTest {
    private val root=if(File("src/main/assets").exists())File(".") else File("app")
    private fun chunks():List<KnowledgeChunk>{val rows=JSONObject(File(root,"src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks");return (0 until rows.length()).map{i->val c=rows.getJSONObject(i);KnowledgeChunk(id=i+1L,documentId=c.getString("documentId"),documentTitle=c.getString("documentTitle"),sourceKind=c.getString("sourceKind"),pageNumber=c.getInt("pageNumber"),chunkIndex=c.getInt("chunkIndex"),body=c.getString("body"))}}
    @Test fun androidAndWebUseTheSameActualQueryVectorsAndPageDecisions(){
        val search=HybridRetrieval(chunks(),File(root,"src/main/assets/knowledge/semantic-index-v1.json").readText())
        val cases=JSONArray(File(root,"src/test/resources/semantic-deep-vectors.json").readText())
        for(i in 0 until cases.length()){
            val row=cases.getJSONObject(i);val vector=row.getJSONArray("vector");val result=search.resolve(row.getString("question"),FloatArray(vector.length()){vector.getDouble(it).toFloat()});val pages=row.getJSONArray("expectedPages")
            assertEquals(row.getString("question"),row.getString("expectedStatus"),result.status)
            assertEquals(row.getString("question"),(0 until pages.length()).map{pages.getInt(it)},result.sources.map{it.chunk.pageNumber})
            if(result.sources.isEmpty())assertFalse(result.message.contains("문서에 없"))
        }
    }
    @Test fun followUpsCarryOnlyExplicitContextAndNewTopicsStayIndependent(){
        assertTrue(HybridRetrieval.contextualQuestion("그럼 언제 해야 해?","가스 측정 방법").contains("가스 측정 방법"))
        assertEquals("심폐소생술 알려줘",HybridRetrieval.contextualQuestion("심폐소생술 알려줘","가스 측정 방법"))
        assertEquals("그건?",HybridRetrieval.contextualQuestion("그건?"))
    }
    @Test fun absentEncoderAndLiveApprovalDoNotInventAnswers(){
        val search=HybridRetrieval(chunks(),File(root,"src/main/assets/knowledge/semantic-index-v1.json").readText())
        assertEquals("answered",search.resolve("산소측정법 알려줘",null).status)
        assertEquals("live-state",search.resolve("우리 현장 작업 승인이 지금 완료됐어?",null).status)
        assertTrue(search.resolve("전등 방수 IP 등급 알려줘",null).sources.isEmpty())
    }
    @Test fun aConfirmedSectionPreservesQualificationsWithoutOtherUnrelatedSections(){
        val search=HybridRetrieval(chunks(),File(root,"src/main/assets/knowledge/semantic-index-v1.json").readText())
        val qualification=FocusedEvidence.format(search.resolve("산소 유해가스 농도 측정자의 자격",null).sources)
        assertTrue(qualification.contains("올바른 해석"))
        val timing=FocusedEvidence.format(search.resolve("측정 시기에 대해 알려줘",null).sources)
        assertTrue(timing.contains("점심시간"));assertFalse(timing.contains("교육과정"))
    }
    @Test fun compoundQuestionsDoNotHideAnUnansweredProperty(){
        val search=HybridRetrieval(chunks(),File(root,"src/main/assets/knowledge/semantic-index-v1.json").readText())
        val result=search.resolve("심폐소생술 그리고 전등 방수 IP 등급 알려줘",null)
        assertEquals("partial",result.status);assertTrue(result.sources.any{it.chunk.pageNumber==45});assertTrue(result.message.contains("IP 등급"))
    }
}
