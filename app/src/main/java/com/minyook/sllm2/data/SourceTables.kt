/* 소스 안내
 * 판본/청크 해시가 맞는 검수 표를 선택 문단에 복원한다. 표시 본문과 모델 문맥이 같은 구조를 사용한다.
 */
package com.minyook.sllm2.data

import java.security.MessageDigest
import java.text.Normalizer

/** Cell values are generated from verified PDF coordinates, not inferred from prose. */
internal data class SourceTable(
    val id:String,val documentId:String,val page:Int,val chunkIndex:Int,
    val sourceBodySha256:String,val pdfSha256:String,val title:String,val rawText:String,
    val headers:List<String>,val rows:List<List<String>>,
) {
    val key by lazy { TableText.of(rawText).value }
    fun markdown():String {
        fun cell(value:String)=value.split('•').map(String::trim).filter(String::isNotEmpty).joinToString("<br>").replace("|","\\|")
        return (listOf(headers,headers.map { "---" })+rows).joinToString("\n") { row -> "| "+row.joinToString(" | ",transform=::cell)+" |" }
    }
}

private data class TableText(val value:String,val positions:List<Int>) {
    companion object {
        fun of(text:String):TableText {
            val value=StringBuilder();val positions=mutableListOf<Int>()
            text.forEachIndexed { i,c -> Normalizer.normalize(c.toString(),Normalizer.Form.NFKC).forEach { normalized ->
                if(normalized.isLetterOrDigit()) { value.append(normalized.lowercaseChar());positions+=i }
            } }
            return TableText(value.toString(),positions)
        }
    }
}

/**
 * 선택 문단과 PC 검수 표 데이터를 연결하는 복원기. FocusedEvidence.selected에서 호출된다.
 * 문서 ID·쪽·원문 본문 해시·PDF 판본 해시가 일치한 자료에만 적용해 다른 판본의 셀이 섞이지 않게 한다.
 * 결과는 Markdown 표/선택 행으로 표시와 모델 문맥에 함께 사용한다. 임의 PDF 표 추출 엔진은 아니다.
 */
internal object SourceTables {
    private val tables by lazy { SourceTableData.tables.withIndex().groupBy { Triple(it.value.documentId,it.value.page,it.value.chunkIndex) } }
    private fun matching(chunk:KnowledgeChunk):List<IndexedValue<SourceTable>> {
        val candidates=tables[Triple(chunk.documentId,chunk.pageNumber,chunk.chunkIndex)].orEmpty()
        if(candidates.isEmpty())return emptyList()
        val hash=MessageDigest.getInstance("SHA-256").digest(chunk.body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return candidates.filter { it.value.sourceBodySha256==hash && SourcePdfCatalog.find(chunk.documentId)?.sha256==it.value.pdfSha256 }
    }
    fun verified(chunk:KnowledgeChunk):List<SourceTable> = matching(chunk).map{it.value}

    /** Restore a whole table when retrieval selects any of its cells. Preserve
     * surrounding paragraphs and warnings, including text sharing a legacy unit. */
    fun restore(chunk:KnowledgeChunk,evidence:List<EvidencePassage>):List<EvidencePassage> {
        var result=evidence
        for((index,table) in matching(chunk)) {
            val tableOrder=2_000_000+index
            var added=result.any { it.order==tableOrder && it.text==table.markdown() }
            val next=mutableListOf<EvidencePassage>()
            result.forEachIndexed { position,unit ->
                if(unit.order==tableOrder) { next+=unit;return@forEachIndexed }
                val parts=parts(unit.text,table)
                if(parts==null) { next+=unit;return@forEachIndexed }
                val originalSection=TableText.of(unit.section).value
                val section=if(originalSection.length>=12 && originalSection in table.key) "" else unit.section
                val base=10_000_000+index*100_000+position*2
                if(parts.first.isNotBlank())next+=unit.copy(text=parts.first.trim(),section=section,order=base)
                if(!added) { next+=EvidencePassage(table.markdown(),table.title,tableOrder,tableOrder);added=true }
                if(parts.second.isNotBlank())next+=unit.copy(text=parts.second.trim(),section=section,order=base+1)
            }
            result=next
        }
        return result
    }

    private fun parts(text:String,table:SourceTable):Pair<String,String>? {
        if(text.startsWith("| ") && text.contains("| ---"))return null
        val exact=text.indexOf(table.rawText)
        if(exact>=0)return text.substring(0,exact) to text.substring(exact+table.rawText.length)
        val normalized=TableText.of(text);val key=normalized.value;val tableKey=table.key
        if(key.length<3)return null
        if(key in tableKey)return "" to ""
        val inside=key.indexOf(tableKey)
        if(inside>=0) {
            var from=normalized.positions[inside];var end=normalized.positions[inside+tableKey.length-1]+1
            val leading=table.rawText.takeWhile { !it.isLetterOrDigit() };val trailing=table.rawText.takeLastWhile { !it.isLetterOrDigit() }
            if(leading.isNotEmpty() && text.take(from).endsWith(leading))from-=leading.length
            if(trailing.isNotEmpty() && text.drop(end).startsWith(trailing))end+=trailing.length
            return text.take(from) to text.drop(end)
        }
        // Boundary fragments can contain the preceding/following prose. Only
        // an ordered overlap with the verified source span is consumed.
        for(size in minOf(key.length,tableKey.length) downTo 12) {
            if(key.endsWith(tableKey.take(size)))return text.take(normalized.positions[key.length-size]) to ""
            if(key.startsWith(tableKey.takeLast(size))) {
                var end=normalized.positions[size-1]+1
                while(end<text.length && text[end] in ")]）】│")end++
                return "" to text.drop(end)
            }
        }
        return null
    }
}
