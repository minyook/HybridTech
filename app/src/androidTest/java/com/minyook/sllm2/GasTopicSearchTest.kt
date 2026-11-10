/* 소스 안내
 * 단말 단일 가스 주제별 실제 검색을 검증하는 Android 계측 검사다.
 * 검사 예: explicitGasReplacesPreviousSubjectsAndKeepsOnlyRequestedEvidence, strings, normalized
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
package com.minyook.sllm2

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Source scope tests, not generated-model quality or rendered frame timing. */
class GasTopicSearchTest {
    @Test fun explicitGasReplacesPreviousSubjectsAndKeepsOnlyRequestedEvidence() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val baseline=InstrumentationRegistry.getArguments().getString("recordBaseline")=="true"
        KnowledgeSeeder(context).seedIfNeeded()
        val repository=KnowledgeRepository()
        val index=context.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use{it.readText()}
        val encoder=SemanticEncoder(context)
        data class Case(val asked:String,val prior:String="",val required:List<String>,val forbidden:List<String>)
        val dataset=JSONObject(instrumentation.context.assets.open("rag-evaluation/gas-subject-scope-20261105.json").bufferedReader().use{it.readText()})
        fun strings(a:JSONArray?)=if(a==null)emptyList() else (0 until a.length()).map{a.getString(it)}
        val raw=dataset.getJSONArray("cases")
        val cases=(0 until raw.length()).map{i->val c=raw.getJSONObject(i);Case(c.getString("asked"),c.optString("prior"),strings(c.optJSONArray("required")),strings(c.optJSONArray("forbidden"))+if(c.optBoolean("oxygen"))strings(dataset.getJSONArray("oxygenForbidden")) else emptyList())}
        val rows=JSONArray();var failures=0
        fun normalized(text:String)=text.replace(Regex("[\\s*]"),"")
        try { for(case in cases) {
            val history=if(case.prior.isBlank())emptyList() else listOf(ConversationTurn(true,case.prior),ConversationTurn(false,"앞선 문서 답변입니다.",topicQuestion=case.prior,answerStatus="answered"))
            val request=ConversationContext.resolve(case.asked,history)
            val started=SystemClock.elapsedRealtimeNanos()
            val result=repository.searchQuestion(request,index){encoder.encode(it)}
            val plan=DocumentAnswerPlan.create(request.answerQuestion,result)
            val issues=case.required.filter{normalized(it) !in normalized(plan.body)}.map{"missing: $it"}+case.forbidden.filter{normalized(it) in normalized(plan.body)}.map{"unwanted: $it"}+if(result.status!="answered")listOf("status: ${result.status}") else emptyList()
            if(issues.isNotEmpty())failures++
            rows.put(JSONObject().put("asked",case.asked).put("prior",case.prior).put("query",request.query).put("kind",request.kind.name).put("status",result.status).put("pages",JSONArray(result.sources.map{it.chunk.pageNumber}.distinct())).put("millis",(SystemClock.elapsedRealtimeNanos()-started)/1e6).put("pass",issues.isEmpty()).put("failures",JSONArray(issues)).put("answerMarkdown",plan.body))
        }}finally{encoder.release()}
        val report=JSONObject().put("device",android.os.Build.MODEL).put("scope","Actual installed ObjectBox repository, current conversation resolver, ONNX query encoder and source answer; no Gemma/history writes/UI frame timing").put("baseline",baseline).put("total",cases.size).put("passed",cases.size-failures).put("results",rows)
        File(context.getExternalFilesDir(null),"gas-scope-checks").apply{mkdirs()}.resolve(if(baseline)"before.json" else "after.json").writeText(report.toString(2))
        if(!baseline)assertEquals(report.toString(2),0,failures)
    }
}
