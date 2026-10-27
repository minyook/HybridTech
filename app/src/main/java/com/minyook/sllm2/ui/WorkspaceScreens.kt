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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minyook.sllm2.data.ChatHistoryStore
import com.minyook.sllm2.model.ModelPhase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkspaceChat(state:FieldGuardUiState,voice:VoiceUiState,actions:FieldGuardActions,imeVisible:Boolean){
    val list=rememberLazyListState()
    val scope=rememberCoroutineScope()
    var follow by remember(state.activeChatId){mutableStateOf(true)}
    val messages=if(state.activeChatId!=null||state.messages.size>1)state.messages else emptyList()
    LaunchedEffect(list){snapshotFlow{list.isScrollInProgress to list.canScrollForward}.collectLatest{(moving,forward)->if(moving)follow=false else if(!forward)follow=true}}
    LaunchedEffect(messages.size,messages.lastOrNull()?.text,imeVisible){if(follow&&messages.isNotEmpty())list.scrollToItem(messages.size)}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){
        Column(Modifier.widthIn(max=1260.dp).fillMaxSize().padding(horizontal=workspacePadding()).padding(bottom=if(imeVisible)8.dp else 0.dp)){
            LazyColumn(state=list,modifier=Modifier.weight(1f).testTag("conversationList"),verticalArrangement=Arrangement.spacedBy(if(imeVisible)12.dp else 24.dp),contentPadding=PaddingValues(top=if(imeVisible)12.dp else 26.dp,bottom=if(imeVisible)12.dp else 24.dp)){
                item(key="heading"){
                    Column(verticalArrangement=Arrangement.spacedBy(18.dp)){
                        if(!imeVisible)WorkspaceHeading("LOCAL DOCUMENT ASSISTANT","현장에서 궁금한 점을 물어보세요","문서에서 근거를 찾고, 같은 쪽의 원문까지 연결해요.",action={WorkspaceButton("새 대화",actions.onNewChat,primary=false,icon=WorkspaceSymbol.PLUS,enabled=!state.isAnswering)})
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                            WorkspaceBadge("● 문서 ${state.documents.sumOf{it.chunkCount}}개 쪽",green=true)
                            WorkspaceBadge(if(state.modelStatus.phase==ModelPhase.READY)"Gemma 모델 저장됨" else "문서 원문 검색 모드")
                            TextButton(onClick={actions.onNavigate(AppDestination.SETTINGS)}){Text("AI 설정 →",fontSize=11.sp,color=WorkspaceDesign.Blue)}
                        }
                    }
                }
                if(messages.isEmpty()&&imeVisible)item(key="inputHint"){
                    Text("궁금한 내용을 입력해 주세요.",fontSize=14.sp,lineHeight=22.sp,color=Color(0xFF8B95A1),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(vertical=16.dp).testTag("chatInputHint"))
                }
                if(messages.isEmpty()&&!imeVisible)item(key="empty"){
                    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)){
                        Surface(color=WorkspaceDesign.BlueWeak,shape=RoundedCornerShape(22.dp)){Box(Modifier.size(66.dp),contentAlignment=Alignment.Center){WorkspaceIcon(WorkspaceSymbol.CHAT,Modifier.size(32.dp),WorkspaceDesign.Blue)}}
                        Text("질문은 편하게,\n근거는 정확하게.",fontSize=26.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                        Text("작업 절차, 측정방법, 점검 항목을 물어보세요.\n검색된 내용의 문서명과 쪽수를 함께 보여드려요.",fontSize=13.sp,lineHeight=22.sp,color=Color(0xFF8B95A1),textAlign=TextAlign.Center)
                        FlowRow(modifier=Modifier.widthIn(max=650.dp).padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            workspaceQuestions.forEach{prompt->Surface(color=Color.White,shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Color(0xFFE1E9F4)),modifier=Modifier.clickable{actions.onPreset(prompt)}){
                                Row(Modifier.padding(13.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically){Text(prompt,fontSize=11.sp,color=Color(0xFF71819A));WorkspaceIcon(WorkspaceSymbol.ARROW,Modifier.size(14.dp))}
                            }}
                        }
                    }
                }
                items(messages,key={it.id}){message->
                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=820.dp).fillMaxWidth()){WorkspaceMessage(message,actions)}}
                }
            }
            if(!follow&&messages.isNotEmpty())TextButton(onClick={follow=true;scope.launch{list.scrollToItem(messages.size)}},modifier=Modifier.align(Alignment.CenterHorizontally).testTag("scrollLatest")){Text("최신 답변으로 이동 ↓",fontSize=11.sp)}
            if(state.inputError!=null)WorkspaceNotice(state.inputError,true)
            Surface(color=if(voice.conversationEnabled)WorkspaceDesign.BlueWeak else Color.White,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,WorkspaceDesign.Border),modifier=Modifier.fillMaxWidth().padding(bottom=8.dp).testTag("voiceConversationBar")) {
                Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(voice.status,fontSize=11.sp,lineHeight=17.sp,color=WorkspaceDesign.Ink,modifier=Modifier.weight(1f))
                        TextButton(onClick=if(voice.conversationEnabled)actions.onStopVoice else actions.onVoiceClick,enabled=voice.conversationEnabled||!state.isAnswering,modifier=Modifier.testTag("toggleVoiceConversation")){Text(if(voice.conversationEnabled)"대화 종료" else "음성 대화 시작",fontSize=11.sp)}
                        TextButton(onClick={actions.onNavigate(AppDestination.VOICE)},modifier=Modifier.testTag("voiceSettings")){Text("설정",fontSize=11.sp)}
                    }
                    if(voice.conversationEnabled&&voice.lastHeard.isNotBlank())Text("인식: "+voice.lastHeard,fontSize=10.sp,color=Color(0xFF71819A),maxLines=2)
                }
            }
            Surface(color=Color.White,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFFDCE5F1)),modifier=Modifier.fillMaxWidth().testTag("chatComposer")){
                Row(Modifier.padding(start=20.dp,end=14.dp,top=12.dp,bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                    BasicTextField(enabled=!voice.conversationEnabled,value=state.input,onValueChange=actions.onInputChange,maxLines=4,textStyle=TextStyle(fontSize=14.sp,lineHeight=24.sp,color=Color(0xFF303D50)),modifier=Modifier.weight(1f).heightIn(min=if(imeVisible)24.dp else 46.dp,max=if(imeVisible)96.dp else 130.dp).testTag("chatInput"),decorationBox={inner->if(state.input.isEmpty())Text("궁금한 절차나 기준을 입력해 주세요",fontSize=14.sp,color=Color(0xFFA1ACBB));inner()})
                    IconButton(onClick={if(voice.conversationEnabled)actions.onStopVoice() else actions.onVoiceClick()},enabled=voice.conversationEnabled||!state.isAnswering,modifier=Modifier.size(37.dp).testTag("voiceQuestion").semantics{contentDescription="음성 질문"}){TossMicrophoneIcon()}
                    Surface(color=if(state.isAnswering||state.input.isBlank())Color(0xFFDEE7F4) else WorkspaceDesign.Blue,shape=RoundedCornerShape(12.dp),modifier=Modifier.size(41.dp).clickable(enabled=!voice.conversationEnabled&&!state.isAnswering&&state.input.isNotBlank()){actions.onSubmit()}.testTag("sendMessage").semantics{contentDescription="메시지 전송"}){
                        Box(contentAlignment=Alignment.Center){WorkspaceIcon(WorkspaceSymbol.ARROW,Modifier.size(21.dp).graphicsLayer{rotationZ=-90f},if(state.isAnswering||state.input.isBlank())Color(0xFFA9B9D1) else Color.White)}
                    }
                }
            }
            if(!imeVisible)Text("생성 답변의 수치와 조건은 원문에서 확인해 주세요.",fontSize=9.sp,color=Color(0xFFA0AABA),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(top=10.dp,bottom=18.dp))
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun WorkspaceMessage(message:ChatMessageUi,actions:FieldGuardActions){
    if(message.fromWorker){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){Surface(color=Color(0xFFE8F2FF),shape=RoundedCornerShape(topStart=17.dp,topEnd=17.dp,bottomStart=17.dp,bottomEnd=4.dp),modifier=Modifier.widthIn(max=700.dp)){Text(message.text,fontSize=14.sp,lineHeight=24.sp,color=Color(0xFF316BB9),modifier=Modifier.padding(horizontal=19.dp,vertical=14.dp))}}
    }else Column(Modifier.fillMaxWidth().padding(top=2.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
            if(message.pending)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
            else Surface(color=WorkspaceDesign.Blue,shape=RoundedCornerShape(9.dp)){Text("O₂",color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(6.dp))}
            Text(if(message.pending){if(message.text.isBlank())"문서 근거를 확인하고 있어요" else "Gemma가 답변을 작성하고 있어요"}else "문서 근거 답변",fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            if(!message.pending)Text("기기 저장됨",fontSize=10.sp,color=Color(0xFF9BA5B3))
        }
        if(message.text.isNotBlank())MarkdownText(message.text)
        if(!message.pending&&message.sourceIds.isNotEmpty()){
            HorizontalDivider(color=WorkspaceDesign.Border)
            Text("참조한 문서",fontSize=11.sp,color=WorkspaceDesign.Muted)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                message.sourceIds.forEachIndexed{index,id->
                    Surface(color=Color.White,shape=RoundedCornerShape(11.dp),border=BorderStroke(1.dp,Color(0xFFE2EAF5)),modifier=Modifier.clickable{actions.onOpenSource(id)}){
                        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically){WorkspaceBadge("${index+1}",blue=true);Text("근거 ${index+1} 원문 보기",fontSize=11.sp,color=Color(0xFF526D93));WorkspaceIcon(WorkspaceSymbol.CHEVRON,Modifier.size(14.dp))}
                    }
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
        WorkspaceHeading("SAVED ON THIS DEVICE","이전에 확인한 내용을 다시 봐요","대화 기록은 현재 기기에 저장돼요.",action={WorkspaceButton("새 대화",actions.onNewChat,icon=WorkspaceSymbol.PLUS)})
        if(state.histories.isEmpty())WorkspaceEmpty(WorkspaceSymbol.HISTORY,"아직 저장된 대화가 없어요","문서에 질문하면 답변과 출처를 이곳에서 다시 볼 수 있어요."){WorkspaceButton("첫 질문하기",actions.onNewChat)}
        else WorkspaceCard{state.histories.forEachIndexed{index,session->WorkspaceHistoryRow(session,actions);if(index<state.histories.lastIndex)HorizontalDivider(color=WorkspaceDesign.Border)}}
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkspaceLibrary(state:FieldGuardUiState,actions:FieldGuardActions){
    var filter by remember{mutableStateOf<String?>(null)}
    WorkspacePage{
        WorkspaceHeading("DOCUMENT LIBRARY","필요한 근거를 바로 찾아요","같은 문서 ${state.documents.size}종, ${state.documents.sumOf{it.chunkCount}}개 페이지를 사용해요.")
        WorkspaceSearch(state.libraryQuery,actions.onLibraryQuery)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            WorkspaceFilter("전체 문서",filter==null){filter=null}
            state.documents.forEach{document->WorkspaceFilter(if(document.id.contains("2022"))"안전작업 가이드" else "자율점검표",filter==document.id){filter=document.id}}
        }
        if(state.libraryQuery.isNotBlank()){
            val results=state.libraryResults.filter{filter==null||it.documentId==filter}
            Text("검색 결과 ${results.size}",fontSize=18.sp,fontWeight=FontWeight.Bold)
            if(results.isEmpty())WorkspaceEmpty(WorkspaceSymbol.SEARCH,"일치하는 근거가 없어요","다른 용어로 검색하거나 문서를 직접 열어 주세요.")
            results.forEach{source->WorkspaceCard(Modifier.clickable{actions.onOpenSource(source.id)}){
                WorkspaceBadge("${source.documentTitle} · ${source.pageNumber}쪽",blue=true);Text(source.heading,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(source.body.take(230),fontSize=13.sp,lineHeight=23.sp,color=WorkspaceDesign.Muted);Text("원문 확인 →",color=WorkspaceDesign.Blue,fontSize=12.sp)
            }}
        }else{
            val documents=state.documents.withIndex().filter{filter==null||it.value.id==filter}
            if(!workspaceMobile())documents.chunked(2).forEach{pair->Row(horizontalArrangement=Arrangement.spacedBy(22.dp)){pair.forEach{(index,document)->FieldWorkspaceDocument(document,index,actions,Modifier.weight(1f))};if(pair.size==1)Spacer(Modifier.weight(1f))}}
            else documents.forEach{(index,document)->FieldWorkspaceDocument(document,index,actions)}
        }
    }
}
@Composable internal fun WorkspaceFilter(label:String,selected:Boolean,click:()->Unit){
    Surface(color=if(selected)WorkspaceDesign.BlueWeak else Color.White,shape=RoundedCornerShape(99.dp),border=BorderStroke(1.dp,if(selected)Color(0xFFCBDFFF) else WorkspaceDesign.Border),modifier=Modifier.clickable(onClick=click)){
        Text(label,fontSize=12.sp,color=if(selected)WorkspaceDesign.Blue else Color(0xFF8B95A1),modifier=Modifier.padding(horizontal=16.dp,vertical=13.dp))
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkspaceSource(state:FieldGuardUiState,actions:FieldGuardActions){
    val source=state.selectedSource
    var mode by remember(source?.id){mutableStateOf("PDF 원본")}
    WorkspacePage{
        TextButton(onClick={actions.onNavigate(state.sourceReturnDestination)}){Text("← 문서로 돌아가기",fontSize=12.sp,color=WorkspaceDesign.Muted)}
        WorkspaceHeading("SOURCE DOCUMENT",source?.documentTitle?:"근거 문서",source?.let{"${it.pageNumber}쪽 · ${it.heading}"})
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
