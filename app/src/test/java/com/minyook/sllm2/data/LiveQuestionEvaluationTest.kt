/* 소스 안내
 * 새 표현 질문의 실제 제품 검색 결과을 검증하는 JVM 회귀 검사다.
 * 검사 예: evaluateOriginal150WithProductionCoordinatorAndRealQueryEmbeddings, evaluateAdditionalExpressionsWithRealQueryEmbeddings, evaluate
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant

/** Opt-in measurement, not a replacement for device/Gemma/UI tests or an unseen holdout. */
class LiveQuestionEvaluationTest {
    /** 실제 사용자 문장과 고정 검색 모델로 주제 선택 메뉴가 되살아나지 않는지 확인한다. */
    @Test fun originalSupervisorQuestionDoesNotOfferTopicChoicesWithRealEmbeddings() {
        assumeTrue(System.getenv("FIELDGUARD_DIRECT_QUESTION") == "1")
        val app = if (File("src/main/assets").exists()) File(".").canonicalFile else File("app").canonicalFile
        val root = app.parentFile
        val out = File(root, "app/build/reports/fieldguard/direct-answers").apply { mkdirs() }
        val questions = listOf(
            "밀폐공간 작업을 중단했다가 다시 시작할 때 감독관이 확인할 사항을 핵심 5개로 정리해줘. 각 항목은 한 문장으로 설명해줘.",
            "산소 및 유해가스 측정방법 알려줘",
        )
        val tokenizer = SemanticTokenizer(File(app,"src/main/assets/semantic-model/tokenizer.json").readText())
        val queries = questions.flatMap { listOf(it, QuestionUnderstanding.normalize(it)) }.distinct()
        File(out,"tokenized-queries.json").writeText(JSONArray(queries.map { q ->
            JSONObject().put("question",q).put("ids",JSONArray(tokenizer.encode("task: search result | query: $q").toList()))
        }).toString())
        File(out,"progress.json").writeText("{}")
        val process = ProcessBuilder("node",File(root,"tools/embed-android-evaluation.mjs").absolutePath,out.absolutePath)
            .directory(root).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        File(out,"embedding.log").writeText(log)
        assertEquals(log,0,process.waitFor())
        val encoded = JSONObject(File(out,"query-vectors.json").readText()).getJSONArray("queries")
        val vectors = (0 until encoded.length()).associate { i ->
            val row=encoded.getJSONObject(i); val vector=row.getJSONArray("vector")
            row.getString("question") to FloatArray(vector.length()){vector.getDouble(it).toFloat()}
        }
        val seed = JSONObject(File(app,"src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
        val chunks = (0 until seed.length()).map { i -> val row=seed.getJSONObject(i)
            KnowledgeChunk(id=i+1L,documentId=row.getString("documentId"),documentTitle=row.getString("documentTitle"),sourceKind=row.getString("sourceKind"),pageNumber=row.getInt("pageNumber"),chunkIndex=row.getInt("chunkIndex"),body=row.getString("body"))
        }
        val search = QuestionSearchCoordinator(HybridRetrieval(chunks,File(app,"src/main/assets/knowledge/semantic-index-v1.json").readText())){vectors.getValue(it)}
        val results=JSONArray()
        questions.forEach { q ->
            val result=search.search(ConversationContext.resolve(q,emptyList()))
            assertTrue(q,result.status in setOf("answered","no-evidence"))
            assertTrue(q,result.suggestions.isEmpty())
            assertFalse(q,result.message.contains("선택"))
            if(result.generalQuestion!=null)assertEquals(q,result.generalQuestion)
            val plan=DocumentAnswerPlan.create(q,result)
            results.put(JSONObject().put("question",q).put("status",result.status).put("method",result.method)
                .put("suggestions",result.suggestions.size).put("generalQuestion",result.generalQuestion?:JSONObject.NULL)
                .put("pages",JSONArray(result.sources.map{it.chunk.pageNumber})).put("documentBody",plan.body))
        }
        File(out,"results.json").writeText(JSONObject().put("scope","PC production Kotlin routing with actual pinned ONNX query embeddings; no device UI or Gemma generation").put("results",results).toString(2))
    }

    @Test fun evaluateOriginal150WithProductionCoordinatorAndRealQueryEmbeddings() {
        evaluate(false)
    }
    @Test fun evaluateAdditionalExpressionsWithRealQueryEmbeddings() {
        evaluate(true)
    }
    private fun evaluate(additional:Boolean) {
        assumeTrue("Set FIELDGUARD_EVALUATE_150=1 for this slower, local-model evaluation", System.getenv("FIELDGUARD_EVALUATE_150") == "1")
        val app = if (File("src/main/assets").exists()) File(".").canonicalFile else File("app").canonicalFile
        val root = app.parentFile
        val baseOut = File(root, System.getenv("FIELDGUARD_EVALUATION_DIR") ?: "app/build/reports/fieldguard/evaluation-150")
        val out = (if(additional)File(baseOut,"additional") else baseOut).apply { mkdirs() }
        val rubricFile = File(app, if(additional)"src/test/resources/additional-questions-20261103.json" else "src/androidTest/assets/rag-evaluation/evaluation-150.json")
        val rubric = JSONObject(rubricFile.readText())
        val cases = rubric.getJSONArray("cases")
        val total=if(additional)24 else 150
        assertEquals(total, cases.length())
        val questionFile = File(root, "밀폐공간_테스트_질문_150개.txt")
        val verifyQuestionFile=questionFile.exists()&&!additional
        val questionList = if(verifyQuestionFile) Regex("(?m)^\\d{3}\\. (.+?)\\r?$").findAll(questionFile.readText()).map { it.groupValues[1] }.toList()
            else (0 until cases.length()).map { cases.getJSONObject(it).getString("question") }
        assertEquals("Use the user's original questions, in original order", (0 until cases.length()).map { cases.getJSONObject(it).getString("question") }, questionList)
        val corpusFile = File(app, "src/main/assets/knowledge/seed_chunks_v1.json")
        val semanticFile = File(app, "src/main/assets/knowledge/semantic-index-v1.json")
        val chunksJson = JSONObject(corpusFile.readText()).getJSONArray("chunks")
        val chunks = (0 until chunksJson.length()).map { i ->
            val c = chunksJson.getJSONObject(i)
            KnowledgeChunk(id=i+1L, documentId=c.getString("documentId"), documentTitle=c.getString("documentTitle"), sourceKind=c.getString("sourceKind"), pageNumber=c.getInt("pageNumber"), chunkIndex=c.getInt("chunkIndex"), heading=c.getString("heading"), body=c.getString("body"))
        }
        val rows = JSONArray()
        val report = JSONObject().put("startedAt", Instant.now().toString()).put("phase", "tokenizing").put("total", total).put("completed", 0).put("encoded", 0)
            .put("scope", "PC JVM: current Android QuestionSearchCoordinator + DocumentAnswerPlan; actual pinned query model on PC ONNX CPU. No Android device, UI, STT/TTS, or Gemma generation execution.")
            .put("rubricStatus", rubric.getString("rubricStatus")).put("originalQuestionFileVerified",verifyQuestionFile).put("hashes", JSONObject().put("questions",if(verifyQuestionFile)sha(questionFile) else JSONObject.NULL).put("rubric",sha(rubricFile)).put("corpus",sha(corpusFile)).put("semanticIndex",sha(semanticFile)))
            .put("questions",JSONArray(questionList)).put("results", rows)
        fun publish() {
            val temporary=File(out,"progress.json.part").apply{writeText(report.toString(2))}
            // A Windows HTTP reader can briefly hold the old snapshot open without delete sharing.
            for(attempt in 0..49)try {
                java.nio.file.Files.move(temporary.toPath(),File(out,"progress.json").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                return
            }catch(error:java.nio.file.FileSystemException){if(attempt==49)throw error;Thread.sleep(20)}
        }
        publish()
        val tokenizer = SemanticTokenizer(File(app,"src/main/assets/semantic-model/tokenizer.json").readText())
        val queries = linkedSetOf<String>()
        fun collect(q: String) { if (queries.add(q)) { queries.add(QuestionUnderstanding.normalize(q));FocusedRetriever.aspects(q).filter { it != q }.forEach(::collect);QuestionSearchCoordinator.parts(q).filter{it!=q}.forEach(::collect) } }
        questionList.forEach(::collect)
        val inputs = JSONArray()
        for(q in queries) inputs.put(JSONObject().put("question",q).put("ids",JSONArray(tokenizer.encode("task: search result | query: $q").toList())))
        File(out,"tokenized-queries.json").writeText(inputs.toString())
        report.put("phase","embedding").put("queryTotal",queries.size); publish()
        val process = ProcessBuilder("node",File(root,"tools/embed-android-evaluation.mjs").absolutePath,out.absolutePath).directory(root).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        File(out,"embedding.log").writeText(log)
        if(process.waitFor()!=0) { report.put("phase","error").put("error",log.takeLast(4000));publish();fail("Query embedding failed: ${log.takeLast(2000)}") }
        val embedded=JSONObject(File(out,"query-vectors.json").readText())
        report.put("embedding",embedded.getJSONObject("metadata")).put("encoded",queries.size).put("phase","evaluating")
        val vectors=embedded.getJSONArray("queries")
        val byQuestion=(0 until vectors.length()).associate { i -> val v=vectors.getJSONObject(i);v.getString("question") to v }
        val indexStarted=System.nanoTime();val index=HybridRetrieval(chunks,semanticFile.readText())
        report.put("indexPrepareMs",(System.nanoTime()-indexStarted)/1e6)
        var passed=0
        for(i in 0 until cases.length()) {
            val c=cases.getJSONObject(i); val q=c.getString("question");val calls=mutableListOf<String>()
            val started=System.nanoTime()
            val result=QuestionSearchCoordinator(index) { query ->
                calls.add(query)
                val vector=byQuestion.getValue(query).getJSONArray("vector")
                FloatArray(vector.length()) { vector.getDouble(it).toFloat() }
            }.search(ConversationContext.resolve(q, emptyList()))
            val base=DocumentAnswerPlan.create(q,result)
            val body=if(result.sources.isEmpty())result.message else base.body+if(result.message.isNotBlank())"\n\n"+result.message else ""
            val kotlinMs=(System.nanoTime()-started)/1e6
            val pages=result.sources.filter{it.chunk.documentId==rubric.getString("documentId")}.map{it.chunk.pageNumber}.distinct()
            val expected=c.getJSONArray("pages")
            val pagePass=if(expected.length()==0)result.sources.isEmpty() else (0 until expected.length()).all { j ->
                val alternatives=expected.optJSONArray(j)
                if(alternatives==null)expected.getInt(j) in pages else (0 until alternatives.length()).any{alternatives.getInt(it) in pages}
            }
            val failures=mutableListOf<String>()
            if(!pagePass)failures.add("정답 페이지 불일치")
            if(c.has("expectedStatus")&&c.getString("expectedStatus")!=result.status)failures.add("검색 상태 불일치: ${c.getString("expectedStatus")}")
            for(clause in strings(c.optJSONArray("required")))if(!norm(body).contains(norm(clause)))failures.add("필수 내용 누락: $clause")
            for(clause in strings(c.optJSONArray("forbidden")))if(norm(body).contains(norm(clause)))failures.add("금지 내용 포함: $clause")
            val first=c.optString("first")
            if(first.isNotBlank()&&!norm(body.take(400)).contains(norm(first)))failures.add("첫 400자에 직접 답변 누락: $first")
            val pass=failures.isEmpty();if(pass)passed++
            val embeddingMs=calls.sumOf{byQuestion.getValue(it).getDouble("inferenceMs")}
            val pendingGeneral=result.generalQuestion!=null&&result.searchComplete
            val row=JSONObject().put("number",i+1).put("id",c.getString("id")).put("category",c.getString("category")).put("question",q)
                .put("status",result.status).put("method",result.method).put("semantic",result.semantic).put("searchComplete",result.searchComplete)
                .put("expectedPages",expected).put("pages",JSONArray(pages)).put("pagePass",pagePass).put("bodyPass",failures.none{!it.startsWith("정답 페이지")}).put("pass",pass)
                .put("failures",JSONArray(failures)).put("required",c.optJSONArray("required")?:JSONArray()).put("forbidden",c.optJSONArray("forbidden")?:JSONArray())
                .put("answerMarkdown",body).put("suggestions",JSONArray(result.suggestions.map{it.question})).put("encoderQueries",JSONArray(calls))
                .put("kotlinSearchAndCompositionMs",kotlinMs).put("pcModelInferenceSumMs",embeddingMs).put("pcMeasuredWorkSumMs",kotlinMs+embeddingMs)
                .put("generation",if(pendingGeneral)"not-run: general knowledge requires Android Gemma" else if(base.supplementRequested&&result.status=="answered")"not-run: optional document supplement" else "not-needed")
                .put("generalQuestion",result.generalQuestion?:JSONObject.NULL)
                .put("sources",JSONArray(result.sources.map { JSONObject().put("documentId",it.chunk.documentId).put("title",it.chunk.documentTitle).put("page",it.chunk.pageNumber) }))
            rows.put(row)
            report.put("completed",i+1).put("passed",passed).put("failed",i+1-passed);publish()
            println("[${i+1}/$total] ${if(pass)"PASS" else "FAIL"} ${result.status} $q")
        }
        val summary=JSONObject()
        for(category in (0 until cases.length()).map{cases.getJSONObject(it).getString("category")}.distinct()) {
            val group=(0 until rows.length()).map{rows.getJSONObject(it)}.filter{it.getString("category")==category}
            summary.put(category,JSONObject().put("total",group.size).put("passed",group.count{it.getBoolean("pass")}))
        }
        report.put("groups",summary).put("phase","complete").put("finishedAt",Instant.now().toString());publish()
        File(out,"results.json").writeText(report.toString(2))
        val markdown=buildString {
            append("# Android ${total}개 질문 평가 · 2026-11-03\n\n")
            append("- 실행: PC JVM의 현재 Android 검색·답변 조립 코드 + 실제 검색 모델 CPU 추론\n- 범위: $total 개 개발 질문, 정답 페이지·필수/금지 내용 자동 검사\n- 제외: Android 기기·UI·Gemma 일반 지식/보충 생성·음성·실기기 응답 시간\n")
            append("- 통과: $passed / $total, 실패: ${total-passed} / $total\n- 문서 밖 질문의 통과는 관련 없는 문서 미반환 검사이며 AI 답변 생성 성공을 뜻하지 않음\n- PC 추론은 미리 계산한 실제 질문 벡터를 사용하며, 검색 시간과 모델 시간의 합은 앱 전송부터 표시까지의 실측 시간이 아님\n\n")
            for(i in 0 until rows.length()) {val r=rows.getJSONObject(i)
                append("## %03d. %s\n\n".format(i+1,r.getString("question")))
                append("- 결과: ${if(r.getBoolean("pass"))"통과" else "실패"} / 상태: ${r.getString("status")} / 경로: ${r.getString("method").ifBlank{"-"}}\n")
                append("- 정답 페이지: ${r.getJSONArray("expectedPages")} / 반환 페이지: ${r.getJSONArray("pages")}\n- 생성: ${r.getString("generation")}\n")
                for(f in strings(r.getJSONArray("failures")))append("- $f\n")
                append("\n### 실제 문서 답변 Markdown\n\n${r.getString("answerMarkdown").ifBlank{"(문서 본문 없음; 일반 지식 생성은 미실행)"}}\n\n---\n\n")
            }
        }
        File(out,"results.md").writeText(markdown.trimEnd()+"\n")
        // Execution assertions only. Quality failures remain visible instead of being tuned away.
        assertEquals(total,rows.length())
        assertTrue(rows.toString(),(0 until rows.length()).none{rows.getJSONObject(it).getString("status")=="search-error"})
        println("EVALUATION COMPLETE: $passed/$total rubric passes. This is not an Android/Gemma end-to-end pass.")
    }
    private fun strings(a:JSONArray?)=if(a==null)emptyList() else (0 until a.length()).map{a.getString(it)}
    private fun norm(s:String)=Normalizer.normalize(s,Normalizer.Form.NFKC).lowercase().replace(Regex("[\\s\\\\*_`#]"),"")
    private fun sha(f:File)=MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString(""){"%02x".format(it)}
}
