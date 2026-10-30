package com.minyook.sllm2.data

/** Production question path. A lexical hit alone is a candidate; only exact
 * headings/inventories skip neural review. Encoder failure is not absence. */
internal class QuestionSearchCoordinator(
    private val index: HybridRetrieval,
    private val encode: (String) -> FloatArray,
) {
    fun search(question: String): SearchResolution {
        if(Regex("^(그거|그것|그건|이거|이것|이건)(어떻게|어케|하는데|해|알려줘|설명해줘|뭐야|좀|는|을|에)*$").matches(EvidenceLanguage.compact(question)))return SearchResolution(question,status="clarify",message="어떤 대상을 말하는지 알려 주세요. 앞선 질문이 없는 상태에서는 ‘그거’의 대상을 확인할 수 없어요.",method="reference-clarification")
        val fast=index.resolve(question,null)
        if(fast.status=="live-state"||fast.method in setOf("heading","inventory"))return fast
        val parts=FocusedRetriever.aspects(question)
        if(parts.size>8)return SearchResolution(question,status="clarify",message="한 번에 확인할 주제가 많아요. 질문을 8개 이하의 주제로 나누어 주세요.",method="question-limit")
        val sharedGas=parts.size>1&&parts.dropLast(1).all{Regex("^(산소|유해가스|일산화탄소|이산화탄소|황화수소)$").matches(EvidenceLanguage.compact(it))}&&EvidenceLanguage.terms(parts.last()).any{it in setOf("측정","농도","기준")}
        if(parts.size>1&&!sharedGas){
            val results=parts.map(::search)
            val found=results.filter{it.status=="answered"}
            val missing=results.filter{it.status!="answered"}
            val sources=found.flatMap{it.sources}.groupBy{it.chunk.id}.values.map{group->group.first().copy(evidence=group.flatMap{FocusedEvidence.selected(it)}.distinctBy{it.order})}
            val complete=results.all{it.searchComplete||it.method in setOf("heading","inventory")}
            if(missing.isEmpty())return SearchResolution(question,sources,status="answered",message="",method="compound",searchComplete=complete)
            if(missing.all{it.status=="no-evidence"}&&complete)return SearchResolution(question,sources,status=if(found.isEmpty())"no-evidence" else "partial",message="",method="compound",searchComplete=true,generalQuestion=missing.joinToString(" 그리고 "){it.question})
            return SearchResolution(question,sources,status=if(found.isEmpty())"clarify" else "partial",message="질문 중 ‘${missing.joinToString(" / "){it.question}}’의 근거를 더 확인해야 해요. ${missing.first().message}",suggestions=missing.flatMap{it.suggestions}.distinctBy{it.question}.take(3),searchComplete=complete,method="compound")
        }
        val vector=try{encode(question)}catch(_:Exception){return fast.copy(sources=emptyList(),status="search-error",message="문장 의미 검색을 완료하지 못했어요. 문서가 없다는 뜻은 아니에요. 다시 질문하거나 원문에서 확인해 주세요.",searchComplete=false,generalQuestion=null)}
        if(vector.size!=768||vector.any{!it.isFinite()}||vector.all{it==0f})return fast.copy(sources=emptyList(),status="search-error",message="문장 의미 검색 결과를 확인하지 못했어요. 다시 질문해 주세요.",searchComplete=false,generalQuestion=null)
        return index.resolve(question,vector,review=true)
    }
}
