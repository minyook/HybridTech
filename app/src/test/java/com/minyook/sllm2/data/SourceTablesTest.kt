/* 소스 안내
 * 검수 표/도식의 해시·셀·범위 복원을 검증하는 JVM 회귀 검사다.
 * 검사 예: page, tables, selected
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.data

import com.minyook.sllm2.ui.MarkdownBlock
import com.minyook.sllm2.ui.markdownBlocks
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Independent row associations, real source boundaries and production answer composition. */
class SourceTablesTest {
    private val app=if(File("src/main/assets").exists())File(".") else File("app")
    private val seed=JSONObject(File(app,"src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
    private fun page(number:Int):KnowledgeChunk {
        val c=(0 until seed.length()).map{seed.getJSONObject(it)}.first{it.getString("documentId")=="confined_space_safety_guide_2022"&&it.getInt("pageNumber")==number}
        return KnowledgeChunk(id=number.toLong(),documentId=c.getString("documentId"),documentTitle=c.getString("documentTitle"),pageNumber=number,chunkIndex=c.getInt("chunkIndex"),body=c.getString("body"))
    }
    private fun tables(text:String)=markdownBlocks(text).filterIsInstance<MarkdownBlock.Table>()
    private fun selected(number:Int)=FocusedEvidence.selected(RetrievedChunk(page(number),1.0))

    @Test fun gasNamesHazardsAndOdorsStayInTheirOwnRows() {
        val body=FocusedEvidence.format(listOf(RetrievedChunk(page(13),1.0)))
        val gas=tables(body).single{it.rows.first().first()=="유해가스"}
        assertEquals(listOf("유해가스","주된 위험","외관 및 냄새"),gas.rows.first())
        assertEquals(9,gas.rows.size)
        assertEquals(listOf("메탄(CH4)","화재와 폭발\n상부에 축적 가능","무색, 무취(징후 없음)"),gas.rows.last())
        assertEquals(listOf("염소(Cl2)","유독성 - 폐와 눈 자극\n바닥에 축적 가능","녹황색, 톡 쏘는 냄새"),gas.rows[4])
        assertEquals(listOf("질소가스(N2)","산소 치환","무색, 무취(징후 없음)"),gas.rows[2])
        assertFalse(body.contains("### 유해가스 주된 위험 외관 및 냄새 아르곤"))
    }
    @Test fun carbonMonoxideRepeatedConcentrationsKeepTheirIndividualEffectsAndTimes() {
        val table=tables(FocusedEvidence.format(listOf(RetrievedChunk(page(13),1.0)))).first()
        assertEquals(8,table.rows.size)
        assertEquals(listOf("1000~2,000","정신혼란, 메스꺼움, 두통","2시간"),table.rows[4])
        assertEquals(listOf("1000~2,000","현기증","1.5시간"),table.rows[5])
        assertEquals(listOf("1000~2,000","심계항진(두근거림)","30분"),table.rows[6])
        assertEquals(listOf("2,000~2,500","의식불명","30분"),table.rows[7])
    }
    @Test fun aPartialSemanticCellRestoresTheTableInBothDisplayAndModelContext() {
        val chunk=page(13)
        val partial=EvidencePassage("상부에 축적 가능 무색, 무취(징후 없음)","유해가스 주된 위험 외관 및 냄새 아르곤(Ar)",10022,10022)
        val plan=DocumentAnswerPlan.create("메탄의 위험과 냄새",listOf(RetrievedChunk(chunk,1.0,listOf(partial))))
        assertEquals(1,tables(plan.body).size)
        assertEquals(9,tables(plan.body).single().rows.size)
        assertEquals(tables(plan.body),tables(plan.context()))
        assertFalse(plan.body.contains("일산화탄소 농도별"))
    }
    @Test fun repeatedRestorationAndSafetyMergesDoNotDuplicateTablesOrDropProse() {
        val chunk=page(13);val first=selected(13)
        assertEquals(first,SourceTables.restore(chunk,first))
        val raw=FocusedEvidence.units(chunk)
        val merged=SourceTables.restore(chunk,first+raw.filter{it.text.contains("산소 치환")})
        assertEquals(2,merged.count{it.text.startsWith("| ")})
        assertTrue(first.any{it.text.contains("산업현장에서는 다양한 가스")})
        assertTrue(first.any{it.text.contains("산소부족상황")})
        assertEquals(first.size,first.map{it.order}.distinct().size)
        // Table extraction must never remove warnings on the same page.
        val h2s=selected(12).joinToString(" "){it.text}
        assertTrue(h2s.contains("후각"))
        assertTrue(tables(h2s).isNotEmpty() || selected(12).any{it.text.startsWith("| ")})
    }
    @Test fun sourceMismatchFailsClosedAndOriginalDataRemainsUntouched() {
        for(table in SourceTableData.tables.distinctBy{it.documentId}) {
            val pdf=SourcePdfCatalog.find(table.documentId)!!
            val bytes=File(app,"src/main/assets/source-pdfs/${pdf.fileName}").readBytes()
            val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
            assertEquals("Bundled PDF must match both the table metadata and source catalog",table.pdfSha256,hash)
            assertEquals(pdf.sha256,hash)
        }
        val chunk=page(13);val original=chunk.body
        val changed=chunk.copy(body=chunk.body.replace("톡 쏘는 냄새","변경된 셀"))
        val input=FocusedEvidence.units(changed)
        assertEquals(input,SourceTables.restore(changed,input))
        FocusedEvidence.format(listOf(RetrievedChunk(chunk,1.0)))
        assertEquals(original,chunk.body)
        assertEquals(emptyList<EvidencePassage>(),SourceTables.restore(chunk,emptyList()))
    }
    @Test fun wideStatisticsAndAmbiguousSourceNotationArePreservedVerbatim() {
        val table=tables(FocusedEvidence.format(listOf(RetrievedChunk(page(14),1.0)))).single()
        assertEquals(12,table.rows.first().size)
        assertEquals("2021",table.rows.first().last())
        assertEquals(listOf("사망자수","165","20","31","16","14","14","18","14","13","14","11"),table.rows[3])
        val h2s=tables(FocusedEvidence.format(listOf(RetrievedChunk(page(12),1.0)))).single()
        assertEquals(listOf("500~700","의식불명, 사망","30~1시간"),h2s.rows[4])
    }
}
