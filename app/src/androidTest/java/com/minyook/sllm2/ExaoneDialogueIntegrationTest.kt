/* 소스 안내
 * 실제 모델과 문맥을 포함한 일반/후속 질문 통합을 검증하는 Android 계측 검사다.
 * 검사 예: screenshotFollowupsGenerateUsefulGeneralExplanation, publish, applyMeasuredGpuPreference
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
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

/** Actual document + missing-attribute -> local Exaone integration, no user history writes. */
class ExaoneDialogueIntegrationTest {
    @Test fun screenshotFollowupsGenerateUsefulGeneralExplanation() = runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runExaoneDialogue")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=InferenceSettingsPreferences(context).load()
        val backend = args.getString("benchmarkBackend")?.let(InferenceBackend::valueOf) ?: InferenceBackend.CPU
        val automaticLength = args.getString("automaticLength") == "true"
        val runtime=LocalModelRuntime(context) { original.copy(backend=backend,automaticAnswerLength=automaticLength,responseTokens=512) }
        val encoder=SemanticEncoder(context)
        KnowledgeSeeder(context).seedIfNeeded()
        val repository=KnowledgeRepository()
        val index=context.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use { it.readText() }
        val history=mutableListOf<ConversationTurn>()
        val results=JSONArray()
        val report=JSONObject().put("scope","Actual Android dialogue + document search + general Exaone inference. No chat/history writes; inference settings overridden in memory only. Raw model text retained for review, not an independent factual grade.").put("results",results)
        report.put("requestedBackend", backend.name).put("automaticLength", automaticLength)
        val label = args.getString("runLabel")?.replace(Regex("[^a-zA-Z0-9_-]"), "_") ?: "dialogue-integration"
        val out=File(context.getExternalFilesDir(null),"generation-checks/$label.json")
        fun publish(){out.parentFile?.mkdirs();out.writeText(report.toString(2))}
        try {
            val followUps = listOf("필요한 도구는 먼데?","구조용 삼각대 등에 대해 알려줘","얼마나 필요해?","아니 갯수를 물어보잖아 삼각대 갯수")
            val questions = if (args.getString("followUpsOnly") == "true") followUps
                else followUps + "구름이 생겨 비가 내리는 원리를 세 문장으로 설명해줘"
            for(q in questions){
                val started=SystemClock.elapsedRealtime()
                val request=ConversationContext.resolve(q,history)
                val result=repository.searchQuestion(request,index) { query -> encoder.cachedVector(query) ?: run { runtime.prepareForSemanticSearch();encoder.encode(query) } }
                val plan=DocumentAnswerPlan.create(request.answerQuestion,result)
                val documentMs=SystemClock.elapsedRealtime()-started
                var generated="";var metrics:GenerationMetrics?=null;var limit=false
                var firstGeneratedBodyMs: Long? = null
                val general=result.generalQuestion.takeIf { result.searchComplete }
                if(general!=null){
                    encoder.release()
                    val raw=runtime.generate(general,plan.context(),generalKnowledge=true,recentConversation=request.turns,latestUserQuestion=request.asked,onMetrics={metrics=it},onOutputLimit={limit=it},onPartialAnswer={ partial ->
                        if (firstGeneratedBodyMs == null && GeneralKnowledgeAnswer.partial(partial, general) != null)
                            firstGeneratedBodyMs = SystemClock.elapsedRealtime() - started
                    })
                    generated=GeneralKnowledgeAnswer.finish(raw,general,limit)
                    results.put(JSONObject().put("question",q).put("query",request.query).put("method",result.method).put("documentMs",documentMs).put("totalMs",SystemClock.elapsedRealtime()-started).put("generalRequested",true).put("rawGeneral",raw).put("generated",generated).put("sourceBody",plan.body).put("backend",metrics?.backend).put("initializationMs",metrics?.initializationMs).put("firstNativeTextMs",metrics?.firstChunkMs).put("firstGeneratedBodyMs",firstGeneratedBodyMs).put("promptBytes",metrics?.promptBytes))
                }else results.put(JSONObject().put("question",q).put("query",request.query).put("method",result.method).put("documentMs",documentMs).put("generalRequested",false).put("sourceBody",plan.body))
                publish()
                if(q.contains("필요해")||q.startsWith("아니")){
                    assertNotNull(report.toString(2),general)
                    assertFalse(generated.contains("구체적인 수치나 작업 가능 여부를 문서 근거 없이"))
                    assertFalse(generated.contains("일반 설명을 완성하지 못했어요"))
                    if (automaticLength) {
                        val body = generated.removePrefix(GeneralKnowledgeAnswer.NOTICE).trim()
                        assertTrue(report.toString(2), listOf("출입구", "작업 구역", "구조조", "배치", "제조사", "장비 조합").any(body::contains))
                    } else assertTrue(report.toString(2),generated.removePrefix(GeneralKnowledgeAnswer.NOTICE).trim().length>100)
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
        check(context.getSharedPreferences("inference_settings",android.content.Context.MODE_PRIVATE).edit().putString("execution_order",InferenceBackend.CPU.name).commit())
        assertEquals(original.copy(backend=InferenceBackend.CPU),preferences.load())
    }
}
