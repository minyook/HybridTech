/* 소스 안내
 * 단말 검색 결과와 검수 표의 연결을 검증하는 Android 계측 검사다.
 * 검사 예: realQuestionsReturnStructuredSourceTables
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
package com.minyook.sllm2

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.data.*
import com.minyook.sllm2.ui.MarkdownBlock
import com.minyook.sllm2.ui.markdownBlocks
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual Android ONNX queries + current production coordinator; no Gemma/history writes. */
class SourceTableSearchTest {
    @Test fun realQuestionsReturnStructuredSourceTables() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        KnowledgeSeeder(context).seedIfNeeded()
        val repository=KnowledgeRepository()
        val json=context.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use{it.readText()}
        val encoder=SemanticEncoder(context)
        val cases=listOf(
            Triple("그 밖의 유해가스의 주된 위험과 외관 및 냄새 알려줘",13,"유해가스"),
            Triple("메탄은 어디에 쌓이고 냄새가 나?",13,"유해가스"),
            Triple("일산화탄소 농도별 인체 영향과 노출시간 알려줘",13,"농도(ppm)"),
            Triple("황화수소 농도별 인체 영향과 노출시간 알려줘",12,"농도(ppm)"),
            Triple("최근 10년간 질식재해 발생추이",14,"구 분"),
        )
        val rows=JSONArray();var failed=0
        try { for((question,page,header) in cases) {
            val start=SystemClock.elapsedRealtimeNanos()
            val result=repository.searchQuestion(question,json){encoder.encode(it)}
            val plan=DocumentAnswerPlan.create(question,result)
            val tables=markdownBlocks(plan.body).filterIsInstance<MarkdownBlock.Table>()
            val passed=result.status=="answered"&&result.sources.any{it.chunk.pageNumber==page}&&tables.any{it.rows.first().first()==header}
            if(!passed)failed++
            rows.put(JSONObject().put("question",question).put("expectedPage",page).put("expectedHeader",header).put("status",result.status).put("pages",JSONArray(result.sources.map{it.chunk.pageNumber})).put("tableCount",tables.size).put("pass",passed).put("millis",(SystemClock.elapsedRealtimeNanos()-start)/1e6).put("answerMarkdown",plan.body))
        } }finally{encoder.release()}
        val report=JSONObject().put("device",android.os.Build.MODEL).put("scope","Real installed Android ObjectBox repository + production coordinator + ONNX query encoder + answer assembly; no Gemma, UI frame or audio timing; no chat/history writes").put("passed",cases.size-failed).put("total",cases.size).put("results",rows)
        File(context.getExternalFilesDir(null),"table-checks").apply{mkdirs()}.resolve("source-table-search.json").writeText(report.toString(2))
        assertEquals(report.toString(2),0,failed)
    }
}
