/* 소스 안내
 * 단일 가스의 대상과 요청 속성을 구분해 원문 절/검수 표 행으로 직접 답한다. 이전 전체 가스 주제를 임의로 상속하지 않는다.
 */
package com.minyook.sllm2.data

import java.text.Normalizer

/** Named subjects and attributes only. No stored answers, concentrations or page numbers. */
internal object GasQuestionFocus {
    data class Subject(val name:String,val forms:Regex) {
        fun matches(text:String)=forms.containsMatchIn(Normalizer.normalize(text,Normalizer.Form.NFKC))
    }
    enum class Attribute { OVERVIEW, EFFECTS, CRITERIA, APPEARANCE }
    data class Request(val subject:Subject,val attribute:Attribute) {
        val query get()=subject.name+when(attribute) {
            Attribute.OVERVIEW->" 알려줘"
            Attribute.EFFECTS->" 농도별 인체 영향"
            Attribute.CRITERIA->" 농도 기준"
            Attribute.APPEARANCE->" 외관과 냄새"
        }
    }
    private fun gas(name:String,forms:String)=Subject(name,Regex(forms,RegexOption.IGNORE_CASE))
    val subjects=listOf(
        gas("산소","산소|(?<![a-z0-9])o2(?![a-z0-9])"),
        gas("일산화탄소","일산화\\s*탄소|(?<![a-z0-9])co(?![a-z0-9])"),
        gas("이산화탄소","이산화\\s*탄소|탄산\\s*가스|(?<![a-z0-9])co2(?![a-z0-9])"),
        gas("황화수소","황화\\s*수소|(?<![a-z0-9])h2s(?![a-z0-9])"),
        gas("아르곤","아르곤|(?<![a-z0-9])ar(?![a-z0-9])"),
        gas("질소","질소(?:가스)?|(?<![a-z0-9])n2(?![a-z0-9])"),
        gas("염소","염소|(?<![a-z0-9])cl2(?![a-z0-9])"),
        gas("이산화질소","이산화\\s*질소|(?<![a-z0-9])no2(?![a-z0-9])"),
        gas("이산화황","이산화\\s*황|(?<![a-z0-9])so2(?![a-z0-9])"),
        gas("메탄","메탄|(?<![a-z0-9])ch4(?![a-z0-9])"),
        gas("휘발유증기","휘발유\\s*증기"),
    )
    fun named(text:String):List<Subject> {
        val normalized=Normalizer.normalize(text,Normalizer.Form.NFKC)
        // Long chemical names own their span: 질소 inside 이산화질소 is not a second subject.
        val spans=subjects.flatMap{s->s.forms.findAll(normalized).map{it.range to s}.toList()}.sortedByDescending{it.first.last-it.first.first}
        val selected=mutableListOf<Pair<IntRange,Subject>>()
        for(span in spans)if(selected.none{other->span.first.first<=other.first.last&&other.first.first<=span.first.last})selected+=span
        return selected.sortedBy{it.first.first}.map{it.second}.distinct()
    }
    fun parse(question:String):Request? {
        var rest=Normalizer.normalize(question,Normalizer.Form.NFKC).trim()
        val named=named(rest).singleOrNull()?:return null
        val attribute=when {
            Regex("영향|증상|중독|결핍|위험|농도별").containsMatchIn(rest)->Attribute.EFFECTS
            Regex("기준|정상|적정|범위|수치|농도").containsMatchIn(rest)->Attribute.CRITERIA
            Regex("외관|냄새|색깔|색상|축적|쌓").containsMatchIn(rest)->Attribute.APPEARANCE
            else->Attribute.OVERVIEW
        }
        rest=rest.replace(named.forms," ")
            .replace(Regex("^(?:(?:그럼|그러면|추가로|다음으로|또|이제)[,，\\s]*)+"),"")
            .replace(Regex("에\\s*대해서|에\\s*대해|대해서|대해|인체에\\s*미치는|농도에\\s*따른|농도별|인체|건강|영향|증상|중독|결핍증|결핍|위험성|위험|농도|기준|정상|적정|범위|수치|외관|냄새|색깔|색상|축적\\s*위치|축적|어디에?\\s*쌓이(?:는지|나|는|고)?|알려주세요|알려줘|알려줄래|설명해주세요|설명해줘|설명|정리해줘|정리|어떤|무엇|뭐야|뭔지|이란|란|어때|어떻게|좀|도|만|은|는|을|를|의|이|가|에|과|와|요|[\\s?？,.·]"),"")
        // Meaningful unknown modifiers (측정방법, 장비, 원인, 통...) stay in the normal search path.
        return if(rest.isBlank())Request(named,attribute) else null
    }
}

/** Assemble only the requested substance's real source section/row and attribute.
 * A mention of oxygen in another gas's mechanism is not an oxygen description. */
/**
 * HybridRetrieval의 단일 가스 직접 응답 경로. GasQuestionFocus의 대상/속성으로 원문 절과 검수 표의 행을 선택한다.
 * 앞선 유해가스 전체 목록을 단일 산소 질문에 붙이지 않고 실제 근거/출처를 SearchResolution으로 반환한다.
 * 생성 모델로 농도·조건을 새로 만들지 않는다. 복합/범위 밖 질문은 일반 검색 경로가 판단하도록 넘긴다.
 */
internal class GasDescriptionAnswer(private val chunks:List<KnowledgeChunk>) {
    private data class Block(val chunk:KnowledgeChunk,val subject:GasQuestionFocus.Subject,val units:List<EvidencePassage>)
    private val blocks by lazy {
        val names=GasQuestionFocus.subjects.joinToString("|"){Regex.escape(it.name)}
        val heading=Regex("(?:(?:$names)\\s*(?:\\([^)]*\\))?\\s*(?:중독|결핍증)|그\\s*밖의\\s*유해가스에\\s*의한\\s*건강장해)\\s*\\d+")
        chunks.flatMap{chunk->
            val body=FocusedEvidence.pageBody(chunk)
            val headings=heading.findAll(body).toList()
            headings.mapIndexedNotNull{index,h->
                val subject=GasQuestionFocus.named(h.value).singleOrNull()?:return@mapIndexedNotNull null
                val end=headings.getOrNull(index+1)?.range?.first?:body.length
                val text=body.substring(h.range.last+1,end).trim()
                    .replace(Regex("\\s+[^.!?\\n]*[?？]\\s+\\d+\\s*$"),"").trim()
                val units=FocusedEvidence.units(chunk.copy(body=text)).mapIndexed{i,u->u.copy(section=subject.name,order=3_000_000+h.range.first*100+i)}
                Block(chunk,subject,SourceTables.restore(chunk,units))
            }
        }
    }
    private val criteria by lazy {chunks.flatMap{chunk->FocusedEvidence.units(chunk).filter{u->
        "적정공기" in EvidenceLanguage.compact(u.section)&&Regex("%|ppm",RegexOption.IGNORE_CASE).containsMatchIn(u.text)
    }.map{chunk to it}}}

    fun resolve(question:String):SearchResolution? {
        val request=GasQuestionFocus.parse(question)?:return null
        val subject=request.subject
        val pieces=mutableListOf<RetrievedChunk>()
        fun add(chunk:KnowledgeChunk,units:List<EvidencePassage>) {if(units.isNotEmpty())pieces+=RetrievedChunk(chunk,1.0,units)}
        val blocks=blocks.filter{it.subject==subject}
        val tableUnits=blocks.flatMap{block->block.units.filter{it.text.startsWith("| ")}.map{block.chunk to it}}
        val intros=blocks.map{block->block to block.units.takeWhile{!it.text.startsWith("| ")}}
        val warnings=blocks.map{block->block to block.units.dropWhile{!it.text.startsWith("| ")}.drop(1).filter{u->
            !u.text.startsWith("| ")&&subject.matches(u.text)&&Regex("경우|위험|않|마비|고농도|한 번|없").containsMatchIn(u.text)
        }}
        val limits=criteria.filter{subject.matches(it.second.text)&&GasQuestionFocus.named(it.second.text).all{it==subject}}
        if(request.attribute==GasQuestionFocus.Attribute.OVERVIEW)intros.forEach{(block,units)->add(block.chunk,units)}
        if(request.attribute in setOf(GasQuestionFocus.Attribute.OVERVIEW,GasQuestionFocus.Attribute.CRITERIA))limits.forEach{(chunk,unit)->add(chunk,listOf(unit.copy(section="${subject.name} 농도 기준")))}
        if(request.attribute in setOf(GasQuestionFocus.Attribute.OVERVIEW,GasQuestionFocus.Attribute.EFFECTS)) {
            tableUnits.forEach{(chunk,unit)->add(chunk,listOf(unit))}
            warnings.forEach{(block,units)->add(block.chunk,units.map{it.copy(section="주의사항")})}
        }
        if(request.attribute==GasQuestionFocus.Attribute.APPEARANCE) {
            intros.forEach{(block,units)->add(block.chunk,units.filter{Regex("외관|냄새|무색|무취|색").containsMatchIn(it.text)})}
            warnings.forEach{(block,units)->add(block.chunk,units.filter{Regex("냄새|후각|판단").containsMatchIn(it.text)}.map{it.copy(section="주의사항")})}
        }
        if(tableUnits.isEmpty()&&request.attribute!=GasQuestionFocus.Attribute.CRITERIA) {
            chunks.forEach{chunk->SourceTables.verified(chunk).filter{it.headers.firstOrNull()=="유해가스"}.forEach{table->
                val rows=table.rows.filter{GasQuestionFocus.named(it.first()).singleOrNull()==subject}
                val order=4_000_000+GasQuestionFocus.subjects.indexOf(subject)
                if(rows.isNotEmpty())add(chunk,listOf(EvidencePassage(table.copy(rows=rows).markdown(),subject.name,order,order)))
            }}
        }
        if(pieces.isEmpty())return null
        val sources=pieces.groupBy{it.chunk.id}.values.map{group->group.first().copy(evidence=group.flatMap{it.evidence.orEmpty()}.distinctBy{it.order})}
        return SearchResolution(question,sources,status="answered",message="",method="gas-description",searchComplete=true,answerBody=FocusedEvidence.format(pieces))
    }
}
