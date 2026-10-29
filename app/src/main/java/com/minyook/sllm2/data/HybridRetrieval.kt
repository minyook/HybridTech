package com.minyook.sllm2.data

import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.sqrt

data class SearchSuggestion(val label: String, val question: String)
data class SearchResolution(val question: String, val sources: List<RetrievedChunk> = emptyList(), val status: String = "unconfirmed", val message: String = "질문에 직접 답할 근거를 아직 확인하지 못했어요. 궁금한 대상과 알고 싶은 내용을 함께 알려 주세요.", val suggestions: List<SearchSuggestion> = emptyList(), val semantic: Boolean = false)
internal data class SemanticPassage(val id:String,val documentId:String,val page:Int,val chunkIndex:Int,val section:String,val text:String,val orders:List<Int>,val units:List<EvidencePassage>,val searchText:String,val sourceBody:String,val vector:FloatArray)
internal class HybridRetrieval(private val chunks:List<KnowledgeChunk>, json:String) {
    private val root = JSONObject(json)
    init { require(root.getInt("dimensions")==768 && root.getString("modelRevision")=="5090578d9565bb06545b4552f76e6bc2c93e4a66") }
    private val passages = root.getJSONArray("passages").let { rows -> (0 until rows.length()).map { i ->
        val p=rows.getJSONObject(i);val orders=p.getJSONArray("orders");val v=p.getJSONArray("vector");val units=p.getJSONArray("units")
        SemanticPassage(p.getString("id"),p.getString("documentId"),p.getInt("page"),p.getInt("chunkIndex"),p.getString("section"),p.getString("text"),(0 until orders.length()).map{orders.getInt(it)},(0 until units.length()).map{j->val u=units.getJSONObject(j);EvidencePassage(u.getString("text"),u.getString("section"),10000+u.getInt("order"),u.optInt("group",j))},p.getString("searchText"),p.getString("sourceBody"),FloatArray(v.length()){v.getDouble(it).toFloat()})
    }.filter { p -> chunks.any { it.documentId==p.documentId && it.pageNumber==p.page && it.chunkIndex==p.chunkIndex && it.body==p.sourceBody } } }
    private val lexical = FocusedRetriever(chunks)
    private val specification = Regex("방수|IP\\s*등급|배터리|전압|볼트|가격|구매|무게|제조사|모델명|충전|유효기간|유효\\s*기간",RegexOption.IGNORE_CASE)
    private data class Candidate(val p:SemanticPassage,val dense:Double,val lex:Double,var fused:Double=0.0)
    private fun completeScope(p:SemanticPassage,chunk:KnowledgeChunk):List<EvidencePassage>{
        val units=FocusedEvidence.units(chunk);val first=units.indexOfFirst{it.section==p.section};val previous=units.getOrNull(first-1)
        val topic=previous?.text?.let{Regex("^\\(([^)]+)\\)").find(it)?.groupValues?.get(1)}
        return if(previous!=null&&topic!=null&&EvidenceLanguage.compact(topic) in EvidenceLanguage.compact(p.section))listOf(previous.copy(section=p.section))+p.units else p.units
    }
    fun resolve(question:String,vector:FloatArray?):SearchResolution {
        var result=SearchResolution(question,semantic=vector!=null)
        if(Regex("(?:우리|현재|지금).*(?:승인|허가).*(?:됐|되었|받았|완료|상태)").containsMatchIn(question))return result.copy(status="live-state",message="현재 현장의 승인 상태는 문서로 확인할 수 없어요. 작업 전 점검 화면에서 요청과 감독관 승인 상태를 확인해 주세요.")
        val parts=FocusedRetriever.aspects(question).take(8)
        val sharedGas=parts.size>1&&parts.dropLast(1).all{Regex("^(산소|유해가스|일산화탄소|이산화탄소|황화수소)$").matches(EvidenceLanguage.compact(it))}&&EvidenceLanguage.terms(parts.last()).any{it in setOf("측정","농도","기준")}
        if(parts.size>1&&!sharedGas){
            val each=parts.map{resolve(it,null)};val found=each.filter{it.status=="answered"};val missing=each.filter{it.status!="answered"}
            if(found.isNotEmpty()&&missing.isNotEmpty())return result.copy(status="partial",sources=found.flatMap{it.sources}.groupBy{it.chunk.id}.values.map{group->group.first().copy(evidence=group.flatMap{FocusedEvidence.selected(it)}.distinctBy{it.text})},message="질문 중 ‘${missing.joinToString(" / "){it.question}}’ 부분은 아직 직접 근거를 확인하지 못했어요. 이 부분에서 궁금한 대상이나 조건을 더 알려 주세요.",suggestions=missing.take(3).map{SearchSuggestion(it.question,it.question)})
        }
        val tokens=EvidenceLanguage.terms(question).filterNot{Regex("^(그거|그건|그것|그러면|그럼|후속|질문)").containsMatchIn(it)}
        val sources=lexical.retrieve(question)
        val unknown=specification.findAll(question).map{it.value}.distinct().filter{s->chunks.none{EvidenceLanguage.compact(s) in EvidenceLanguage.compact(it.body)}}.toList()
        val vague=tokens.size<=1&&tokens.none{it !in EvidenceLanguage.generic}
        val heading=EvidenceLanguage.compact(question.replace(Regex("(?:에\\s*대해|관련|내용|알려줘|알려주세요|설명해줘|설명해주세요|정리해줘|주세요|좀|\\?)"),""))
        val exact=passages.filter{heading.isNotBlank()&&EvidenceLanguage.compact(it.section)==heading}
        if(unknown.isEmpty()&&exact.isNotEmpty()&&exact.map{it.documentId to it.page}.distinct().size<=2){
            val selected=exact.groupBy{it.documentId to it.page}.values.map{group->group.maxBy{it.orders.size}}
            return result.copy(status="answered",message="",semantic=false,sources=selected.map{p->val chunk=chunks.first{it.documentId==p.documentId&&it.pageNumber==p.page&&it.chunkIndex==p.chunkIndex};RetrievedChunk(chunk,1.0,completeScope(p,chunk))})
        }
        if(vector==null||vector.size!=768||vector.any{!it.isFinite()}){
            if(sources.isNotEmpty()&&unknown.isEmpty()&&!vague)return result.copy(sources=sources,status="answered",message="",semantic=false)
            val suggestions=if(vague&&tokens.isNotEmpty())passages.filter{it.section.length<50&&tokens.any{t->t in EvidenceLanguage.compact(it.section)}}.map{it.section}.distinctBy{EvidenceLanguage.compact(it)}.take(3).map{val label=it.replace(Regex("^\\((.*)\\)$"),"$1");SearchSuggestion(label,"${label}에 대해 알려줘")} else emptyList()
            return result.copy(status=if(vague)"clarify" else "unconfirmed",message=if(vague)"어떤 대상의 방법이나 조건이 궁금한가요? 대상과 궁금한 점을 함께 알려 주세요." else result.message,suggestions=suggestions,semantic=false)
        }
        val texts=passages.map{EvidenceLanguage.aliases(it.searchText)}
        val frequencies=tokens.associateWith{t->texts.count{t in it}}
        val candidates=passages.mapIndexed{index,p->
            var dense=p.vector.indices.sumOf{i->p.vector[i].toDouble()*vector[i]}
            if(Regex("사망\\s*\\d+명|\\[답변|┃사례").containsMatchIn(p.text)&&!Regex("사례|사고사례|질의|해석").containsMatchIn(question))dense-=.035
            if(p.text.length<40)dense-=.025
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
        if(vague)return result.copy(status="clarify",message="어떤 내용을 확인하려는 건가요? 관련 문서에서 찾은 항목 중 선택하거나 궁금한 대상을 알려 주세요.")
        if(unknown.isNotEmpty())return result.copy(message="관련 주제는 찾았지만 요청한 ‘${unknown.joinToString("·")}’ 정보는 현재 문서 근거로 확인하지 못했어요. 아래 항목은 다른 관련 내용이에요.")
        val semanticClear=top!=null&&top.dense>=.40&&dense.indexOf(top)<5&&top.dense-(dense.firstOrNull{it.p.page!=top.p.page||it.p.documentId!=top.p.documentId}?.dense?:top.dense)>=(if(top.dense>=.5).04 else .06)
        if(sources.isEmpty()&&!semanticClear)return result.copy(status="clarify",message="질문의 뜻을 한 가지로 좁히지 못했어요. 아래 관련 항목 중 어떤 내용인지 알려 주세요. 다른 뜻이라면 대상이나 상황을 조금 더 설명해 주세요.")
        if(sources.isNotEmpty())return result.copy(sources=sources,status="answered",message="",suggestions=emptyList())
        if(top==null)return result
        val p=top.p;val chunk=chunks.first{it.documentId==p.documentId&&it.pageNumber==p.page&&it.chunkIndex==p.chunkIndex}
        val units=FocusedEvidence.units(chunk)
        val selected=completeScope(p,chunk)
        val context=units.filter{(it.section==p.section||Regex("반드시|금지|없다면").containsMatchIn(it.text))&&EvidenceLanguage.compact(it.text) !in EvidenceLanguage.compact(p.text)}
        return result.copy(sources=listOf(RetrievedChunk(chunk,top.fused,selected+context)),status="answered",message="",suggestions=emptyList())
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
