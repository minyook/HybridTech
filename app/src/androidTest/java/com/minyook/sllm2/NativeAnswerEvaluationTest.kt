package com.minyook.sllm2

import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.data.KnowledgeRepository
import com.minyook.sllm2.data.KnowledgeSeeder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer

/** Fixed development regression rubric, NOT an unseen holdout or generated-answer accuracy score. */
class NativeAnswerEvaluationTest {
    @Test fun evaluateUnchanged150AndFocused33WithoutModelOrHistoryWrites() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        KnowledgeSeeder(app).seedIfNeeded()
        val repository = KnowledgeRepository()
        val mode = InstrumentationRegistry.getArguments().getString("evaluationMode") ?: "after"
        val results = JSONArray(); val groups = JSONObject(); val hashes = JSONObject()
        for (name in listOf("evaluation-150.json", "focused-retrieval.json")) {
            val raw = instrumentation.context.assets.open("rag-evaluation/$name").use { it.readBytes() }
            hashes.put(name, sha(raw))
            val dataset = JSONObject(String(raw)); val cases = dataset.getJSONArray("cases")
            var pagePasses = 0; var bodyPasses = 0; var passes = 0
            for (index in 0 until cases.length()) {
                val item = cases.getJSONObject(index); val question = item.getString("question")
                val started = System.nanoTime(); val sources = repository.retrieveForQuestion(question)
                val answer = repository.answerWithoutModel(sources)
                val millis = (System.nanoTime() - started) / 1_000_000.0
                val pages = sources.filter { it.chunk.documentId == dataset.getString("documentId") }.map { it.chunk.pageNumber }
                val expected = item.getJSONArray("pages")
                val pagePass = if (expected.length() == 0) sources.isEmpty() else (0 until expected.length()).all { j ->
                    val alternatives = expected.optJSONArray(j)
                    if (alternatives == null) expected.getInt(j) in pages else (0 until alternatives.length()).any { alternatives.getInt(it) in pages }
                }
                val failures = mutableListOf<String>()
                for (clause in strings(item.optJSONArray("required"))) if (!norm(answer).contains(norm(clause))) failures.add("missing: $clause")
                for (clause in strings(item.optJSONArray("forbidden"))) if (norm(answer).contains(norm(clause))) failures.add("unrelated/forbidden: $clause")
                val first = item.optString("first")
                if (first.isNotEmpty() && !norm(answer.take(400)).contains(norm(first))) failures.add("no direct answer in first 400: $first")
                val bodyPass = failures.isEmpty()
                if (pagePass) pagePasses++; if (bodyPass) bodyPasses++; if (pagePass && bodyPass) passes++
                results.put(JSONObject().put("set", name).put("question", question).put("pages", JSONArray(pages)).put("pagePass", pagePass).put("bodyPass", bodyPass).put("pass", pagePass && bodyPass).put("failures", JSONArray(failures)).put("chars", answer.length).put("millis", millis))
            }
            groups.put(name, JSONObject().put("total", cases.length()).put("pagePass", pagePasses).put("bodyPass", bodyPasses).put("pass", passes))
        }
        hashes.put("corpus", sha(app.assets.open("knowledge/seed_chunks_v1.json").use { it.readBytes() }))
        val report = JSONObject().put("mode", mode).put("scope", "retrieval + selected source body; no generation/audio; development regression, not unseen holdout").put("hashes", hashes).put("groups", groups).put("results", results)
        File(app.getExternalFilesDir(null), "rag-checks").apply { mkdirs() }.resolve("native-$mode.json").writeText(report.toString(2))
        if (mode != "baseline") assertEquals(report.getJSONObject("groups").toString(), 0, (0 until results.length()).count { !results.getJSONObject(it).getBoolean("pass") })
    }
    private fun strings(array: JSONArray?) = if (array == null) emptyList() else (0 until array.length()).map { array.getString(it) }
    private fun norm(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().replace(Regex("[\\s\\\\*_`#]"), "")
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
