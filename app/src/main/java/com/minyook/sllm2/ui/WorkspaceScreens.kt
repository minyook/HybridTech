/* 소스 안내
 * 채팅·음성 대화 목록과 문서/기록 화면을 구성한다. 입력/출처 이벤트는 actions로 넘기고 목록 따라가기는 표시 상태로 관리한다.
 * 연결 모듈: com.minyook.sllm2.data.ChatHistoryStore, com.minyook.sllm2.data.GeneralKnowledgeAnswer, com.minyook.sllm2.model.ModelPhase.
 */
package com.minyook.sllm2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minyook.sllm2.data.ChatHistoryStore
import com.minyook.sllm2.data.GeneralKnowledgeAnswer
import com.minyook.sllm2.model.ModelPhase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 텍스트/음성 대화가 공유하는 대화 목록과 입력창. ViewModel 메시지를 표시 전용 버퍼에 통과시킨 뒤
 * ChatTimelineBuilder로 Markdown 블록별 lazy 항목을 만든다. 원문·기록 저장과 모델 생성은 수행하지 않는다.
 * 사용자 위쪽 탐색은 자동 따라가기를 멈추고 끝 복귀/최근 답변 버튼은 끝 앵커 추적을 다시 시작한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkspaceChat(state:FieldGuardUiState,voice:VoiceUiState,actions:FieldGuardActions,imeVisible:Boolean,voiceMode:Boolean=false){
    val list=key(state.activeChatId){rememberLazyListState()}
    var follow by rememberSaveable(state.activeChatId){mutableStateOf(true)}
    var jumpRequest by remember(state.activeChatId){mutableIntStateOf(0)}
    var viewportHeight by remember(list){mutableIntStateOf(0)}
    var autoScrolling by remember(list) { mutableStateOf(false) }
    val scrollScope=rememberCoroutineScope()
    var scrollJob by remember(list) { mutableStateOf<Job?>(null) }
    DisposableEffect(list){onDispose{scrollJob?.cancel()}}
    var voiceSettings by remember{mutableStateOf(false)}
    var showTranscript by rememberSaveable(voiceMode) { mutableStateOf(false) }
    val conversation = if (state.activeChatId != null || state.messages.size > 1) state.messages else emptyList()
    val incoming = if (voiceMode && !showTranscript) emptyList() else conversation
    val messages = key(state.activeChatId) {
        val latest = incoming.lastOrNull()
        if (latest?.animateAnswer == true) {
            val revealed = key(latest.id) { rememberRevealedMessage(latest) }
            remember(incoming, revealed) { incoming.dropLast(1) + revealed }
        } else incoming
    }
    val timelineBuilder=remember{ChatTimelineBuilder()}
    val rows=remember(messages){timelineBuilder.build(messages)}
    val latestEndIndex by rememberUpdatedState(rows.size+1)
    val following by rememberUpdatedState(follow)
    val endAnchorHeight = with(LocalDensity.current) { 1.dp.roundToPx() }
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val canSend=!voice.conversationEnabled&&!state.isAnswering&&state.input.isNotBlank()
    val submit:()->Unit={if(canSend){follow=true;keyboard?.hide();focus.clearFocus();actions.onSubmit()}}
    val openVoice:()->Unit={keyboard?.hide();focus.clearFocus();actions.onNavigate(AppDestination.VOICE);if(!voice.conversationEnabled)actions.onVoiceClick()}
    val toggleVoice:()->Unit={if(voice.conversationEnabled)actions.onStopVoice() else actions.onVoiceClick()}
    // Only actual upward input pauses following. Programmatic scrolls and
    // changing answer height must not be mistaken for the reader scrolling up.
    val scrollConnection=remember(list){object:NestedScrollConnection{
        override fun onPreScroll(available:Offset,source:NestedScrollSource):Offset{
            if(source==NestedScrollSource.UserInput&&available.y>0f&&list.canScrollBackward){follow=false;scrollJob?.cancel();autoScrolling=false}
            return Offset.Zero
        }
    }}
    LaunchedEffect(list){snapshotFlow{Triple(list.layoutInfo.totalItemsCount,list.isScrollInProgress,list.canScrollForward)}.collectLatest{(count,moving,forward)->if(count>0&&!moving&&!forward)follow=true}}
    LaunchedEffect(imeVisible){if(imeVisible){follow=true;jumpRequest++}}
    // Nearby streamed text should move by its added height. Long jumps and
    // viewport changes can still anchor directly to the end.
    val latestMessage = messages.lastOrNull()
    LaunchedEffect(list, rows.size, latestMessage, viewportHeight, follow, jumpRequest, endAnchorHeight) {
        if(!follow){scrollJob?.cancel();autoScrolling=false;return@LaunchedEffect}
        if (follow && rows.isNotEmpty() && viewportHeight > 0) {
            val endIndex = rows.size + 1
            withFrameNanos { }
            if(scrollJob?.isActive==true)return@LaunchedEffect
            autoScrolling = true
            try {
                val layout = list.layoutInfo
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (state.isAnswering && list.canScrollForward && layout.totalItemsCount == endIndex + 1 &&
                    layout.viewportSize.height == viewportHeight && endIndex - lastVisible in 0..4
                ) {
                    // Keep the scroll alive across character updates; restarting a spring on
                    // every frame leaves the viewport permanently chasing the growing text.
                    scrollJob=scrollScope.launch {
                        try {
                            do {
                                val current=list.layoutInfo
                                val endOffset=current.viewportEndOffset-current.afterContentPadding-endAnchorHeight
                                list.animateScrollToItem(latestEndIndex, scrollOffset = -endOffset.coerceAtLeast(0))
                                withFrameNanos { }
                            } while(following && list.canScrollForward)
                        } finally {autoScrolling=false}
                    }
                } else {
                    list.scrollToItem(endIndex)
                }
            } finally {
                if(scrollJob?.isActive!=true)autoScrolling = false
            }
        }
    }
    val showScrollLatest by remember(list) {
        derivedStateOf { list.canScrollForward && (!follow || !autoScrolling) }
    }
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
        Column(Modifier.widthIn(max=1260.dp).fillMaxSize().padding(horizontal=workspacePadding()).padding(bottom=if(imeVisible)8.dp else 0.dp)){
            Box(Modifier.weight(1f)){
            if (voiceMode && !showTranscript) {
                VoiceSessionStatus(voice, Modifier.fillMaxSize())
            } else {
            LazyColumn(state=list,modifier=Modifier.fillMaxSize().onSizeChanged{viewportHeight=it.height}.nestedScroll(scrollConnection).testTag("conversationList"),contentPadding=PaddingValues(top=if(imeVisible)12.dp else 26.dp,bottom=16.dp)){
                item(key="heading"){
                    if (!imeVisible && !voiceMode) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = actions.onNewChat, enabled = !state.isAnswering && !voice.conversationEnabled) {
                            Text("새 대화")
                        }
                    }
                }
                if(messages.isEmpty()&&imeVisible)item(key="inputHint"){
                    Text("궁금한 내용을 입력해 주세요.",fontSize=14.sp,lineHeight=22.sp,color=Color(0xFF8B95A1),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(vertical=16.dp).testTag("chatInputHint"))
                }
                if(messages.isEmpty()&&!imeVisible&&!voiceMode)item(key="empty"){
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("질문 예시", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        workspaceQuestions.forEach { prompt ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { actions.onPreset(prompt) }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(prompt, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                WorkspaceIcon(WorkspaceSymbol.CHEVRON, Modifier.size(14.dp))
                            }
                            HorizontalDivider(color = WorkspaceDesign.Border)
                        }
                    }
                }
                items(rows,key={it.key},contentType={it.contentType}){row->
                    Box(Modifier.fillMaxWidth().testTag("chatRow:${row.key}"),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=820.dp).fillMaxWidth()){
                        when(row){
                            is ChatTimelineRow.Question->WorkspaceQuestion(row.message)
                            is ChatTimelineRow.Header->WorkspaceAnswerHeader(row.message)
                            is ChatTimelineRow.Body->MarkdownBlockText(row.block)
                            is ChatTimelineRow.Footer->WorkspaceAnswerFooter(row.message,actions)
                        }
                    }}
                }
                if(rows.isNotEmpty())item(key="conversationEnd",contentType="end"){Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("conversationEnd"))}
            }
            if(showScrollLatest&&messages.isNotEmpty())FilledTonalButton(onClick={follow=true;jumpRequest++},modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=8.dp).testTag("scrollLatest")){Text("최근 답변으로 이동 ↓",fontSize=12.sp)}
            }
            }
            if(state.inputError!=null&&(!voiceMode||state.inputError!=voice.error))WorkspaceNotice(state.inputError,true)
            if (voiceMode) {
                VoiceControls(
                    voice = voice,
                    canStart = !state.isAnswering,
                    showTranscript = showTranscript,
                    hasConversation = conversation.isNotEmpty(),
                    onToggleTranscript = {
                        showTranscript = !showTranscript
                        follow = true
                    },
                    onToggleVoice = toggleVoice,
                    onOpenSettings = { voiceSettings = true },
                    onReturnToChat = {
                        actions.onStopVoice()
                        actions.onNavigate(AppDestination.CHAT)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }else{
            Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFFDCE5F1)),modifier=Modifier.fillMaxWidth().testTag("chatComposer")){
                Row(Modifier.padding(start=20.dp,end=14.dp,top=12.dp,bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                    BasicTextField(enabled=!voice.conversationEnabled,value=state.input,onValueChange=actions.onInputChange,maxLines=4,keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences,imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={submit()}),textStyle=TextStyle(fontSize=14.sp,lineHeight=24.sp,color=Color(0xFF303D50)),modifier=Modifier.weight(1f).heightIn(min=if(imeVisible)24.dp else 46.dp,max=if(imeVisible)96.dp else 130.dp).testTag("chatInput"),decorationBox={inner->if(state.input.isEmpty())Text("궁금한 절차나 기준을 입력해 주세요",fontSize=14.sp,color=Color(0xFFA1ACBB));inner()})
                    IconButton(onClick=openVoice,enabled=voice.conversationEnabled||!state.isAnswering,modifier=Modifier.size(48.dp).testTag("voiceQuestion").semantics{contentDescription="음성 대화 화면 열기"}){TossMicrophoneIcon()}
                    FilledIconButton(onClick=submit,enabled=canSend,shape=RoundedCornerShape(12.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=WorkspaceDesign.Blue,contentColor=Color.White,disabledContainerColor=Color(0xFFDEE7F4),disabledContentColor=Color(0xFFA9B9D1)),modifier=Modifier.size(48.dp).testTag("sendMessage").semantics{contentDescription="메시지 전송"}){WorkspaceIcon(WorkspaceSymbol.ARROW,Modifier.size(21.dp).graphicsLayer{rotationZ=-90f},if(canSend)Color.White else Color(0xFFA9B9D1))}
                }
            }
            }
            if(!imeVisible)Text("생성 답변의 수치와 조건은 원문에서 확인해 주세요.",fontSize=9.sp,color=Color(0xFFA0AABA),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(top=10.dp,bottom=18.dp))
        }
    }
    if(voiceSettings)AlertDialog(onDismissRequest={voiceSettings=false},title={Text("음성 설정")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())){WorkspaceVoiceSettings(voice,actions)}},confirmButton={TextButton(onClick={voiceSettings=false}){Text("닫기")}})
}

@Composable
private fun VoiceControls(
    voice: VoiceUiState,
    canStart: Boolean,
    showTranscript: Boolean,
    hasConversation: Boolean,
    onToggleTranscript: () -> Unit,
    onToggleVoice: () -> Unit,
    onOpenSettings: () -> Unit,
    onReturnToChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = voice.conversationEnabled
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.widthIn(max = 460.dp).fillMaxWidth().padding(vertical = 8.dp).testTag("voiceControls"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showTranscript) {
            Text(
                voice.status,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            voice.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (hasConversation || showTranscript) {
            TextButton(
                onClick = onToggleTranscript,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("toggleVoiceTranscript"),
            ) { Text(if (showTranscript) "음성 상태 보기" else "대화 내용과 출처 보기") }
        }
        Button(
            onClick = onToggleVoice,
            enabled = active || canStart,
            shape = shape,
            colors = if (active) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) else ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("toggleVoiceConversation"),
        ) {
            Text(
                if (active) "음성 대화 종료" else "음성 대화 시작",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onReturnToChat,
                shape = shape,
                border = BorderStroke(1.dp, WorkspaceDesign.Border),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("returnToTextChat"),
            ) { Text("텍스트 질문", color = WorkspaceDesign.Ink, textAlign = TextAlign.Center) }
            OutlinedButton(
                onClick = onOpenSettings,
                shape = shape,
                border = BorderStroke(1.dp, WorkspaceDesign.Border),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("voiceSettings"),
            ) { Text("음성 설정", color = WorkspaceDesign.Ink, textAlign = TextAlign.Center) }
        }
    }
}

@Composable private fun VoiceSessionStatus(voice: VoiceUiState, modifier: Modifier = Modifier) {
    val status = when {
        voice.error != null -> "음성 대화 오류"
        voice.speaking -> "답변 읽는 중"
        voice.waitingForAnswer -> "답변 준비 중"
        voice.listening -> "듣는 중"
        voice.awaitingWakeWord -> "호출 대기"
        voice.conversationEnabled -> "음성 대화 연결 중"
        else -> "음성 대화 대기"
    }
    Box(modifier.testTag("voiceSessionStatus"), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 400.dp).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = WorkspaceDesign.BlueWeak,
                shape = RoundedCornerShape(50),
            ) {
                Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
                    WorkspaceIcon(WorkspaceSymbol.MIC, Modifier.size(34.dp), WorkspaceDesign.Blue)
                }
            }
            Column(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(status, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    voice.error ?: voice.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (voice.error != null) MaterialTheme.colorScheme.error else WorkspaceDesign.Muted,
                    textAlign = TextAlign.Center,
                )
            }
            if (voice.lastHeard.isNotBlank()) {
                Text(voice.lastHeard, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 3)
            }
        }
    }
}
@Composable private fun WorkspaceQuestion(message:ChatMessageUi){
    Box(Modifier.padding(top=20.dp,bottom=12.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){Surface(color=Color(0xFFE8F2FF),shape=RoundedCornerShape(topStart=17.dp,topEnd=17.dp,bottomStart=17.dp,bottomEnd=4.dp),modifier=Modifier.widthIn(max=700.dp)){Text(message.text,fontSize=14.sp,lineHeight=24.sp,color=Color(0xFF316BB9),modifier=Modifier.padding(horizontal=19.dp,vertical=14.dp))}}
    }
}
@Composable private fun WorkspaceAnswerHeader(message:ChatMessageUi){
    val context=LocalContext.current
    var copied by remember(message.id,message.text){mutableStateOf(false)}
    val origin=when{message.answerStatus?.startsWith("conversation-")==true||message.answerStatus=="no-more"->"대화 이어가기";GeneralKnowledgeAnswer.isGeneral(message.text)->"일반 지식 답변";GeneralKnowledgeAnswer.containsGeneral(message.text)->"문서 · 일반 지식 답변";message.sourceIds.isEmpty()&&message.text.isNotBlank()->"문서 검색 안내";else->"문서 근거 답변"}
        Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
            if(message.pending)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
            Text(if(message.pending){if(message.text.isBlank())"문서 검색 중" else "$origin · 작성 중"}else origin,fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f).semantics{liveRegion=LiveRegionMode.Polite})
            if(!message.pending&&message.text.isNotBlank())TextButton(onClick={context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Field Guard 답변",message.text));copied=true},modifier=Modifier.testTag("copyAnswer:${message.id}").semantics{contentDescription="답변 복사"}){Text(if(copied)"복사됨" else "복사",fontSize=11.sp)}
        }
}
@Composable private fun WorkspaceAnswerFooter(message: ChatMessageUi, actions: FieldGuardActions) {
    if (message.sourceIds.isEmpty()) return
    Column(Modifier.padding(vertical = 12.dp)) {
        WorkspaceListGroup("참조한 문서") {
            message.sourceIds.forEachIndexed { index, id ->
                if (index > 0) WorkspaceRowDivider()
                WorkspaceActionRow(WorkspaceSymbol.BOOK, message.sourceLabels[id] ?: "근거 ${index + 1} 원문 보기") {
                    actions.onOpenSource(id)
                }
            }
        }
    }
}

@Composable internal fun WorkspaceHistoryRow(session:ChatHistoryStore.Session,actions:FieldGuardActions){
    Row(Modifier.fillMaxWidth().heightIn(min=84.dp).clickable{actions.onLoadChat(session.id)},horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically){
        WorkspaceTile(WorkspaceSymbol.CHAT)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Text(session.title,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
            Text("${SimpleDateFormat("M월 d일 a h:mm",Locale.KOREAN).format(Date(session.updatedAtMillis))} · 메시지 ${session.turns.size}개",fontSize=11.sp,color=Color(0xFF8B95A1))
        }
        WorkspaceIcon(WorkspaceSymbol.CHEVRON,Modifier.size(16.dp),Color(0xFFA6B0BF))
    }
}
@Composable internal fun WorkspaceHistory(state:FieldGuardUiState,actions:FieldGuardActions){
    WorkspacePage{
        WorkspaceHeading("저장된 대화",action={WorkspaceButton("새 대화",actions.onNewChat,icon=WorkspaceSymbol.PLUS)})
        if(state.histories.isEmpty())WorkspaceEmpty(WorkspaceSymbol.HISTORY,"아직 저장된 대화가 없어요","문서에 질문하면 답변과 출처를 이곳에서 다시 볼 수 있어요."){WorkspaceButton("첫 질문하기",actions.onNewChat)}
        else WorkspaceCard{state.histories.forEachIndexed{index,session->WorkspaceHistoryRow(session,actions);if(index<state.histories.lastIndex)HorizontalDivider(color=WorkspaceDesign.Border)}}
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkspaceLibrary(state: FieldGuardUiState, actions: FieldGuardActions) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    WorkspacePage {
        WorkspaceStatusHeading("문서 보관함", state.documents.takeIf { it.isNotEmpty() }?.let { "오프라인 문서 ${it.size}종" })
        WorkspaceSearch(state.libraryQuery, actions.onLibraryQuery)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkspaceFilter("전체 문서", filter == null) { filter = null }
            state.documents.forEach { document ->
                WorkspaceFilter(if (document.id.contains("2022")) "안전작업 가이드" else "자율점검표", filter == document.id) {
                    filter = document.id
                }
            }
        }
        if (state.libraryQuery.isNotBlank()) {
            val results = state.libraryResults.filter { filter == null || it.documentId == filter }
            if (results.isEmpty()) {
                WorkspaceEmpty(WorkspaceSymbol.SEARCH, "검색 결과 없음", "검색어를 바꾸거나 문서 목록에서 원문을 열어보세요.")
                WorkspaceButton("문서 목록 보기", { filter = null; actions.onLibraryQuery("") }, primary = false)
            } else WorkspaceListGroup("검색 결과 ${results.size}건") {
                results.forEachIndexed { index, source ->
                    if (index > 0) WorkspaceRowDivider()
                    WorkspaceActionRow(WorkspaceSymbol.BOOK, source.heading, "${source.documentTitle} · ${source.pageNumber}쪽") {
                        actions.onOpenSource(source.id)
                    }
                }
            }
        } else {
            val documents = state.documents.filter { filter == null || it.id == filter }
            if (documents.isEmpty()) WorkspaceEmpty(WorkspaceSymbol.BOOK, "문서 준비 중", state.knowledgeStatus)
            else WorkspaceListGroup("오프라인 문서") {
                documents.forEachIndexed { index, document ->
                    if (index > 0) WorkspaceRowDivider()
                    FieldWorkspaceDocument(document, actions)
                }
            }
        }
    }
}

@Composable internal fun WorkspaceLibraryAndSource(state: FieldGuardUiState, actions: FieldGuardActions, showSource: Boolean) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(.42f)) { WorkspaceLibrary(state, actions) }
        VerticalDivider(color = WorkspaceDesign.Border)
        Box(Modifier.weight(.58f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (showSource) WorkspaceSource(state, actions, showBack = false)
            else Box(Modifier.padding(32.dp)) {
                WorkspaceEmpty(WorkspaceSymbol.BOOK, "원문 보기", "목록에서 문서나 검색 결과를 선택하세요.")
            }
        }
    }
}

@Composable internal fun WorkspaceFilter(label:String,selected:Boolean,click:()->Unit){
    Surface(color=if(selected)WorkspaceDesign.BlueWeak else Color.White,shape=RoundedCornerShape(99.dp),border=BorderStroke(1.dp,if(selected)Color(0xFFCBDFFF) else WorkspaceDesign.Border),modifier=Modifier.clickable(onClick=click)){
        Text(label,fontSize=12.sp,color=if(selected)WorkspaceDesign.Blue else Color(0xFF8B95A1),modifier=Modifier.padding(horizontal=16.dp,vertical=13.dp))
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkspaceSource(state:FieldGuardUiState,actions:FieldGuardActions,showBack:Boolean=true){
    val source=state.selectedSource
    var mode by remember(source?.id){mutableStateOf("PDF 원본")}
    WorkspacePage{
        if (showBack) TextButton(onClick={actions.onNavigate(state.sourceReturnDestination)}){Text("← 문서로 돌아가기",fontSize=12.sp,color=WorkspaceDesign.Muted)}
        WorkspaceHeading(source?.documentTitle?:"근거 문서",source?.let{"${it.pageNumber}쪽 · ${it.heading}"})
        if(source==null)WorkspaceNotice("열 문서를 찾지 못했어요.",true)
        else{
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("PDF 원본","읽기 편한 보기","추출 텍스트").forEach{label->WorkspaceFilter(label,mode==label){mode=label}}
            }
            if(mode=="PDF 원본")WorkspaceCard(color=Color(0xFFEEF1F6)){SourcePdfPage(source)}
            else WorkspaceCard{if(mode=="추출 텍스트")Text(source.body,fontSize=14.sp,lineHeight=25.sp) else MarkdownText(SourceDocumentFormatter.format(source.body))}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                WorkspaceButton("← 이전 부분",{actions.onAdjacentSource(-1)},Modifier.weight(1f),primary=false)
                WorkspaceButton("다음 부분 →",{actions.onAdjacentSource(1)},Modifier.weight(1f),primary=false)
            }
            WorkspaceNotice("PDF 원본에서 그림과 표를 확인할 수 있어요. 추출 텍스트는 검색과 답변에 사용된 내용을 보여줍니다.")
        }
    }
}
