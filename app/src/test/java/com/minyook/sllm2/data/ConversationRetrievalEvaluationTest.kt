/* 소스 안내
 * 연속 발화의 실제 검색 근거와 확인 선택지을 검증하는 JVM 회귀 검사다.
 * 검사 예: chainedQuestionsAndScreenshotRepairUseTheRealDocumentIndex
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Actual local query embeddings + production dialogue resolver/retriever; no Gemma/UI claim. */
class ConversationRetrievalEvaluationTest {
    @Test fun chainedQuestionsAndScreenshotRepairUseTheRealDocumentIndex() {
        assumeTrue("FIELDGUARD_EVALUATE_150=1", System.getenv("FIELDGUARD_EVALUATE_150") == "1")
        val app = (if(File("src/main/assets").exists()) File(".") else File("app")).canonicalFile
        val root = app.parentFile
        val out = File(root, "app/build/reports/fieldguard/conversation").apply { mkdirs() }
        val steps = listOf(
            "산소 유해가스 측정방법", "그럼 언제 해야 해?", "누가 해야 해?", "또 다른 건 없어?", "항목이 안보이는데?",
            "심폐소생술 알려줘", "그럼 언제까지 반복해?", "그럼 호흡이 돌아오면?", "다른 얘기: 커피 추출 원리", "그거 쉽게 설명해줘",
        )
        val probes = mutableListOf<ConversationTurn>(); val queries = linkedSetOf<String>()
        for (asked in steps) {
            val request = ConversationContext.resolve(asked, probes)
            if (request.query.isNotBlank()) { queries += request.query; queries += QuestionUnderstanding.normalize(request.query) }
            probes += ConversationTurn(true, asked, request.query, request.topic)
            probes += ConversationTurn(false, "${request.topic}에 대한 답변입니다. 호흡과 조건을 확인해요.", topicQuestion = request.topic)
        }
        val tokenizer = SemanticTokenizer(File(app, "src/main/assets/semantic-model/tokenizer.json").readText())
        File(out, "tokenized-queries.json").writeText(JSONArray(queries.map { JSONObject().put("question", it).put("ids", JSONArray(tokenizer.encode("task: search result | query: $it").toList())) }).toString())
        File(out, "progress.json").writeText("{}")
        val process = ProcessBuilder("node", File(root, "tools/embed-android-evaluation.mjs").absolutePath, out.absolutePath).directory(root).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().use { it.readText() }; File(out, "embedding.log").writeText(log)
        assertEquals(log.takeLast(1000), 0, process.waitFor())
        val rows = JSONObject(File(out, "query-vectors.json").readText()).getJSONArray("queries")
        val vectors = (0 until rows.length()).associate { i -> val row = rows.getJSONObject(i); row.getString("question") to row.getJSONArray("vector").let { v -> FloatArray(v.length()) { v.getDouble(it).toFloat() } } }
        val corpus = JSONObject(File(app, "src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
        val chunks = (0 until corpus.length()).map { i -> val c = corpus.getJSONObject(i); KnowledgeChunk(id = i + 1L, documentId = c.getString("documentId"), documentTitle = c.getString("documentTitle"), sourceKind = c.getString("sourceKind"), pageNumber = c.getInt("pageNumber"), chunkIndex = c.getInt("chunkIndex"), body = c.getString("body")) }
        val search = QuestionSearchCoordinator(HybridRetrieval(chunks, File(app, "src/main/assets/knowledge/semantic-index-v1.json").readText())) { query -> vectors[query] ?: error("Unprepared query: $query") }
        val history = mutableListOf<ConversationTurn>(); val results = JSONArray(); var failures = 0
        val expectedPages = mapOf(0 to 27, 1 to 25, 2 to 25, 5 to 45, 6 to 46, 7 to 46)
        for ((number, asked) in steps.withIndex()) {
            val request = ConversationContext.resolve(asked, history)
            val result = search.search(request)
            val plan = DocumentAnswerPlan.create(request.answerQuestion, result)
            val body = if(result.sources.isEmpty()) result.message else plan.body + if(result.message.isBlank()) "" else "\n\n" + result.message
            val pages = result.sources.filter { it.chunk.documentId.contains("2022") }.map { it.chunk.pageNumber }
            val expected = expectedPages[number]
            val passed = when {
                expected != null -> result.status == "answered" && expected in pages
                number == 3 -> result.status in setOf("answered", "no-more") && result.generalQuestion == null
                number == 4 -> result.status == "conversation-choices" && result.generalQuestion == null && !body.contains("전체 검색에서")
                number >= 8 -> request.query.contains("커피") && !request.query.contains("산소") && result.generalQuestion != null
                else -> true
            }
            if (!passed) failures++
            results.put(JSONObject().put("step", number + 1).put("asked", asked).put("resolvedQuestion", request.query).put("topic", request.topic).put("kind", request.kind.name).put("status", result.status).put("pages", JSONArray(pages)).put("expectedPage", expected ?: JSONObject.NULL).put("pass", passed).put("answerMarkdown", body).put("choiceLabels", JSONArray(result.suggestions.map { it.label })))
            history += ConversationTurn(true, asked, request.query, request.topic)
            history += ConversationTurn(false, body, topicQuestion = request.topic, answerStatus = result.status, sourceIds = plan.sourceIds, suggestions = result.suggestions)
        }
        val report = JSONObject().put("scope", "PC real ONNX query embeddings and production dialogue/RAG, no generated model/UI").put("total", steps.size).put("passed", steps.size - failures).put("failed", failures).put("results", results)
        File(out, "results.json").writeText(report.toString(2))
        assertEquals(report.toString(2), 0, failures)
    }
}
