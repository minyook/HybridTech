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
private val TossBlueWeak = Color(0xFFE8F3FF)
private val TossInk = Color(0xFF191F28)
private val TossSecondary = Color(0xFF4E5968)
private val TossMuted = Color(0xFF6B7684)
private val TossSurface = Color(0xFFF2F4F6)
private val TossBorder = Color(0xFFE5E8EB)
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
            background = Color.White, onBackground = TossInk,
            surface = Color.White, onSurface = TossInk,
            surfaceVariant = TossSurface, onSurfaceVariant = TossSecondary,
            outline = TossBorder, error = TossRed,
        ),
        typography = Typography(
            headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = TossInk),
            headlineSmall = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = TossInk),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = TossInk),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = TossInk),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = TossInk),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 23.sp, color = TossSecondary),
            bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 19.sp, color = TossMuted),
            labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

@Composable
fun TossFieldGuardApp(state: FieldGuardUiState, voice: VoiceUiState, actions: FieldGuardActions) {
    FieldGuardTossTheme {
        if (!state.onboardingComplete) {
            TossOnboarding(actions.onCompleteOnboarding)
            return@FieldGuardTossTheme
        }
        val destination = state.destination
        val parent = when (destination) {
            AppDestination.HOME -> null
            AppDestination.CHAT, AppDestination.SENSOR, AppDestination.SETTINGS -> AppDestination.HOME
            AppDestination.HISTORY, AppDestination.VOICE -> AppDestination.CHAT
            AppDestination.SOURCE -> state.sourceReturnDestination
            AppDestination.SENSOR_DISCONNECTED -> AppDestination.SENSOR
            AppDestination.CHECKLIST, AppDestination.LIBRARY -> AppDestination.HOME
            AppDestination.MODEL, AppDestination.INFERENCE, AppDestination.DATA, AppDestination.SYSTEM -> AppDestination.SETTINGS
        }
        BackHandler(enabled = parent != null) {
            if (destination == AppDestination.VOICE) actions.onStopVoice()
            parent?.let(actions.onNavigate)
        }
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val navigateFromTab: (AppDestination) -> Unit = { target ->
            if (destination == AppDestination.VOICE && target != AppDestination.VOICE) actions.onStopVoice()
            actions.onNavigate(target)
        }
        Scaffold(
            modifier = Modifier.imePadding(),
            containerColor = Color.White,
            topBar = {
                TossTopBar(destination, parent, state, onBack = {
                    if (destination == AppDestination.VOICE) actions.onStopVoice()
                    parent?.let(actions.onNavigate)
                }, onHistory = { actions.onNavigate(AppDestination.HISTORY) })
            },
            bottomBar = {
                if (!imeVisible) TossBottomNavigation(destination, state.sourceReturnDestination, navigateFromTab)
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (destination) {
                    AppDestination.HOME -> TossHome(state, actions)
                    AppDestination.CHAT -> TossChat(state, voice, actions, imeVisible)
                    AppDestination.HISTORY -> TossHistory(state, actions)
                    AppDestination.SOURCE -> TossSource(state, actions)
                    AppDestination.VOICE -> TossVoice(voice, actions)
                    AppDestination.SENSOR -> TossSensor(state, actions)
                    AppDestination.SENSOR_DISCONNECTED -> TossSensorDisconnected(state, actions)
                    AppDestination.CHECKLIST -> TossChecklist(state, actions)
                    AppDestination.LIBRARY -> TossLibrary(state, actions)
                    AppDestination.MODEL -> TossModel(state, actions)
                    AppDestination.INFERENCE -> TossInference(state, actions)
                    AppDestination.DATA -> TossData(state, actions)
                    AppDestination.SETTINGS -> TossSettings(actions)
                    AppDestination.SYSTEM -> TossSystem(state)
                }
            }
        }
    }
}

@Composable
private fun TossTopBar(destination: AppDestination, parent: AppDestination?, state: FieldGuardUiState, onBack: () -> Unit, onHistory: () -> Unit) {
    val title = when (destination) {
        AppDestination.HOME -> "O₂ Field Guard"
        AppDestination.CHAT -> "문서 질문"
        AppDestination.HISTORY -> "대화 기록"
        AppDestination.SOURCE -> "근거 문서"
        AppDestination.VOICE -> "음성 질문"
        AppDestination.SENSOR -> "가스 측정"
        AppDestination.SENSOR_DISCONNECTED -> "센서 상태"
        AppDestination.CHECKLIST -> "작업 전 점검"
        AppDestination.LIBRARY -> "문서 보관함"
        AppDestination.MODEL -> "로컬 모델"
        AppDestination.INFERENCE -> "추론 설정"
        AppDestination.DATA -> "내 데이터"
        AppDestination.SETTINGS -> "설정"
        AppDestination.SYSTEM -> "위젯과 알림"
    }
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (parent != null && destination !in setOf(AppDestination.CHAT, AppDestination.SENSOR, AppDestination.SETTINGS)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = "뒤로 가기" }) {
                TossBackIcon()
            }
        } else if (destination == AppDestination.HOME) {
            Surface(color = TossBlue, shape = RoundedCornerShape(10.dp)) {
                Text("O₂", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (destination == AppDestination.CHAT) {
            TextButton(onClick = onHistory) { Text("기록", color = TossSecondary) }
        } else if (destination == AppDestination.HOME && state.modelStatus.phase == ModelPhase.READY) {
            TossStatusPill("로컬 준비됨", TossGreenWeak, TossGreen)
        }
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
        AppDestination.SOURCE -> if (sourceReturnDestination == AppDestination.LIBRARY) AppDestination.HOME else AppDestination.CHAT
        AppDestination.SENSOR_DISCONNECTED -> AppDestination.SENSOR
        AppDestination.CHECKLIST, AppDestination.LIBRARY -> AppDestination.HOME
        AppDestination.MODEL, AppDestination.INFERENCE, AppDestination.DATA, AppDestination.SYSTEM -> AppDestination.SETTINGS
        else -> destination
    }
    Column(Modifier.background(Color.White)) {
        HorizontalDivider(color = TossBorder)
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                AppDestination.HOME to "홈",
                AppDestination.CHAT to "질문",
                AppDestination.SENSOR to "측정",
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
private fun TossNavigationIcon(page: AppDestination, color: Color) {
    Canvas(Modifier.size(19.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (page) {
            AppDestination.HOME -> {
                val roof = Path().apply {
                    moveTo(w * .14f, h * .45f)
                    lineTo(w * .5f, h * .14f)
                    lineTo(w * .86f, h * .45f)
                }
                drawPath(roof, color, style = stroke)
                val house = Path().apply {
                    moveTo(w * .22f, h * .43f)
                    lineTo(w * .22f, h * .86f)
                    lineTo(w * .78f, h * .86f)
                    lineTo(w * .78f, h * .43f)
                }
                drawPath(house, color, style = stroke)
            }
            AppDestination.CHAT -> {
                drawRoundRect(color, topLeft = Offset(w * .12f, h * .13f), size = Size(w * .76f, h * .61f), cornerRadius = CornerRadius(w * .16f), style = stroke)
                drawLine(color, Offset(w * .35f, h * .74f), Offset(w * .23f, h * .87f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            AppDestination.SENSOR -> {
                val wave = Path().apply {
                    moveTo(w * .08f, h * .54f)
                    lineTo(w * .28f, h * .54f)
                    lineTo(w * .39f, h * .27f)
                    lineTo(w * .56f, h * .76f)
                    lineTo(w * .69f, h * .46f)
                    lineTo(w * .92f, h * .46f)
                }
                drawPath(wave, color, style = stroke)
            }
            AppDestination.SETTINGS -> {
                listOf(.26f to .65f, .5f to .34f, .74f to .61f).forEach { (x, dotY) ->
                    drawLine(color, Offset(w * x, h * .12f), Offset(w * x, h * .88f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(Color.White, radius = w * .12f, center = Offset(w * x, h * dotY))
                    drawCircle(color, radius = w * .12f, center = Offset(w * x, h * dotY), style = stroke)
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun TossScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun TossPageTitle(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = TossMuted)
    }
}

@Composable
private fun TossPanel(modifier: Modifier = Modifier, color: Color = TossSurface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = color) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun TossStatusPill(label: String, background: Color = TossBlueWeak, foreground: Color = TossBlue) {
    Surface(color = background, shape = CircleShape) {
        Text(label, color = foreground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun TossPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TossBlue, contentColor = Color.White),
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun TossSecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = TossSurface, contentColor = TossInk),
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun TossRow(title: String, subtitle: String? = null, onClick: () -> Unit, trailing: String = "›") {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text(trailing, color = TossMuted, fontSize = 18.sp)
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
            Text("BLE 가스 측정기", style = MaterialTheme.typography.titleMedium)
            Text("연결되면 실제 수신값을 확인할 수 있어요.", style = MaterialTheme.typography.bodySmall)
        }
        TossPanel(color = TossBlueWeak) { Text("모델 설치 전에도 제공 문서를 검색할 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = TossInk) }
        Spacer(Modifier.weight(1f))
        TossPrimaryButton("시작하기", onStart)
        Text("안전 판단과 작업 허가를 대신하지 않아요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TossHome(state: FieldGuardUiState, actions: FieldGuardActions) {
    val now = currentTimeTick()
    TossScrollPage {
        TossPageTitle("오늘 현장에서\n필요한 것")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TossStatusPill("문서 · 오프라인 준비됨", TossGreenWeak, TossGreen)
            if (state.sensorConnectionLost) TossStatusPill("센서 연결 끊김", TossRedWeak, TossRed)
            else TossStatusPill(if (state.gasReading.source == GasReadingSource.BLE && state.gasReading.isFresh(now)) "BLE 수신" else "센서 연결 대기", TossSurface, TossSecondary)
        }
        TossGasSummary(state.gasReading, onClick = { actions.onNavigate(AppDestination.SENSOR) })
        TossPanel(color = TossBlueWeak) {
            Text("문서에 바로 질문", style = MaterialTheme.typography.titleLarge)
            Text("작업 전 측정, 환기, 구조 절차를 문서 근거로 확인해요.", style = MaterialTheme.typography.bodyMedium)
            TossPrimaryButton("질문하기", { actions.onNavigate(AppDestination.CHAT) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TossHomeTile("작업 전 점검", "5개 항목", Modifier.weight(1f)) { actions.onNavigate(AppDestination.CHECKLIST) }
            TossHomeTile("문서 보관함", "${state.documents.size}개 문서", Modifier.weight(1f)) { actions.onNavigate(AppDestination.LIBRARY) }
        }
        if (state.modelStatus.phase != ModelPhase.READY) {
            TossRow("로컬 AI 모델 준비", "문서 검색은 지금도 사용할 수 있어요.", { actions.onNavigate(AppDestination.MODEL) })
        }
    }
}

@Composable
private fun TossHomeTile(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = TossSurface, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(color = TossBlueWeak, shape = RoundedCornerShape(11.dp)) {
                Text(if (title == "작업 전 점검") "✓" else "▤", color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TossGasSummary(reading: GasReading, onClick: () -> Unit) {
    val now = currentTimeTick()
    val current = reading.isFresh(now)
    TossPanel(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가스 측정", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TossStatusPill(when { !current -> "연결·수신 대기"; reading.source == GasReadingSource.BLE -> "BLE 수신"; reading.source == GasReadingSource.SIMULATION -> "데모 · 실측 아님"; else -> "연결 대기" }, TossSurface, TossMuted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TossCompactGas("O₂", reading.oxygenPercent.takeIf { current }, "%")
            TossCompactGas("H₂S", reading.h2sPpm.takeIf { current }, "")
            TossCompactGas("CO", reading.carbonMonoxidePpm.takeIf { current }, "")
            TossCompactGas("LEL", reading.lelPercent.takeIf { current }, "")
        }
        Text(if (current) "마지막 수신 ${reading.receivedAtMillis.localTime()} · 출처를 확인해 주세요." else "새 측정값이 없어요. 현재값으로 사용하지 마세요.", style = MaterialTheme.typography.bodySmall)
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
private fun TossChat(state: FieldGuardUiState, voice: VoiceUiState, actions: FieldGuardActions, imeVisible: Boolean) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.activeChatId) {
        if (state.messages.size > 1) listState.scrollToItem(state.messages.lastIndex)
    }
    val showLatest by remember(state.messages.size) {
        derivedStateOf {
            state.messages.size > 3 && (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) < state.messages.lastIndex - 1
        }
    }
    LaunchedEffect(state.messages.size, state.isAnswering) {
        if (state.messages.size > 1 && !showLatest) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        if (!imeVisible) TossGasSummary(state.gasReading) { actions.onNavigate(AppDestination.SENSOR) }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("conversationList"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.messages.size <= 1) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TossPageTitle("안전 작업에 관해\n궁금한 걸 물어보세요", "제공 문서에서 근거를 찾아 답해요.")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("공기 측정 기준", "구조 절차").forEach { prompt ->
                                    FilterChip(selected = false, onClick = { actions.onPreset(prompt) }, label = { Text(prompt) })
                                }
                            }
                            FilterChip(selected = false, onClick = { actions.onPreset("작업허가 전에 어떤 안전조치를 확인해야 하나요?") }, label = { Text("작업허가 점검") })
                        }
                    }
                }
                if (state.messages.size > 1 || state.activeChatId != null) {
                    items(state.messages, key = { it.id }) { message ->
                        TossChatMessage(message, actions.onOpenSource)
                    }
                }
            }
            if (showLatest) {
                OutlinedButton(
                    onClick = { scope.launch { listState.animateScrollToItem(state.messages.lastIndex) } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).testTag("scrollLatest"),
                    shape = CircleShape,
                ) {
                    Text("최근 답변으로", color = TossBlue)
                }
            }
        }
        if (state.inputError != null) Text(state.inputError, color = TossRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.input,
                onValueChange = actions.onInputChange,
                placeholder = { Text(if (voice.wakeWordEnabled) "‘오투야’라고 불러 주세요" else "안전 작업에 대해 물어보세요", fontSize = 14.sp) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 125.dp).testTag("chatInput"),
                shape = RoundedCornerShape(16.dp), maxLines = 4, isError = state.inputError != null,
            )
            IconButton(
                onClick = { actions.onNavigate(AppDestination.VOICE); actions.onVoiceClick() },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "음성 질문" },
            ) { TossMicrophoneIcon() }
            IconButton(
                onClick = { if (!state.isAnswering) actions.onSubmit() },
                enabled = !state.isAnswering,
                modifier = Modifier.size(48.dp).testTag("sendMessage").semantics { contentDescription = "메시지 전송" },
            ) {
                Surface(color = if (state.isAnswering) TossBorder else TossBlue, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { TossSendIcon() }
                }
            }
        }
    }
}

@Composable
private fun TossMicrophoneIcon() {
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
private fun TossHistory(state: FieldGuardUiState, actions: FieldGuardActions) {
    TossScrollPage {
        TossPageTitle("이전에 물어본 내용", "최근 대화는 이 기기에만 보관돼요.")
        TossPrimaryButton("새 대화", actions.onNewChat)
        if (state.histories.isEmpty()) {
            TossPanel { Text("아직 저장된 대화가 없어요.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            TossPanel {
                state.histories.forEachIndexed { index, session ->
                    TossRow(session.title, "${session.updatedAtMillis.localTime()} · ${session.turns.size}개 메시지", { actions.onLoadChat(session.id) })
                    if (index != state.histories.lastIndex) HorizontalDivider(color = TossBorder)
                }
            }
        }
        TossPanel(color = TossBlueWeak) { Text("계정 없이 이 기기에서 이어서 볼 수 있어요.", style = MaterialTheme.typography.bodyMedium) }
    }
}

private enum class SourceViewMode { PDF, FORMATTED, RAW }

@Composable
private fun TossSource(state: FieldGuardUiState, actions: FieldGuardActions) {
    val source = state.selectedSource
    var viewMode by remember(source?.id) { mutableStateOf(SourceViewMode.PDF) }
    TossScrollPage {
        TossPageTitle("답변이 어디에서\n왔는지 확인해요")
        if (source == null) {
            TossPanel { Text("열 문서를 찾지 못했어요.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            TossPanel {
                Text(source.documentTitle, style = MaterialTheme.typography.titleMedium)
                Text("${source.pageNumber}쪽 · ${source.heading}", style = MaterialTheme.typography.bodySmall)
            }
            TossStatusPill("문서 근거", TossBlueWeak, TossBlue)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = viewMode == SourceViewMode.PDF, onClick = { viewMode = SourceViewMode.PDF }, label = { Text("PDF 원본") })
                FilterChip(selected = viewMode == SourceViewMode.FORMATTED, onClick = { viewMode = SourceViewMode.FORMATTED }, label = { Text("읽기 편한 보기") })
                FilterChip(selected = viewMode == SourceViewMode.RAW, onClick = { viewMode = SourceViewMode.RAW }, label = { Text("추출 텍스트") })
            }
            if (viewMode == SourceViewMode.PDF) {
                SourcePdfPage(source)
            } else {
                Surface(color = Color.White, border = BorderStroke(1.dp, TossBorder), shape = RoundedCornerShape(20.dp)) {
                    if (viewMode == SourceViewMode.FORMATTED) {
                        MarkdownText(
                            markdown = remember(source.body) { SourceDocumentFormatter.format(source.body) },
                            modifier = Modifier.padding(18.dp),
                        )
                    } else {
                        Text(source.body, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TossSecondaryButton("이전 부분", { actions.onAdjacentSource(-1) }, Modifier.weight(1f))
                TossSecondaryButton("다음 부분", { actions.onAdjacentSource(1) }, Modifier.weight(1f))
            }
            TossPanel(color = TossBlueWeak) {
                Text("PDF 원본에서 그림과 표를 확인할 수 있어요. 추출 텍스트는 검색과 답변에 사용된 내용을 보여줍니다.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TossVoice(voice: VoiceUiState, actions: FieldGuardActions) {
    TossScrollPage {
        TossPageTitle(if (voice.listening) "말씀해 주세요" else "음성으로 질문해요", "말씀한 질문을 문서에서 찾고 기기에서 답해요.")
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(color = TossBlueWeak, shape = CircleShape, modifier = Modifier.size(150.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(if (voice.listening) "듣는 중" else "마이크", color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 19.sp) }
            }
        }
        Spacer(Modifier.height(26.dp))
        TossPanel { Text(if (voice.listening) "질문을 듣고 있어요. 말을 마치면 자동으로 문서 검색이 시작돼요." else "마이크를 누르고 질문을 말씀해 주세요.", style = MaterialTheme.typography.bodyMedium) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("‘오투야’ 호출 대기", style = MaterialTheme.typography.titleMedium)
                Text("앱을 보고 있을 때만 작동해요.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = voice.wakeWordEnabled, onCheckedChange = { actions.onVoiceLongClick() })
        }
        TossSecondaryButton(if (voice.listening) "듣기 중단" else "음성 질문 시작", if (voice.listening) actions.onStopVoice else actions.onVoiceClick)
        Text("음성 인식의 오프라인 동작은 기기 음성 서비스에 따라 달라져요.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TossSensor(state: FieldGuardUiState, actions: FieldGuardActions) {
    if (state.sensorConnectionLost) {
        TossSensorDisconnected(state, actions)
        return
    }
    val now = currentTimeTick()
    val reading = state.gasReading
    val fresh = reading.isFresh(now)
    TossScrollPage {
        TossPageTitle(if (fresh && reading.source == GasReadingSource.BLE) "측정값을\n확인해요" else "가스 측정기를\n연결해요", state.bluetoothStatus)
        if (fresh) {
            TossStatusPill(if (reading.source == GasReadingSource.SIMULATION) "데모 · 실제 측정값 아님" else "BLE 수신 · ${reading.receivedAtMillis.localTime()}", if (reading.source == GasReadingSource.SIMULATION) TossBlueWeak else TossGreenWeak, if (reading.source == GasReadingSource.SIMULATION) TossBlue else TossGreen)
            TossGasMetrics(reading)
            TossPanel {
                Text(if (reading.source == GasReadingSource.SIMULATION) "시뮬레이션 값이에요. 실제 계측값으로 사용하지 마세요." else "수신 시각과 측정기 출처를 함께 확인해 주세요.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            TossPanel {
                Text("현재 표시할 측정값이 없어요.", style = MaterialTheme.typography.titleMedium)
                Text("실제 BLE 데이터를 새로 받으면 수치를 보여드려요.", style = MaterialTheme.typography.bodySmall)
            }
        }
        TossPanel {
            Text("Bluetooth LE 측정기", style = MaterialTheme.typography.titleMedium)
            Text(state.bluetoothStatus, style = MaterialTheme.typography.bodySmall)
            TossPrimaryButton(if (state.isScanning) "검색 중…" else "기기 검색", actions.onScanBluetooth, modifier = Modifier.testTag("scanBluetooth"), enabled = !state.isScanning)
            if (state.bluetoothStatus.startsWith("연결됨") || fresh && reading.source == GasReadingSource.BLE) {
                TossSecondaryButton("연결 해제", actions.onDisconnectDevice)
            }
        }
        if (state.devices.isNotEmpty()) {
            TossPanel {
                Text("검색된 기기", style = MaterialTheme.typography.titleMedium)
                state.devices.forEachIndexed { index, device ->
                    TossRow(device.name, "${device.address} · ${device.rssi} dBm", { actions.onConnectDevice(device.address) }, "연결")
                    if (index != state.devices.lastIndex) HorizontalDivider(color = TossBorder)
                }
            }
        }
        Text("제조사별 통신 규격과 계측 정확도는 실제 장비에서 검증해야 해요. 앱 수치만으로 작업 가능 여부를 판단하지 말아 주세요.", style = MaterialTheme.typography.bodySmall)
    }
}

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
private fun TossSensorDisconnected(state: FieldGuardUiState, actions: FieldGuardActions) {
    TossScrollPage {
        TossPageTitle("측정기 연결이\n끊겼어요", "현재 측정값을 받을 수 없어요.")
        TossPanel(color = TossRedWeak) {
            Text("이전 수치는 현재 상태가 아니에요", color = TossRed, style = MaterialTheme.typography.titleMedium)
            Text("마지막 수신 ${state.lastGasReading.receivedAtMillis.localTime()} · ${state.lastGasReading.deviceName ?: "BLE 측정기"}", style = MaterialTheme.typography.bodySmall)
        }
        TossPanel {
            Text("다음 내용을 확인해 주세요", style = MaterialTheme.typography.titleMedium)
            listOf("측정기 전원과 거리를 확인해요.", "Bluetooth 연결을 다시 시도해요.", "현장 절차에 따라 계측 상태를 확인해요.").forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        TossPrimaryButton("기기 다시 검색", { actions.onScanBluetooth(); actions.onNavigate(AppDestination.SENSOR) })
        Text("앱 화면만으로 작업 가능 여부를 판단하지 말아 주세요.", style = MaterialTheme.typography.bodySmall)
    }
}

private val checklistTitles = listOf("작업 위치와 출입 경로", "공기 측정 기록", "환기 상태", "감시인과 연락 방법", "구조 장비와 절차")
private val checklistDescriptions = listOf("현장 계획과 비교", "검교정된 기기 사용 여부", "작업 중 유지 계획", "현장 담당자 확인", "원문 근거 확인")

@Composable
private fun TossChecklist(state: FieldGuardUiState, actions: FieldGuardActions) {
    TossScrollPage {
        TossPageTitle("작업 전 확인할\n내용을 모았어요")
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
private fun TossLibrary(state: FieldGuardUiState, actions: FieldGuardActions) {
    TossScrollPage {
        TossPageTitle("인터넷 없이도\n문서를 찾아요", "${state.documents.size}개 문서 · ${state.documents.sumOf { it.chunkCount }}개 근거 조각")
        OutlinedTextField(
            value = state.libraryQuery, onValueChange = actions.onLibraryQuery,
            placeholder = { Text("문서에서 찾기") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true,
        )
        if (state.libraryQuery.isNotBlank()) {
            Text("검색 결과", style = MaterialTheme.typography.titleMedium)
            if (state.libraryResults.isEmpty()) Text("관련 부분을 찾는 중이거나 결과가 없어요.", style = MaterialTheme.typography.bodyMedium)
            state.libraryResults.forEach { source ->
                TossPanel(modifier = Modifier.clickable { actions.onOpenSource(source.id) }) {
                    Text(source.heading, style = MaterialTheme.typography.titleMedium)
                    Text("${source.documentTitle} · ${source.pageNumber}쪽", style = MaterialTheme.typography.bodySmall)
                    Text(source.body.take(90), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            TossPanel(color = TossBlueWeak) { Text("질문하면 관련 부분을 먼저 찾아서 답해요.", style = MaterialTheme.typography.bodyMedium) }
            TossPanel {
                Text("저장된 문서", style = MaterialTheme.typography.titleMedium)
                state.documents.forEachIndexed { index, document ->
                    TossRow(document.title, "근거 조각 ${document.chunkCount}개", { actions.onOpenDocument(document.id) })
                    if (index != state.documents.lastIndex) HorizontalDivider(color = TossBorder)
                }
                if (state.documents.isEmpty()) Text("문서를 준비하고 있어요.", style = MaterialTheme.typography.bodyMedium)
            }
            Text("지금은 기본 제공 문서만 검색해요. 문서 추가는 향후 확장 기능이에요.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TossSettings(actions: FieldGuardActions) {
    TossScrollPage {
        TossPageTitle("설정", "모델과 기기 데이터를 관리해요.")
        TossPanel {
            TossRow("로컬 AI 모델", "Gemma 4 E2B 설치와 상태", { actions.onNavigate(AppDestination.MODEL) })
            HorizontalDivider(color = TossBorder)
            TossRow("추론 설정", "실행 방식과 답변 길이", { actions.onNavigate(AppDestination.INFERENCE) })
            HorizontalDivider(color = TossBorder)
            TossRow("내 데이터", "기기에 저장된 기록", { actions.onNavigate(AppDestination.DATA) })
            HorizontalDivider(color = TossBorder)
            TossRow("위젯과 알림", "앱 밖에서 측정 상태 보기", { actions.onNavigate(AppDestination.SYSTEM) })
        }
        TossPanel(color = TossBlueWeak) {
            Text("질문과 문서 검색은 기기에서 처리해요.", style = MaterialTheme.typography.bodyMedium)
            Text("모델을 처음 받을 때만 네트워크가 필요해요.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TossModel(state: FieldGuardUiState, actions: FieldGuardActions) {
    TossScrollPage {
        val phase = state.modelStatus.phase
        TossPageTitle(
            when (phase) {
                ModelPhase.READY -> "AI 모델이\n준비됐어요"
                ModelPhase.DOWNLOADING -> "AI 모델을\n받고 있어요"
                ModelPhase.FAILED -> "모델을 받지\n못했어요"
                ModelPhase.NOT_INSTALLED -> "기기에서 답할\n모델을 준비해요"
            },
            "설치가 끝나면 이 기기에서 문서를 참고해 답해요.",
        )
        TossPanel {
            Text("Gemma 4 E2B", style = MaterialTheme.typography.titleLarge)
            Text("온디바이스 모델 · 약 2.41GB", style = MaterialTheme.typography.bodySmall)
            when (phase) {
                ModelPhase.DOWNLOADING -> {
                    Text("${state.modelProgress ?: 0}%", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TossBlue)
                    LinearProgressIndicator(progress = { (state.modelProgress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = TossBlue, trackColor = TossBorder)
                    Text("앱을 닫아도 다운로드를 이어가요.", style = MaterialTheme.typography.bodySmall)
                }
                ModelPhase.READY -> TossStatusPill("로컬 모델 준비됨", TossGreenWeak, TossGreen)
                ModelPhase.FAILED -> Text(state.modelStatus.failureMessage ?: "다시 다운로드해 주세요.", color = TossRed, style = MaterialTheme.typography.bodyMedium)
                ModelPhase.NOT_INSTALLED -> Text("문서 검색은 모델 없이도 사용할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        TossPanel {
            TossRow("저장 공간", "남은 공간 ${state.deviceProfile?.availableStorageGb ?: "—"}GB · 설치에는 최소 4GB 필요", {})
            HorizontalDivider(color = TossBorder)
            TossRow("네트워크", "모델 설치할 때만 사용해요.", {})
            HorizontalDivider(color = TossBorder)
            TossRow("설치 후 질문", "문서 검색과 생성은 기기에서 실행해요.", {})
        }
        if (phase == ModelPhase.NOT_INSTALLED || phase == ModelPhase.FAILED) {
            TossPrimaryButton(if (phase == ModelPhase.FAILED) "다시 다운로드" else "모델 다운로드", actions.onDownloadModel)
        }
        if (phase == ModelPhase.READY) TossSecondaryButton("추론 설정 보기", { actions.onNavigate(AppDestination.INFERENCE) })
    }
}

@Composable
private fun TossInference(state: FieldGuardUiState, actions: FieldGuardActions) {
    val settings = state.settings
    val contextLimit = state.deviceProfile?.let(ContextTokenPolicy::deviceLimit) ?: ContextTokenPolicy.MIN_CONTEXT_TOKENS
    TossScrollPage {
        TossPageTitle("이 기기에 맞게\n설정해요")
        TossPanel {
            Text("실행 방식", style = MaterialTheme.typography.titleMedium)
            InferenceBackend.entries.forEach { backend ->
                FilterChip(
                    selected = settings.backend == backend,
                    onClick = { actions.onBackend(backend) },
                    label = { Text(backend.label) },
                )
            }
            Text("실행되지 않으면 다른 방식으로 전환해요.", style = MaterialTheme.typography.bodySmall)
        }
        TossPanel {
            Text("총 문맥 한도", style = MaterialTheme.typography.titleMedium)
            Text("${settings.contextTokens} 토큰", color = TossBlue, style = MaterialTheme.typography.titleLarge)
            Slider(
                value = settings.contextTokens.toFloat(),
                onValueChange = { actions.onContextTokens((it / 1024f).roundToInt() * 1024) },
                valueRange = ContextTokenPolicy.MIN_CONTEXT_TOKENS.toFloat()..contextLimit.toFloat(),
                enabled = contextLimit > ContextTokenPolicy.MIN_CONTEXT_TOKENS,
            )
            Text("이 기기 RAM 기준 안전 한도 안에서 선택해요.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = TossBorder)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("답변 길이 자동", style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.automaticAnswerLength, onCheckedChange = actions.onAutomaticAnswerLength)
            }
            Text("모델이 답을 마칠 때까지 생성해요. 기기의 문맥 한도는 적용됩니다.", style = MaterialTheme.typography.bodySmall)
            if (!settings.automaticAnswerLength) {
                Text("수동 최대 답변 길이", style = MaterialTheme.typography.titleMedium)
                Text("${settings.responseTokens} 토큰", color = TossBlue, style = MaterialTheme.typography.titleLarge)
                Slider(
                    value = settings.responseTokens.toFloat(),
                    onValueChange = { actions.onResponseTokens((it / 128f).roundToInt() * 128) },
                    valueRange = ContextTokenPolicy.MIN_RESPONSE_TOKENS.toFloat()..ContextTokenPolicy.responseLimit(settings.contextTokens).toFloat(),
                    enabled = ContextTokenPolicy.responseLimit(settings.contextTokens) > ContextTokenPolicy.MIN_RESPONSE_TOKENS,
                )
                Text("수동 상한에 도달하면 문장 중간에 멈출 수 있어요.", style = MaterialTheme.typography.bodySmall)
            }
        }
        TossPanel(color = TossBlueWeak) { Text("바꾼 설정은 다음 질문부터 적용돼요.", style = MaterialTheme.typography.bodyMedium) }
        TossPrimaryButton(if (state.settingsSaved) "저장했어요" else "설정 저장", actions.onSaveSettings)
    }
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
