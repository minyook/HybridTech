/* 소스 안내
 * 공통 현장 문항을 사용한 실제 모델 비교을 검증하는 Android 계측 검사다.
 * 검사 예: diagnoseSelectedBackend, measureDetailedFieldAnswers, write
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
package com.minyook.sllm2

import android.os.Bundle
import android.os.SystemClock
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.data.KnowledgeSeeder
import com.minyook.sllm2.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in physical-device comparison using the same runner as Settings. */
class FieldModelComparisonTest {
    @Test fun diagnoseSelectedBackend() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runBackendProbe") == "true")
        val context = instrumentation.targetContext
        val model = LocalModel.entries.single { it.id == args.getString("modelId") }
        val backend = InferenceBackend.valueOf(args.getString("backend") ?: "CPU")
        val contextTokens = (args.getString("contextTokens") ?: "4096").toInt()
        val original = InferenceSettingsPreferences(context).load()
        val label = "probe-${model.id}-${backend.name.lowercase()}-$contextTokens"
        val output = File(context.getExternalFilesDir(null), "generation-checks/$label.json")
        val rows = org.json.JSONArray()
        var outputLimit = 256
        val runtime = LocalModelRuntime(context) {
            InferenceSettings(backend, contextTokens, outputLimit, automaticAnswerLength = false, model = model)
        }
        val report = JSONObject().put("modelId", model.id).put("requestedBackend", backend.name)
            .put("scope", "Diagnostic short prompts, separate from the 12-case detailed comparison; do not pool timings.")
            .put("shortPromptOutputTokens", 256).put("contextTokens", contextTokens).put("rows", rows).put("phase", "running")
        try {
            data class Probe(val question: String, val evidence: String, val general: Boolean, val limit: Int)
            val probes = mutableListOf(
                Probe("Explain why oxygen is important for breathing in three sentences.", "", true, 256),
                Probe("산소가 호흡에 필요한 이유를 한국어 세 문장으로 설명해줘.", "", true, 256))
            if (args.getString("includeField") == "true") {
                check(contextTokens == FieldComparisonSuite.CONTEXT_TOKENS) { "Detailed field diagnostic requires the common comparison context" }
                KnowledgeSeeder(context).seedIfNeeded()
                val repository = com.minyook.sllm2.data.KnowledgeRepository()
                val index = context.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use { it.readText() }
                val encoder = com.minyook.sllm2.data.SemanticEncoder(context)
                val scenario = FieldComparisonSuite.cases.first()
                val sources = try { FieldComparisonSuite.mergeSources(scenario.evidenceQueries.map { query ->
                    repository.searchQuestion(query, index) { encoder.cachedVector(it) ?: encoder.encode(it) }
                }) } finally { encoder.release() }
                val plan = com.minyook.sllm2.data.DocumentAnswerPlan.create(scenario.question, sources)
                if (args.getString("fieldOnly") == "true") probes.clear()
                probes += Probe(scenario.question + "\n\n" + FieldComparisonSuite.INSTRUCTION, plan.context(), false, 2048)
            }
            for (probe in probes) {
                val question = probe.question
                outputLimit = probe.limit
                val started = SystemClock.elapsedRealtime()
                var metrics: GenerationMetrics? = null
                var raw = ""
                var error = ""
                try { raw = runtime.generate(question, probe.evidence, generalKnowledge = probe.general, onMetrics = { metrics = it }) }
                catch (failure: Exception) { error = "${failure.javaClass.simpleName}: ${failure.message}" }
                rows.put(JSONObject().put("question", question).put("rawAnswer", raw).put("error", error)
                    .put("maxOutputTokens", probe.limit).put("generalKnowledge", probe.general)
                    .put("totalMs", SystemClock.elapsedRealtime() - started).put("actualBackend", metrics?.backend)
                    .put("initializationMs", metrics?.initializationMs).put("promptBytes", metrics?.promptBytes)
                    .put("firstNativeTextMs", metrics?.firstChunkMs).put("modelFileName", metrics?.modelFileName)
                    .put("effectiveContextTokens", metrics?.effectiveContextTokens).put("fallbackReason", metrics?.fallbackReason)
                    .put("outputTokens", metrics?.decodeTokens).put("tokensPerSecond", metrics?.decodeTokensPerSecond))
                output.writeText(report.toString(2))
            }
        } finally {
            runtime.releaseAndWait()
            report.put("phase", "complete").put("settingsUnchanged", original == InferenceSettingsPreferences(context).load())
            output.writeText(report.toString(2))
        }
        assertEquals(original, InferenceSettingsPreferences(context).load())
    }

    @Test fun measureDetailedFieldAnswers() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runFieldComparison") == "true")
        val context = instrumentation.targetContext
        val backend = InferenceBackend.valueOf(args.getString("backend") ?: "GPU")
        val modelIds = args.getString("modelIds")?.split(",")
        val models = LocalModel.entries.filter { modelIds == null || it.id in modelIds }
        check(models.isNotEmpty()) { "No comparison models selected" }
        check(models.all { ModelPreferences(context).status(it).phase == ModelPhase.READY }) { "Comparison model files are missing" }
        val originalSettings = InferenceSettingsPreferences(context).load()
        val label = (args.getString("runLabel") ?: "field-${backend.name.lowercase()}").replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val directory = File(context.getExternalFilesDir(null), "generation-checks").apply { mkdirs() }
        val resultFile = AtomicFile(File(directory, "$label.json"))
        val liveFile = AtomicFile(File(directory, "$label-live.json"))
        fun write(file: AtomicFile, json: JSONObject) {
            val stream = file.startWrite()
            try { stream.write(json.toString(2).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
        }
        KnowledgeSeeder(context).seedIfNeeded()
        val store = ModelComparisonStore(context)
        var progress = "Starting"
        var lastLiveAt = 0L
        var latest: ModelComparisonReport? = null
        try {
            val report = ModelComparisonRunner(context).run(models, backend, { false }, onPartial = { text ->
                val now = SystemClock.elapsedRealtime()
                if (text.isBlank() || now - lastLiveAt >= 1000) {
                    write(liveFile, JSONObject().put("progress", progress).put("preview", text)
                        .put("elapsedRealtimeMs", now).put("completed", latest?.rows?.size ?: 0))
                    lastLiveAt = now
                }
            }) { report, message ->
                latest = report
                progress = message
                write(resultFile, report.json().put("runLabel", label).put("progress", message))
                store.save(report)
                instrumentation.sendStatus(1, Bundle().apply {
                    putString("fieldComparisonProgress", "$label: ${report.rows.size}/${models.size * FieldComparisonSuite.cases.size} · $message")
                })
            }
            latest = report
            val unchanged = originalSettings == InferenceSettingsPreferences(context).load()
            write(resultFile, report.json().put("runLabel", label).put("settingsUnchanged", unchanged))
            store.save(report)
            assertEquals("All cases must be attempted", models.size * FieldComparisonSuite.cases.size, report.rows.size)
            assertEquals(originalSettings, InferenceSettingsPreferences(context).load())
        } catch (error: Throwable) {
            latest?.let { report -> write(resultFile, report.copy(phase = "interrupted").json()
                .put("runLabel", label).put("testError", "${error.javaClass.simpleName}: ${error.message}")) }
            throw error
        }
    }
}
