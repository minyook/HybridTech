package com.minyook.sllm2

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.data.*
import com.minyook.sllm2.model.LocalModelRuntime
import com.minyook.sllm2.model.InferenceBackend
import com.minyook.sllm2.model.InferenceSettingsPreferences
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Real-device measurement. No chat/history writes; optional backend override is restored. */
class AndroidQuestionLatencyEvaluationTest {
    @Test fun restoreBackendFromArgument() {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.containsKey("restoreBackend"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val backend=InferenceBackend.valueOf(args.getString("restoreBackend")!!)
        check(context.getSharedPreferences("inference_settings",android.content.Context.MODE_PRIVATE).edit().putString("backend",backend.name).commit())
        assertEquals(backend,InferenceSettingsPreferences(context).load().backend)
    }
    @Test fun measureCurrentProductionQuestionPath() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val args=InstrumentationRegistry.getArguments()
        val label=args.getString("runLabel")?:"after"
        val includeGeneration=args.getString("includeGeneration")=="true"
        val numbers=args.getString("numbers")?.split(',')?.map{it.toInt()}?.toSet()
        val preferences=InferenceSettingsPreferences(context)
        val original=preferences.load()
        val override=args.getString("backendOverride")?.let{InferenceBackend.valueOf(it)}
        val cases=JSONObject(instrumentation.context.assets.open("rag-evaluation/evaluation-150.json").bufferedReader().use{it.readText()}).getJSONArray("cases")
        KnowledgeSeeder(context).seedIfNeeded()
        val repository=KnowledgeRepository()
        val json=context.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use{it.readText()}
        val prepareStart=SystemClock.elapsedRealtimeNanos()
        repository.prepareQuestionIndex(json)
        val encoder=SemanticEncoder(context);val runtime=LocalModelRuntime(context)
        val rows=JSONArray()
        val report=JSONObject().put("platform","${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}")
            .put("label",label).put("scope","Real Android repository, query encoder and answer assembly; optional real Gemma generation. First-body state timing, not rendered UI frame timing. No audio/history writes.")
            .put("total",numbers?.size?:150).put("indexPrepareMs",(SystemClock.elapsedRealtimeNanos()-prepareStart)/1e6).put("includeGeneration",includeGeneration).put("results",rows)
            .put("requestedBackend",(override?:original.backend).name).put("contextTokens",original.contextTokens).put("responseTokens",original.responseTokens)
        val out=File(context.getExternalFilesDir(null),"rag-checks").apply{mkdirs()}.resolve("device-$label.json")
        fun publish(){out.writeText(report.toString(2))}
        try {
            if(override!=null)preferences.save(original.copy(backend=override))
            for(i in 0 until cases.length()){
                if(numbers!=null&&i+1 !in numbers)continue
                val c=cases.getJSONObject(i);val question=c.getString("question")
                val started=SystemClock.elapsedRealtimeNanos();var encodeMs=0.0;var calls=0
                val resolution=repository.searchQuestion(question,json){query->
                    encoder.cachedVector(query)?:run{runtime.release();val t=SystemClock.elapsedRealtimeNanos();encoder.encode(query).also{calls++;encodeMs+=(SystemClock.elapsedRealtimeNanos()-t)/1e6}}
                }
                val searchMs=(SystemClock.elapsedRealtimeNanos()-started)/1e6
                val plan=DocumentAnswerPlan.create(question,resolution.sources)
                var body=if(resolution.sources.isEmpty())resolution.message else plan.body+if(resolution.message.isNotBlank())"\n\n"+resolution.message else ""
                var firstBodyMs=if(resolution.sources.isNotEmpty())(SystemClock.elapsedRealtimeNanos()-started)/1e6 else -1.0
                var firstGeneratedMs = -1.0
                var generationMs=0.0;var generation="not-requested";var error=""
                val general=resolution.generalQuestion.takeIf{resolution.searchComplete}
                if(includeGeneration&&(general!=null||resolution.status=="answered"&&plan.supplementRequested)){
                    encoder.release()
                    val t=SystemClock.elapsedRealtimeNanos()
                    try {
                        if(general!=null){
                            val raw=runtime.generate(general,"",generalKnowledge=true,onPartialAnswer={partial->
                                GeneralKnowledgeAnswer.partial(partial,general)?.let{if(firstGeneratedMs<0)firstGeneratedMs=(SystemClock.elapsedRealtimeNanos()-started)/1e6;if(firstBodyMs<0)firstBodyMs=firstGeneratedMs}
                            })
                            val generated=GeneralKnowledgeAnswer.finish(raw,general)
                            body=if(resolution.sources.isEmpty())generated else body+"\n\n"+generated
                            if(firstBodyMs<0)firstBodyMs=(SystemClock.elapsedRealtimeNanos()-started)/1e6
                        }else{
                            val raw=runtime.generate(question,plan.context(),supplementOnly=true)
                            body=AnswerSupplementVerifier.compose(plan,AnswerSupplementVerifier.fromJson(raw,plan))
                        }
                        generation="completed"
                    }catch(e:Exception){generation="error";error=e.javaClass.simpleName+": "+e.message.orEmpty()}
                    generationMs=(SystemClock.elapsedRealtimeNanos()-t)/1e6
                }else if(general!=null)generation="skipped-general"
                if(firstBodyMs<0&&general==null)firstBodyMs=(SystemClock.elapsedRealtimeNanos()-started)/1e6
                rows.put(JSONObject().put("number",i+1).put("question",question).put("status",resolution.status).put("method",resolution.method)
                    .put("pages",JSONArray(resolution.sources.map{it.chunk.pageNumber})).put("searchMs",searchMs).put("encoderMs",encodeMs).put("encoderCalls",calls)
                    .put("sources",JSONArray(resolution.sources.map{JSONObject().put("documentId",it.chunk.documentId).put("page",it.chunk.pageNumber)}))
                    .put("firstBodyStateMs",firstBodyMs).put("firstGeneratedBodyStateMs",firstGeneratedMs).put("generationMs",generationMs).put("generation",generation).put("error",error)
                    .put("totalMs",(SystemClock.elapsedRealtimeNanos()-started)/1e6).put("answerMarkdown",body))
                if(i+1 in setOf(1,39,113)&&resolution.sources.isNotEmpty()){
                    val repeatStarted=SystemClock.elapsedRealtimeNanos()
                    val repeated=repository.searchQuestion(question,json){query->encoder.cachedVector(query)?:encoder.encode(query)}
                    DocumentAnswerPlan.create(question,repeated.sources)
                    rows.getJSONObject(rows.length()-1).put("immediateRepeatSearchAndCompositionMs",(SystemClock.elapsedRealtimeNanos()-repeatStarted)/1e6)
                }
                report.put("completed",rows.length()).put("phase","running");publish()
            }
        }finally{
            encoder.release();runtime.release()
            if(override!=null){
                preferences.save(original)
                // Instrumentation can end its process before apply() reaches disk.
                check(context.getSharedPreferences("inference_settings",android.content.Context.MODE_PRIVATE).edit().putString("backend",original.backend.name).commit())
            }
        }
        report.put("phase","complete");publish()
        assertEquals(numbers?.size?:150,rows.length())
    }
}
