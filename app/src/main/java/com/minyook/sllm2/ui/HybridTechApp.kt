package com.minyook.sllm2.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minyook.sllm2.data.ChatHistoryStore
import com.minyook.sllm2.gas.GasReading
import com.minyook.sllm2.model.ContextTokenPolicy
import com.minyook.sllm2.model.InferenceBackend
import com.minyook.sllm2.model.ModelPhase
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class FieldGuardActions(
    val onNavigate: (AppDestination) -> Unit,
    val onNewChat: () -> Unit,
    val onLoadChat: (String) -> Unit,
    val onInputChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onPreset: (String) -> Unit,
    val onDownloadModel: () -> Unit,
    val onBackend: (InferenceBackend) -> Unit,
    val onContextTokens: (Int) -> Unit,
    val onResponseTokens: (Int) -> Unit,
    val onSaveSettings: () -> Unit,
    val onScanBluetooth: () -> Unit,
    val onConnectDevice: (String) -> Unit,
    val onDisconnectDevice: () -> Unit,
    val onVoiceClick: () -> Unit,
    val onVoiceLongClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HybridTechApp(
    state: FieldGuardUiState,
    voice: VoiceUiState,
    actions: FieldGuardActions,
) {
    HybridTechTheme {
        val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        BackHandler(enabled = drawerState.isOpen || state.destination != AppDestination.CHAT) {
            when {
                drawerState.isOpen -> scope.launch { drawerState.close() }
                else -> actions.onNavigate(AppDestination.CHAT)
            }
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                FieldGuardDrawer(
                    state = state,
                    onNewChat = {
                        actions.onNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onNavigate = {
                        actions.onNavigate(it)
                        scope.launch { drawerState.close() }
                    },
                    onHistory = {
                        actions.onLoadChat(it)
                        scope.launch { drawerState.close() }
                    },
                )
            },
        ) {
            val title = when (state.destination) {
                AppDestination.CHAT -> "O₂ Field Guard"
                AppDestination.SENSOR -> "센서 연결 상태"
                AppDestination.SETTINGS -> "설정"
            }
            val subtitle = when (state.destination) {
                AppDestination.CHAT -> state.knowledgeStatus
                AppDestination.SENSOR -> "Bluetooth LE 가스 검출기"
                AppDestination.SETTINGS -> "온디바이스 모델 및 추론"
            }
            Scaffold(
                containerColor = if (state.destination == AppDestination.SETTINGS) Color(0xFF101010) else HybridChatCanvas,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(title, style = MaterialTheme.typography.titleLarge, color = if (state.destination == AppDestination.SETTINGS) Color.White else HybridInk)
                                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = if (state.destination == AppDestination.SETTINGS) Color(0xFFB8C7C5) else HybridMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.testTag("openDrawer")) {
                                Text("☰", fontSize = 25.sp, color = if (state.destination == AppDestination.SETTINGS) Color.White else HybridInk)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (state.destination == AppDestination.SETTINGS) Color(0xFF101010) else HybridChatCanvas),
                    )
                },
            ) { padding ->
                when (state.destination) {
                    AppDestination.CHAT -> ChatScreen(state, voice, actions, padding)
                    AppDestination.SENSOR -> SensorScreen(state, actions, padding)
                    AppDestination.SETTINGS -> SettingsScreen(state, actions, padding)
                }
            }
        }
    }
}

@Composable
private fun FieldGuardDrawer(
    state: FieldGuardUiState,
    onNewChat: () -> Unit,
    onNavigate: (AppDestination) -> Unit,
    onHistory: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxHeight().fillMaxWidth(0.84f).widthIn(min = 180.dp, max = 304.dp),
        color = HybridDark,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
            Text("O₂", color = HybridTeal, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("HYBRIDTECH", color = HybridOnDark, style = MaterialTheme.typography.labelLarge)
            Text("O₂ FIELD GUARD", color = HybridOnDark, style = MaterialTheme.typography.titleMedium)
            Text("밀폐공간 안전 운영 콘솔 · DEVICE ONLY", color = HybridOnDarkSoft, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(20.dp))
            Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) { Text("＋ 새 대화") }
            Spacer(Modifier.height(12.dp))
            DrawerDestination("▣", "문서 채팅", state.destination == AppDestination.CHAT) { onNavigate(AppDestination.CHAT) }
            DrawerDestination("◉", "센서 연결 상태", state.destination == AppDestination.SENSOR) { onNavigate(AppDestination.SENSOR) }
            DrawerDestination("⚙", "설정", state.destination == AppDestination.SETTINGS) { onNavigate(AppDestination.SETTINGS) }
            Spacer(Modifier.height(12.dp))
            Text("저장된 대화", color = HybridOnDarkSoft, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 10.dp, bottom = 6.dp))
            if (state.histories.isEmpty()) {
                Text("저장된 대화가 없습니다", color = HybridOnDarkSoft, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp))
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(state.histories, key = { it.id }) { session ->
                        TextButton(
                            onClick = { onHistory(session.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(session.title, color = if (session.id == state.activeChatId) HybridOnDark else HybridOnDarkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = HybridDarkElevated), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("현장 작업자", color = HybridOnDark, style = MaterialTheme.typography.titleMedium)
                    Text("기기 단독 실행", color = HybridOnDarkSoft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DrawerDestination(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Text(symbol) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color.White,
            selectedTextColor = HybridInk,
            selectedIconColor = HybridInk,
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = HybridOnDarkSoft,
            unselectedIconColor = HybridOnDarkSoft,
        ),
    )
}

@Composable
private fun ChatScreen(state: FieldGuardUiState, voice: VoiceUiState, actions: FieldGuardActions, padding: PaddingValues) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showLatest by remember(state.messages.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last < state.messages.lastIndex - 1
        }
    }
    LaunchedEffect(state.messages.size, state.isAnswering) {
        if (!showLatest && state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        GasSummaryBar(state.gasReading, state.gasStatus)
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("conversationList"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.messages.size <= 1) {
                    item("quick-prompts") { QuickPrompts(actions.onPreset) }
                }
                items(state.messages, key = { it.id }) { message -> ChatBubble(message) }
            }
            if (showLatest) {
                OutlinedButton(onClick = { scope.launch { listState.scrollToLatest() } }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("scrollLatest"), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, HybridHairline)) {
                    Text("↓ 최근 답변으로", color = HybridTeal)
                }
            }
        }
        ChatComposer(state, voice, actions)
    }
}

private suspend fun LazyListState.scrollToLatest() {
    val last = layoutInfo.totalItemsCount - 1
    if (last >= 0) animateScrollToItem(last)
}

@Composable
private fun GasSummaryBar(reading: GasReading, status: String) {
    Surface(color = HybridDark, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LIVE GAS", color = HybridOnDark, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(status, color = HybridOnDarkSoft, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GasCompactValue("O₂", formatGas(reading.oxygenPercent, "%"))
                GasCompactValue("H₂S", formatGas(reading.h2sPpm, "ppm"))
                GasCompactValue("CO", formatGas(reading.carbonMonoxidePpm, "ppm"))
                GasCompactValue("LEL", formatGas(reading.lelPercent, "%LEL"))
            }
        }
    }
}

@Composable
private fun GasCompactValue(name: String, value: String) {
    Column {
        Text(name, color = HybridOnDarkSoft, style = MaterialTheme.typography.labelMedium)
        Text(value, color = HybridOnDark, fontFamily = GasNumberFont, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun QuickPrompts(onPreset: (String) -> Unit) {
    Column {
        Text("빠른 질문", color = HybridMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
        AssistChip(onClick = { onPreset("밀폐공간 작업 전 공기 측정 기준과 측정 시기를 알려줘") }, label = { Text("공기 측정 기준") })
        AssistChip(onClick = { onPreset("밀폐공간에서 작업자가 쓰러졌을 때 구조 절차를 알려줘") }, label = { Text("구조 절차") }, modifier = Modifier.padding(start = 6.dp))
        AssistChip(onClick = { onPreset("밀폐공간 작업허가 전에 어떤 안전조치를 확인해야 하나요?") }, label = { Text("작업허가 점검") }, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ChatBubble(message: ChatMessageUi) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromWorker) Arrangement.End else Arrangement.Start) {
        Card(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(if (message.fromWorker) 20.dp else 14.dp),
            colors = CardDefaults.cardColors(containerColor = if (message.fromWorker) HybridWorker else HybridAssistant),
            border = if (message.fromWorker) null else BorderStroke(1.dp, HybridHairline),
        ) {
            if (message.pending) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("제공 문서에서 근거를 찾는 중입니다…", color = HybridMuted, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (message.fromWorker) {
                Text(message.text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
            } else {
                MarkdownText(message.text, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatComposer(state: FieldGuardUiState, voice: VoiceUiState, actions: FieldGuardActions) {
    Surface(color = HybridCard, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = actions.onInputChange,
                modifier = Modifier.weight(1f).heightIn(min = 54.dp, max = 136.dp).testTag("chatInput"),
                placeholder = { Text(if (voice.wakeWordEnabled) "‘오투야’라고 부르면 음성 대화를 시작합니다" else "안전 작업에 대해 물어보세요") },
                supportingText = state.inputError?.let { { Text(it) } },
                isError = state.inputError != null,
                maxLines = 4,
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = if (voice.listening || voice.wakeWordEnabled) Color(0xFFFFE9E6) else Color(0xFFEAF3F7),
                modifier = Modifier.size(48.dp).testTag("voiceButton").combinedClickable(onClick = actions.onVoiceClick, onLongClick = actions.onVoiceLongClick),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(if (voice.listening || voice.wakeWordEnabled) "●" else "◉", color = if (voice.listening || voice.wakeWordEnabled) HybridDanger else HybridTeal) }
            }
            Spacer(Modifier.width(6.dp))
            FloatingActionButton(onClick = { if (!state.isAnswering) actions.onSubmit() }, containerColor = HybridTeal, modifier = Modifier.size(48.dp).testTag("sendMessage")) {
                Text("↑", color = Color.White, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun SensorScreen(state: FieldGuardUiState, actions: FieldGuardActions, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SensorGasCard(state.gasReading, state.gasStatus) }
        item {
            FieldCard {
                Text("Bluetooth LE 가스 검출기", style = MaterialTheme.typography.titleLarge)
                Text(state.bluetoothStatus, style = MaterialTheme.typography.bodyMedium, color = HybridMuted, modifier = Modifier.padding(top = 5.dp))
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions.onScanBluetooth, modifier = Modifier.testTag("scanBluetooth")) { Text(if (state.isScanning) "검색 중…" else "기기 검색") }
                    if (state.bluetoothStatus.startsWith("연결")) OutlinedButton(onClick = actions.onDisconnectDevice) { Text("연결 해제") }
                }
            }
        }
        if (state.devices.isNotEmpty()) {
            item { Text("검색된 기기", style = MaterialTheme.typography.titleMedium) }
            items(state.devices, key = { it.address }) { device ->
                FieldCard(onClick = { actions.onConnectDevice(device.address) }) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text("${device.address} · ${device.rssi} dBm", color = HybridMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                    Text("탭하여 연결", color = HybridTeal, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            Text("BLE 알림 채널의 JSON, key=value 또는 O₂/H₂S/CO/LEL 4열 CSV 패킷을 자동 해석합니다. 실제 계측값이 수신될 때만 저장·알림·위젯에 반영됩니다.", color = HybridMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SensorGasCard(reading: GasReading, status: String) {
    Card(colors = CardDefaults.cardColors(containerColor = HybridDark), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("실시간 가스 측정", color = HybridOnDark, style = MaterialTheme.typography.titleLarge)
            Text(status, color = HybridOnDarkSoft, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GasSensorValue("O₂", formatGas(reading.oxygenPercent, "%"))
                GasSensorValue("H₂S", formatGas(reading.h2sPpm, "ppm"))
                GasSensorValue("CO", formatGas(reading.carbonMonoxidePpm, "ppm"))
                GasSensorValue("LEL", formatGas(reading.lelPercent, "%LEL"))
            }
        }
    }
}

@Composable
private fun GasSensorValue(name: String, value: String) {
    Column {
        Text(name, color = HybridOnDarkSoft, style = MaterialTheme.typography.labelLarge)
        Text(value, color = HybridOnDark, fontFamily = GasNumberFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsScreen(state: FieldGuardUiState, actions: FieldGuardActions, padding: PaddingValues) {
    val profile = state.deviceProfile
    val contextLimit = profile?.let(ContextTokenPolicy::deviceLimit) ?: ContextTokenPolicy.MIN_CONTEXT_TOKENS
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { RuntimeStatusCard(state, contextLimit) }
        item { Text("모델 관리", color = Color.White, style = MaterialTheme.typography.headlineSmall) }
        item { ModelManagementCard(state, actions) }
        item { Text("추론 설정", color = Color.White, style = MaterialTheme.typography.headlineSmall) }
        item { InferenceSettingsCard(state, contextLimit, actions) }
    }
}

@Composable
private fun RuntimeStatusCard(state: FieldGuardUiState, safeLimit: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)), border = BorderStroke(1.dp, HybridTeal), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val phase = state.modelStatus.phase
            Text(
                when (phase) {
                    ModelPhase.READY -> "준비됨 · Gemma 4 E2B / ${state.settings.backend.label}"
                    ModelPhase.DOWNLOADING -> "다운로드 중 · ${state.modelProgress?.let { "$it%" } ?: "진행률 확인 중"}"
                    ModelPhase.FAILED -> "모델 준비 실패 · 다시 시도 필요"
                    ModelPhase.NOT_INSTALLED -> "문서 검색 모드 · 모델 준비 필요"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            if (phase == ModelPhase.DOWNLOADING) LinearProgressIndicator(progress = { (state.modelProgress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Text("RAM ${state.deviceProfile?.totalRamGb ?: "—"}GB · 저장 공간 ${state.deviceProfile?.availableStorageGb ?: "—"}GB\n문서 문맥 ${state.settings.contextTokens.tokenText()} / 이 기기 안전 한도 ${safeLimit.tokenText()} · 답변 ${state.settings.responseTokens.tokenText()}", color = Color(0xFFB8C7C5), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun ModelManagementCard(state: FieldGuardUiState, actions: FieldGuardActions) {
    DarkCard {
        Text("온디바이스 모델 카탈로그", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text("Gemma 4 E2B Instruct · 약 2.41 GB", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text("현장 대화와 안전 안내 기본 권장 모델 · 다운로드가 끝나면 추론은 오프라인으로 실행됩니다.", color = Color(0xFFB8C7C5), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        when (state.modelStatus.phase) {
            ModelPhase.READY -> Text("로컬 Gemma 준비됨 · ${state.settings.backend.label}", color = Color(0xFF69D99A), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            ModelPhase.DOWNLOADING -> Text("앱을 닫아도 다운로드를 이어갑니다.", color = Color(0xFFB8C7C5), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 14.dp))
            else -> Button(onClick = actions.onDownloadModel, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(if (state.modelStatus.phase == ModelPhase.FAILED) "다시 다운로드" else "앱에서 다운로드") }
        }
    }
}

@Composable
private fun InferenceSettingsCard(state: FieldGuardUiState, contextLimit: Int, actions: FieldGuardActions) {
    DarkCard {
        Text("실행 백엔드", color = Color.White, style = MaterialTheme.typography.titleMedium)
        InferenceBackend.entries.forEach { backend ->
            FilterChip(
                selected = state.settings.backend == backend,
                onClick = { actions.onBackend(backend) },
                label = { Text(backend.label) },
                modifier = Modifier.padding(end = 6.dp, top = 8.dp),
            )
        }
        SliderSetting(
            label = "총 컨텍스트 한도",
            description = "Gemma 원본 128,000 토큰 · 이 기기 안전 한도 안에서 선택",
            value = state.settings.contextTokens,
            min = ContextTokenPolicy.MIN_CONTEXT_TOKENS,
            max = contextLimit,
            step = 1_024,
            onChange = actions.onContextTokens,
        )
        SliderSetting(
            label = "최대 출력 길이",
            description = "4096 토큰도 최대치일 뿐, 질문에 필요한 답변만 생성합니다.",
            value = state.settings.responseTokens,
            min = ContextTokenPolicy.MIN_RESPONSE_TOKENS,
            max = ContextTokenPolicy.responseLimit(state.settings.contextTokens),
            step = 128,
            onChange = actions.onResponseTokens,
        )
        if (state.settingsSaved) Text("저장됨 · 다음 질문부터 적용됩니다.", color = Color(0xFF69D99A), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = actions.onSaveSettings, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("설정 저장") }
    }
}

@Composable
private fun SliderSetting(label: String, description: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    val actualMax = max.coerceAtLeast(min)
    Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
    Text("${value.tokenText()} 토큰", color = Color(0xFF69D99A), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 3.dp))
    Text(description, color = Color(0xFFB8C7C5), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    Slider(
        value = value.toFloat().coerceIn(min.toFloat(), actualMax.toFloat()),
        onValueChange = { raw -> onChange((raw / step).roundToInt() * step) },
        valueRange = min.toFloat()..actualMax.toFloat(),
        steps = ((actualMax - min) / step - 1).coerceAtLeast(0),
        enabled = actualMax > min,
    )
}

@Composable
private fun DarkCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), content = { content() })
    }
}

@Composable
private fun FieldCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val modifier = if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = HybridCard),
        border = BorderStroke(1.dp, HybridHairline),
        shape = RoundedCornerShape(16.dp),
    ) { Column(Modifier.padding(16.dp), content = { content() }) }
}

private fun formatGas(value: Double?, unit: String): String = value?.let {
    "${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.US, it)}$unit"
} ?: "—"

private fun Int.tokenText(): String = String.format(Locale.KOREA, "%,d", this)
