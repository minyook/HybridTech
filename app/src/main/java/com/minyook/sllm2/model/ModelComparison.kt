/* 소스 안내
 * 설정의 비교 요청을 실제 모델별 생성으로 실행하고 원문·오류·시간·품질 표시를 별도 AtomicFile 보고서로 저장한다.
 * 연결 모듈: com.minyook.sllm2.data.AnswerMarkdown, com.minyook.sllm2.data.DocumentAnswerPlan, com.minyook.sllm2.data.KnowledgeRepository, com.minyook.sllm2.data.SemanticEncoder.
 */
package com.minyook.sllm2.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.AtomicFile
import com.minyook.sllm2.data.AnswerMarkdown
import com.minyook.sllm2.data.DocumentAnswerPlan
import com.minyook.sllm2.data.KnowledgeRepository
import com.minyook.sllm2.data.SemanticEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ModelComparisonRow(
    val modelId: String,
    val question: String,
    val reference: String,
    val answer: String,
    val rawAnswer: String,
    val firstSentenceMs: Long?,
    val totalMs: Long,
    val backend: String,
    val initializationMs: Long,
    val promptBytes: Int,
    val outputTokens: Int?,
    val tokensPerSecond: Double?,
    val availableRamMb: Long,
    val thermalStatus: Int,
    val error: String = "",
    val correct: Boolean? = null,
    val reusedEngine: Boolean = false,
    val modelFileName: String = "",
    val effectiveContextTokens: Int = 0,
    val fallbackReason: String = "",
    val caseId: String = "",
    val caseTitle: String = "",
    val reviewChecklist: List<String> = emptyList(),
    val missingEvidenceQueries: List<String> = emptyList(),
    val requestedOutputTokens: Int = 0,
    val outputLimitReached: Boolean? = null,
) {
    fun json() = JSONObject().put("modelId", modelId).put("question", question).put("reference", reference)
        .put("answer", answer).put("rawAnswer", rawAnswer).put("firstSentenceMs", firstSentenceMs ?: JSONObject.NULL)
        .put("totalMs", totalMs).put("backend", backend).put("initializationMs", initializationMs)
        .put("promptBytes", promptBytes).put("outputChars", rawAnswer.length).put("outputTokens", outputTokens ?: JSONObject.NULL)
        .put("tokensPerSecond", tokensPerSecond ?: JSONObject.NULL).put("availableRamMb", availableRamMb)
        .put("thermalStatus", thermalStatus).put("error", error).put("correct", correct ?: JSONObject.NULL)
        .put("reusedEngine", reusedEngine)
        .put("modelFileName", modelFileName).put("effectiveContextTokens", effectiveContextTokens).put("fallbackReason", fallbackReason)
        .put("caseId", caseId).put("caseTitle", caseTitle).put("reviewChecklist", JSONArray(reviewChecklist))
        .put("missingEvidenceQueries", JSONArray(missingEvidenceQueries)).put("requestedOutputTokens", requestedOutputTokens)
        .put("outputLimitReached", outputLimitReached ?: JSONObject.NULL)

    companion object {
        fun parse(j: JSONObject) = ModelComparisonRow(
            j.getString("modelId"), j.getString("question"), j.getString("reference"), j.getString("answer"),
            j.getString("rawAnswer"), if (j.isNull("firstSentenceMs")) null else j.getLong("firstSentenceMs"),
            j.getLong("totalMs"), j.getString("backend"), j.getLong("initializationMs"), j.getInt("promptBytes"),
            if (j.isNull("outputTokens")) null else j.getInt("outputTokens"),
            if (j.isNull("tokensPerSecond")) null else j.getDouble("tokensPerSecond"),
            j.getLong("availableRamMb"), j.getInt("thermalStatus"), j.optString("error"),
            if (j.isNull("correct")) null else j.getBoolean("correct"),
            j.optBoolean("reusedEngine"),
            j.optString("modelFileName"), j.optInt("effectiveContextTokens"), j.optString("fallbackReason"),
            j.optString("caseId"), j.optString("caseTitle"),
            j.optJSONArray("reviewChecklist")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            j.optJSONArray("missingEvidenceQueries")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            j.optInt("requestedOutputTokens"), if(j.isNull("outputLimitReached")) null else j.getBoolean("outputLimitReached"),
        )
    }
}

data class ModelComparisonReport(
    val startedAt: Long,
    val device: String,
    val requestedBackend: String,
    val contextTokens: Int = FieldComparisonSuite.CONTEXT_TOKENS,
    val outputTokens: Int = FieldComparisonSuite.OUTPUT_TOKENS,
    val rows: List<ModelComparisonRow> = emptyList(),
    val phase: String = "running",
    val suiteId: String = FieldComparisonSuite.ID,
    val questionCount: Int = FieldComparisonSuite.cases.size,
) {
    fun json() = JSONObject().put("version", 2).put("startedAt", startedAt).put("device", device)
        .put("scope", "Same retrieved evidence and prompts, forced native generation. Excludes retrieval, UI and audio. Human quality review.")
        .put("requestedBackend", requestedBackend).put("contextTokens", contextTokens).put("outputTokens", outputTokens)
        .put("phase", phase).put("rows", JSONArray(rows.map { it.json() }))
        .put("suiteId", suiteId).put("questionCount", questionCount)
        .put("comparisonInstruction", if(suiteId == FieldComparisonSuite.ID) FieldComparisonSuite.INSTRUCTION else "")
        .put("caseSpecifications", if(suiteId == FieldComparisonSuite.ID) JSONArray(FieldComparisonSuite.cases.map { scenario ->
            JSONObject().put("id", scenario.id).put("title", scenario.title).put("question", scenario.question)
                .put("evidenceQueries", JSONArray(scenario.evidenceQueries)).put("reviewChecklist", JSONArray(scenario.reviewChecklist))
        }) else JSONArray())

    companion object {
        fun parse(j: JSONObject): ModelComparisonReport {
            val rows = j.getJSONArray("rows")
            return ModelComparisonReport(j.getLong("startedAt"), j.getString("device"), j.getString("requestedBackend"),
                j.getInt("contextTokens"), j.getInt("outputTokens"),
                (0 until rows.length()).map { ModelComparisonRow.parse(rows.getJSONObject(it)) }, j.getString("phase"),
                j.optString("suiteId", "quick-v1"), j.optInt("questionCount", 5))
        }
    }
}

/** 모델 비교 결과만 AtomicFile로 저장한다. 일반 대화 기록/평소 추론 설정과 저장소를 공유하지 않는다. */
class ModelComparisonStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "model-comparison.json"))

    fun save(report: ModelComparisonReport) {
        val stream = file.startWrite()
        try {
            stream.write(report.json().toString(2).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    fun load(): ModelComparisonReport? = runCatching {
        val j = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        ModelComparisonReport.parse(j).let { if(it.phase == "running") it.copy(phase = "interrupted") else it }
    }.getOrNull()
}

/** Common source packets and detailed field scenarios for native model comparison. */
/**
 * 설정의 모델 비교 기능 실행기. FieldComparisonSuite의 같은 문항/근거/예산으로 선택한 모델을 순서대로 실행한다.
 * 실제 본문과 시간·backend·오류를 문항별로 기록하고 진행 결과를 UI에 전달한다.
 * 텍스트 반환 성공과 정확도 평가는 다른 지표다. 사용자 품질 표시는 저장 보고서에 별도로 남긴다.
 */
class ModelComparisonRunner(private val context: Context) {
    suspend fun run(
        models: List<LocalModel>,
        backend: InferenceBackend,
        shouldStop: () -> Boolean,
        onPartial: (String) -> Unit = {},
        onProgress: (ModelComparisonReport, String) -> Unit,
    ): ModelComparisonReport = withContext(Dispatchers.Default) {
        val cases = FieldComparisonSuite.cases
        var report = ModelComparisonReport(System.currentTimeMillis(), "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}", backend.name)
        val repository = KnowledgeRepository()
        val index = context.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use { it.readText() }
        val encoder = SemanticEncoder(context)
        onProgress(report, "공통 문서 근거를 준비하고 있어요.")
        val plans = try {
            cases.mapIndexed { number, scenario ->
                check(!shouldStop()) { "비교를 중지했어요." }
                onProgress(report, "공통 근거 준비 · ${number + 1}/${cases.size} · ${scenario.title}")
                val results = scenario.evidenceQueries.map { query ->
                    check(!shouldStop()) { "비교를 중지했어요." }
                    repository.searchQuestion(query, index) { encoder.cachedVector(it) ?: encoder.encode(it) }
                }
                // The same source page can supply different conditions for different subquestions.
                val sources = FieldComparisonSuite.mergeSources(results)
                val missing = scenario.evidenceQueries.zip(results).filter { (_, result) ->
                    result.sources.isEmpty() || !result.searchComplete && result.method !in setOf("heading", "inventory")
                }.map { it.first }
                DocumentAnswerPlan.create(scenario.question, sources) to missing
            }
        } finally { encoder.release() }
        for (model in models) {
            val runtime = LocalModelRuntime(context) {
                InferenceSettings(backend, report.contextTokens, report.outputTokens, automaticAnswerLength = false, model = model)
            }
            try {
                for ((number, prepared) in plans.withIndex()) {
                    val (plan, missingQueries) = prepared
                    val scenario = cases[number]
                    if (shouldStop()) return@withContext report.copy(phase = "stopped")
                    onPartial("")
                    onProgress(report, "${model.title} · ${number + 1}/${plans.size} · ${scenario.title}")
                    val memory = ActivityManager.MemoryInfo().also {
                        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
                    }
                    val thermal = if (Build.VERSION.SDK_INT >= 29)
                        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else -1
                    var metrics: GenerationMetrics? = null
                    var firstSentence: Long? = null
                    var raw = ""
                    var latestRaw = ""
                    var answer = ""
                    var failure = ""
                    var lastPreview: String? = null
                    var lastPublish = 0L
                    val started = SystemClock.elapsedRealtime()
                    try {
                        check(plan.evidence.isNotEmpty()) { "비교에 필요한 공통 문서 근거를 준비하지 못했어요." }
                        val question = plan.question + "\n\n" + FieldComparisonSuite.INSTRUCTION
                        raw = runtime.generate(question, plan.context(),
                            onMetrics = { metrics = it }, onPartialAnswer = { partial ->
                                latestRaw = partial
                                val preview = AnswerMarkdown.preview(partial)
                                val now = SystemClock.elapsedRealtime()
                                if (firstSentence == null && preview != null) firstSentence = now - started
                                if (preview != null && preview != lastPreview && now - lastPublish >= 250) {
                                    onPartial(preview)
                                    lastPreview = preview
                                    lastPublish = now
                                }
                            })
                        check(raw.isNotBlank()) { "모델이 답변을 반환하지 않았어요." }
                        answer = AnswerMarkdown.normalize(raw)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failure = error.message?.take(200) ?: "모델 실행에 실패했어요."
                        raw = latestRaw
                        answer = lastPreview.orEmpty()
                        runtime.release()
                    }
                    report = report.copy(rows = report.rows + ModelComparisonRow(
                        model.id, plan.question, plan.body, answer, raw, firstSentence,
                        SystemClock.elapsedRealtime() - started, metrics?.backend ?: "unknown",
                        metrics?.initializationMs ?: 0, metrics?.promptBytes ?: 0, metrics?.decodeTokens,
                        metrics?.decodeTokensPerSecond, memory.availMem / 1024 / 1024, thermal, failure,
                        reusedEngine = metrics?.reusedEngine == true,
                        modelFileName = metrics?.modelFileName.orEmpty(), effectiveContextTokens = metrics?.effectiveContextTokens ?: 0,
                        fallbackReason = metrics?.fallbackReason.orEmpty(),
                        caseId = scenario.id, caseTitle = scenario.title, reviewChecklist = scenario.reviewChecklist,
                        missingEvidenceQueries = missingQueries, requestedOutputTokens = report.outputTokens,
                        outputLimitReached = metrics?.decodeTokens?.let { it >= report.outputTokens },
                    ))
                    onProgress(report, "${model.title} · ${number + 1}/${plans.size} 완료")
                }
            } finally { runtime.releaseAndWait() }
        }
        onPartial("")
        report.copy(phase = "complete")
    }
}
