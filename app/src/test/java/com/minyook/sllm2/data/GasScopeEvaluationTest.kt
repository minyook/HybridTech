/* 소스 안내
 * 산소 후속 질문의 원문/표/관련 없는 내용 배제을 검증하는 JVM 회귀 검사다.
 * 검사 예: explicitSubjectDescriptionsHaveCorrectScopeWithoutLoadingTheEncoder, strings, norm
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Executes the actual coordinator/plan on real source assets without a phone or an LLM. */
class GasScopeEvaluationTest {
    @Test fun explicitSubjectDescriptionsHaveCorrectScopeWithoutLoadingTheEncoder() {
        val app=(if(File("src/main/assets").exists())File(".") else File("app")).canonicalFile
        val seed=JSONObject(File(app,"src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
        val chunks=(0 until seed.length()).map{i->val c=seed.getJSONObject(i);KnowledgeChunk(id=i+1L,documentId=c.getString("documentId"),documentTitle=c.getString("documentTitle"),sourceKind=c.getString("sourceKind"),pageNumber=c.getInt("pageNumber"),chunkIndex=c.getInt("chunkIndex"),body=c.getString("body"))}
        val prepare=System.nanoTime()
        val index=HybridRetrieval(chunks,File(app,"src/main/assets/knowledge/semantic-index-v1.json").readText())
        val prepareMs=(System.nanoTime()-prepare)/1e6
        var encoderCalls=0
        val coordinator=QuestionSearchCoordinator(index){encoderCalls++;error("Explicit source description must not need an encoder")}
        val dataset=JSONObject(File(app,"src/androidTest/assets/rag-evaluation/gas-subject-scope-20261105.json").readText())
        fun strings(a:JSONArray?)=if(a==null)emptyList() else (0 until a.length()).map{a.getString(it)}
        fun norm(text:String)=text.replace(Regex("[\\s*]"),"")
        val cases=dataset.getJSONArray("cases");val rows=JSONArray();var failed=0
        for(i in 0 until cases.length()) {
            val c=cases.getJSONObject(i);val asked=c.getString("asked");val prior=c.optString("prior")
            val history=if(prior.isBlank())emptyList() else listOf(ConversationTurn(true,prior),ConversationTurn(false,"앞선 문서 답변입니다.",topicQuestion=prior,answerStatus="answered"))
            val request=ConversationContext.resolve(asked,history)
            val started=System.nanoTime();val result=coordinator.search(request);val plan=DocumentAnswerPlan.create(request.answerQuestion,result)
            val required=strings(c.optJSONArray("required"))
            val forbidden=strings(c.optJSONArray("forbidden")) + if(c.optBoolean("oxygen"))strings(dataset.getJSONArray("oxygenForbidden")) else emptyList()
            val issues=required.filter{norm(it) !in norm(plan.body)}.map{"missing: $it"}+forbidden.filter{norm(it) in norm(plan.body)}.map{"unwanted: $it"}+if(result.status!="answered")listOf("status: ${result.status}") else emptyList()
            if(issues.isNotEmpty())failed++
            rows.put(JSONObject().put("asked",asked).put("prior",prior).put("query",request.query).put("kind",request.kind.name).put("status",result.status).put("method",result.method).put("pages",JSONArray(result.sources.map{it.chunk.pageNumber}.distinct())).put("millis",(System.nanoTime()-started)/1e6).put("pass",issues.isEmpty()).put("failures",JSONArray(issues)).put("answerMarkdown",plan.body))
        }
        val report=JSONObject().put("platform","PC JVM").put("scope","Actual Android coordinator/conversation resolver/answer plan with real assets; no Android execution, UI frame, STT/TTS or Gemma inference; explicit description path needs no query encoding").put("datasetScope",dataset.getString("scope")).put("total",cases.length()).put("passed",cases.length()-failed).put("encoderCalls",encoderCalls).put("indexPrepareMs",prepareMs).put("results",rows)
        val out=File(app,"build/reports/fieldguard/gas-scope").apply{mkdirs()}
        File(out,"after-pc.json").writeText(report.toString(2))
        File(out,"after-answers.md").writeText("# 대상별 문서 답변 · PC 제품 코드 검사\n\n실기기/Gemma/UI 프레임 검사가 아닙니다.\n\n"+(0 until rows.length()).joinToString("\n\n---\n\n"){i->val row=rows.getJSONObject(i);"## ${row.getString("asked")}\n\n이전 주제: ${row.getString("prior").ifBlank{"없음"}}\n\n${row.getString("answerMarkdown")}"})
        assertEquals(report.toString(2),0,failed);assertEquals(0,encoderCalls)
    }
}
