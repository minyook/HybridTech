/* 소스 안내
 * 같은 프롬프트/근거에서 native 추측 생성 플래그와 실제 토큰 지표을 검증하는 Android 계측 검사다.
 * 검사 예: compareNativeDecodingPath, publish, sha
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
package com.minyook.sllm2

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.minyook.sllm2.data.*
import com.minyook.sllm2.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Same long prompts and output limits; changes only the engine's draft-decoding flag. */
class GemmaDecodingPathBenchmarkTest {
    @OptIn(ExperimentalApi::class)
    @Test fun compareNativeDecodingPath() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runDecodingPath") == "true")
        val variant = args.getString("variant") ?: "baseline"
        require(variant in setOf("baseline", "no-spec"))
        val context = instrumentation.targetContext
        val original = InferenceSettingsPreferences(context).load()
        val originalBenchmark = ExperimentalFlags.enableBenchmark
        val originalSpeculative = ExperimentalFlags.enableSpeculativeDecoding
        ExperimentalFlags.enableBenchmark = true
        ExperimentalFlags.enableSpeculativeDecoding = if (variant == "no-spec") false else null
        val rows = JSONArray()
        val report = JSONObject().put("variant", variant).put("contextTokens", 4096).put("maxOutputTokens", 2048)
            .put("requestedBackend", "GPU").put("benchmarkEnabled", true).put("rows", rows).put("phase", "preparing")
            .put("scope", "Physical S24 production runtime; identical field prompts, sources and output budget. One run per case; not a repeated accuracy or throughput certification.")
        val out = File(context.getExternalFilesDir(null), "generation-checks/decoding-$variant.json")
        fun publish() { out.parentFile?.mkdirs(); out.writeText(report.toString(2)) }
        val runtime = LocalModelRuntime(context) {
            InferenceSettings(InferenceBackend.GPU, 4096, 2048, automaticAnswerLength = false, model = LocalModel.GEMMA4_E2B)
        }
        fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        try {
            KnowledgeSeeder(context).seedIfNeeded()
            val repository = KnowledgeRepository()
            val index = context.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use { it.readText() }
            val encoder = SemanticEncoder(context)
            val plans = try {
                FieldComparisonSuite.cases.filter { it.id in setOf("entry-check", "rescue-equipment") }.map { scenario ->
                    val results = scenario.evidenceQueries.map { query -> repository.searchQuestion(query, index) {
                        encoder.cachedVector(it) ?: encoder.encode(it)
                    } }
                    scenario to DocumentAnswerPlan.create(scenario.question, FieldComparisonSuite.mergeSources(results))
                }
            } finally { encoder.release() }
            publish()
            for ((scenario, plan) in plans) {
                report.put("phase", scenario.id); publish()
                instrumentation.sendStatus(1, Bundle().apply { putString("decodingProgress", "$variant / ${scenario.id}") })
                val question = scenario.question + "\n\n" + FieldComparisonSuite.INSTRUCTION
                var metrics: GenerationMetrics? = null
                var firstBodyMs: Long? = null
                var raw = ""
                var failure = ""
                val started = SystemClock.elapsedRealtime()
                try {
                    raw = runtime.generate(question, plan.context(), onMetrics = { metrics = it }, onPartialAnswer = {
                        if (firstBodyMs == null && AnswerMarkdown.preview(it) != null) firstBodyMs = SystemClock.elapsedRealtime() - started
                    })
                } catch (error: Exception) { failure = "${error.javaClass.simpleName}: ${error.message}" }
                rows.put(JSONObject().put("caseId", scenario.id).put("question", question).put("reference", plan.context())
                    .put("promptAndEvidenceSha256", sha(question + "\n" + plan.context())).put("rawAnswer", raw)
                    .put("error", failure).put("outputChars", raw.length).put("totalMs", SystemClock.elapsedRealtime() - started)
                    .put("firstDisplayableMs", firstBodyMs ?: JSONObject.NULL).put("backend", metrics?.backend)
                    .put("initializationMs", metrics?.initializationMs).put("firstNativeTextMs", metrics?.firstChunkMs)
                    .put("promptBytes", metrics?.promptBytes).put("prefillTokens", metrics?.prefillTokens)
                    .put("decodeTokens", metrics?.decodeTokens).put("decodeTokensPerSecond", metrics?.decodeTokensPerSecond))
                publish()
            }
            report.put("phase", "complete")
            assertEquals(2, rows.length())
            assertEquals(original, InferenceSettingsPreferences(context).load())
        } finally {
            runtime.releaseAndWait()
            ExperimentalFlags.enableBenchmark = originalBenchmark
            ExperimentalFlags.enableSpeculativeDecoding = originalSpeculative
            report.put("settingsUnchanged", original == InferenceSettingsPreferences(context).load()); publish()
        }
    }
}
