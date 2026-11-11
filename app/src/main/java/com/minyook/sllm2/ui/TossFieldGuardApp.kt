/* 소스 안내
 * 실제 앱 화면 셸. 목적지·뒤로 가기·반응형 메뉴·키보드/시스템 여백을 조정해 기능 화면으로 연결한다.
 * 연결 모듈: com.minyook.sllm2.gas.GasReading, com.minyook.sllm2.gas., com.minyook.sllm2.gas.GasReadingSource, com.minyook.sllm2.model.ContextTokenPolicy, com.minyook.sllm2.model.InferenceBackend 등.
 */
package com.minyook.sllm2.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.minyook.sllm2.gas.GasReading
import com.minyook.sllm2.gas.*
import com.minyook.sllm2.gas.GasReadingSource
import com.minyook.sllm2.model.ContextTokenPolicy
import com.minyook.sllm2.model.InferenceBackend
import com.minyook.sllm2.model.ModelPhase
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TossBlue = Color(0xFF3182F6)
private val TossBlueWeak = WorkspaceDesign.BlueWeak
private val TossInk = Color(0xFF191F28)
private val TossSecondary = Color(0xFF4E5968)
private val TossMuted = Color(0xFF6B7684)
private val TossSurface = Color(0xFFF2F4F6)
private val TossBorder = WorkspaceDesign.Border
private val TossGreen = Color(0xFF178265)
private val TossGreenWeak = Color(0xFFE8F7F1)
private val TossRed = Color(0xFFF04452)
private val TossRedWeak = Color(0xFFFFEFF0)

@Composable
private fun FieldGuardTossTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TossBlue, onPrimary = Color.White,
            primaryContainer = TossBlueWeak, onPrimaryContainer = TossInk,
            background = WorkspaceDesign.Canvas, onBackground = TossInk,
            surface = Color.White, onSurface = TossInk,
            surfaceVariant = TossSurface, onSurfaceVariant = TossSecondary,
            outline = TossBorder, error = TossRed,
        ),
        typography = Typography(
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = if (workspaceMobile()) 26.sp else 32.sp, lineHeight = if (workspaceMobile()) 35.sp else 43.sp, letterSpacing = (-1.2).sp, fontWeight = FontWeight.Bold, color = TossInk),
            headlineSmall = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = TossInk),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = TossInk),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = TossInk),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 25.sp, color = TossInk),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 22.sp, color = TossSecondary),
            bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 19.sp, color = TossMuted),
            labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

/**
 * MainActivity가 사용하는 실제 화면 셸. destination에 따라 기능 화면을 선택하고 공통 테마·뒤로 가기·
 * 휴대폰 하단 메뉴/넓은 화면 사이드바·시스템/키보드 여백을 관리한다.
 * 화면의 입력은 actions로 돌려보내며, 채팅은 WorkspaceChat, 작업 점검/인증은 NativeWorkPage로 이어진다.
 */
@Composable
fun TossFieldGuardApp(state: FieldGuardUiState, voice: VoiceUiState, actions: FieldGuardActions) {
    FieldGuardTossTheme {
        if (!state.onboardingComplete) {
            TossOnboarding(actions.onCompleteOnboarding)
            return@FieldGuardTossTheme
        }
        val destination = state.destination
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(destination) { if (destination != AppDestination.CHAT) keyboard?.hide() }
        val wide = workspaceWide()
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val workspaceScope = rememberCoroutineScope()
        val parent = when (destination) {
            AppDestination.HOME -> null
            AppDestination.CHAT, AppDestination.SENSOR, AppDestination.SETTINGS -> AppDestination.HOME
            AppDestination.HISTORY, AppDestination.VOICE -> AppDestination.CHAT
            AppDestination.SOURCE -> state.sourceReturnDestination
            AppDestination.SENSOR_DISCONNECTED -> AppDestination.SENSOR
            AppDestination.CHECKLIST, AppDestination.LIBRARY -> AppDestination.HOME
            AppDestination.LOCAL_CHECKLIST, AppDestination.WORK_SERVER, AppDestination.SUPERVISOR -> AppDestination.CHECKLIST
            AppDestination.MODEL, AppDestination.INFERENCE, AppDestination.DATA, AppDestination.SYSTEM -> AppDestination.SETTINGS
        }
        BackHandler(enabled = parent != null || drawer.isOpen) {
            if (drawer.isOpen) { workspaceScope.launch { drawer.close() }; return@BackHandler }
            if (destination == AppDestination.VOICE) actions.onStopVoice()
            parent?.let(actions.onNavigate)
        }
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val navigateFromTab: (AppDestination) -> Unit = { target ->
            if (destination == AppDestination.VOICE && target != AppDestination.VOICE) actions.onStopVoice()
            actions.onNavigate(target)
        }
        val navigateFromMenu: (AppDestination) -> Unit = { target -> workspaceScope.launch { drawer.close() }; navigateFromTab(target) }
        ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = !wide, drawerContent = {
            if (!wide) FieldWorkspaceSidebar(destination, navigateFromMenu)
        }) {
        Row(Modifier.fillMaxSize().background(WorkspaceDesign.Canvas)) {
        if (wide) FieldWorkspaceSidebar(destination, navigateFromMenu)
        Scaffold(
            modifier = Modifier.weight(1f).then(if(destination == AppDestination.CHAT) Modifier.imePadding() else Modifier),
            containerColor = WorkspaceDesign.Canvas,
            topBar = {
                TossTopBar(destination, parent, state, onBack = {
                    if (destination == AppDestination.VOICE) actions.onStopVoice()
                    parent?.let(actions.onNavigate)
                }, onHistory = { actions.onNavigate(AppDestination.HISTORY) }, onSettings = { actions.onNavigate(AppDestination.SETTINGS) }, onMenu = if (wide) null else ({ workspaceScope.launch { drawer.open() } }))
            },
            bottomBar = {
                if ((!imeVisible || destination != AppDestination.CHAT) && !wide) TossBottomNavigation(destination, state.sourceReturnDestination, navigateFromTab)
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when (destination) {
                    AppDestination.HOME -> FieldWorkspaceHome(state, actions)
                    AppDestination.CHAT -> TossChat(state, voice, actions, imeVisible)
                    AppDestination.HISTORY -> TossHistory(state, actions)
                    AppDestination.SOURCE -> TossSource(state, actions)
                    AppDestination.VOICE -> WorkspaceChat(state, voice, actions, imeVisible=false, voiceMode=true)
                    AppDestination.SENSOR -> TossSensor(state, actions)
                    AppDestination.SENSOR_DISCONNECTED -> TossSensorDisconnected(state, actions)
                    AppDestination.CHECKLIST, AppDestination.SUPERVISOR -> key(destination) { NativeWorkPage(state, actions) }
                    AppDestination.LOCAL_CHECKLIST -> TossChecklist(state, actions)
                    AppDestination.WORK_SERVER -> WorkServerSettings(state, actions)
                    AppDestination.LIBRARY -> TossLibrary(state, actions)
                    AppDestination.MODEL -> TossModel(state, actions)
                    AppDestination.INFERENCE -> TossInference(state, actions)
                    AppDestination.DATA -> TossData(state, actions)
                    AppDestination.SETTINGS -> TossSettings(state, actions)
                    AppDestination.SYSTEM -> TossSystem(state)
                }
            }
        }
        }
        }
    }
}

@Composable
private fun TossTopBar(destination:AppDestination,parent:AppDestination?,state:FieldGuardUiState,onBack:()->Unit,onHistory:()->Unit,onSettings:()->Unit,onMenu:(()->Unit)?) {
    val title=when(destination){
        AppDestination.HOME->"홈";AppDestination.CHAT->"문서 질문";AppDestination.LIBRARY->"문서 보관함";AppDestination.HISTORY->"대화 기록"
        AppDestination.CHECKLIST->"작업 전 점검";AppDestination.SUPERVISOR->"감독관 인증";AppDestination.SOURCE->"근거 문서"
        AppDestination.VOICE->"음성 대화";AppDestination.SENSOR,AppDestination.SENSOR_DISCONNECTED->"가스 측정"
        AppDestination.LOCAL_CHECKLIST->"이전 기기 점검";AppDestination.WORK_SERVER->"작업 서버"
        else->"설정"
    }
    Column(Modifier.background(Color.White)){
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(if(workspaceMobile())60.dp else WorkspaceDesign.TopbarHeight.dp).padding(horizontal=workspacePadding()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            if(parent!=null&&destination !in setOf(AppDestination.CHAT,AppDestination.SETTINGS,AppDestination.LIBRARY,AppDestination.HISTORY,AppDestination.CHECKLIST)){
                IconButton(onClick=onBack,modifier=Modifier.size(36.dp).semantics{contentDescription="뒤로 가기"}){Text("←",fontSize=20.sp,color=TossMuted)}
            }else if(onMenu!=null)IconButton(onClick=onMenu,modifier=Modifier.size(36.dp).testTag("workspaceMenu").semantics{contentDescription="전체 메뉴 열기"}){Text("☰",fontSize=20.sp,color=TossMuted)}
            if(!workspaceMobile()){Text("워크스페이스",fontSize=12.sp,color=Color(0xFF8B95A1));Text("/",fontSize=12.sp,color=Color(0xFF8B95A1))}
            Text(title,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=TossSecondary,modifier=Modifier.weight(1f))
            Text("● 기기 내",fontSize=11.sp,color=TossGreen)
            IconButton(onClick=onSettings,modifier=Modifier.size(36.dp).semantics{contentDescription="오프라인 준비 설정"}){WorkspaceIcon(WorkspaceSymbol.DOWNLOAD,Modifier.size(18.dp))}
            if(workspaceWide())Surface(color=Color(0xFFEDF1F7),shape=RoundedCornerShape(11.dp)){Text("HT",fontSize=10.sp,fontWeight=FontWeight.ExtraBold,color=Color(0xFF657289),modifier=Modifier.padding(9.dp))}
        }
        HorizontalDivider(color=TossBorder)
    }
}

@Composable
private fun TossBackIcon() {
    Canvas(Modifier.size(20.dp)) {
        val line = 2.dp.toPx()
        val x = size.width
        val y = size.height
        drawLine(TossInk, Offset(x * .74f, y * .5f), Offset(x * .27f, y * .5f), strokeWidth = line, cap = StrokeCap.Round)
        drawLine(TossInk, Offset(x * .27f, y * .5f), Offset(x * .5f, y * .25f), strokeWidth = line, cap = StrokeCap.Round)
        drawLine(TossInk, Offset(x * .27f, y * .5f), Offset(x * .5f, y * .75f), strokeWidth = line, cap = StrokeCap.Round)
    }
}

@Composable
private fun TossBottomNavigation(destination: AppDestination, sourceReturnDestination: AppDestination, onNavigate: (AppDestination) -> Unit) {
    val selected = when (destination) {
        AppDestination.HISTORY, AppDestination.VOICE -> AppDestination.CHAT
        AppDestination.SOURCE -> if (sourceReturnDestination == AppDestination.LIBRARY) AppDestination.LIBRARY else AppDestination.CHAT
        AppDestination.SENSOR_DISCONNECTED -> AppDestination.SENSOR
        AppDestination.CHECKLIST, AppDestination.LOCAL_CHECKLIST, AppDestination.WORK_SERVER, AppDestination.SUPERVISOR -> AppDestination.CHECKLIST
        AppDestination.MODEL, AppDestination.INFERENCE, AppDestination.DATA, AppDestination.SYSTEM -> AppDestination.SETTINGS
        else -> destination
    }
    Column(Modifier.background(Color.White)) {
        HorizontalDivider(color = TossBorder)
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                AppDestination.HOME to "홈",
                AppDestination.CHAT to "질문",
                AppDestination.LIBRARY to "문서",
                AppDestination.SETTINGS to "설정",
            ).forEach { (page, label) ->
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { onNavigate(page) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    TossNavigationIcon(page, if (selected == page) TossBlue else TossMuted)
                    Spacer(Modifier.height(3.dp))
                    Text(label, color = if (selected == page) TossBlue else TossMuted, fontWeight = if (selected == page) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
internal fun TossNavigationIcon(page:AppDestination,color:Color) {
    WorkspaceIcon(when(page){
        AppDestination.HOME->WorkspaceSymbol.HOME
        AppDestination.CHAT,AppDestination.VOICE->WorkspaceSymbol.CHAT
        AppDestination.LIBRARY,AppDestination.SOURCE->WorkspaceSymbol.BOOK
        AppDestination.CHECKLIST,AppDestination.LOCAL_CHECKLIST,AppDestination.SUPERVISOR->WorkspaceSymbol.CHECK
        AppDestination.HISTORY->WorkspaceSymbol.HISTORY
        AppDestination.MODEL->WorkspaceSymbol.CPU
        AppDestination.SENSOR,AppDestination.SENSOR_DISCONNECTED->WorkspaceSymbol.WIFI
        else->WorkspaceSymbol.SETTINGS
    },Modifier.size(21.dp),color)
}

@Composable
internal fun TossScrollPage(content: @Composable ColumnScope.() -> Unit) { WorkspacePage(content) }

@Composable
internal fun TossPageTitle(title: String, description: String? = null) { WorkspaceHeading("FIELD WORKSPACE",title.replace("\n"," "),description) }

@Composable
internal fun TossPanel(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) { WorkspaceCard(modifier,color,content) }

@Composable
internal fun TossStatusPill(label: String, background: Color = TossBlueWeak, foreground: Color = TossBlue) {
    Surface(color=background,shape=RoundedCornerShape(7.dp)){Text(label,color=foreground,fontSize=10.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp))}
}

@Composable
internal fun TossPrimaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) { WorkspaceButton(label,onClick,modifier,enabled=enabled) }

@Composable
internal fun TossSecondaryButton(label:String,onClick:()->Unit,modifier:Modifier=Modifier) { WorkspaceButton(label,onClick,modifier,primary=false) }

@Composable
internal fun TossRow(title:String,subtitle:String?=null,onClick:()->Unit,trailing:String="›") {
    Row(Modifier.fillMaxWidth().heightIn(min=70.dp).clickable(onClick=onClick).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,fontSize=13.sp,fontWeight=FontWeight.SemiBold);if(subtitle!=null)Text(subtitle,fontSize=11.sp,color=TossMuted)}
        WorkspaceIcon(WorkspaceSymbol.CHEVRON,Modifier.size(16.dp))
    }
}

@Composable
private fun TossOnboarding(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("O₂ Field Guard", color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        TossPageTitle("현장에서 필요한 답,\n휴대전화 안에 있어요", "처음 모델을 받을 때만 인터넷이 필요해요. 이후 질문과 기록은 기기에 남아요.")
        TossPanel {
            Text("안전 문서 2개", style = MaterialTheme.typography.titleMedium)
            Text("87개 근거 조각이 기기에 저장돼요.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = TossBorder)
            Text("로컬 AI 모델", style = MaterialTheme.typography.titleMedium)
            Text("설치 후 인터넷 없이 문서 근거로 답해요.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = TossBorder)
            Text("Wi-Fi AP 가스 감지기", style = MaterialTheme.typography.titleMedium)
            Text("연결되면 실제 수신값을 확인할 수 있어요.", style = MaterialTheme.typography.bodySmall)
        }
        TossPanel(color = TossBlueWeak) { Text("모델 설치 전에도 제공 문서를 검색할 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = TossInk) }
        Spacer(Modifier.weight(1f))
        TossPrimaryButton("시작하기", onStart)
        Text("안전 판단과 작업 허가를 대신하지 않아요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun TossGasSummary(reading: GasReading, onClick: () -> Unit) {
    val now = currentTimeTick()
    val current = reading.isFresh(now)
    TossPanel(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가스 측정", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TossStatusPill(when { !current -> "연결·수신 대기"; reading.source == GasReadingSource.WIFI_AP -> "Wi-Fi 수신"; reading.source == GasReadingSource.SIMULATION -> "테스트 · 실측 아님"; else -> "연결 대기" }, TossSurface, TossMuted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TossCompactGas("O₂", reading.oxygenPercent.takeIf { current }, "%")
            TossCompactGas("H₂S", reading.h2sPpm.takeIf { current }, "ppm")
            TossCompactGas("CO", reading.carbonMonoxidePpm.takeIf { current }, "ppm")
            TossCompactGas("LEL", reading.lelPercent.takeIf { current }, "%")
        }
        reading.deviceName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(if (current) "최근 측정 ${reading.receivedAtMillis.localTime()} · 출처를 확인해 주세요." else "새 측정값이 없어요. 현재값으로 사용하지 마세요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TossCompactGas(label: String, value: Double?, suffix: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value?.let { "${it.gasNumber()}$suffix" } ?: "—", color = TossInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
    }
}

@Composable
private fun TossChat(state:FieldGuardUiState,voice:VoiceUiState,actions:FieldGuardActions,imeVisible:Boolean){WorkspaceChat(state,voice,actions,imeVisible)}

@Composable
internal fun TossMicrophoneIcon() {
    Canvas(Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val line = 2.dp.toPx()
        val stroke = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(TossBlue, topLeft = Offset(w * .36f, h * .08f), size = Size(w * .28f, h * .51f), cornerRadius = CornerRadius(w * .14f), style = stroke)
        val cradle = Path().apply {
            moveTo(w * .19f, h * .45f)
            lineTo(w * .19f, h * .57f)
            quadraticTo(w * .5f, h * .91f, w * .81f, h * .57f)
            lineTo(w * .81f, h * .45f)
        }
        drawPath(cradle, TossBlue, style = stroke)
        drawLine(TossBlue, Offset(w * .5f, h * .78f), Offset(w * .5f, h * .96f), strokeWidth = line, cap = StrokeCap.Round)
        drawLine(TossBlue, Offset(w * .36f, h * .96f), Offset(w * .64f, h * .96f), strokeWidth = line, cap = StrokeCap.Round)
    }
}

@Composable
private fun TossSendIcon() {
    Canvas(Modifier.size(17.dp)) {
        val w = size.width
        val h = size.height
        val line = 2.dp.toPx()
        drawLine(Color.White, Offset(w * .5f, h * .82f), Offset(w * .5f, h * .2f), strokeWidth = line, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * .5f, h * .2f), Offset(w * .22f, h * .48f), strokeWidth = line, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(w * .5f, h * .2f), Offset(w * .78f, h * .48f), strokeWidth = line, cap = StrokeCap.Round)
    }
}

@Composable
private fun TossChatMessage(message: ChatMessageUi, onOpenSource: (Long) -> Unit) {
    val elapsed = if (message.pending) answerElapsedTime(message.startedAtElapsedRealtime) else null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromWorker) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (message.fromWorker) 0.83f else 1f),
            color = if (message.fromWorker) TossBlueWeak else TossSurface,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (message.pending && message.text.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("근거 검색 중 · $elapsed", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (message.fromWorker) {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                } else {
                    if (message.pending) Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    else MarkdownText(message.text)
                    if (message.pending) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("답변 작성 중 · $elapsed", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!message.pending && message.sourceIds.isNotEmpty()) {
                        HorizontalDivider(color = TossBorder)
                        Text("참조한 문서", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        message.sourceIds.forEachIndexed { index, id ->
                            TextButton(onClick = { onOpenSource(id) }) { Text("근거 ${index + 1} 원문 보기", color = TossBlue) }
                        }
                    }
                }
            }
        }
    }
}

private fun Double.gasNumber(): String = if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)
private fun Long.localTime(): String = if (this <= 0L) "—" else DateFormat.getTimeInstance(DateFormat.SHORT, Locale.KOREA).format(Date(this))

@Composable
private fun currentTimeTick(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun TossHistory(state:FieldGuardUiState,actions:FieldGuardActions){WorkspaceHistory(state,actions)}

private enum class SourceViewMode { PDF, FORMATTED, RAW }

@Composable
private fun TossSource(state:FieldGuardUiState,actions:FieldGuardActions){WorkspaceSource(state,actions)}

@Composable
internal fun WorkspaceVoiceSettings(voice: VoiceUiState, actions: FieldGuardActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TossPanel {
            Text("이 기기의 음성 준비", style = MaterialTheme.typography.titleMedium)
            Text(voice.localSpeechStatus, style = MaterialTheme.typography.bodyMedium)
            Text(if (voice.ttsAvailable) "로컬 한국어 답변 읽기 준비됨" else "로컬 한국어 읽기 음성 확인 필요 · 읽기를 끄면 화면 답변을 사용할 수 있어요.", style = MaterialTheme.typography.bodySmall)
            TossSecondaryButton(if (voice.languagePreparing) "한국어 음성 준비 중" else "한국어 음성 준비·다시 확인", { if (!voice.languagePreparing) actions.onPrepareVoice() })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("답변 읽기", style = MaterialTheme.typography.titleMedium); Text("기기에 설치된 한국어 로컬 음성만 사용해요.", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = voice.readAnswers, onCheckedChange = actions.onVoiceReadAnswers)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("‘오투야’로 대화 시작", style = MaterialTheme.typography.titleMedium); Text("호출 뒤에는 다시 부르지 않아도 이어서 대화해요.", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = voice.wakeWordEnabled, onCheckedChange = { actions.onVoiceLongClick() })
        }
        Text("앱 화면을 열어둔 동안만 들어요. 화면을 벗어나거나 잠그면 종료돼요. 한국어 데이터 최초 준비에는 인터넷이 필요할 수 있지만 질문 음성은 원격 인식으로 보내지 않아요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TossSensor(state: FieldGuardUiState, actions: FieldGuardActions) = WifiApSensorScreen(state, actions)

@Composable
private fun TossGasMetrics(reading: GasReading) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            TossGasMetric("산소 · O₂", reading.oxygenPercent, "%", Modifier.weight(1f))
            TossGasMetric("황화수소 · H₂S", reading.h2sPpm, "ppm", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            TossGasMetric("일산화탄소 · CO", reading.carbonMonoxidePpm, "ppm", Modifier.weight(1f))
            TossGasMetric("가연성 · LEL", reading.lelPercent, "%LEL", Modifier.weight(1f))
        }
    }
}

@Composable
private fun TossGasMetric(label: String, value: Double?, unit: String, modifier: Modifier) {
    Surface(modifier = modifier, color = TossSurface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value?.gasNumber() ?: "—", color = TossInk, fontWeight = FontWeight.Bold, fontSize = 27.sp)
                Text(unit, color = TossSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
            }
        }
    }
}

@Composable
private fun TossSensorDisconnected(state: FieldGuardUiState, actions: FieldGuardActions) = WifiApSensorScreen(state, actions)

private val checklistTitles = listOf("작업 위치와 출입 경로", "공기 측정 기록", "환기 상태", "감시인과 연락 방법", "구조 장비와 절차")
private val checklistDescriptions = listOf("현장 계획과 비교", "검교정된 기기 사용 여부", "작업 중 유지 계획", "현장 담당자 확인", "원문 근거 확인")

@Composable
private fun TossChecklist(state: FieldGuardUiState, actions: FieldGuardActions) {
    TossScrollPage {
        TossPageTitle("이전 기기 점검 기록", "이 기록은 기기 안에 저장한 개인 점검 메모예요. 새 작업 요청·감독관 승인은 작업 전 점검에서 진행해 주세요.")
        Text("${state.checklistChecked.size} / 5 확인", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { state.checklistChecked.size / 5f }, color = TossBlue, trackColor = TossBorder, modifier = Modifier.fillMaxWidth().height(6.dp))
        TossPanel {
            checklistTitles.forEachIndexed { index, title ->
                Row(Modifier.fillMaxWidth().clickable { actions.onToggleChecklist(index) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = index in state.checklistChecked, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(checklistDescriptions[index], style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (index != checklistTitles.lastIndex) HorizontalDivider(color = TossBorder)
            }
        }
        TossPrimaryButton(if (state.checklistSaved) "기기에 저장했어요" else "점검 기록 저장", actions.onSaveChecklist)
        Text("이 목록은 작업허가 또는 현장 승인 절차를 대신하지 않아요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TossLibrary(state:FieldGuardUiState,actions:FieldGuardActions){WorkspaceLibrary(state,actions)}

@Composable
private fun TossSettings(state: FieldGuardUiState, actions: FieldGuardActions) {
    FieldWorkspaceSettings(state, actions)
}

@Composable
private fun TossModel(state:FieldGuardUiState,actions:FieldGuardActions){
    WorkspacePage{WorkspaceHeading("ON-DEVICE AI","기기 내 EXAONE","문서와 AI를 이 기기에 저장해요.");WorkspaceModelPanel(state,actions);WorkspaceButton("AI 토큰 설정",{actions.onNavigate(AppDestination.INFERENCE)},primary=false)}
}

@Composable
private fun TossInference(state:FieldGuardUiState,actions:FieldGuardActions){
    WorkspacePage{WorkspaceHeading("YOUR WORKSPACE","AI 토큰 설정","답변 길이와 AI가 사용할 문맥 크기를 조절해요.");WorkspaceTokenSettings(state,actions)}
}

@Composable
private fun TossData(state: FieldGuardUiState, actions: FieldGuardActions) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("대화 기록을 삭제할까요?") },
            text = { Text("이 기기에 저장된 대화 기록을 모두 지워요. 삭제한 기록은 되돌릴 수 없어요.") },
            confirmButton = { TextButton(onClick = { actions.onClearChatHistory(); confirmClear = false }) { Text("삭제", color = TossRed) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }
    TossScrollPage {
        TossPageTitle("내 기록은\n이 기기에 있어요", "대화 기록과 마지막 수신 가스값은 앱 전용 저장소에 보관돼요.")
        TossPanel(color = TossGreenWeak) {
            Text("계정 없이 사용해요", style = MaterialTheme.typography.titleMedium, color = TossGreen)
            Text("현재 앱에는 질문·답변 전송 서버가 없어요.", style = MaterialTheme.typography.bodyMedium)
        }
        TossPanel {
            Text("저장된 항목", style = MaterialTheme.typography.titleMedium)
            TossRow("대화 기록", "${state.histories.size}개 세션 · 기기 저장", { actions.onNavigate(AppDestination.HISTORY) })
            HorizontalDivider(color = TossBorder)
            TossRow("문서와 검색 색인", "기본 제공 문서 ${state.documents.size}개", { actions.onNavigate(AppDestination.LIBRARY) })
            HorizontalDivider(color = TossBorder)
            TossRow("마지막 가스값", "출처와 수신 시각 포함", { actions.onNavigate(AppDestination.SENSOR) })
        }
        TossPanel { Text("모델을 처음 받을 때만 네트워크를 사용해요. 음성 인식은 기기 음성 서비스 정책을 따라요.", style = MaterialTheme.typography.bodyMedium) }
        if (state.isAnswering) Text("답변 생성이 끝난 뒤 대화 기록을 삭제할 수 있어요.", style = MaterialTheme.typography.bodySmall)
        else TossSecondaryButton("대화 기록 삭제", { confirmClear = true })
    }
}

@Composable
private fun TossSystem(state: FieldGuardUiState) {
    val now = currentTimeTick()
    TossScrollPage {
        TossPageTitle("앱 밖에서도\n측정 상태를 봐요")
        TossPanel(color = TossBlueWeak) {
            Text("홈 화면 위젯", style = MaterialTheme.typography.titleMedium)
            Text("마지막 수신 상태와 출처를 보여줘요.", style = MaterialTheme.typography.bodyMedium)
            TossGasSummary(state.gasReading, onClick = {})
        }
        TossPanel {
            Text("백그라운드 알림", style = MaterialTheme.typography.titleMedium)
            Text(if (state.gasReading.isFresh(now)) "최근 수신 ${state.gasReading.receivedAtMillis.localTime()} · ${state.gasReading.deviceName ?: "가스 측정기"}" else "새 측정값 없음 · 현재값을 표시하지 않아요.", style = MaterialTheme.typography.bodyMedium)
            Text("서비스가 실행되는 동안 알림 패널에서 마지막 측정값을 확인할 수 있어요.", style = MaterialTheme.typography.bodySmall)
        }
        Text("OS와 제조사에 따라 위젯·알림 모양이 달라질 수 있어요. 수신이 멈춘 값은 현재값으로 사용하지 마세요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun WifiApSensorScreen(state: FieldGuardUiState, actions: FieldGuardActions) {
    val ap = state.wifiAp
    val now = currentTimeTick()
    TossScrollPage {
        TossPageTitle("감지기를\nWi-Fi로 연결해요", ap.status)
        TossPanel {
            TossStatusPill(when(ap.phase) { ApPhase.OFF -> "AP 꺼짐"; ApPhase.STARTING -> "준비 중"; ApPhase.RUNNING -> "AP 켜짐 · 인터넷 불필요"; ApPhase.ERROR -> "시작 확인 필요" }, TossBlueWeak, TossBlue)
            if(ap.phase == ApPhase.RUNNING || ap.phase == ApPhase.STARTING) {
                TossSecondaryButton("AP 끄기", actions.onStopAp, modifier = Modifier.testTag("stopWifiAp"))
            } else {
                TossPrimaryButton("휴대폰 AP 시작", actions.onStartAp, modifier = Modifier.testTag("startWifiAp"))
            }
            Text("감지기를 휴대폰 Wi-Fi에 연결해 값을 받아요. 통신 규격은 장비 연동 전 시험 버전이에요.", style = MaterialTheme.typography.bodySmall)
        }
        if(ap.phase == ApPhase.RUNNING) {
            TossPanel {
                Text("감지기에 입력할 연결 정보", style = MaterialTheme.typography.titleMedium)
                Text("Wi-Fi 이름: ${ap.ssid}")
                Text("Wi-Fi 암호: ${ap.password}")
                Text("수신 주소: ${ap.host}:${ap.port}")
                Text("연결 코드: ${ap.token}", style = MaterialTheme.typography.bodySmall)
                Text("${ap.band} · AP를 다시 켜면 연결 정보가 바뀌어요.", style = MaterialTheme.typography.bodySmall)

            }
        }
        Text("감지기 ${ap.meters.count { it.link != MeterLink.DISCONNECTED }}대 연결", style = MaterialTheme.typography.titleMedium)
        if(ap.meters.isEmpty()) TossPanel {
            Text("아직 연결된 감지기가 없어요.")
            Text("AP를 켠 뒤 감지기에 연결 정보를 입력해 주세요. 테스트 송신기도 같은 방식으로 확인할 수 있어요.", style = MaterialTheme.typography.bodySmall)
        }
        ap.meters.forEach { meter ->
            val current = ap.phase == ApPhase.RUNNING && meter.current && meter.reading().isFresh(now)
            TossPanel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(meter.name, style = MaterialTheme.typography.titleMedium)
                        Text(meter.deviceId, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { actions.onSelectMeter(meter.deviceId) }, modifier = Modifier.testTag("selectMeter_${meter.deviceId}")) {
                        Text(if(ap.selectedDeviceId == meter.deviceId) "선택됨" else "요약에 표시")
                    }
                }
                Text(if(meter.link == MeterLink.RECEIVING && !current) "수신 지연 · 현재값 없음" else meter.link.label(), color = if(current) TossGreen else TossSecondary)
                if(meter.test) TossStatusPill("테스트 데이터 · 실측 아님", TossBlueWeak, TossBlue)
                TossGasMetrics(if(current) meter.reading() else GasReading())
                val age = meter.sample?.let { (now - meter.receivedAtMillis).coerceAtLeast(0) + it.ageMs }
                Text("측정 경과 ${age?.let { "${it / 1000.0}초" } ?: "—"} · 왕복 ${meter.rttMs?.let { "%.1f ms".format(java.util.Locale.US, it) } ?: "측정 대기"}", style = MaterialTheme.typography.bodySmall)
                Text("누락 순번 ${meter.missed} · 중복/역순 ${meter.rejected}", style = MaterialTheme.typography.bodySmall)
                if(meter.sample?.alarm in setOf("warning", "danger") && ap.phase == ApPhase.RUNNING && meter.hasRecentSample) Text("장비 경보: ${if(meter.sample?.alarm == "danger") "위험" else "주의"}", color = TossRed)
                if(meter.reason.isNotBlank()) Text(meter.reason, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("감지기마다 수치를 따로 표시해요. 연결 상태는 작업 승인이나 안전 판정이 아니에요.", style = MaterialTheme.typography.bodySmall)
    }
}
