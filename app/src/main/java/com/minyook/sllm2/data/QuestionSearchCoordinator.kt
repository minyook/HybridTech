package com.minyook.sllm2.data

/** Production question path. A lexical hit alone is a candidate; only exact
 * headings/inventories skip neural review. Encoder failure is not absence. */
internal class QuestionSearchCoordinator(
    private val index: HybridRetrieval,
    private val encode: (String) -> FloatArray,
) {
    fun search(question: String): SearchResolution = searchInternal(question).withVisibleChoices()

    fun search(request: ConversationRequest): SearchResolution {
        if (request.kind == FollowUpKind.UNRESOLVED) return SearchResolution(request.asked, status = "clarify", method = "conversation-reference", message = "이어 볼 이전 주제를 확인하지 못했어요. 궁금한 대상이나 앞선 질문을 알려 주세요.")
        if (request.kind == FollowUpKind.ACK) return SearchResolution(request.query, status = "conversation-ack", method = "local-dialogue", message = "알겠어요. 앞선 ‘${request.topic}’에 이어 궁금한 조건이나 다음 질문을 말씀해 주세요.")
        if (request.kind == FollowUpKind.RECALL) {
            val users = request.turns.filter { it.user }.takeLast(4)
            val text = if (users.isEmpty()) "이 대화에 앞서 기록된 질문이 없어요." else "이 대화에서 최근에 말씀하신 내용이에요.\n\n" + users.mapIndexed { i, t -> "${i + 1}. ${t.text}" }.joinToString("\n\n")
            return SearchResolution(request.asked, status = "conversation-recall", method = "local-transcript", message = text)
        }
        if (request.kind == FollowUpKind.SHOW_CHOICES) {
            val previous = request.previousAnswer?.suggestions.orEmpty()
            val recovered = if (previous.isEmpty()) search(request.query) else null
            val options = previous.ifEmpty { recovered?.suggestions.orEmpty().ifEmpty { index.relatedChoices(request.query, recovered?.sources.orEmpty()) } }
            return SearchResolution(request.query, status = "conversation-choices", method = "conversation-choices", message = if(options.isEmpty()) "앞선 안내에 실제 선택 항목이 연결되지 않았어요. ‘${request.topic}’에 대해 확인할 대상이나 조건을 말씀해 주시면 이어서 답할게요." else "앞선 주제 ‘${request.topic}’에서 확인할 수 있는 항목을 다시 적어 드릴게요. 번호나 항목 이름으로 말씀하셔도 돼요.", suggestions = options).withVisibleChoices()
        }
        val resolved = search(request.query)
        if (request.kind != FollowUpKind.MORE || resolved.status in setOf("search-error", "live-state")) return resolved
        if (resolved.answerBody != null) {
            val alreadyShown = request.seenAnswers.any { EvidenceLanguage.compact(resolved.answerBody) in EvidenceLanguage.compact(it) }
            return if (!alreadyShown) resolved else resolved.copy(status = "no-more", sources = emptyList(), answerBody = null, message = "앞선 ‘${request.topic}’에 관해 이미 보여드린 내용 외에 추가로 확인한 근거는 없어요. 더 궁금한 조건이나 장비를 말씀해 주세요.")
        }
        if (resolved.searchComplete && resolved.generalQuestion != null && request.previousAnswer?.text?.let(GeneralKnowledgeAnswer::containsGeneral) == true) return resolved.copy(method = "conversation-general")
        val vector = try { encode(QuestionUnderstanding.normalize(request.query)) } catch (_: Exception) { return SearchResolution(request.query, status = "search-error", method = "conversation-more", message = "앞선 주제의 추가 검색을 완료하지 못했어요. 문서가 없다는 뜻은 아니에요.") }
        if (vector.size != 768 || vector.any { !it.isFinite() } || vector.all { it == 0f }) return SearchResolution(request.query, status = "search-error", method = "conversation-more", message = "추가 검색의 의미 벡터를 확인하지 못했어요. 다시 시도해 주세요.")
        val extra = index.additional(request.query, vector, request.seenAnswers)
        if (extra.isNotEmpty()) return SearchResolution(request.query, extra, status = "answered", method = "conversation-more", message = "앞선 ‘${request.topic}’ 답변에 이어, 아직 설명하지 않은 문서 내용을 확인했어요.", searchComplete = true)
        val options = request.previousAnswer?.suggestions.orEmpty().ifEmpty { index.relatedChoices(request.query, resolved.sources) }
        return SearchResolution(request.query, status = "no-more", method = "conversation-more", message = "앞선 ‘${request.topic}’ 답변과 겹치지 않는 추가 근거는 이번 검색에서 찾지 못했어요. 이미 설명한 내용이 문서에 없다는 뜻은 아니에요." + if(options.isEmpty()) " 궁금한 세부 조건을 말씀해 주세요." else " 더 살펴볼 항목을 적어 드릴게요.", suggestions = options, searchComplete = true).withVisibleChoices()
    }

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
            return SearchResolution(question,sources,status=if(found.isEmpty())"clarify" else "partial",message="질문 중 ‘${missing.joinToString(" / "){it.question}}’의 근거를 더 확인해야 해요. ${missing.first().message}",suggestions=missing.flatMap{it.suggestions}.distinctBy{it.question}.take(3),searchComplete=complete,method="compound")
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
