package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual production dialogue+index+answer assembly, with independent attribute/coverage checks.
 * PC timing is not Android/Gemma/rendered UI timing. No preset answers or page lookup. */
class EquipmentDialogueEvaluationTest {
    @Test fun screenshotAndAttributeChangesRemainDirectAndComplete() {
        val app = (if (File("src/main/assets").exists()) File(".") else File("app")).canonicalFile
        val root = app.parentFile
        val out = File(root, "tmp/dialogue-focus-20261105").apply { mkdirs() }
        val source = JSONObject(File(app,"src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
        val chunks = (0 until source.length()).map { i -> val c = source.getJSONObject(i); KnowledgeChunk(id=i+1L,documentId=c.getString("documentId"),documentTitle=c.getString("documentTitle"),sourceKind=c.getString("sourceKind"),pageNumber=c.getInt("pageNumber"),chunkIndex=c.getInt("chunkIndex"),body=c.getString("body")) }
        val preparedAt = System.nanoTime()
        val index = HybridRetrieval(chunks,File(app,"src/main/assets/knowledge/semantic-index-v1.json").readText())
        val preparationMs = (System.nanoTime()-preparedAt)/1e6
        var encoderCalls = 0
        val search = QuestionSearchCoordinator(index) { encoderCalls++; error("This direct source scope must not allocate the encoder/Gemma") }
        data class Case(val asked:String,val kind:FollowUpKind,val names:List<String>,val count:Boolean,val condition:String?=null)
        val all = listOf("안전대","구명줄","구조용 삼각대")
        val subset = listOf("구명줄","구조용 삼각대")
        val cases = listOf(
            Case("안전대와 구명줄 , 구조용 삼각대에 대해 알려줘",FollowUpKind.NEW,all,false),
            Case("갯수는 어느정도로 챙겨?",FollowUpKind.FOLLOW_UP,all,true),
            Case("구조용 삼각대랑 구명줄 등등 갯수 정해진 게 있나?",FollowUpKind.FOLLOW_UP,subset,true),
            Case("그럼 두 명이면?",FollowUpKind.FOLLOW_UP,subset,true,"두 명"),
            Case("쉽게 설명해줘",FollowUpKind.EXPLAIN,subset,true,"두 명"),
            Case("그럼 언제 써?",FollowUpKind.FOLLOW_UP,subset,false),
            Case("둘의 차이는?",FollowUpKind.FOLLOW_UP,subset,false),
            Case("각각 몇 개 필요해?",FollowUpKind.FOLLOW_UP,subset,true),
            Case("삼각대만 몇 대?",FollowUpKind.FOLLOW_UP,listOf("구조용 삼각대"),true),
            Case("그럼 한 개면 돼?",FollowUpKind.FOLLOW_UP,listOf("구조용 삼각대"),true,"한 개"),
        )
        val history = mutableListOf<ConversationTurn>()
        val results = JSONArray()
        for ((i,c) in cases.withIndex()) {
            val started = System.nanoTime()
            val request = ConversationContext.resolve(c.asked,history)
            val result = search.search(request)
            val plan = DocumentAnswerPlan.create(request.answerQuestion,result)
            val body = plan.body
            val ms = (System.nanoTime()-started)/1e6
            val errors = mutableListOf<String>()
            if (request.kind!=c.kind) errors += "대화 종류 ${request.kind} != ${c.kind}"
            if (QuestionFocus.targets(request.query).map { it.name }!=c.names) errors += "대상 불일치"
            if (result.status!="answered") errors += "직접 답변 실패: ${result.status}"
            if (result.generalQuestion!=null || plan.supplementRequested) errors += "불필요한 생성 요청"
            if (c.count && !body.take(160).contains("고정 준비 수량을 확인하지 못했어요")) errors += "첫 문장에 수량 확인 결과 누락"
            if (!c.count && c.names.contains("구조용 삼각대") && !body.take(700).contains("구조용 삼각대, 사다리, 섬유로프")) errors += "삼각대의 구조 용도 누락"
            if (c.asked.contains("차이") && !body.take(180).contains("비교")) errors += "첫 문장에 비교 요청 반영 누락"
            if (c.condition!=null && !body.contains("**${c.condition}**")) errors += "사용자 조건 누락"
            if (c.condition!=null && !body.contains("충분하다고 단정할 수는 없어요")) errors += "미확인 수량을 계산/승인"
            if (c.names.any { !body.contains(it) }) errors += "요청 대상의 설명 누락"
            if (Regex("어떤 종류|작업의 종류|몇 개씩 준비하면 됩니다").containsMatchIn(body)) errors += "불필요한 재질문/임의 수량"
            results.put(JSONObject().put("step",i+1).put("asked",c.asked).put("resolvedQuestion",request.query).put("kind",request.kind.name).put("method",result.method).put("expectedTargets",JSONArray(c.names)).put("expectedAttribute",if(c.count)"quantity" else "role/usage").put("pass",errors.isEmpty()).put("errors",JSONArray(errors)).put("pcSearchCompositionMs",ms).put("pages",JSONArray(result.sources.map { it.chunk.pageNumber })).put("answerMarkdown",body))
            history += ConversationTurn(true,c.asked,request.query,request.topic)
            history += ConversationTurn(false,body,topicQuestion=request.topic,answerStatus=result.status,sourceIds=plan.sourceIds,suggestions=result.suggestions)
        }
        val times = (0 until results.length()).map { results.getJSONObject(it).getDouble("pcSearchCompositionMs") }.sorted()
        val report = JSONObject().put("scope","PC JVM production dialogue resolver + actual document corpus/index + answer plan. No Android/Gemma/native generation/UI execution.").put("indexPrepareMs",preparationMs).put("encoderCalls",encoderCalls).put("gemmaCalls",0).put("total",cases.size).put("passed",(0 until results.length()).count { results.getJSONObject(it).getBoolean("pass") }).put("pcSearchCompositionMedianMs",times[times.size/2]).put("pcSearchCompositionMaxMs",times.last()).put("results",results)
        File(out,"dialogue-results.json").writeText(report.toString(2))
        File(out,"dialogue-examples.md").writeText("# 장비 질문 연속 대화 검사 · 2026-11-05\n\n실제 Android 대화 해석·문서·답변 조립 코드를 PC JVM에서 실행한 결과입니다. Gemma 생성/휴대폰/음성/화면 시간을 측정한 결과가 아닙니다.\n\n" + (0 until results.length()).joinToString("\n\n---\n\n") { i -> val r=results.getJSONObject(i); "## ${i+1}. ${r.getString("asked")}\n\n검색 질문: ${r.getString("resolvedQuestion")}\n\n${r.getString("answerMarkdown")}" })
        assertEquals(report.toString(2),cases.size,report.getInt("passed")); assertEquals(0,encoderCalls)
    }
}
