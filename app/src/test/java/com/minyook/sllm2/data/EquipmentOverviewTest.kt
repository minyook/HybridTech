package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class EquipmentOverviewTest {
    private val root = if (File("src/main/assets").exists()) File(".") else File("app")
    private fun chunks(): List<KnowledgeChunk> {
        val rows = JSONObject(File(root, "src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            KnowledgeChunk(id = index + 1L, documentId = row.getString("documentId"), documentTitle = row.getString("documentTitle"), sourceKind = row.getString("sourceKind"), pageNumber = row.getInt("pageNumber"), chunkIndex = row.getInt("chunkIndex"), body = row.getString("body"))
        }
    }
    @Test fun recordEquipmentQuestions() {
        val search = HybridRetrieval(chunks(), File(root, "src/main/assets/knowledge/semantic-index-v1.json").readText())
        val questions = listOf("작업장비", "장비", "작업 장비 알려줘", "밀폐공간 작업에 필요한 장비가 뭐야?", "현장에 챙겨야 할 준비물 알려줘", "안전장비 종류 알려줘", "보호구 종류 알려줘", "구조 장비는 뭐가 필요해?", "장비의 전압과 가격은?", "공기호흡기와 송기마스크 차이가 뭐야?", "작업장비는 언제 점검해야 해?", "구조 장비 없이 들어가도 돼?")
        val rows = JSONArray()
        for (question in questions) {
            val result = search.resolve(question, null)
            rows.put(JSONObject().put("question", question).put("status", result.status).put("pages", JSONArray(result.sources.map { it.chunk.pageNumber })).put("body", if (result.sources.isEmpty()) result.message else FocusedEvidence.format(result.sources)))
        }
        File(root, "build/reports/equipment-rag").apply { mkdirs() }.resolve("current.json").writeText(rows.toString(2))
        for (index in 0..7) assertEquals(questions[index], "answered", rows.getJSONObject(index).getString("status"))
        assertEquals("unconfirmed", rows.getJSONObject(8).getString("status"))
    }

    @Test fun generalInventoriesCollectComplementarySourcesWithoutAQueryEncoder() {
        val search = HybridRetrieval(chunks(), File(root, "src/main/assets/knowledge/semantic-index-v1.json").readText())
        val questions = listOf(
            "작업장비", "장비", "작업 장비 알려줘", "밀폐공간 작업에 필요한 장비가 뭐야?",
            "현장에 챙겨야 할 준비물 알려줘", "안전장비 종류 알려줘",
            "밀폐 공간에서 사용하는 장비는 무엇인가요?", "작업할 때 챙길 장비들 좀 알려주세요",
            "작업에 필요한 도구 목록", "현장에 가져갈 준비물", "필수 작업장비 정리해줘", "밀폐공간 장비 뭐 있어요?",
            "밀폐공간에 들어가기 전에 어떤 장비를 챙겨야 돼?", "작업장비가 뭔지 알려줘", "작업 전에 준비해야 하는 장비는?",
            "안전 장비에는 어떤 종류가 있나요?", "작업시 필요한 장구 알려줘", "밀폐공간 준비물 목록", "작업장비에 대해 설명해주세요"
        )
        for (question in questions) {
            val result = search.resolve(question, null)
            assertEquals(question, "answered", result.status)
            assertFalse(question, result.semantic)
            assertTrue(question, result.suggestions.isEmpty())
            assertEquals(question, setOf(27, 29, 35, 36, 37), result.sources.map { it.chunk.pageNumber }.toSet())
            val body = FocusedEvidence.format(result.sources)
            for (equipment in listOf("측정기", "채기관", "방폭구조의 전등", "환기팬", "덕트", "공기호흡기", "송기마스크", "안전대", "구명 밧줄", "삼각대", "연락")) {
                assertTrue("$question : $equipment", equipment in body)
            }
            assertFalse(question, "관리감독자의 직무" in body)
            assertFalse(question, "교육과정" in body)
            assertFalse(question, "작업자 정보 직책" in body)
        }
    }

    @Test fun equipmentConditionsTravelWithTheItemsAndAnswerPlan() {
        val result = EquipmentOverview(chunks()).resolve("작업장비")!!
        val plan = DocumentAnswerPlan.create(result.question, result.sources)
        val body = plan.body
        for (condition in listOf("20.9%", "교정 또는 센서교체", "제조사", "별도", "15미터", "폭발위험지역", "방폭형", "환기만으로 불충분", "송기라인", "정전", "추락위험", "즉시 사용 가능")) {
            assertTrue(condition, condition in body)
            assertTrue(condition, condition in plan.context())
        }
        assertFalse(plan.supplementRequested)
        assertEquals(plan.evidence.size, plan.evidence.map { it.id }.distinct().size)
        for (source in result.sources) {
            assertEquals(chunks().first { it.id == source.chunk.id }.body, source.chunk.body)
            for (unit in source.evidence.orEmpty()) {
                assertTrue(unit.text, unit.text in FocusedEvidence.pageBody(source.chunk))
            }
        }
    }

    @Test fun protectionAndRescueInventoriesHaveTheirOwnScopeAndRestrictions() {
        val search = EquipmentOverview(chunks())
        val protection = search.resolve("보호구 종류 알려줘")!!
        assertEquals(setOf(36, 37), protection.sources.map { it.chunk.pageNumber }.toSet())
        assertTrue("보호가드" in FocusedEvidence.format(protection.sources))
        assertFalse("환기팬은" in FocusedEvidence.format(protection.sources))
        val rescue = search.resolve("구조 장비는 뭐가 필요해?")!!
        assertEquals(setOf(37, 44), rescue.sources.map { it.chunk.pageNumber }.toSet())
        val body = FocusedEvidence.format(rescue.sources)
        for (condition in listOf("삼각대", "공기호흡기", "119", "안전한지 확인할 수 없거나", "기다리십시오")) assertTrue(condition, condition in body)
        assertFalse("심폐소생술" in body)
    }

    @Test fun detailsExceptionsAndUnknownDomainsNeverBecomeGenericInventories() {
        val search = EquipmentOverview(chunks())
        for (question in listOf(
            "장비의 전압과 가격은?", "공기호흡기와 송기마스크 차이가 뭐야?", "작업장비는 언제 점검해야 해?",
            "구조 장비 없이 들어가도 돼?", "어두운 밀폐공간 측정 전등은 어떤 걸 써야해", "환기팬 용량 알려줘",
            "측정장비 사용 방법", "장비가 고장나면?", "장비가 없으면 작업 가능해?", "보호구 대신 마스크만 해도 돼?",
            "잠수 장비 종류 알려줘", "방사선 작업에 필요한 장비", "용접 장비 종류 알려줘", "작업장비 중 전압이 220V인 것은?"
        )) assertNull(question, search.resolve(question))
    }

    @Test fun itemsComeFromAvailableTextRatherThanPageNumbersOrCannedAnswers() {
        val original = chunks()
        val renamed = original.map { it.copy(id = it.id + 1000, documentId = "repacked-guide", pageNumber = it.pageNumber + 100) }
        val moved = EquipmentOverview(renamed).resolve("장비")!!
        assertEquals(setOf(127, 129, 135, 136, 137), moved.sources.map { it.chunk.pageNumber }.toSet())
        val withoutMeasurement = EquipmentOverview(original.filterNot { it.pageNumber == 27 && it.documentId == "confined_space_safety_guide_2022" }).resolve("장비")!!
        assertFalse(withoutMeasurement.sources.any { "방폭구조의 전등" in FocusedEvidence.format(listOf(it)) })
        assertFalse("측정 장비" in FocusedEvidence.format(withoutMeasurement.sources))
        assertTrue(withoutMeasurement.sources.all { source -> source.evidence.orEmpty().all { it.text in FocusedEvidence.pageBody(source.chunk) } })
        val office = KnowledgeChunk(id = 1, documentId = "office", body = "사무실 준비물은 프린터와 노트북입니다.")
        assertNull(EquipmentOverview(listOf(office)).resolve("장비"))
    }
}
