package com.minyook.sllm2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minyook.sllm2.model.ContextTokenPolicy
import com.minyook.sllm2.model.InferenceBackend
import com.minyook.sllm2.model.ModelPhase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val workspaceNavigation = listOf(
    AppDestination.HOME to "홈", AppDestination.CHAT to "문서 질문",
    AppDestination.LIBRARY to "문서 보관함", AppDestination.CHECKLIST to "작업 전 점검",
    AppDestination.HISTORY to "대화 기록", AppDestination.SETTINGS to "설정",
)
internal fun workspaceSection(destination: AppDestination) = when(destination) {
    AppDestination.MODEL, AppDestination.INFERENCE, AppDestination.DATA, AppDestination.SYSTEM, AppDestination.WORK_SERVER -> AppDestination.SETTINGS
    AppDestination.SUPERVISOR, AppDestination.LOCAL_CHECKLIST -> AppDestination.CHECKLIST
    AppDestination.VOICE -> AppDestination.CHAT
    else -> destination
}
@Composable internal fun FieldWorkspaceSidebar(destination: AppDestination, navigate: (AppDestination) -> Unit) {
    val compact = LocalConfiguration.current.screenWidthDp <= 1150
    Column(Modifier.width(if(compact && workspaceWide()) 216.dp else WorkspaceDesign.SidebarWidth.dp).fillMaxHeight().background(Color.White).statusBarsPadding().padding(horizontal=18.dp,vertical=26.dp)) {
        Row(Modifier.fillMaxWidth().clickable{navigate(AppDestination.HOME)}.padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(11.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(color=WorkspaceDesign.Blue,shape=RoundedCornerShape(14.dp)) { Box(Modifier.size(42.dp,44.dp),contentAlignment=Alignment.Center){Text("O₂",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.ExtraBold)} }
            Column{Text("Field Guard",fontWeight=FontWeight.Bold,fontSize=19.sp,letterSpacing=(-.76).sp);Text("HYBRIDTECH",fontSize=9.sp,color=Color(0xFF8B95A1),letterSpacing=1.26.sp)}
        }
        Spacer(Modifier.height(44.dp))
        Text("WORKSPACE",fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color(0xFF8B95A1),letterSpacing=1.2.sp,modifier=Modifier.padding(horizontal=14.dp,vertical=14.dp))
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            workspaceNavigation.forEach{(target,title)->
                val selected=workspaceSection(destination)==target
                Surface(color=if(selected)WorkspaceDesign.BlueWeak else Color.White,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().height(50.dp).clickable{navigate(target)}.testTag("workspace-${target.name}")) {
                    Row(Modifier.padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        TossNavigationIcon(target,if(selected)WorkspaceDesign.Blue else Color(0xFF8B95A1))
                        Text(title,modifier=Modifier.weight(1f),fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=if(selected)WorkspaceDesign.Blue else Color(0xFF626E7D))
                        if(selected)Surface(color=WorkspaceDesign.Blue,shape=RoundedCornerShape(3.dp),modifier=Modifier.size(5.dp)){}
                    }
                }
            }
        }
        TextButton(onClick={navigate(AppDestination.SENSOR)},modifier=Modifier.padding(top=12.dp).testTag("workspace-SENSOR")){Text("가스 측정 · 기기 기능",fontSize=11.sp,color=WorkspaceDesign.Muted)}
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            WorkspaceIcon(WorkspaceSymbol.SHIELD,color=Color(0xFF8B95A1))
            Text("현장 지식은 내 기기에",fontSize=13.sp,fontWeight=FontWeight.Bold)
            Text("문서와 AI는 기기 안에서,\n작업 승인은 담당자 확인으로.",fontSize=12.sp,lineHeight=20.sp,color=WorkspaceDesign.Muted)
            WorkspaceBadge("기기 내 문서",green=true)
            Text("O₂ Field Guard · Android",fontSize=10.sp,color=Color(0xFF8B95A1),modifier=Modifier.padding(top=12.dp))
        }
    }
}
@Composable internal fun FieldWorkspaceHome(state:FieldGuardUiState,actions:FieldGuardActions) {
    val mobile=workspaceMobile()
    WorkspacePage {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("HYBRIDTECH · FIELD WORKSPACE",fontSize=if(mobile)8.sp else 10.sp,fontWeight=FontWeight.Bold,letterSpacing=.6.sp,color=Color(0xFF8B95A1))
            Text(SimpleDateFormat("M월 d일 EEEE",Locale.KOREAN).format(Date()),fontSize=if(mobile)8.sp else 10.sp,color=Color(0xFF8B95A1))
        }
        Surface(color=Color.White,border=BorderStroke(1.dp,WorkspaceDesign.Border),shape=RoundedCornerShape(24.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min=if(mobile)320.dp else 300.dp).padding(if(mobile)24.dp else 38.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("현장의 질문에, 문서로 답해요",color=WorkspaceDesign.Blue,fontSize=11.sp,fontWeight=FontWeight.Bold)
                        Text("안전한 작업의 시작,\n근거부터 확인하세요.",fontSize=if(mobile)28.sp else 39.sp,lineHeight=if(mobile)38.sp else 53.sp,letterSpacing=(-1.5).sp,fontWeight=FontWeight.Bold)
                        Text("밀폐공간 안전 문서를 내 기기에서 바로 검색하고\n필요한 절차와 기준을 원문으로 확인해요.",fontSize=if(mobile)12.sp else 14.sp,lineHeight=24.sp,color=Color(0xFF7A8594))
                        WorkspaceButton("문서에 질문하기",actions.onNewChat,Modifier.padding(top=8.dp).testTag("homeAsk"),icon=WorkspaceSymbol.ARROW)
                    }
                    if(!mobile)FieldWorkspaceShield(Modifier.padding(start=18.dp).size(220.dp))
                }
                if(mobile)FieldWorkspaceShield(Modifier.size(if(LocalConfiguration.current.screenWidthDp<=360)64.dp else 84.dp).align(Alignment.BottomEnd))
            }
        }
        WorkspaceCard {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                WorkspaceReadiness("문서 검색",if(state.documents.isEmpty())"준비 중" else "사용 가능",WorkspaceSymbol.SEARCH)
                WorkspaceReadiness("PDF 원본","준비됨",WorkspaceSymbol.BOOK)
                WorkspaceReadiness("기기 내 AI",if(state.modelStatus.phase==ModelPhase.READY)"모델 저장됨" else "준비하기",WorkspaceSymbol.CPU){actions.onNavigate(AppDestination.SETTINGS)}
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("오늘의 현장 준비",fontSize=18.sp,fontWeight=FontWeight.Bold);Text("한 번 더 확인해요",fontSize=11.sp,color=Color(0xFF8B95A1))}
        if(!mobile)Row(horizontalArrangement=Arrangement.spacedBy(15.dp)){(0..2).forEach{WorkspaceQuickCard(it,state,actions,Modifier.weight(1f))}}
        else (0..2).forEach{WorkspaceQuickCard(it,state,actions)}
        if(!mobile)Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){WorkspaceQuestions(actions,Modifier.weight(1.55f));WorkspaceAndroidCard(state,actions,Modifier.weight(1f))}
        else {WorkspaceQuestions(actions);WorkspaceAndroidCard(state,actions)}
        if(state.histories.isNotEmpty()){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("최근 확인한 질문",fontSize=18.sp,fontWeight=FontWeight.Bold);TextButton(onClick={actions.onNavigate(AppDestination.HISTORY)}){Text("전체 보기 →",fontSize=12.sp)}}
            WorkspaceCard{state.histories.take(3).forEach{WorkspaceHistoryRow(it,actions)}}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){WorkspaceIcon(WorkspaceSymbol.SHIELD,Modifier.size(13.dp));Text("질문과 답변은 이 기기에 보관해요.",fontSize=10.sp,color=Color(0xFF9BA5B3))}
    }
}
@Composable private fun WorkspaceReadiness(title:String,value:String,symbol:WorkspaceSymbol,click:()->Unit={}) {
    Column(Modifier.clickable(onClick=click),verticalArrangement=Arrangement.spacedBy(6.dp)){Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically){WorkspaceIcon(symbol,Modifier.size(15.dp));Text(title,fontSize=10.sp,color=WorkspaceDesign.Muted)};Text(value,fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF4E5968))}
}
@Composable private fun WorkspaceQuickCard(index:Int,state:FieldGuardUiState,actions:FieldGuardActions,modifier:Modifier=Modifier) {
    val titles=listOf("작업 전 점검","문서 보관함","오프라인 준비")
    val descriptions=listOf("요청·감독관 점검·승인부터\n투입과 퇴장까지 확인해요","안전작업 가이드와 점검표를\n원본 그대로 확인해요","통신이 어려운 현장에서도\n필요한 문서를 열어요")
    val symbols=listOf(WorkspaceSymbol.CHECK,WorkspaceSymbol.BOOK,WorkspaceSymbol.DOWNLOAD)
    val targets=listOf(AppDestination.CHECKLIST,AppDestination.LIBRARY,AppDestination.SETTINGS)
    val colors=listOf(WorkspaceDesign.Blue,Color(0xFF229575),Color(0xFF8B72DB))
    val backgrounds=listOf(WorkspaceDesign.BlueWeak,Color(0xFFE9F7F2),Color(0xFFF0EDFF))
    val notes=listOf("작업자 요청 · 감독관 승인","문서 ${state.documents.size}종 · ${state.documents.sumOf{it.chunkCount}}개 근거","현장에 가기 전 준비하기")
    WorkspaceCard(modifier.clickable{actions.onNavigate(targets[index])}) {
        if(workspaceMobile())Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically){
            WorkspaceTile(symbols[index],colors[index],backgrounds[index]);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(titles[index],fontSize=16.sp,fontWeight=FontWeight.Bold);Text(descriptions[index],fontSize=12.sp,lineHeight=20.sp,color=Color(0xFF8B95A1))};WorkspaceIcon(WorkspaceSymbol.ARROW,Modifier.size(16.dp))
        }else{
            WorkspaceTile(symbols[index],colors[index],backgrounds[index]);Text(titles[index],fontSize=16.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=3.dp));Text(descriptions[index],fontSize=12.sp,lineHeight=20.sp,color=Color(0xFF8B95A1))
            Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(notes[index],fontSize=10.sp,color=Color(0xFF637083));WorkspaceIcon(WorkspaceSymbol.ARROW,Modifier.size(15.dp))}
        }
    }
}
@Composable private fun WorkspaceQuestions(actions:FieldGuardActions,modifier:Modifier=Modifier){
    WorkspaceCard(modifier){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("이렇게 질문해 보세요",fontSize=18.sp,fontWeight=FontWeight.Bold);WorkspaceIcon(WorkspaceSymbol.CHAT)}
        workspaceQuestions.forEachIndexed{index,question->
            Row(Modifier.fillMaxWidth().heightIn(min=59.dp).clickable{actions.onPreset(question)},horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically){Text("0${index+1}",fontSize=10.sp,color=Color(0xFFA9B4C3));Text(question,fontSize=12.sp,modifier=Modifier.weight(1f));WorkspaceIcon(WorkspaceSymbol.CHEVRON,Modifier.size(15.dp))}
            if(index<2)HorizontalDivider(color=Color(0xFFF0F3F7))
        }
    }
}
@Composable private fun WorkspaceAndroidCard(state:FieldGuardUiState,actions:FieldGuardActions,modifier:Modifier=Modifier){
    WorkspaceCard(modifier.clickable{actions.onNavigate(AppDestination.SENSOR)},Color(0xFFF1F5FA)){
        WorkspaceBadge("ANDROID APP");Text("가스 측정과 현장 경보",fontSize=20.sp,fontWeight=FontWeight.Bold)
        Text("측정기 연결과 수신 상태를\n설치형 앱에서 확인해요.",fontSize=12.sp,lineHeight=20.sp,color=WorkspaceDesign.Muted)
        Text(state.gasStatus,fontSize=11.sp,lineHeight=18.sp,color=WorkspaceDesign.Muted);Text("가스 측정 열기 →",fontSize=12.sp,color=WorkspaceDesign.Blue)
    }
}
@Composable internal fun FieldWorkspaceShield(modifier:Modifier=Modifier){
    Box(modifier,contentAlignment=Alignment.Center){
        Canvas(Modifier.fillMaxSize()){
            drawCircle(Color(0xFFF5F9FF),radius=size.minDimension*.49f);drawCircle(Color(0xFFDFEBFC),radius=size.minDimension*.4f,style=Stroke(1.dp.toPx()))
            val w=size.width;val h=size.height
            drawPath(Path().apply{moveTo(w*.5f,h*.22f);lineTo(w*.73f,h*.31f);lineTo(w*.71f,h*.55f);quadraticTo(w*.7f,h*.69f,w*.5f,h*.79f);quadraticTo(w*.3f,h*.69f,w*.29f,h*.55f);lineTo(w*.27f,h*.31f);close()},Color(0xFF8BB8FF),style=Stroke(2.dp.toPx()))
            drawPath(Path().apply{moveTo(w*.38f,h*.48f);lineTo(w*.47f,h*.57f);lineTo(w*.63f,h*.41f)},WorkspaceDesign.Blue,style=Stroke(2.dp.toPx()))
        }
        if(!workspaceMobile())Surface(color=Color.White,shape=RoundedCornerShape(9.dp),border=BorderStroke(1.dp,WorkspaceDesign.Border),modifier=Modifier.align(Alignment.BottomCenter)){Text("●  ON-DEVICE KNOWLEDGE",fontSize=8.sp,color=Color(0xFF7186A3),modifier=Modifier.padding(11.dp))}
    }
}
@Composable internal fun FieldWorkspaceDocument(document:DocumentUi,index:Int,actions:FieldGuardActions,modifier:Modifier=Modifier){
    val checklist=document.id.contains("2026");val ink=if(checklist)Color(0xFF44866D) else Color(0xFF3674BF)
    WorkspaceCard(modifier){
        Surface(color=if(checklist)Color(0xFFEDF6F2) else Color(0xFFEDF4FF),border=BorderStroke(1.dp,if(checklist)Color(0xFFDFEEE6) else Color(0xFFE0EBFB)),shape=RoundedCornerShape(12.dp)){
            Column(Modifier.fillMaxWidth().heightIn(min=255.dp).padding(25.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
                Text("MINISTRY OF EMPLOYMENT AND LABOR",fontSize=7.sp,color=ink.copy(alpha=.7f),letterSpacing=.56.sp)
                WorkspaceIcon(if(checklist)WorkspaceSymbol.CHECK else WorkspaceSymbol.SHIELD,Modifier.padding(top=10.dp).size(36.dp),ink)
                Text(if(checklist)"밀폐공간\n자율점검표" else "밀폐공간\n안전작업 가이드",fontSize=24.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold,color=ink)
                Text(if(checklist)"2026 · PDF" else "2022 · PDF",fontSize=10.sp,color=ink.copy(alpha=.7f))
            }
        }
        Text("DOCUMENT 0${index+1}",fontSize=9.sp,fontWeight=FontWeight.Bold,color=WorkspaceDesign.Blue,letterSpacing=.72.sp)
        Text(document.title,fontSize=18.sp,fontWeight=FontWeight.Bold);Text("${document.chunkCount}쪽 · 오프라인 PDF 준비됨",fontSize=12.sp,color=WorkspaceDesign.Muted)
        WorkspaceButton("문서 열기 →",{actions.onOpenDocument(document.id)},Modifier.fillMaxWidth(),primary=false)
    }
}
@Composable internal fun FieldWorkspaceSettings(state:FieldGuardUiState,actions:FieldGuardActions){
    WorkspacePage{
        WorkspaceHeading("YOUR WORKSPACE","현장에 가기 전, 준비해요","문서와 AI를 이 기기에 저장하면 인터넷 없이 사용할 수 있어요.")
        if(LocalConfiguration.current.screenWidthDp>1150)Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){WorkspaceOfflinePanel(state,actions,Modifier.weight(1f));WorkspaceModelPanel(state,actions,Modifier.weight(1f))}
        else{WorkspaceOfflinePanel(state,actions);WorkspaceModelPanel(state,actions)}
        WorkspaceTokenSettings(state,actions)
        WorkspaceCard{
            WorkspaceCardTitle(WorkspaceSymbol.HISTORY,"내 기록","대화와 이전 점검은 이 기기에, 새 작업·승인은 서버에 보관해요.")
            Text("저장된 대화 ${state.histories.size}개",fontSize=13.sp,color=WorkspaceDesign.Muted)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){WorkspaceButton("대화 기록",{actions.onNavigate(AppDestination.HISTORY)},primary=false);WorkspaceButton("기기 데이터",{actions.onNavigate(AppDestination.DATA)},primary=false)}
        }
        WorkspaceCard{
            Text("감독관 인증",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("오늘 전달받은 6자리 암호코드로 감독관 화면을 열어요.",fontSize=13.sp,color=WorkspaceDesign.Muted)
            WorkspaceButton("감독관 화면으로 전환",{actions.onNavigate(AppDestination.SUPERVISOR)},primary=false)
        }
        WorkspaceCard{
            WorkspaceCardTitle(WorkspaceSymbol.SETTINGS,"현장 작업 서버",state.workServerUrl.ifBlank{"앱과 웹에서 같은 작업 기록을 사용해요."})
            WorkspaceButton("서버 주소 설정",{actions.onNavigate(AppDestination.WORK_SERVER)},primary=false)
        }
        WorkspaceCard{
            WorkspaceCardTitle(WorkspaceSymbol.INFO,"Android 기기 기능","측정기 연결·음성 질문·위젯과 알림")
            TossRow("가스 측정","측정기 연결과 현장 경보",{actions.onNavigate(AppDestination.SENSOR)})
            TossRow("음성 질문","기기 내 음성 인식",{actions.onNavigate(AppDestination.VOICE)})
            TossRow("위젯과 알림","앱 밖에서 측정 상태 보기",{actions.onNavigate(AppDestination.SYSTEM)})
        }
    }
}
@Composable internal fun WorkspaceOfflinePanel(state:FieldGuardUiState,actions:FieldGuardActions,modifier:Modifier=Modifier){
    WorkspaceCard(modifier){
        WorkspaceCardTitle(WorkspaceSymbol.DOWNLOAD,"오프라인 준비","앱에 포함된 문서와 원본 PDF를 사용해요.")
        listOf("문서 검색" to "${state.documents.sumOf{it.chunkCount}}개 페이지의 검색 데이터","PDF 원본" to "문서 ${state.documents.size}종과 PDF 표시 기능","AI 실행 파일" to "Android 기기 내 LiteRT-LM").forEach{(title,description)->
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,fontSize=13.sp,fontWeight=FontWeight.SemiBold);Text(description,fontSize=10.sp,color=Color(0xFF8B95A1))};WorkspaceBadge("앱에 포함",green=true)
            };HorizontalDivider(color=WorkspaceDesign.Border)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){WorkspaceBadge("✓ 검색 준비됨",green=true);WorkspaceBadge("✓ PDF 준비됨",green=true)}
        WorkspaceButton("문서 보관함 열기",{actions.onNavigate(AppDestination.LIBRARY)},Modifier.fillMaxWidth())
    }
}
@Composable internal fun WorkspaceModelPanel(state:FieldGuardUiState,actions:FieldGuardActions,modifier:Modifier=Modifier){
    val phase=state.modelStatus.phase
    WorkspaceCard(modifier){
        WorkspaceCardTitle(WorkspaceSymbol.CPU,"기기 내 Gemma","질문과 답변은 기기 안에서 처리해요")
        WorkspaceBadge(when(phase){ModelPhase.READY->"모델 파일 저장됨";ModelPhase.DOWNLOADING->"모델 다운로드 중";ModelPhase.FAILED->"모델 준비 실패";else->"모델 다운로드 필요"},green=phase==ModelPhase.READY,blue=phase==ModelPhase.DOWNLOADING)
        WorkspaceNotice(when(phase){ModelPhase.READY->"저장된 모델은 질문할 때 앱에서 자동으로 준비해요.";ModelPhase.DOWNLOADING->"모델 다운로드 중 · ${state.modelProgress?.let{"$it%"}?:"크기 확인 중"}";ModelPhase.FAILED->state.modelStatus.failureMessage?:"다운로드를 다시 시도해 주세요.";else->"모델을 다운로드하면 기기 안에서 답변을 만들 수 있어요."},warning=phase==ModelPhase.FAILED)
        Text("모델 파일은 한 번 다운로드하면 이 기기에 저장돼요. 실행 준비에는 기기 메모리와 시간이 필요해요.",fontSize=12.sp,lineHeight=21.sp,color=Color(0xFF8B95A1))
        Surface(color=Color(0xFFF8FAFF),shape=RoundedCornerShape(13.dp)){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){WorkspaceIcon(WorkspaceSymbol.CPU);Text("Gemma 4 E2B",fontSize=14.sp,fontWeight=FontWeight.Bold)}
                WorkspaceBadge("Android용 · 2.59 GB",blue=true)
                Text("한 번 다운로드하면 인터넷 없이 사용할 수 있어요. 별도의 실행 버튼은 필요 없어요.",fontSize=12.sp,lineHeight=20.sp,color=WorkspaceDesign.Muted)
                WorkspaceButton(when(phase){ModelPhase.READY->"Gemma 4 E2B 다운로드 완료";ModelPhase.DOWNLOADING->"Gemma 4 E2B 다운로드 중";ModelPhase.FAILED->"Gemma 4 E2B 다시 다운로드";else->"Gemma 4 E2B 다운로드 받기"},actions.onDownloadModel,Modifier.fillMaxWidth().testTag("downloadGemma"),enabled=phase !in setOf(ModelPhase.READY,ModelPhase.DOWNLOADING),icon=WorkspaceSymbol.DOWNLOAD)
                if(phase==ModelPhase.DOWNLOADING)LinearProgressIndicator(progress={((state.modelProgress?:0)/100f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            }
        }
    }
}
@Composable internal fun WorkspaceTokenSettings(state:FieldGuardUiState,actions:FieldGuardActions){
    val limit=state.deviceProfile?.let(ContextTokenPolicy::deviceLimit)?:ContextTokenPolicy.MIN_CONTEXT_TOKENS
    WorkspaceCard{
        WorkspaceCardTitle(WorkspaceSymbol.SETTINGS,"AI 토큰 설정","답변 길이와 AI가 사용할 문맥 크기를 조절해요")
        val outputField: @Composable () -> Unit = { WorkspaceSelect("최대 답변 토큰",if(state.settings.automaticAnswerLength)"자동 · 문맥 한도 내" else "${state.settings.responseTokens} 토큰",listOf("자동 · 문맥 한도 내")+(listOf(128,256,512,1024,1536,2048,4096,8192)+state.settings.responseTokens).distinct().sorted().filter{it<=ContextTokenPolicy.responseLimit(state.settings.contextTokens)}.map{"$it 토큰"}){
            if(it.startsWith("자동"))actions.onAutomaticAnswerLength(true) else{actions.onAutomaticAnswerLength(false);actions.onResponseTokens(it.substringBefore(' ').toInt())}
        } }
        val contextField: @Composable () -> Unit = { WorkspaceSelect("문맥 토큰 한도","${state.settings.contextTokens} 토큰",(listOf(2048,4096,8192,16384,24576,32768)+state.settings.contextTokens).distinct().sorted().filter{it<=limit}.map{"$it 토큰"}){actions.onContextTokens(it.substringBefore(' ').toInt())} }
        if(workspaceMobile()) { outputField();contextField() }
        else Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) { Column(Modifier.weight(1f)){outputField()};Column(Modifier.weight(1f)){contextField()} }
        Text("한 번의 답변 길이와 질문·문서 근거에 사용할 공간이에요. 이 기기의 메모리 한도 안에서 선택할 수 있어요.",fontSize=11.sp,lineHeight=19.sp,color=Color(0xFF8B95A1))
        WorkspaceSelect("실행 방식",state.settings.backend.label,InferenceBackend.entries.map{it.label}){label->actions.onBackend(InferenceBackend.entries.first{it.label==label})}
        WorkspaceButton(if(state.settingsSaved)"토큰 설정 저장됨" else "토큰 설정 저장",actions.onSaveSettings,enabled=!state.isAnswering)
        Text("저장한 설정은 다음 질문부터 적용돼요. 기기 내 실행이므로 토큰별 API 요금은 없어요.",fontSize=11.sp,lineHeight=19.sp,color=Color(0xFF8B95A1))
    }
}
@Composable internal fun WorkspaceSelect(label:String,value:String,options:List<String>,choose:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF586577))
        Box{
            Surface(color=Color(0xFFFAFBFD),shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Color(0xFFE1E7EF)),modifier=Modifier.fillMaxWidth().clickable{expanded=true}.testTag("select-$label")){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(value,modifier=Modifier.weight(1f),fontSize=13.sp);Text("⌄",color=WorkspaceDesign.Muted)}
            }
            DropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(option,fontSize=13.sp)},onClick={choose(option);expanded=false})}}
        }
    }
}
