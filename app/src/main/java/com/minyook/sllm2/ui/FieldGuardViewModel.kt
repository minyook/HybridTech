package com.minyook.sllm2.ui

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.minyook.sllm2.data.ChatHistoryStore
import com.minyook.sllm2.data.KnowledgeRepository
import com.minyook.sllm2.data.KnowledgeSeeder
import com.minyook.sllm2.data.KnowledgeChunk
import com.minyook.sllm2.gas.BleGasClient
import com.minyook.sllm2.gas.GasMonitoringService
import com.minyook.sllm2.gas.GasReading
import com.minyook.sllm2.gas.GasReadingSource
import com.minyook.sllm2.gas.GasReadingStore
import com.minyook.sllm2.gas.GasSimulationService
import com.minyook.sllm2.model.ContextTokenPolicy
import com.minyook.sllm2.model.DeviceProfile
import com.minyook.sllm2.model.InferenceBackend
import com.minyook.sllm2.model.InferenceSettings
import com.minyook.sllm2.model.InferenceSettingsPreferences
import com.minyook.sllm2.model.LocalModelRuntime
import com.minyook.sllm2.model.ModelDownloadScheduler
import com.minyook.sllm2.model.ModelPhase
import com.minyook.sllm2.model.ModelPreferences
import com.minyook.sllm2.model.ModelStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class AppDestination {
    HOME, CHAT, HISTORY, SOURCE, VOICE, SENSOR, SENSOR_DISCONNECTED,
    CHECKLIST, LIBRARY, MODEL, INFERENCE, DATA, SETTINGS, SYSTEM,
}

data class SourceUi(
    val id: Long,
    val documentId: String,
    val documentTitle: String,
    val pageNumber: Int,
    val heading: String,
    val body: String,
)

data class DocumentUi(val id: String, val title: String, val chunkCount: Int)

private fun KnowledgeChunk.toSourceUi() = SourceUi(id, documentId, documentTitle, pageNumber, heading, body)

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fromWorker: Boolean,
    val pending: Boolean = false,
    val sourceIds: List<Long> = emptyList(),
)

data class BleDeviceUi(
    val address: String,
    val name: String,
    val rssi: Int,
)

data class FieldGuardUiState(
    val destination: AppDestination = AppDestination.CHAT,
    val activeChatId: String? = null,
    val messages: List<ChatMessageUi> = listOf(
        ChatMessageUi(
            text = "안녕하세요. 밀폐공간 안전작업 가이드와 점검표를 근거로 답변합니다.\n\n무엇을 확인해 드릴까요?",
            fromWorker = false,
        ),
    ),
    val histories: List<ChatHistoryStore.Session> = emptyList(),
    val input: String = "",
    val inputError: String? = null,
    val isAnswering: Boolean = false,
    val knowledgeStatus: String = "제공 문서를 준비하는 중입니다…",
    val modelStatus: ModelStatus = ModelStatus(ModelPhase.NOT_INSTALLED, 0),
    val modelProgress: Int? = null,
    val deviceProfile: DeviceProfile? = null,
    val settings: InferenceSettings = InferenceSettings(),
    val settingsSaved: Boolean = false,
    val gasReading: GasReading = GasReading(),
    val gasStatus: String = "센서 연결 대기 · 실제 측정값만 표시",
    val bluetoothStatus: String = "연결된 가스 측정기가 없습니다.",
    val isScanning: Boolean = false,
    val devices: List<BleDeviceUi> = emptyList(),
    val onboardingComplete: Boolean = true,
    val documents: List<DocumentUi> = emptyList(),
    val selectedSource: SourceUi? = null,
    val sourceReturnDestination: AppDestination = AppDestination.CHAT,
    val libraryQuery: String = "",
    val libraryResults: List<SourceUi> = emptyList(),
    val checklistChecked: Set<Int> = emptySet(),
    val checklistSaved: Boolean = false,
    val sensorConnectionLost: Boolean = false,
    val lastGasReading: GasReading = GasReading(),
)

/**
 * UI-independent coordinator for the already proven ObjectBox RAG, LiteRT model,
 * WorkManager download and BLE/foreground service paths.  Compose only observes
 * [uiState] and sends explicit events back here.
 */
class FieldGuardViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val modelPreferences = ModelPreferences(appContext)
    private val inferencePreferences = InferenceSettingsPreferences(appContext)
    private val repository = KnowledgeRepository()
    private val runtime = LocalModelRuntime(appContext)
    private val history = ChatHistoryStore(appContext)
    private val uiPreferences = appContext.getSharedPreferences("field_guard_ui", Context.MODE_PRIVATE)
    private val scannedDevices = linkedMapOf<String, BluetoothDevice>()

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(
        FieldGuardUiState(
            destination = AppDestination.HOME,
            onboardingComplete = uiPreferences.getBoolean("onboarding_complete", false),
            checklistChecked = (0..4).filterTo(mutableSetOf()) { uiPreferences.getBoolean("check_$it", false) },
            checklistSaved = uiPreferences.getBoolean("checklist_saved", false),
            modelStatus = modelPreferences.status(),
            deviceProfile = DeviceProfile.read(appContext),
            settings = inferencePreferences.load(),
            histories = history.list(),
            gasReading = GasReadingStore.current(appContext),
            lastGasReading = GasReadingStore.current(appContext),
            gasStatus = gasStatusFor(GasReadingStore.current(appContext)),
        ),
    )
    val uiState: kotlinx.coroutines.flow.StateFlow<FieldGuardUiState> = _uiState

    private val gasReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                GasReadingStore.ACTION_READING_CHANGED -> updateGas(GasReadingStore.current(appContext))
                GasMonitoringService.ACTION_CONNECTION_STATUS -> updateState {
                    val message = intent.getStringExtra(GasMonitoringService.EXTRA_STATUS)
                        .orEmpty().ifBlank { it.bluetoothStatus }
                    val lost = message.contains("끊겼") || message.contains("연결 오류") || message.contains("연결을 해제")
                    it.copy(
                        bluetoothStatus = message,
                        sensorConnectionLost = lost,
                        lastGasReading = if (lost && it.gasReading.hasValues) it.gasReading else it.lastGasReading,
                        destination = if (lost && it.destination == AppDestination.SENSOR) AppDestination.SENSOR_DISCONNECTED else it.destination,
                    )
                }
            }
        }
    }

    private val bleClient = BleGasClient(appContext, object : BleGasClient.Listener {
        override fun onScanResult(device: BluetoothDevice, rssi: Int) {
            val name = deviceNameOrFallback(device, "이름 없는 BLE 기기")
            synchronized(scannedDevices) { scannedDevices[device.address] = device }
            updateState { state ->
                val devices = state.devices
                    .filterNot { it.address == device.address }
                    .plus(BleDeviceUi(device.address, name, rssi))
                    .sortedByDescending { it.rssi }
                state.copy(devices = devices, isScanning = true)
            }
        }

        override fun onConnectionStatus(message: String) = updateState {
            it.copy(bluetoothStatus = message, isScanning = message.contains("검색하는 중"), sensorConnectionLost = false)
        }

        override fun onReading(reading: GasReading) = updateGas(reading)
    })

    private val workManager = WorkManager.getInstance(appContext)
    private val modelWork = workManager.getWorkInfosForUniqueWorkLiveData(ModelDownloadScheduler.UNIQUE_WORK_NAME)
    private val workObserver = Observer<List<WorkInfo>> { infos ->
        val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
        val pending = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
        val status = modelPreferences.status()
        when {
            running != null -> {
                val downloaded = running.progress.getLong("downloadedBytes", 0L)
                val total = running.progress.getLong("totalBytes", -1L)
                val progress = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else null
                updateState { it.copy(modelStatus = ModelStatus(ModelPhase.DOWNLOADING, downloaded), modelProgress = progress) }
            }
            pending -> updateState { it.copy(modelStatus = ModelStatus(ModelPhase.DOWNLOADING, 0), modelProgress = null) }
            else -> {
                if (status.phase == ModelPhase.READY) runCatching { GasSimulationService.start(appContext) }
                updateState { it.copy(modelStatus = status, modelProgress = null) }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            gasReceiver,
            IntentFilter().apply {
                addAction(GasReadingStore.ACTION_READING_CHANGED)
                addAction(GasMonitoringService.ACTION_CONNECTION_STATUS)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        modelWork.observeForever(workObserver)
        seedKnowledge()
        if (_uiState.value.modelStatus.phase == ModelPhase.READY) {
            runCatching { GasSimulationService.start(appContext) }
        }
    }

    fun navigate(destination: AppDestination) {
        if (_uiState.value.destination == AppDestination.INFERENCE && destination != AppDestination.INFERENCE) {
            persistSettings()
        }
        updateState { it.copy(destination = destination, settingsSaved = false) }
    }

    fun completeOnboarding() {
        uiPreferences.edit().putBoolean("onboarding_complete", true).apply()
        updateState { it.copy(onboardingComplete = true, destination = AppDestination.HOME) }
    }

    fun newChat() = updateState {
        it.copy(
            destination = AppDestination.CHAT,
            activeChatId = null,
            messages = listOf(ChatMessageUi(text = WELCOME, fromWorker = false)),
            input = "",
            inputError = null,
        )
    }

    fun loadChat(id: String) {
        val session = history.find(id) ?: return
        updateState {
            it.copy(
                destination = AppDestination.CHAT,
                activeChatId = session.id,
                messages = session.turns.map { turn ->
                    ChatMessageUi(text = turn.text, fromWorker = turn.role == ChatHistoryStore.Role.USER, sourceIds = turn.sourceIds)
                },
                input = "",
                inputError = null,
                histories = history.list(),
            )
        }
    }

    fun changeInput(input: String) = updateState { it.copy(input = input, inputError = null) }

    fun askPreset(question: String) {
        changeInput(question)
        submitQuestion()
    }

    fun submitVoiceQuestion(question: String, onAnswer: ((String) -> Unit)? = null) {
        navigate(AppDestination.CHAT)
        changeInput(normalizeVoiceQuestion(question))
        submitQuestion(compactVoiceAnswer = true, speakAnswer = true, onAnswer = onAnswer)
    }

    fun showInputError(message: String) = updateState { it.copy(inputError = message) }

    fun openSource(id: Long) {
        repository.chunk(id)?.toSourceUi()?.let { source ->
            updateState {
                it.copy(
                    selectedSource = source,
                    sourceReturnDestination = if (it.destination == AppDestination.LIBRARY) AppDestination.LIBRARY else AppDestination.CHAT,
                    destination = AppDestination.SOURCE,
                )
            }
        }
    }

    fun openDocument(documentId: String) {
        repository.chunksForDocument(documentId).firstOrNull()?.toSourceUi()?.let { source ->
            updateState { it.copy(selectedSource = source, sourceReturnDestination = AppDestination.LIBRARY, destination = AppDestination.SOURCE) }
        }
    }

    fun adjacentSource(direction: Int) {
        val current = _uiState.value.selectedSource ?: return
        val chunks = repository.chunksForDocument(current.documentId)
        val index = chunks.indexOfFirst { it.id == current.id }
        chunks.getOrNull(index + direction)?.toSourceUi()?.let { source ->
            updateState { it.copy(selectedSource = source) }
        }
    }

    fun changeLibraryQuery(query: String) {
        updateState { it.copy(libraryQuery = query) }
        viewModelScope.launch(Dispatchers.IO) {
            val results = if (query.isBlank()) emptyList() else repository.retrieve(query, 8).map { it.chunk.toSourceUi() }
            withContext(Dispatchers.Main) {
                if (_uiState.value.libraryQuery == query) updateState { it.copy(libraryResults = results) }
            }
        }
    }

    fun toggleChecklist(index: Int) {
        if (index !in 0..4) return
        val checked = _uiState.value.checklistChecked.toMutableSet()
        if (!checked.add(index)) checked.remove(index)
        uiPreferences.edit().putBoolean("check_$index", index in checked).apply()
        uiPreferences.edit().putBoolean("checklist_saved", false).apply()
        updateState { it.copy(checklistChecked = checked, checklistSaved = false) }
    }

    fun saveChecklist() {
        uiPreferences.edit().putBoolean("checklist_saved", true).apply()
        updateState { it.copy(checklistSaved = true) }
    }

    fun clearChatHistory() {
        if (_uiState.value.isAnswering) return
        history.clear()
        updateState { it.copy(histories = emptyList(), activeChatId = null, messages = listOf(ChatMessageUi(text = WELCOME, fromWorker = false))) }
    }

    fun submitQuestion(compactVoiceAnswer: Boolean = false, speakAnswer: Boolean = false, onAnswer: ((String) -> Unit)? = null) {
        if (_uiState.value.isAnswering) return
        val asked = _uiState.value.input.trim()
        if (asked.isBlank()) {
            updateState { it.copy(inputError = "확인할 내용을 입력해 주세요") }
            return
        }
        val chatId = _uiState.value.activeChatId ?: history.create(asked).id
        val pending = ChatMessageUi(text = "", fromWorker = false, pending = true)
        updateState {
            it.copy(
                activeChatId = chatId,
                messages = it.messages + ChatMessageUi(text = asked, fromWorker = true) + pending,
                input = "",
                inputError = null,
                isAnswering = true,
                histories = history.list(),
            )
        }
        history.append(chatId, ChatHistoryStore.Role.USER, asked)

        viewModelScope.launch(Dispatchers.IO) {
            val sourceIds = repository.retrieve(asked, limit = 2).map { it.chunk.id }
            val fallback = repository.answerWithoutModel(asked)
            val modelAnswer = if (modelPreferences.status().phase == ModelPhase.READY) {
                try {
                    val generated = runtime.generate(asked, repository, compactVoiceAnswer) { partialAnswer ->
                        updateState { state ->
                            if (state.activeChatId != chatId || state.messages.none { it.id == pending.id }) state
                            else state.copy(messages = state.messages.map { message ->
                                if (message.id == pending.id) message.copy(text = partialAnswer) else message
                            })
                        }
                    }
                    generated.takeIf { it.isNotBlank() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            } else null
            val response = modelAnswer ?: if (modelPreferences.status().phase == ModelPhase.READY) {
                "$fallback\n\n> 생성 모델 응답을 받지 못해 문서 근거를 우선 보여드립니다."
            } else fallback
            val spokenResponse = if (modelAnswer != null) response else if (sourceIds.isEmpty()) {
                "제공 문서를 준비하고 있어요. 잠시 후 다시 질문해 주세요."
            } else {
                "로컬 AI 답변을 사용할 수 없어요. 관련 문서 근거를 화면에 표시했으니 확인해 주세요."
            }
            history.append(chatId, ChatHistoryStore.Role.ASSISTANT, response, sourceIds)
            withContext(Dispatchers.Main) {
                updateState { state ->
                    state.copy(
                        messages = state.messages.map { if (it.id == pending.id) pending.copy(text = response, pending = false, sourceIds = sourceIds) else it },
                        isAnswering = false,
                        histories = history.list(),
                    )
                }
                if (speakAnswer) onAnswer?.invoke(spokenResponse)
            }
        }
    }

    fun updateBackend(backend: InferenceBackend) = updateSettings { it.copy(backend = backend) }

    fun updateContextTokens(tokens: Int) = updateSettings { current ->
        val limit = ContextTokenPolicy.deviceLimit(appContext)
        val context = tokens.coerceIn(ContextTokenPolicy.MIN_CONTEXT_TOKENS, limit)
        current.copy(
            contextTokens = context,
            responseTokens = current.responseTokens.coerceAtMost(ContextTokenPolicy.responseLimit(context)),
        )
    }

    fun updateResponseTokens(tokens: Int) = updateSettings { current ->
        current.copy(responseTokens = tokens.coerceIn(ContextTokenPolicy.MIN_RESPONSE_TOKENS, ContextTokenPolicy.responseLimit(current.contextTokens)))
    }

    fun saveSettings() {
        val settings = _uiState.value.settings
        inferencePreferences.save(settings)
        runtime.release()
        updateState { it.copy(settingsSaved = true) }
    }

    fun downloadModel() {
        val profile = DeviceProfile.read(appContext)
        if (profile.availableStorageGb < 4) {
            val message = "## 저장 공간이 부족합니다\n\n모델 설치에는 최소 **4GB** 이상의 여유 공간이 필요합니다. 저장 공간을 확보한 뒤 다시 시도해 주세요."
            updateState { it.copy(destination = AppDestination.CHAT, messages = it.messages + ChatMessageUi(text = message, fromWorker = false)) }
            return
        }
        ModelDownloadScheduler.enqueue(appContext)
        updateState { it.copy(modelStatus = ModelStatus(ModelPhase.DOWNLOADING, 0), modelProgress = null) }
    }

    fun scanBluetooth() {
        synchronized(scannedDevices) { scannedDevices.clear() }
        updateState { it.copy(devices = emptyList(), bluetoothStatus = "주변 가스 측정기를 검색하는 중입니다…", isScanning = true, sensorConnectionLost = false) }
        bleClient.scan()
    }

    fun bluetoothPermissionDenied() = updateState {
        it.copy(bluetoothStatus = "가스 측정기 검색에는 Bluetooth 권한이 필요합니다.", isScanning = false, sensorConnectionLost = false, destination = AppDestination.SENSOR)
    }

    fun connectDevice(address: String, notificationsAllowed: Boolean) {
        val device = synchronized(scannedDevices) { scannedDevices[address] } ?: return
        val name = deviceNameOrFallback(device, "BLE 가스 측정기")
        bleClient.stopScan()
        GasMonitoringService.connect(appContext, address)
        updateState {
            it.copy(
                isScanning = false,
                sensorConnectionLost = false,
                bluetoothStatus = if (notificationsAllowed) "$name 연결을 시작했습니다…"
                else "$name 연결을 시작했습니다. 알림 패널 표시는 알림 권한을 허용하면 사용할 수 있습니다.",
            )
        }
    }

    fun disconnectDevice() {
        GasMonitoringService.disconnect(appContext)
        val last = _uiState.value.gasReading
        GasReadingStore.clear(appContext)
        updateState { it.copy(bluetoothStatus = "가스 측정기 연결을 해제했습니다.", isScanning = false, sensorConnectionLost = false, lastGasReading = last) }
    }

    fun persistSettings() {
        val current = _uiState.value.settings
        if (current != inferencePreferences.load()) {
            inferencePreferences.save(current)
            runtime.release()
        }
    }

    private fun seedKnowledge() = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching { KnowledgeSeeder(appContext).seedIfNeeded() }
        val documents = runCatching { repository.documents().map { DocumentUi(it.id, it.title, it.chunkCount) } }
            .getOrDefault(emptyList())
        withContext(Dispatchers.Main) {
            updateState {
                it.copy(
                    documents = documents,
                    knowledgeStatus = result.fold(
                        onSuccess = { count -> "제공 문서 2개 · ${count}개 근거 조각을 기기에 저장했습니다" },
                        onFailure = { "지식베이스 준비에 실패했습니다. 앱을 다시 시작해 주세요." },
                    ),
                )
            }
        }
    }

    private fun updateSettings(transform: (InferenceSettings) -> InferenceSettings) = updateState {
        it.copy(settings = transform(it.settings), settingsSaved = false)
    }

    private fun updateGas(reading: GasReading) = updateState {
        it.copy(
            gasReading = reading,
            gasStatus = gasStatusFor(reading),
            lastGasReading = if (reading.hasValues) reading else it.lastGasReading,
        )
    }

    private fun updateState(transform: (FieldGuardUiState) -> FieldGuardUiState) {
        _uiState.update(transform)
    }

    private fun deviceNameOrFallback(device: BluetoothDevice, fallback: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return fallback
        return try {
            device.name?.takeIf { it.isNotBlank() } ?: fallback
        } catch (_: SecurityException) {
            fallback
        }
    }

    override fun onCleared() {
        persistSettings()
        modelWork.removeObserver(workObserver)
        runCatching { appContext.unregisterReceiver(gasReceiver) }
        bleClient.close()
        runtime.release()
        super.onCleared()
    }

    private fun gasStatusFor(reading: GasReading): String = when {
        reading.source == GasReadingSource.SIMULATION -> "데모 시뮬레이션 · 실제 측정값 아님"
        reading.receivedAtMillis > 0L -> "${reading.deviceName ?: "BLE 측정기"} · ${DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.KOREA).format(Date(reading.receivedAtMillis))}"
        else -> "센서 연결 대기 · 실제 측정값만 표시"
    }

    private fun normalizeVoiceQuestion(spoken: String): String = spoken
        .replace(WAKE_WORD, "")
        .replace(Regex("^(?:저기|음+|어+)[, ]*"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', ',', '.', '?', '!')

    private companion object {
        const val WELCOME = "안녕하세요. 밀폐공간 안전작업 가이드와 점검표를 근거로 답변합니다.\n\n무엇을 확인해 드릴까요?"
        val WAKE_WORD = Regex("(?i)(오\\s*투\\s*야|o\\s*2\\s*야)")
    }
}
