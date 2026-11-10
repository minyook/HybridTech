/* 소스 안내
 * 단일 가스/속성의 직접 응답 범위을 검증하는 JVM 회귀 검사다.
 * 검사 예: subjectAndAttributeRecognitionDoesNotTurnProceduresIntoOverviews, explicitGasSubsetReplacesAnEarlierBroadTopicEvenWithAdditionalWording, oxygenOverviewHasOnlyItsOwnIntroLimitEffectRowsAndWarning
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import com.minyook.sllm2.ui.MarkdownBlock
import com.minyook.sllm2.ui.markdownBlocks
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GasDescriptionAnswerTest {
    private val app=if(File("src/main/assets").exists())File(".") else File("app")
    private val chunks by lazy {
        val seed=JSONObject(File(app,"src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
        (0 until seed.length()).map{i->val c=seed.getJSONObject(i);KnowledgeChunk(id=i+1L,documentId=c.getString("documentId"),documentTitle=c.getString("documentTitle"),sourceKind=c.getString("sourceKind"),pageNumber=c.getInt("pageNumber"),chunkIndex=c.getInt("chunkIndex"),body=c.getString("body"))}
    }
    @Test fun subjectAndAttributeRecognitionDoesNotTurnProceduresIntoOverviews() {
        for(q in listOf("산소도 알려줘","그럼 산소는?","산소만 설명해줘","O₂도 알려줘"))assertEquals(q,"산소",GasQuestionFocus.parse(q)?.subject?.name)
        assertEquals(listOf("이산화질소"),GasQuestionFocus.named("이산화질소(NO2)").map{it.name})
        assertEquals(listOf("이산화탄소"),GasQuestionFocus.named("CO2").map{it.name})
        for(q in listOf("산소 유해가스 측정방법","산소 측정 시기","산소통 관리방법","산소 발생 원인","산소와 일산화탄소 비교"))assertNull(q,GasQuestionFocus.parse(q))
    }
    @Test fun explicitGasSubsetReplacesAnEarlierBroadTopicEvenWithAdditionalWording() {
        val topic="산소와 유해가스의 종류 알려줘"
        val history=listOf(ConversationTurn(true,topic),ConversationTurn(false,"앞선 답변",topicQuestion=topic))
        for(q in listOf("추가로 산소도 알려줘","그럼 산소는?","O2도 알려줘")) {
            val request=ConversationContext.resolve(q,history)
            assertEquals(FollowUpKind.NEW,request.kind);assertEquals("산소 알려줘",request.query)
        }
    }
    @Test fun oxygenOverviewHasOnlyItsOwnIntroLimitEffectRowsAndWarning() {
        val result=GasDescriptionAnswer(chunks).resolve("산소도 알려줘")!!
        val plan=DocumentAnswerPlan.create("산소도 알려줘",result)
        for(text in listOf("21%","18%","23.5%","한 번의 호흡"))assertTrue(text,plan.body.contains(text))
        for(text in listOf("아르곤","휘발유","일산화탄소","황화수소","측정 시기","호랑이굴","위험한 공기는","│"))assertFalse(text,plan.body.contains(text))
        assertEquals(setOf(11,24),result.sources.map{it.chunk.pageNumber}.toSet())
        assertFalse(plan.context().contains("일산화탄소"))
        assertEquals(plan.evidence.size,plan.evidence.map{it.id}.distinct().size)
        val table=markdownBlocks(plan.body).filterIsInstance<MarkdownBlock.Table>().single()
        assertEquals(listOf("16%","호흡, 맥박의 증가, 두통, 메스꺼움, 토할 것 같음"),table.rows[2])
        assertEquals(listOf("18%","안전한계이나 연속환기 필요"),table.rows[1])
        assertEquals(listOf("8%","실신혼절, 7~8분 이내에 사망"),table.rows[5])
        assertEquals(listOf("6%","순간에 혼절, 호흡정지, 경련, 6분 이상이면 사망"),table.rows[6])
        assertEquals(7,table.rows.size)
    }
    @Test fun criteriaAndEffectsAreDifferentRequestsWithoutUnwantedTopics() {
        val answers=GasDescriptionAnswer(chunks)
        val criteria=answers.resolve("산소 농도 기준만 알려줘")!!
        assertEquals(listOf(24),criteria.sources.map{it.chunk.pageNumber})
        assertTrue(criteria.answerBody!!.contains("23.5%"));assertFalse(criteria.answerBody.contains("16%"))
        val effects=answers.resolve("산소농도별 인체 영향 알려줘")!!
        assertEquals(listOf(11),effects.sources.map{it.chunk.pageNumber})
        assertFalse(effects.answerBody!!.contains("21%"));assertTrue(effects.answerBody.contains("16%"))
    }
    @Test fun gasRowsAreFilteredByNameAndHydrogenSulfideSmellWarningSurvives() {
        val answers=GasDescriptionAnswer(chunks)
        val co2=answers.resolve("이산화탄소 냄새 알려줘")!!
        val table=markdownBlocks(co2.answerBody!!).filterIsInstance<MarkdownBlock.Table>().single()
        assertEquals(2,table.rows.size);assertEquals("이산화탄소(CO2)",table.rows[1][0]);assertFalse(co2.answerBody.contains("아르곤"))
        val sulfide=answers.resolve("황화수소 냄새 알려줘")!!.answerBody!!
        assertTrue(sulfide.contains("후각"));assertTrue(sulfide.contains("판단해서는 안 됩니다"))
        val co=answers.resolve("일산화탄소도 알려줘")!!.answerBody!!
        assertTrue(co.contains("불완전"));assertFalse(co.contains("건강장해4"));assertFalse(co.contains("아르곤"))
        val index=HybridRetrieval(chunks,File(app,"src/main/assets/knowledge/semantic-index-v1.json").readText())
        val coordinator=QuestionSearchCoordinator(index){error("Explicit descriptions need no encoder")}
        val compound=coordinator.search("이산화탄소 냄새 알려줘 그리고 메탄 냄새 알려줘")
        val body=DocumentAnswerPlan.create(compound.question,compound).body
        assertEquals("answered",compound.status)
        assertTrue(body.contains("이산화탄소(CO2)"));assertTrue(body.contains("메탄(CH4)"));assertFalse(body.contains("아르곤"))
    }
}
