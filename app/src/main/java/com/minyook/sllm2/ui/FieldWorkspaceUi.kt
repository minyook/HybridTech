/* 소스 안내
 * 홈·사이드바·문서/설정 등의 공통 화면 구성. 상태는 읽고 기능 명령은 FieldGuardActions로 전달한다.
 * 연결 모듈: com.minyook.sllm2.model.ContextTokenPolicy, com.minyook.sllm2.model.InferenceBackend, com.minyook.sllm2.model.ModelPhase, com.minyook.sllm2.model.LocalModel.
 */
package com.minyook.sllm2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minyook.sllm2.gas.ApPhase
import com.minyook.sllm2.model.ContextTokenPolicy
import com.minyook.sllm2.model.ModelDownloadWorker
import com.minyook.sllm2.model.InferenceBackend
import com.minyook.sllm2.model.ModelPhase
import com.minyook.sllm2.model.LocalModel

// TossFieldGuardApp이 사용하는 내비게이션과 홈/보관함/설정 등 공통 화면 구성.
// 각 화면은 FieldGuardUiState를 읽고 FieldGuardActions로 이벤트를 전달한다. 실제 기능 처리는 ViewModel이 맡는다.
// 채팅 목록/입력은 WorkspaceScreens, 원문 렌더는 SourcePdfPage, 점검 업무는 NativeWorkPage에 분리되어 있다.
internal val workspaceNavigation = listOf(
    AppDestination.HOME to "홈", AppDestination.CHAT to "문서 질문",
    AppDestination.VOICE to "음성 대화",
    AppDestination.LIBRARY to "문서 보관함", AppDestination.CHECKLIST to "작업 전 점검",
    AppDestination.HISTORY to "대화 기록", AppDestination.SETTINGS to "설정",
)
internal fun workspaceSection(destination: AppDestination) = when(destination) {
    AppDestination.MODEL, AppDestination.INFERENCE, AppDestination.DATA, AppDestination.SYSTEM -> AppDestination.SETTINGS
    AppDestination.SUPERVISOR, AppDestination.LOCAL_CHECKLIST -> AppDestination.CHECKLIST
    else -> destination
}
@Composable internal fun FieldWorkspaceSidebar(destination: AppDestination, navigate: (AppDestination) -> Unit) {
    val compact = LocalConfiguration.current.screenWidthDp <= 1150
    Column(Modifier.width(if(compact && workspaceWide()) 216.dp else WorkspaceDesign.SidebarWidth.dp).fillMaxHeight().background(Color.White).statusBarsPadding().padding(horizontal=18.dp,vertical=26.dp)) {
        Row(Modifier.fillMaxWidth().clickable{navigate(AppDestination.HOME)}.padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(11.dp),verticalAlignment=Alignment.CenterVertically) {
            Surface(color=WorkspaceDesign.Blue,shape=RoundedCornerShape(14.dp)) { Box(Modifier.size(42.dp,44.dp),contentAlignment=Alignment.Center){Text("O₂",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.ExtraBold)} }
            Column{Text("Field Guard",fontWeight=FontWeight.Bold,fontSize=19.sp,letterSpacing=(-.76).sp)}
        }
        Spacer(Modifier.height(44.dp))
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
            Text("O₂ Field Guard · Android",fontSize=10.sp,color=Color(0xFF8B95A1),modifier=Modifier.padding(top=12.dp))
        }
    }
}
@Composable internal fun FieldWorkspaceHome(
    state: FieldGuardUiState,
    actions: FieldGuardActions,
    work: WorkScenarioState,
    openStep: (ScenarioStep?) -> Unit,
) {
    val meterStatus = workspaceMeterStatus(state)
    WorkspacePage {
        WorkspaceStatusHeading("밀폐공간 안전", meterStatus)
        HomeWorkScenario(state, work, openStep)
        WorkspaceListGroup("문서 확인") {
            WorkspaceActionRow(WorkspaceSymbol.CHAT, "문서에 질문하기", modifier = Modifier.testTag("homeAsk"), onClick = actions.onNewChat)
            WorkspaceRowDivider()
            WorkspaceActionRow(WorkspaceSymbol.MIC, "음성 대화") { actions.onNavigate(AppDestination.VOICE) }
            WorkspaceRowDivider()
            WorkspaceActionRow(WorkspaceSymbol.BOOK, "문서 보관함", workspaceDocumentStatus(state), Modifier.testTag("homeLibrary")) {
                actions.onNavigate(AppDestination.LIBRARY)
            }
        }
        if (state.histories.isNotEmpty()) {
            WorkspaceListGroup("최근 대화") {
                state.histories.take(3).forEachIndexed { index, session ->
                    if (index > 0) WorkspaceRowDivider()
                    WorkspaceActionRow(WorkspaceSymbol.HISTORY, session.title) { actions.onLoadChat(session.id) }
                }
                WorkspaceRowDivider()
                WorkspaceActionRow(WorkspaceSymbol.HISTORY, "전체 대화 기록") { actions.onNavigate(AppDestination.HISTORY) }
            }
        }
    }
}

@Composable private fun workspaceMeterStatus(state: FieldGuardUiState): String {
    val now = if (state.gasReading.hasValues) currentTimeTick() else 0L
    return when {
        state.wifiAp.phase == ApPhase.ERROR -> "측정기 연결 오류"
        state.gasReading.isFresh(now) ->
            if (state.wifiAp.selected?.test == true) "테스트 데이터 수신 중" else "측정값 수신 중"
        state.sensorConnectionLost || state.gasReading.hasValues -> "측정값 수신 끊김"
        state.wifiAp.phase == ApPhase.STARTING -> "연결 준비 중"
        state.wifiAp.phase == ApPhase.RUNNING -> "측정기 연결 대기"
        else -> "측정기 미연결"
    }
}

private fun workspaceDocumentStatus(state: FieldGuardUiState): String =
    if (state.documents.isEmpty()) "문서 준비 중" else "오프라인 문서 ${state.documents.size}종"

@Composable internal fun FieldWorkspaceDocument(document: DocumentUi, actions: FieldGuardActions) {
    WorkspaceActionRow(WorkspaceSymbol.BOOK, document.title, "${document.chunkCount}쪽 · PDF") {
        actions.onOpenDocument(document.id)
    }
}

@Composable internal fun FieldWorkspaceSettings(state: FieldGuardUiState, actions: FieldGuardActions) {
    WorkspacePage {
        WorkspaceStatusHeading("설정")
        WorkspaceModelPanel(state, actions)
        WorkspaceListGroup("기기 관리") {
            WorkspaceActionRow(WorkspaceSymbol.INFO, "기기 데이터") { actions.onNavigate(AppDestination.DATA) }
            WorkspaceRowDivider()
            WorkspaceActionRow(WorkspaceSymbol.HISTORY, "이전 기기 점검 기록") { actions.onNavigate(AppDestination.LOCAL_CHECKLIST) }
            WorkspaceRowDivider()
            WorkspaceActionRow(WorkspaceSymbol.INFO, "위젯과 알림") { actions.onNavigate(AppDestination.SYSTEM) }
        }
    }
}
@Composable internal fun WorkspaceModelPanel(state: FieldGuardUiState, actions: FieldGuardActions) {
    val phase = state.modelStatus.phase
    val model = LocalModel
    val uriHandler = LocalUriHandler.current
    var token by remember(model) { mutableStateOf("") }
    WorkspaceCard(Modifier.testTag("modelSettingsPanel")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WorkspaceTile(WorkspaceSymbol.CPU)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("기기 내 EXAONE", style = MaterialTheme.typography.headlineSmall)
                Text("${model.title} · ${model.sizeLabel}", style = MaterialTheme.typography.bodySmall, color = WorkspaceDesign.Muted)
            }
        }
        val status = when (phase) {
            ModelPhase.READY -> "모델 파일 저장됨"
            ModelPhase.DOWNLOADING -> "모델 다운로드 중"
            ModelPhase.FAILED -> "모델 준비 실패"
            ModelPhase.NOT_INSTALLED -> "모델 다운로드 필요"
        }
        WorkspaceBadge(status, green = phase == ModelPhase.READY, blue = phase == ModelPhase.DOWNLOADING)
        if (phase == ModelPhase.FAILED) {
            WorkspaceNotice(state.modelStatus.failureMessage ?: "다운로드를 다시 시도해 주세요.", warning = true)
        }
        if (phase == ModelPhase.FAILED && state.modelStatus.failureMessage == ModelDownloadWorker.AUTH_ERROR) {
            Text("모델 페이지에서 이용 약관에 동의한 계정의 Hugging Face 읽기 토큰을 입력하세요. 입력한 토큰은 기기에 암호화 저장돼요.", fontSize = 12.sp, lineHeight = 20.sp, color = WorkspaceDesign.Muted)
            TextButton(onClick = { uriHandler.openUri("https://huggingface.co/settings/tokens") }) { Text("읽기 토큰 발급 페이지 열기") }
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hugging Face 읽기 토큰") },
                placeholder = { Text("저장한 토큰이 있으면 비워두세요") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        when (phase) {
            ModelPhase.NOT_INSTALLED, ModelPhase.FAILED -> WorkspaceButton(
                label = if (phase == ModelPhase.FAILED) "모델 다시 다운로드" else "모델 다운로드",
                onClick = {
                    if (token.isBlank()) actions.onDownloadModel()
                    else {
                        actions.onDownloadWithToken(token)
                        token = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("downloadExaone"),
                icon = WorkspaceSymbol.DOWNLOAD,
            )
            ModelPhase.DOWNLOADING -> {
                val progress = state.modelProgress
                if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else {
                    Text("$progress%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { (progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ModelPhase.READY -> Unit
        }
        Surface(color = WorkspaceDesign.Canvas, shape = RoundedCornerShape(12.dp)) {
            WorkspaceActionRow(
                WorkspaceSymbol.SETTINGS,
                "실행 방식 및 토큰",
                "${state.settings.backend.label} · 문맥 ${state.settings.contextTokens} 토큰",
                Modifier.testTag("modelInferenceSettings"),
            ) { actions.onNavigate(AppDestination.INFERENCE) }
        }
        state.lastInference?.let { metrics ->
            WorkspaceNotice("최근 실행 · ${metrics.backend}" +
                if(metrics.fallbackReason.isBlank()) "" else "\n${metrics.fallbackReason}")
        }
        TextButton(onClick = { uriHandler.openUri(model.modelPage) }) { Text("모델 정보 및 이용 약관") }
    }
}
@Composable internal fun WorkspaceTokenSettings(state:FieldGuardUiState,actions:FieldGuardActions){
    val limit=minOf(LocalModel.contextLimit, state.deviceProfile?.let(ContextTokenPolicy::deviceLimit)?:ContextTokenPolicy.MIN_CONTEXT_TOKENS)
    WorkspaceCard{
        WorkspaceCardTitle(WorkspaceSymbol.SETTINGS,"AI 토큰 설정","질문과 문서에 사용할 문맥 크기를 조절해요")
        Text("최대 답변 토큰",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        Text("문맥의 남은 토큰 전체 사용",fontSize=13.sp)
        WorkspaceSelect("문맥 토큰 한도","${state.settings.contextTokens} 토큰",(listOf(2048,4096,8192,16384,24576,32768)+state.settings.contextTokens).distinct().sorted().filter{it<=limit}.map{"$it 토큰"}){actions.onContextTokens(it.substringBefore(' ').toInt())}
        WorkspaceSelect("실행 방식", state.settings.backend.label, InferenceBackend.entries.map { it.label },
            enabled = !state.isAnswering) { label ->
            actions.onBackend(InferenceBackend.entries.first { it.label == label })
        }
        WorkspaceButton(if(state.settingsSaved)"토큰 설정 저장됨" else "토큰 설정 저장",actions.onSaveSettings,enabled=!state.isAnswering)
        Text("저장한 설정은 다음 질문부터 적용돼요. 기기 내 실행이므로 토큰별 API 요금은 없어요.",fontSize=11.sp,lineHeight=19.sp,color=Color(0xFF8B95A1))
    }
}
@Composable internal fun WorkspaceSelect(label:String,value:String,options:List<String>,enabled:Boolean=true,choose:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF586577))
        Box{
            Surface(color=Color(0xFFFAFBFD),shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,Color(0xFFE1E7EF)),modifier=Modifier.fillMaxWidth().clickable(enabled=enabled){expanded=true}.testTag("select-$label")){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(value,modifier=Modifier.weight(1f),fontSize=13.sp);Text("⌄",color=WorkspaceDesign.Muted)}
            }
            DropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(option,fontSize=13.sp)},onClick={choose(option);expanded=false})}}
        }
    }
}
