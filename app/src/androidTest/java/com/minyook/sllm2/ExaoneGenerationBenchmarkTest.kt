/* 소스 안내
 * 실제 모델 backend·초기화·생성 지연을 검증하는 Android 계측 검사다.
 * 검사 예: measureNativeGeneration, publish, memory
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
package com.minyook.sllm2

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.data.GeneralKnowledgeAnswer
import com.minyook.sllm2.data.SemanticEncoder
import com.minyook.sllm2.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in real Exaone measurement; no history/settings/model writes or RAG substitution. */
class ExaoneGenerationBenchmarkTest {
    @Test fun streamsKoreanAcrossIndependentTurns() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runExaoneSmoke") == "true")
        val backend = args.getString("backend")?.let(InferenceBackend::valueOf) ?: InferenceBackend.CPU
        val expected = args.getString("expectedBackend")
            ?: "CPU".takeIf { backend == InferenceBackend.CPU }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(ModelPhase.READY, ModelPreferences(context).status().phase)
        val runtime = LocalModelRuntime(context) {
            InferenceSettings(backend, contextTokens = 2048, responseTokens = 128, automaticAnswerLength = false)
        }
        try {
            for ((index, country) in listOf("대한민국" to "서울", "프랑스" to "파리").withIndex()) {
                val (name, capital) = country
                val chunks = mutableListOf<String>()
                var metrics: GenerationMetrics? = null
                val answer = runtime.generate(
                    "${name}의 수도는 어디인가요? 한국어 한 문장으로 답해 주세요.",
                    evidence = "",
                    generalKnowledge = true,
                    onPartialAnswer = { chunks.add(it) },
                    onMetrics = { metrics = it },
                )
                assertTrue(answer, capital in answer)
                assertFalse(answer, '\uFFFD' in answer)
                assertTrue(chunks.isNotEmpty())
                assertTrue(chunks.all { answer.startsWith(it.trimStart()) || it.trim() == answer })
                val stats = checkNotNull(metrics)
                InstrumentationRegistry.getInstrumentation().sendStatus(1, Bundle().apply {
                    putString("actualBackend", stats.backend)
                    putString("fallbackReason", stats.fallbackReason)
                })
                assertEquals(index != 0, stats.reusedEngine)
                if (expected != null) assertEquals(stats.fallbackReason, expected, stats.backend.substringBefore(' '))
                args.getString("expectedFallback")?.let { assertTrue(stats.fallbackReason, it in stats.fallbackReason) }
                assertTrue((stats.decodeTokens ?: 0) in 1..128)
            }
        } finally {
            runtime.release()
        }
    }

    @Test fun measureNativeGeneration() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runExaoneBenchmark") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val backend = InferenceBackend.valueOf(args.getString("backend") ?: "CPU")
        val contextTokens = (args.getString("contextTokens") ?: "8192").toInt()
        val label = (args.getString("runLabel") ?: "${backend.name.lowercase()}-$contextTokens").replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val original = InferenceSettingsPreferences(context).load()
        val rows = JSONArray()
        val report = JSONObject().put("platform", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}")
            .put("scope", "Actual production LocalModelRuntime/Exaone; direct general generation, no document answer, no UI frames/audio/history writes. Settings injected in memory only.")
            .put("requestedBackend",backend.name).put("contextTokens",contextTokens).put("thinkingEnabled",false)
            .put("originalSettings",JSONObject().put("backend",original.backend.name).put("contextTokens",original.contextTokens).put("responseTokens",original.responseTokens).put("automaticAnswerLength",original.automaticAnswerLength))
            .put("runLabel",label).put("phase","starting").put("results",rows)
        val out = File(context.getExternalFilesDir(null),"generation-checks").apply { mkdirs() }.resolve("$label.json")
        fun publish() { out.writeText(report.toString(2)); instrumentation.sendStatus(1,Bundle().apply { putString("generationProgress","$label: ${rows.length()} completed / ${report.optString("phase")}") }) }
        fun memory():JSONObject {
            val info = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
            val battery = context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            return JSONObject().put("availableRamBytes",info.availMem).put("lowMemory",info.lowMemory)
                .put("thermalStatus",if(android.os.Build.VERSION.SDK_INT>=29)(context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else -1)
                .put("batteryTemperatureC",battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1)?.div(10.0))
        }
        data class Case(val name:String,val question:String,val limit:Int)
        val base = "구름이 생겨 비가 내리는 원리를 설명해줘."
        val short = "$base 한국어로 세 문장 이내의 짧은 답변을 써줘."
        val long = "$base 증발, 상승과 냉각, 응결, 물방울의 성장, 강수 과정을 순서대로 자세히 설명해줘. 한국어 Markdown으로 각 과정의 이유와 연결을 포함해 써줘."
        val cases = listOf(Case("cold-short",short,128),Case("warm-short-1",short,128),Case("warm-short-2",short,128),Case("warm-long",long,384))
        var selectedSettings = original.copy(backend=backend,contextTokens=contextTokens,automaticAnswerLength=false,responseTokens=128)
        val runtime = LocalModelRuntime(context) { selectedSettings }
        publish()
        try {
            for (c in cases) {
                selectedSettings = selectedSettings.copy(responseTokens=c.limit)
                if(c.name=="warm-long" && args.getString("includeSearch")=="true") {
                    val t=SystemClock.elapsedRealtime()
                    val retained=runtime.prepareForSemanticSearch()
                    val encoder=SemanticEncoder(context)
                    try { encoder.encode("삼각대의 준비 수량을 정하는 기준") } finally { encoder.release() }
                    report.put("interveningSearch",JSONObject().put("engineRetained",retained).put("searchMs",SystemClock.elapsedRealtime()-t).put("memory",memory()));publish()
                }
                val before = memory()
                val started = SystemClock.elapsedRealtime()
                var firstSentenceMs:Long? = null
                var outputLimit = false
                var metrics:GenerationMetrics? = null
                var raw = ""
                var error:String? = null
                report.put("phase",c.name); publish()
                try {
                    raw = runtime.generate(c.question,"",generalKnowledge=true,onPartialAnswer={ partial ->
                        if(firstSentenceMs==null && GeneralKnowledgeAnswer.partial(partial,c.question)!=null) firstSentenceMs=SystemClock.elapsedRealtime()-started
                    },onOutputLimit={outputLimit=it},onMetrics={metrics=it})
                } catch(e:Exception) { error="${e.javaClass.simpleName}: ${e.message}" }
                val total = SystemClock.elapsedRealtime()-started
                val m = metrics
                rows.put(JSONObject().put("case",c.name).put("question",c.question).put("maxOutputTokens",c.limit)
                    .put("firstValidatedSentenceMs",firstSentenceMs).put("totalMs",total).put("outputLimitReached",outputLimit).put("error",error)
                    .put("metrics",m?.let { JSONObject().put("backend",it.backend).put("initializationMs",it.initializationMs).put("firstNativeTextMs",it.firstChunkMs).put("generationTotalMs",it.totalMs).put("promptBytes",it.promptBytes).put("prefillTokens",it.prefillTokens).put("decodeTokens",it.decodeTokens).put("decodeTokensPerSecond",it.decodeTokensPerSecond) })
                    .put("before",before).put("after",memory()).put("rawAnswer",raw).put("displayAnswer",if(error==null) GeneralKnowledgeAnswer.finish(raw,c.question,outputLimit) else ""))
                publish()
                if(error!=null)break
            }
        } finally { runtime.release();report.put("phase","complete").put("settingsUnchanged",original==InferenceSettingsPreferences(context).load());publish() }
        assertEquals(report.toString(2),cases.size,rows.length())
        for(i in 0 until rows.length())assertTrue(report.toString(2),rows.getJSONObject(i).optString("error").isEmpty())
        assertEquals(original,InferenceSettingsPreferences(context).load())
    }
}
