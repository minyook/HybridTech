package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer

/** Executes the production pure-Kotlin retriever on the real assets and unchanged 150+33 rubric. */
class NativeAnswerCorpusTest {
    @Test fun fixedCorpusAndRubric() {
        val root = if (File("src/main/assets").exists()) File(".") else File("app")
        val rawCorpus = File(root, "src/main/assets/knowledge/seed_chunks_v1.json").readBytes()
        val raw = JSONObject(String(rawCorpus)).getJSONArray("chunks")
        val chunks = (0 until raw.length()).map { index -> val row = raw.getJSONObject(index); KnowledgeChunk(
            id = index + 1L, documentId = row.getString("documentId"), documentTitle = row.getString("documentTitle"), sourceKind = row.getString("sourceKind"), pageNumber = row.getInt("pageNumber"), heading = row.getString("heading"), body = row.getString("body"), chunkIndex = row.getInt("chunkIndex"),
        ) }
        val retriever = FocusedRetriever(chunks)
        val results = JSONArray(); val groups = JSONObject(); val hashes = JSONObject().put("corpus", sha(rawCorpus))
        for (name in listOf("evaluation-150.json", "focused-retrieval.json")) {
            val bytes = File(root, "src/androidTest/assets/rag-evaluation/$name").readBytes(); hashes.put(name, sha(bytes))
            assertEquals("Android/Web rubric must stay identical", sha(File(root, "../web/tests/$name").readBytes()), sha(bytes))
            val dataset = JSONObject(String(bytes)); val cases = dataset.getJSONArray("cases")
            var pagePasses = 0; var bodyPasses = 0; var passes = 0
            for (index in 0 until cases.length()) {
                val item = cases.getJSONObject(index); val question = item.getString("question")
                val started = System.nanoTime(); val sources = retriever.retrieve(question); val answer = FocusedEvidence.format(sources)
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
                if (first.isNotBlank() && !norm(answer.take(400)).contains(norm(first))) failures.add("no direct answer: $first")
                val bodyPass = failures.isEmpty()
                if (pagePass) pagePasses++; if (bodyPass) bodyPasses++; if (pagePass && bodyPass) passes++
                results.put(JSONObject().put("set", name).put("question", question).put("pages", JSONArray(pages)).put("pagePass", pagePass).put("bodyPass", bodyPass).put("pass", pagePass && bodyPass).put("failures", JSONArray(failures)).put("chars", answer.length).put("millis", millis))
            }
            groups.put(name, JSONObject().put("total", cases.length()).put("pagePass", pagePasses).put("bodyPass", bodyPasses).put("pass", passes))
        }
        val report = JSONObject().put("platform", "PC JVM; not Android device timing").put("scope", "production retriever + selected source body; no generation or audio; development regression, not unseen holdout").put("hashes", hashes).put("groups", groups).put("results", results)
        File(root, "build/reports/native-rag").apply { mkdirs() }.resolve("after-jvm.json").writeText(report.toString(2))
        assertEquals(groups.toString(), 0, (0 until results.length()).count { !results.getJSONObject(it).getBoolean("pass") })
    }
    private fun strings(array: JSONArray?) = if (array == null) emptyList() else (0 until array.length()).map { array.getString(it) }
    private fun norm(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase().replace(Regex("[\\s\\\\*_`#]"), "")
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
