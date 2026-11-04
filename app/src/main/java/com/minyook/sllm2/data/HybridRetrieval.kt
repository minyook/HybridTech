package com.minyook.sllm2.data

import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.sqrt

data class SearchSuggestion(val label: String, val question: String)
data class SearchResolution(val question: String, val sources: List<RetrievedChunk> = emptyList(), val status: String = "unconfirmed", val message: String = "질문에 직접 답할 근거를 아직 확인하지 못했어요. 궁금한 대상과 알고 싶은 내용을 함께 알려 주세요.", val suggestions: List<SearchSuggestion> = emptyList(), val semantic: Boolean = false, val method: String = "", val searchComplete: Boolean = false, val generalQuestion: String? = null)
internal data class SemanticPassage(val id:String,val documentId:String,val page:Int,val chunkIndex:Int,val section:String,val text:String,val orders:List<Int>,val units:List<EvidencePassage>,val searchText:String,val sourceBody:String,val vector:FloatArray)
internal class HybridRetrieval(private val chunks:List<KnowledgeChunk>, json:String) {
    private val root = JSONObject(json)
    init { require(root.getInt("dimensions")==768 && root.getString("modelRevision")=="5090578d9565bb06545b4552f76e6bc2c93e4a66") }
    private val passages = root.getJSONArray("passages").let { rows -> (0 until rows.length()).map { i ->
        val p=rows.getJSONObject(i);val orders=p.getJSONArray("orders");val v=p.getJSONArray("vector");val units=p.getJSONArray("units")
        SemanticPassage(p.getString("id"),p.getString("documentId"),p.getInt("page"),p.getInt("chunkIndex"),p.getString("section"),p.getString("text"),(0 until orders.length()).map{orders.getInt(it)},(0 until units.length()).map{j->val u=units.getJSONObject(j);EvidencePassage(u.getString("text"),u.getString("section"),10000+u.getInt("order"),u.optInt("group",j))},p.getString("searchText"),p.getString("sourceBody"),FloatArray(v.length()){v.getDouble(it).toFloat()})
    }.filter { p -> chunks.any { it.documentId==p.documentId && it.pageNumber==p.page && it.chunkIndex==p.chunkIndex && it.body==p.sourceBody } } }
    private val lexical = FocusedRetriever(chunks)
    private val equipment = EquipmentOverview(chunks)
    private val passageTexts = passages.map{EvidenceLanguage.aliases(it.searchText)}
    private val pageUnits = chunks.associate{it.id to FocusedEvidence.units(it)}
    private val pageTexts = chunks.associate{it.id to EvidenceLanguage.aliases(it.body)}
    private val pageTitles = chunks.associate{it.id to EvidenceLanguage.aliases(FocusedEvidence.pageBody(it).substringBefore('▶').substringBefore('•').substringBefore("산업안전보건기준").take(160))}
    private val sectionKeys=passages.map{it.section}.plus(pageUnits.values.flatten().map{it.section}).distinct().associateWith{EvidenceLanguage.compact(it)}
    private val caseText=passages.map{Regex("사망\\s*\\d+명|\\[답변|┃사례").containsMatchIn(it.text)}
    private val caseBody=passages.map{Regex("┃사례|확인\\s*부탁|문의드립니다|\\[답변").containsMatchIn(it.sourceBody)}
    private val prefaces=passages.map{it.units.size<=2&&Regex("다음.*조치.*하여야").containsMatchIn(it.text)&&!Regex("유지할|착용|지급|설치할|측정하고").containsMatchIn(it.text)}
    private val tokenFrequency = java.util.concurrent.ConcurrentHashMap<String,Int>()
    private val specification = Regex("방수|IP\\s*등급|배터리|전압|볼트|가격|구매|무게|제조사|모델명|충전|유효기간|유효\\s*기간",RegexOption.IGNORE_CASE)
    private data class Candidate(val p:SemanticPassage,val dense:Double,val lex:Double,var fused:Double=0.0)
    private fun completeScope(p:SemanticPassage,chunk:KnowledgeChunk):List<EvidencePassage>{
        val units=pageUnits.getValue(chunk.id);val first=units.indexOfFirst{it.section==p.section};val previous=units.getOrNull(first-1)
        val topic=previous?.text?.let{Regex("^\\(([^)]+)\\)").find(it)?.groupValues?.get(1)}
        return if(previous!=null&&topic!=null&&EvidenceLanguage.compact(topic) in EvidenceLanguage.compact(p.section))listOf(previous.copy(section=p.section))+p.units else p.units
    }
    fun resolve(question:String,vector:FloatArray?,review:Boolean=false,fastOnly:Boolean=false):SearchResolution {
        var result=SearchResolution(question,semantic=vector!=null)
        if(Regex("(?:우리|현재|지금).*(?:승인|허가).*(?:됐|되었|받았|완료|상태)").containsMatchIn(question))return result.copy(status="live-state",message="현재 현장의 승인 상태는 문서로 확인할 수 없어요. 작업 전 점검 화면에서 요청과 감독관 승인 상태를 확인해 주세요.")
        val parts=FocusedRetriever.aspects(question).take(8)
        val sharedGas=parts.size>1&&parts.dropLast(1).all{Regex("^(산소|유해가스|일산화탄소|이산화탄소|황화수소)$").matches(EvidenceLanguage.compact(it))}&&EvidenceLanguage.terms(parts.last()).any{it in setOf("측정","농도","기준")}
        if(!review&&!fastOnly&&parts.size>1&&!sharedGas){
            val each=parts.map{resolve(it,null)};val found=each.filter{it.status=="answered"};val missing=each.filter{it.status!="answered"}
            if(found.isNotEmpty()&&missing.isNotEmpty())return result.copy(status="partial",sources=found.flatMap{it.sources}.groupBy{it.chunk.id}.values.map{group->group.first().copy(evidence=group.flatMap{FocusedEvidence.selected(it)}.distinctBy{it.text})},message="질문 중 ‘${missing.joinToString(" / "){it.question}}’ 부분은 아직 직접 근거를 확인하지 못했어요. 이 부분에서 궁금한 대상이나 조건을 더 알려 주세요.",suggestions=missing.take(3).map{SearchSuggestion(it.question,it.question)})
        }
        val tokenQuestion=if(review)question.replace(Regex("(?<=[가-힣])(?:과|와)\\s+")," ") else question
        val tokens=EvidenceLanguage.terms(tokenQuestion).filterNot{Regex("^(그거|그건|그것|그러면|그럼|후속|질문)").containsMatchIn(it)}
        equipment.resolve(question)?.let { return it }
        val sources=if(fastOnly)emptyList() else lexical.retrieve(question,splitQuestion=!review)
        val unknown=specification.findAll(question).map{it.value}.distinct().filter{s->chunks.none{EvidenceLanguage.compact(s) in EvidenceLanguage.compact(it.body)}}.toList()
        val vague=tokens.size<=1&&tokens.none{it !in EvidenceLanguage.generic}
        val heading=EvidenceLanguage.compact(question.replace(Regex("(?:에\\s*대해|관련|내용|알려줘|알려주세요|설명해줘|설명해주세요|정리해줘|주세요|좀|\\?)"),""))
        val exact=passages.filter{heading.isNotBlank()&&sectionKeys.getValue(it.section)==heading}
        if(unknown.isEmpty()&&exact.isNotEmpty()&&exact.map{it.documentId to it.page}.distinct().size<=2){
            val selected=exact.groupBy{it.documentId to it.page}.values.map{group->group.maxBy{it.orders.size}}
            return result.copy(status="answered",message="",semantic=false,method="heading",sources=selected.map{p->val chunk=chunks.first{it.documentId==p.documentId&&it.pageNumber==p.page&&it.chunkIndex==p.chunkIndex};RetrievedChunk(chunk,1.0,completeScope(p,chunk))})
        }
        if(vector==null||vector.size!=768||vector.any{!it.isFinite()}){
            if(sources.isNotEmpty()&&unknown.isEmpty()&&!vague)return result.copy(sources=sources,status="answered",message="",semantic=false,method="lexical")
            val suggestions=if(vague&&tokens.isNotEmpty())passages.filter{it.section.length<50&&tokens.any{t->t in EvidenceLanguage.compact(it.section)}}.map{it.section}.distinctBy{EvidenceLanguage.compact(it)}.take(3).map{val label=it.replace(Regex("^\\((.*)\\)$"),"$1");SearchSuggestion(label,"${label}에 대해 알려줘")} else emptyList()
            return result.copy(status=if(vague)"clarify" else "unconfirmed",message=if(vague)"어떤 대상의 방법이나 조건이 궁금한가요? 대상과 궁금한 점을 함께 알려 주세요." else result.message,suggestions=suggestions,semantic=false)
        }
        val texts=passageTexts
        val frequencies=tokens.associateWith{t->tokenFrequency.computeIfAbsent(t){texts.count{t in it}}}
        val wantsCase=Regex("사례|사고사례|질의|해석").containsMatchIn(question)
        val wantsCaseBody=wantsCase||Regex("해당\\s*여부").containsMatchIn(question)
        val candidates=passages.mapIndexed{index,p->
            var dense=p.vector.indices.sumOf{i->p.vector[i].toDouble()*vector[i]}
            if(caseText[index]&&!wantsCase)dense-=.035
            if(p.text.length<40)dense-=.025
            if(review&&!wantsCaseBody){
                if(caseBody[index])dense-=.13
                // A repeated introductory sentence is not the requested procedure.
                if(prefaces[index])dense-=.18
            }
            val text=texts[index]
            val lex=tokens.sumOf{t->if(t in text)ln(1+passages.size.toDouble()/(1+frequencies.getValue(t)))*(if(t in EvidenceLanguage.generic).2 else 1.0) else 0.0}/sqrt(1+p.text.length/500.0)
            Candidate(p,dense,lex)
        }
        val dense=candidates.sortedByDescending{it.dense};val sparse=candidates.filter{it.lex>0}.sortedByDescending{it.lex}
        dense.take(30).forEachIndexed{i,c->c.fused+=1.0/(21+i)};sparse.take(30).forEachIndexed{i,c->c.fused+=1.0/(21+i)}
        val fused=candidates.filter{it.dense>=(dense.firstOrNull()?.dense?:0.0)-.075}.sortedByDescending{it.fused}
        val lead=dense.firstOrNull();val gap=if(lead!=null)lead.dense-(dense.firstOrNull{it.p.page!=lead.p.page||it.p.documentId!=lead.p.documentId}?.dense?:lead.dense) else 0.0
        val top=if(lead!=null&&lead.dense>=.40&&gap>=(if(lead.dense>=.5).04 else .06))lead else fused.firstOrNull()
        if((dense.firstOrNull()?.dense?:0.0)>=.4)result=result.copy(suggestions=fused.map{it.p.section}.filter{it.isNotBlank()&&it.length<=50}.distinctBy{EvidenceLanguage.compact(it)}.take(3).map{SearchSuggestion(it,"${it}에 대해 알려줘")})
        val semanticClear=top!=null&&top.dense>=.40&&dense.indexOf(top)<5&&top.dense-(dense.firstOrNull{it.p.page!=top.p.page||it.p.documentId!=top.p.documentId}?.dense?:top.dense)>=(if(top.dense>=.5).04 else .06)
        if(review){
            result=result.copy(searchComplete=true,method="reviewed-semantic")
            if(passages.isEmpty()||passages.size!=root.getJSONArray("passages").length())return result.copy(status="search-error",searchComplete=false,message="현재 문서와 검색 인덱스를 함께 확인하지 못했어요. 문서를 준비한 뒤 다시 질문해 주세요.",suggestions=emptyList())
            if(vague)return result.copy(status="clarify",message="어떤 대상의 내용을 확인할까요? 관련 항목을 선택하거나 질문의 대상을 알려 주세요.")
            if(unknown.isNotEmpty() || (sources.isEmpty()&&(lead?.dense?:0.0)<.40))return result.copy(status="no-evidence",message="제공 문서 전체 검색에서 질문에 직접 답할 근거를 확인하지 못했어요.",suggestions=emptyList(),generalQuestion=question)
            val attributes=tokens.filter{it in setOf("방법","시기","자격","직무","절차")}
            val titled=if(attributes.isEmpty()||tokens.count{it !in EvidenceLanguage.generic}<1)emptyList() else chunks.filter{c->
                val title=pageTitles.getValue(c.id)
                attributes.all{it in title}&&tokens.all{it in title}&&candidates.any{it.p.documentId==c.documentId&&it.p.page==c.pageNumber&&it.dense>=.35}
            }
            if(titled.size==1)return result.copy(status="answered",sources=completeSafety(titled.map{RetrievedChunk(it,1.0,pageUnits.getValue(it.id))},question),message="",suggestions=emptyList(),method="title-semantic")
            // Rare source anchors can disambiguate a specialist term that the
            // embedding model underweights. Require source coverage plus an
            // independently computed semantic score; never accept token overlap alone.
            val relationWords=setOf("역할","요건","기능","조건","배치","대상","준비","설치","사용","관리","원리","설명","목적")
            val anchors=tokens.filter{it !in EvidenceLanguage.generic&&it !in relationWords&&pageTexts.values.count{text->it in text} in 1..4}
            val anchorCandidates=if(anchors.isEmpty())emptyList() else chunks.filter{c->
                val text=pageTexts.getValue(c.id)
                val covered=anchors.count{it in text}
                covered>=minOf(2,anchors.size)&&covered.toDouble()/anchors.size>=.75&&tokens.count{it in text}>=minOf(3,tokens.size)&&candidates.any{it.p.documentId==c.documentId&&it.p.page==c.pageNumber&&it.dense>=.30}
            }
            val anchored=sources.isNotEmpty()&&anchors.isNotEmpty()&&sources.any{s->
                val text=EvidenceLanguage.aliases(s.chunk.body)
                val covered=anchors.count{it in text}
                val support=candidates.filter{it.p.documentId==s.chunk.documentId&&it.p.page==s.chunk.pageNumber}.maxOfOrNull{it.dense}?:0.0
                covered>=minOf(2,anchors.size)&&covered.toDouble()/anchors.size>=.75&&support>=.30
            }
            if(!semanticClear){
                if(anchorCandidates.size==1)return result.copy(status="answered",sources=completeSafety(anchorCandidates.map{RetrievedChunk(it,1.0,pageUnits.getValue(it.id))},question),message="",suggestions=emptyList(),method="unique-source-anchor-semantic")
                val agreement=sources.isNotEmpty()&&dense.take(5).any{c->c.dense>=.40&&sources.any{it.chunk.documentId==c.p.documentId&&it.chunk.pageNumber==c.p.page}}
                if((agreement||anchored)&&!vague)return result.copy(status="answered",sources=completeSafety(sources,question),message="",suggestions=emptyList(),method=if(anchored)"source-anchor-semantic" else "lexical-semantic-agreement")
                return result.copy(status="clarify",message="관련 근거가 여러 항목으로 나뉘어 있어요. 아래 항목 중 궁금한 범위를 선택해 주세요.")
            }
        }else{
            if(vague)return result.copy(status="clarify",message="어떤 내용을 확인하려는 건가요? 관련 문서에서 찾은 항목 중 선택하거나 궁금한 대상을 알려 주세요.")
            if(unknown.isNotEmpty())return result.copy(message="관련 주제는 찾았지만 요청한 ‘${unknown.joinToString("·")}’ 정보는 현재 문서 근거로 확인하지 못했어요. 아래 항목은 다른 관련 내용이에요.")
        }
        if(sources.isEmpty()&&!semanticClear)return result.copy(status="clarify",message="질문의 뜻을 한 가지로 좁히지 못했어요. 아래 관련 항목 중 어떤 내용인지 알려 주세요. 다른 뜻이라면 대상이나 상황을 조금 더 설명해 주세요.")
        if(sources.isNotEmpty()&&!review)return result.copy(sources=sources,status="answered",message="",suggestions=emptyList())
        if(top==null)return result
        val p=top.p;val chunk=chunks.first{it.documentId==p.documentId&&it.pageNumber==p.page&&it.chunkIndex==p.chunkIndex}
        val units=pageUnits.getValue(chunk.id)
        val selected=completeScope(p,chunk)
        val context=units.filter{(it.section==p.section||Regex("반드시|금지|없다면").containsMatchIn(it.text))&&EvidenceLanguage.compact(it.text) !in EvidenceLanguage.compact(p.text)}
        val evidence=listOf(RetrievedChunk(chunk,top.fused,selected+context))
        return result.copy(sources=if(review)completeSafety(evidence,question) else evidence,status="answered",message="",suggestions=emptyList())
    }
    fun relatedChoices(question: String, sources: List<RetrievedChunk>): List<SearchSuggestion> {
        val selected = sources.flatMap { FocusedEvidence.selected(it) }.map { it.section }.filter { it.isNotBlank() && it.length <= 70 }.distinct()
        val terms = EvidenceLanguage.terms(question)
        val other = passages.filter { passage -> terms.any { it in EvidenceLanguage.aliases(passage.section) } }.map { it.section }.filter(String::isNotBlank)
        return (selected + other).distinctBy { EvidenceLanguage.compact(it) }.take(3).map { SearchSuggestion(it, "$question $it 에 대해 알려줘") }
    }

    /** Additional evidence is searched in the same topic and kept with its complete safety scope. */
    fun additional(question: String, vector: FloatArray, seenAnswers: List<String>): List<RetrievedChunk> {
        val seen = seenAnswers.joinToString(" ") { EvidenceLanguage.compact(it) }
        val terms = EvidenceLanguage.terms(question)
        val anchors = terms.filter { it !in EvidenceLanguage.generic }
        val attributes = terms.filter { it in setOf("시기", "자격", "직무", "주의사항") }
        if (anchors.isEmpty()) return emptyList() // An unscoped 'more' must not pull unrelated safety procedures.
        val ranked = passages.mapIndexedNotNull { position, p ->
            val text = EvidenceLanguage.aliases(p.section + " " + p.text)
            val pageTitle = chunks.firstOrNull { it.documentId == p.documentId && it.pageNumber == p.page && it.chunkIndex == p.chunkIndex }?.let { pageTitles[it.id] }.orEmpty()
            if (attributes.any { it !in text && it !in pageTitle }) return@mapIndexedNotNull null
            if (anchors.none { it in text } || caseBody[position] || prefaces[position]) return@mapIndexedNotNull null
            val score = vector.indices.sumOf { vector[it].toDouble() * p.vector[it] }
            if (score < .40) null else p to score
        }.sortedByDescending { it.second }
        val best = ranked.firstOrNull()?.second ?: return emptyList()
        val candidates = ranked.filter { it.second >= best - .06 }.mapNotNull { (p, score) ->
            val chunk = chunks.firstOrNull { it.documentId == p.documentId && it.pageNumber == p.page && it.chunkIndex == p.chunkIndex } ?: return@mapNotNull null
            val source = completeSafety(listOf(RetrievedChunk(chunk, score, completeScope(p, chunk))), question)
            if (source.flatMap { FocusedEvidence.selected(it) }.none { EvidenceLanguage.compact(it.text).let { key -> key.isNotBlank() && key !in seen } }) null else source
        }.take(2).flatten()
        return candidates.groupBy { it.chunk.id }.values.map { group -> group.first().copy(evidence = group.flatMap { FocusedEvidence.selected(it) }.distinctBy { it.text }) }
    }

    private fun completeSafety(sources:List<RetrievedChunk>,question:String):List<RetrievedChunk> {
        fun procedure(c:KnowledgeChunk)=Regex("^순\\s*서\\s*실\\s*시\\s*방\\s*법").containsMatchIn(FocusedEvidence.pageBody(c))
        val complete=sources.toMutableList()
        for(source in sources.filter{procedure(it.chunk)}){
            val topic=EvidenceLanguage.vocabulary.filter{it !in EvidenceLanguage.generic&&it.length>=3&&it in EvidenceLanguage.aliases(source.chunk.body)}
            val adjacent=chunks.filter{it.documentId==source.chunk.documentId&&kotlin.math.abs(it.pageNumber-source.chunk.pageNumber)==1&&procedure(it)&&topic.any{t->t in EvidenceLanguage.aliases(it.body)}}
            for(chunk in adjacent)if(complete.none{it.chunk.id==chunk.id})complete.add(RetrievedChunk(chunk,source.score,pageUnits.getValue(chunk.id)))
        }
        return complete.map{source->
        val units=pageUnits.getValue(source.chunk.id)
        if(Regex("^순\\s*서\\s*실\\s*시\\s*방\\s*법").containsMatchIn(FocusedEvidence.pageBody(source.chunk)))source.copy(evidence=units)
        else {
            val initial=FocusedEvidence.selected(source)
            // "방법" can refer to a check/preparation as well as the measured
            // procedure; it is not a sufficiently specific section constraint.
            val attributes=EvidenceLanguage.terms(question).filter{it in setOf("시기","자격","직무","주의사항")}
            val requestedScopes=units.filter{unit->attributes.any{it in EvidenceLanguage.compact(unit.section)}}
            val scopeCovered=initial.any{unit->attributes.any{it in EvidenceLanguage.compact(unit.section)}}
            val selected=if(requestedScopes.isNotEmpty()&&!scopeCovered)requestedScopes+initial.filter{Regex("반드시|금지|없다면").containsMatchIn(it.text)} else initial
            val text=selected.joinToString(" "){it.section+" "+it.text}
            val normalizedText=EvidenceLanguage.aliases(text)
            val entities=EvidenceLanguage.vocabulary.filter{it.length>=3&&it !in EvidenceLanguage.generic&&it in normalizedText}
            val guards=units.filter{Regex("^다만|^이 경우|^그 밖|^또한|^그러나").containsMatchIn(it.text)&&entities.any{e->e in EvidenceLanguage.aliases(it.text)}}
            val groups=guards.map{it.group}.toSet()
            // A queried condition must not disappear just because another
            // section scored higher. Add its original local action/condition.
            val missing=EvidenceLanguage.terms(question).filter{it !in EvidenceLanguage.generic&&pageTexts.values.count{body->it in body} in 1..4&&it in pageTexts.getValue(source.chunk.id)&&it !in normalizedText}
            val requested=units.filter{unit->missing.any{it in EvidenceLanguage.aliases(unit.text)}}
            val requestedGroups=requested.map{it.group}.toSet()
            source.copy(evidence=(selected+units.filter{it.group in groups||it.group in requestedGroups}).distinctBy{EvidenceLanguage.compact(it.text)})
        }
    }
    }
    companion object {
        fun contextualQuestion(question:String,previous:String=""):String {
            val value=question.trim()
            if(previous.isBlank()||previous.length>500||!Regex("^(?:그거|그건|그것|그러면|그럼|이 경우|그 경우)").containsMatchIn(value))return value
            val subjects=EvidenceLanguage.terms(value).filter{it !in EvidenceLanguage.generic&&!Regex("^(그거|그건|그것|그러면|그럼|경우)").containsMatchIn(it)}
            return if(subjects.size>1)value else "$previous\n후속 질문: $value"
        }
    }
}
