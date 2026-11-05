package com.minyook.sllm2

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.data.*
import com.minyook.sllm2.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Actual document + missing-attribute -> local Gemma integration, no user history writes. */
class GemmaDialogueIntegrationTest {
    @Test fun screenshotFollowupsGenerateUsefulGeneralExplanation() = runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runGemmaDialogue")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=InferenceSettingsPreferences(context).load()
        val runtime=LocalModelRuntime(context) { original.copy(backend=InferenceBackend.GPU,automaticAnswerLength=false,responseTokens=512) }
        val encoder=SemanticEncoder(context)
        KnowledgeSeeder(context).seedIfNeeded()
        val repository=KnowledgeRepository()
        val index=context.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use { it.readText() }
        val history=mutableListOf<ConversationTurn>()
        val results=JSONArray()
        val report=JSONObject().put("scope","Actual Android dialogue + document search + general Gemma inference. No chat/history writes; inference settings overridden in memory only. Raw model text retained for review, not an independent factual grade.").put("results",results)
        val out=File(context.getExternalFilesDir(null),"generation-checks/dialogue-integration.json")
        fun publish(){out.parentFile?.mkdirs();out.writeText(report.toString(2))}
        try {
            for(q in listOf("필요한 도구는 먼데?","구조용 삼각대 등에 대해 알려줘","얼마나 필요해?","아니 갯수를 물어보잖아 삼각대 갯수","구름이 생겨 비가 내리는 원리를 세 문장으로 설명해줘")){
                val started=SystemClock.elapsedRealtime()
                val request=ConversationContext.resolve(q,history)
                val result=repository.searchQuestion(request,index) { query -> encoder.cachedVector(query) ?: run { runtime.prepareForSemanticSearch();encoder.encode(query) } }
                val plan=DocumentAnswerPlan.create(request.answerQuestion,result)
                val documentMs=SystemClock.elapsedRealtime()-started
                var generated="";var metrics:GenerationMetrics?=null;var limit=false
                val general=result.generalQuestion.takeIf { result.searchComplete }
                if(general!=null){
                    encoder.release()
                    val raw=runtime.generate(general,plan.context(),generalKnowledge=true,recentConversation=request.turns,latestUserQuestion=request.asked,onMetrics={metrics=it},onOutputLimit={limit=it})
                    generated=GeneralKnowledgeAnswer.finish(raw,general,limit)
                    results.put(JSONObject().put("question",q).put("query",request.query).put("method",result.method).put("documentMs",documentMs).put("totalMs",SystemClock.elapsedRealtime()-started).put("generalRequested",true).put("rawGeneral",raw).put("generated",generated).put("sourceBody",plan.body).put("backend",metrics?.backend).put("initializationMs",metrics?.initializationMs).put("firstNativeTextMs",metrics?.firstChunkMs))
                }else results.put(JSONObject().put("question",q).put("query",request.query).put("method",result.method).put("documentMs",documentMs).put("generalRequested",false).put("sourceBody",plan.body))
                publish()
                if(q.contains("필요해")||q.startsWith("아니")){
                    assertNotNull(report.toString(2),general)
                    assertFalse(generated.contains("구체적인 수치나 작업 가능 여부를 문서 근거 없이"))
                    assertFalse(generated.contains("일반 설명을 완성하지 못했어요"))
                    assertTrue(report.toString(2),generated.removePrefix(GeneralKnowledgeAnswer.NOTICE).trim().length>100)
                }
                val body=if(generated.isBlank())plan.body else plan.body+"\n\n"+generated
                history+=ConversationTurn(true,q,request.query,request.topic)
                history+=ConversationTurn(false,body,topicQuestion=request.topic,sourceIds=plan.sourceIds,answerStatus=result.status)
            }
        }finally{encoder.release();runtime.release();report.put("phase","complete").put("settingsUnchanged",original==InferenceSettingsPreferences(context).load());publish()}
        assertEquals(original,InferenceSettingsPreferences(context).load())
    }
    @Test fun applyMeasuredGpuPreference() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("applyMeasuredGpu")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val preferences=InferenceSettingsPreferences(context)
        val original=preferences.load()
        // Preserve the user's context/output choices; change only the measured backend.
        check(context.getSharedPreferences("inference_settings",android.content.Context.MODE_PRIVATE).edit().putString("backend",InferenceBackend.GPU.name).commit())
        assertEquals(original.copy(backend=InferenceBackend.GPU),preferences.load())
    }
}
