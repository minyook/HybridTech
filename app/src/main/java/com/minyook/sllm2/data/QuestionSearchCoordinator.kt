/* 소스 안내
 * 대화 의도·복합 질문·빠른 경로·질문 벡터 생성을 조정한다. 오류/모호성/근거 미확인을 구분해 ViewModel 분기를 결정한다.
 */
package com.minyook.sllm2.data

/**
 * KnowledgeRepository가 호출하는 실제 질문 검색 조정기.
 * 후속 질문·선택지·대화 기억 요청을 처리하고, 명확한 직접 응답 외에는 질문 정규화 → 벡터 생성 →
 * HybridRetrieval 재검토를 실행한다. 복합 질문은 주제별 검색 결과를 합친다.
 * 검색 실패(search-error), 질문 대상 자체가 없는 경우(clarify), 검색 완료 후 근거 미확인(no-evidence)을 구분한다.
 * 문서 주제를 여러 선택지로 나누지 않는다. 이전 기록의 선택 메타데이터는 명시적 후속 질문 호환에만 사용한다.
 * searchComplete/generalQuestion 계약으로 ViewModel의 일반 지식 생성 분기를 제어한다.
 */
internal class QuestionSearchCoordinator(
    private val index: HybridRetrieval,
    private val encode: (String) -> FloatArray,
) {
    fun search(question: String): SearchResolution = searchInternal(question)

    fun search(request: ConversationRequest): SearchResolution {
        if (request.kind == FollowUpKind.UNRESOLVED) return SearchResolution(request.asked, status = "clarify", method = "conversation-reference", message = "이어 볼 이전 주제를 확인하지 못했어요. 궁금한 대상이나 앞선 질문을 알려 주세요.")
        if (request.kind == FollowUpKind.ACK) return SearchResolution(request.query, status = "conversation-ack", method = "local-dialogue", message = "알겠어요. 앞선 ‘${request.topic}’에 이어 궁금한 조건이나 다음 질문을 말씀해 주세요.")
        if (request.kind == FollowUpKind.RECALL) {
            val users = request.turns.filter { it.user }.takeLast(4)
            val text = if (users.isEmpty()) "이 대화에 앞서 기록된 질문이 없어요." else "이 대화에서 최근에 말씀하신 내용이에요.\n\n" + users.mapIndexed { i, t -> "${i + 1}. ${t.text}" }.joinToString("\n\n")
            return SearchResolution(request.asked, status = "conversation-recall", method = "local-transcript", message = text)
        }
        if (request.kind == FollowUpKind.SHOW_CHOICES) {
            // 이전 기록의 선택지 요청도 주제 자체를 다시 검색한다. 새 주제 선택 목록을 만들지 않는다.
            return search(request.query)
        }
        val resolved = search(request.query)
        if (request.kind != FollowUpKind.MORE || resolved.status in setOf("search-error", "live-state")) return resolved
        if (resolved.answerBody != null) {
            if (resolved.generalQuestion != null) return resolved
            val alreadyShown = request.seenAnswers.any { EvidenceLanguage.compact(resolved.answerBody) in EvidenceLanguage.compact(it) }
            return if (!alreadyShown) resolved else resolved.copy(status = "no-more", sources = emptyList(), answerBody = null, message = "앞선 ‘${request.topic}’에 관해 이미 보여드린 내용 외에 추가로 확인한 근거는 없어요. 더 궁금한 조건이나 장비를 말씀해 주세요.")
        }
        if (resolved.searchComplete && resolved.generalQuestion != null && request.previousAnswer?.text?.let(GeneralKnowledgeAnswer::containsGeneral) == true) return resolved.copy(method = "conversation-general")
        val vector = try { encode(QuestionUnderstanding.normalize(request.query)) } catch (_: Exception) { return SearchResolution(request.query, status = "search-error", method = "conversation-more", message = "앞선 주제의 추가 검색을 완료하지 못했어요. 문서가 없다는 뜻은 아니에요.") }
        if (vector.size != 768 || vector.any { !it.isFinite() } || vector.all { it == 0f }) return SearchResolution(request.query, status = "search-error", method = "conversation-more", message = "추가 검색의 의미 벡터를 확인하지 못했어요. 다시 시도해 주세요.")
        val extra = index.additional(request.query, vector, request.seenAnswers)
        if (extra.isNotEmpty()) return SearchResolution(request.query, extra, status = "answered", method = "conversation-more", message = "앞선 ‘${request.topic}’ 답변에 이어, 아직 설명하지 않은 문서 내용을 확인했어요.", searchComplete = true)
        return SearchResolution(request.query, status = "no-more", method = "conversation-more", message = "앞선 ‘${request.topic}’ 답변과 겹치지 않는 추가 근거는 이번 검색에서 찾지 못했어요. 이미 설명한 내용이 문서에 없다는 뜻은 아니에요.", searchComplete = true)
    }

    /** 단어 일치는 후보일 뿐이다. 직접 응답 계약이 아니면 신경망 검색으로 재검토하고 인코더 오류를 별도 반환한다. */
    private fun searchInternal(question: String): SearchResolution {
        if(Regex("^(그거|그것|그건|이거|이것|이건)(어떻게|어케|하는데|해|알려줘|설명해줘|뭐야|좀|는|을|에)*$").matches(EvidenceLanguage.compact(question)))return SearchResolution(question,status="clarify",message="어떤 대상을 말하는지 알려 주세요. 앞선 질문이 없는 상태에서는 ‘그거’의 대상을 확인할 수 없어요.",method="reference-clarification")
        val query=QuestionUnderstanding.normalize(question)
        val fast=index.resolve(query,null,fastOnly=true).copy(question=question)
        if(fast.status=="live-state"||fast.answerBody!=null||fast.method in setOf("heading","inventory"))return fast
        val parts=parts(question)
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
            return SearchResolution(question,sources,status=if(found.isEmpty())"clarify" else "partial",message="질문 중 ‘${missing.joinToString(" / "){it.question}}’의 근거를 더 확인해야 해요. ${missing.first().message}",searchComplete=complete,method="compound")
        }
        val vector=try{encode(query)}catch(_:Exception){return fast.copy(sources=emptyList(),status="search-error",message="문장 의미 검색을 완료하지 못했어요. 문서가 없다는 뜻은 아니에요. 다시 질문하거나 원문에서 확인해 주세요.",searchComplete=false,generalQuestion=null)}
        if(vector.size!=768||vector.any{!it.isFinite()}||vector.all{it==0f})return fast.copy(sources=emptyList(),status="search-error",message="문장 의미 검색 결과를 확인하지 못했어요. 다시 질문해 주세요.",searchComplete=false,generalQuestion=null)
        val reviewed=index.resolve(query,vector,review=true)
        return reviewed.copy(question=question,generalQuestion=reviewed.generalQuestion?.let{if(it==query)question else it})
    }
    companion object {
        /** Korean noun conjunctions belong to one question. Only explicit discourse
         * boundaries introduce independently searchable questions. */
        fun parts(question:String):List<String> = question.trim()
            .split(Regex("\\s+그리고\\s+|[;；]|\\n(?!(?:후속 질문:))"))
            .map(String::trim).filter(String::isNotBlank)
    }
}
