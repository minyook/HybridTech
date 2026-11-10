/* 소스 안내
 * 화면 이벤트를 문맥·검색·생성·기록·다운로드·AP로 조정하고 Compose용 StateFlow를 제공한다.
 * 연결 모듈: com.minyook.sllm2.data.ChatHistoryStore, com.minyook.sllm2.data.KnowledgeRepository, com.minyook.sllm2.data.KnowledgeSeeder, com.minyook.sllm2.data.KnowledgeChunk, com.minyook.sllm2.data.DocumentAnswerPlan 등.
 */
package com.minyook.sllm2.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.minyook.sllm2.data.ChatHistoryStore
import com.minyook.sllm2.data.KnowledgeRepository
import com.minyook.sllm2.data.KnowledgeSeeder
import com.minyook.sllm2.data.KnowledgeChunk
import com.minyook.sllm2.data.DocumentAnswerPlan
import com.minyook.sllm2.data.AnswerSupplementVerifier
import com.minyook.sllm2.data.HybridRetrieval
import com.minyook.sllm2.data.SemanticEncoder
import com.minyook.sllm2.data.SearchSuggestion
import com.minyook.sllm2.data.ConversationContext
import com.minyook.sllm2.data.ConversationTurn
import com.minyook.sllm2.data.GeneralKnowledgeAnswer
import com.minyook.sllm2.gas.WifiApHub
import com.minyook.sllm2.gas.WifiApUiState
import com.minyook.sllm2.gas.ApPhase
import com.minyook.sllm2.gas.GasMonitoringService
import com.minyook.sllm2.gas.GasReading
import com.minyook.sllm2.gas.GasReadingSource
import com.minyook.sllm2.gas.GasReadingStore
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
import com.minyook.sllm2.model.LocalModel
import com.minyook.sllm2.model.NpuModel
import com.minyook.sllm2.model.GenerationMetrics
import com.minyook.sllm2.model.EngineReusePolicy
import com.minyook.sllm2.model.ModelDownloadAccess
import com.minyook.sllm2.model.ModelComparisonReport
import com.minyook.sllm2.model.ModelComparisonRunner
import com.minyook.sllm2.model.ModelComparisonStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class AppDestination {
    HOME, CHAT, HISTORY, SOURCE, VOICE, SENSOR, SENSOR_DISCONNECTED,
    CHECKLIST, LIBRARY, MODEL, INFERENCE, DATA, SETTINGS, SYSTEM, LOCAL_CHECKLIST, WORK_SERVER, SUPERVISOR,
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
    val startedAtElapsedRealtime: Long = 0L,
    val sourceIds: List<Long> = emptyList(),
    val sourceLabels: Map<Long, String> = emptyMap(),
    val suggestions: List<SearchSuggestion> = emptyList(),
    val resolvedQuestion: String? = null,
    val topicQuestion: String? = null,
    val answerStatus: String? = null,
    val animateAnswer: Boolean = false,
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
    val modelStatuses: Map<LocalModel, ModelStatus> = emptyMap(),
    val npuModel: NpuModel? = null,
    val npuStatus: ModelStatus = ModelStatus(ModelPhase.NOT_INSTALLED, 0),
    val npuProgress: Int? = null,
    val lastInference: GenerationMetrics? = null,
    val comparison: ModelComparisonReport? = null,
    val isComparing: Boolean = false,
    val comparisonProgress: String = "",
    val comparisonPreview: String = "",
    val comparisonStopRequested: Boolean = false,
    val deviceProfile: DeviceProfile? = null,
    val settings: InferenceSettings = InferenceSettings(),
    val settingsSaved: Boolean = false,
    val gasReading: GasReading = GasReading(),
    val gasStatus: String = "센서 연결 대기 · 실제 측정값만 표시",
    val wifiAp: WifiApUiState = WifiApUiState(),
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
    val workServerUrl: String = "",
)

/**
 * 화면 이벤트를 문서 검색·로컬 생성·기록·다운로드·AP 기능으로 연결하는 조정 계층.
 *
 * MainActivity의 FieldGuardActions가 진입점이며 Compose에는 읽기 전용 [uiState]를 제공한다.
 * 질문은 ConversationContext → KnowledgeRepository.searchQuestion → DocumentAnswerPlan 순으로 처리한다.
 * 문서 본문은 먼저 표시하고, 필요한 일반 설명만 LocalModelRuntime으로 생성해 검사 후 추가한다.
 * 최종 Markdown과 출처/대화 문맥은 ChatHistoryStore에 저장한다. AP의 실제 수명은 foreground service가,
 * 음성 장치의 수명은 Activity의 VoiceConversationController가 소유한다.
 */
class FieldGuardViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val modelPreferences = ModelPreferences(appContext)
    private val inferencePreferences = InferenceSettingsPreferences(appContext)
    private val comparisonStore = ModelComparisonStore(appContext)
    @Volatile private var stopComparison = false
    private val repository = KnowledgeRepository()
    private val runtime = LocalModelRuntime(appContext)
    private var idlePreparation: Job? = null
    private var attemptedPreparation: Triple<LocalModel, InferenceBackend, Int>? = null
    @Volatile private var appForeground = true
    private val semanticEncoder = SemanticEncoder(appContext)
    private val questionSearchIndex by lazy { appContext.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use { it.readText() } }
    private val history = ChatHistoryStore(appContext)
    private val uiPreferences = appContext.getSharedPreferences("field_guard_ui", Context.MODE_PRIVATE)

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(
        FieldGuardUiState(
            destination = AppDestination.HOME,
            onboardingComplete = uiPreferences.getBoolean("onboarding_complete", false),
            checklistChecked = (0..4).filterTo(mutableSetOf()) { uiPreferences.getBoolean("check_$it", false) },
            checklistSaved = uiPreferences.getBoolean("checklist_saved", false),
            workServerUrl = uiPreferences.getString("work_server_url", "") ?: "",
            modelStatus = modelPreferences.status(),
            modelStatuses = LocalModel.entries.associateWith { modelPreferences.status(it) },
            comparison = comparisonStore.load(),
            deviceProfile = DeviceProfile.read(appContext),
            settings = inferencePreferences.load(),
            histories = history.list(),
            gasReading = GasReadingStore.current(appContext),
            lastGasReading = GasReadingStore.current(appContext),
            gasStatus = gasStatusFor(GasReadingStore.current(appContext)),
        ),
    )
    val uiState: kotlinx.coroutines.flow.StateFlow<FieldGuardUiState> = _uiState

    private val workManager = WorkManager.getInstance(appContext)
    private val modelWork = workManager.getWorkInfosByTagLiveData(ModelDownloadScheduler.TAG)
    private var modelWorkInfos = emptyList<WorkInfo>()
    private val workObserver = Observer<List<WorkInfo>> { infos ->
        modelWorkInfos = infos.orEmpty()
        refreshModelStatus()
    }

    /** WorkManager 진행률과 디스크의 모델 상태를 합친다. READY는 파일 저장 상태이며 엔진 적재 완료가 아니다. */
    private fun refreshModelStatus() {
        val model = _uiState.value.settings.model
        fun workFor(selected: LocalModel, npu: NpuModel? = null) = modelWorkInfos.filter { info ->
            val matchingModel = ModelDownloadScheduler.modelTag(selected) in info.tags ||
                (selected == LocalModel.GEMMA4_E2B && info.tags.none { it.startsWith("model:") })
            matchingModel && (ModelDownloadScheduler.artifactTag(npu) in info.tags ||
                (npu == null && info.tags.none { it.startsWith("artifact:") }))
        }
        val selectedWork = workFor(model)
        val running = selectedWork.firstOrNull { it.state == WorkInfo.State.RUNNING }
        val pending = selectedWork.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
        val downloaded = running?.progress?.getLong("downloadedBytes", 0L) ?: 0L
        val total = running?.progress?.getLong("totalBytes", -1L) ?: -1L
        val statuses = LocalModel.entries.associateWith { selected ->
            val saved = modelPreferences.status(selected)
            if (saved.phase == ModelPhase.DOWNLOADING && workFor(selected).none { !it.state.isFinished })
                saved.copy(phase = ModelPhase.FAILED, failureMessage = "이전 다운로드가 중단됐어요. 다시 다운로드해 주세요.")
            else saved
        }
        val status = if (running != null || pending) ModelStatus(ModelPhase.DOWNLOADING, downloaded) else statuses.getValue(model)
        val npu = NpuModel.forDevice(model, _uiState.value.deviceProfile?.socModel.orEmpty())
        val npuWork = workFor(model, npu).takeIf { npu != null }.orEmpty()
        val npuRunning = npuWork.firstOrNull { it.state == WorkInfo.State.RUNNING }
        val npuActive = npuWork.any { !it.state.isFinished }
        val npuTotal = npuRunning?.progress?.getLong("totalBytes", -1) ?: -1
        val npuDownloaded = npuRunning?.progress?.getLong("downloadedBytes", 0) ?: 0
        val npuSaved = npu?.let { modelPreferences.status(model, it) } ?: ModelStatus(ModelPhase.NOT_INSTALLED, 0)
        val npuStatus = when {
            npuActive -> ModelStatus(ModelPhase.DOWNLOADING, npuDownloaded)
            npuSaved.phase == ModelPhase.DOWNLOADING -> npuSaved.copy(phase = ModelPhase.FAILED, failureMessage = "이전 NPU 다운로드가 중단됐어요. 다시 시도해 주세요.")
            else -> npuSaved
        }
        updateState { it.copy(modelStatus = status, modelStatuses = statuses + (model to status),
            modelProgress = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else null,
            npuModel = npu, npuStatus = npuStatus,
            npuProgress = if (npuTotal > 0) ((npuDownloaded * 100) / npuTotal).toInt().coerceIn(0, 100) else null) }
        prepareIdleModel()
    }

    /** 채팅 진입 후 입력하는 동안 첫 모델 적재를 수행한다. 실패/부족 상태를 자동 재시도하지 않는다. */
    private fun prepareIdleModel() {
        val state = _uiState.value
        if (!appForeground || state.destination !in setOf(AppDestination.CHAT, AppDestination.VOICE) ||
            state.isAnswering || state.isComparing || state.modelStatus.phase != ModelPhase.READY || idlePreparation?.isActive == true) return
        val settings = inferencePreferences.load()
        if (settings.model != LocalModel.GEMMA4_E2B || settings.backend !in setOf(InferenceBackend.AUTO, InferenceBackend.GPU)) return
        val key = Triple(settings.model, settings.backend, settings.contextTokens)
        if (attemptedPreparation == key) return
        idlePreparation = viewModelScope.launch {
            delay(250) // 예시 질문을 바로 보냈다면 준비 작업보다 실제 검색을 먼저 처리한다.
            if (!appForeground || _uiState.value.isAnswering || _uiState.value.isComparing) return@launch
            attemptedPreparation = key
            try {
                withContext(Dispatchers.Default) {
                    semanticEncoder.release()
                    runtime.prepareForQuestion()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("FieldGuardGenerationLatency", "Idle preparation skipped: ${error.javaClass.simpleName}")
            }
        }
    }

    init {
        modelWork.observeForever(workObserver)
        seedKnowledge()
        viewModelScope.launch {
            WifiApHub.state.collect { ap ->
                val reading = ap.selected?.reading() ?: GasReading()
                updateState { it.copy(wifiAp = ap, gasReading = reading,
                    gasStatus = if(reading.isFresh()) gasStatusFor(reading) else "현재 측정값 없음 · ${ap.status}",
                    lastGasReading = if(reading.hasValues) reading else it.lastGasReading) }
            }
        }
    }

    fun navigate(destination: AppDestination) {
        if (_uiState.value.destination == AppDestination.INFERENCE && destination != AppDestination.INFERENCE) {
            persistSettings()
        }
        updateState { it.copy(destination = destination, settingsSaved = false) }
        prepareIdleModel()
    }

    fun completeOnboarding() {
        uiPreferences.edit().putBoolean("onboarding_complete", true).apply()
        updateState { it.copy(onboardingComplete = true, destination = AppDestination.HOME) }
    }

    fun saveWorkServer(value: String) {
        val address = canonicalWorkServerUrl(value, com.minyook.sllm2.BuildConfig.DEBUG)
        uiPreferences.edit().putString("work_server_url", address).apply()
        updateState { it.copy(workServerUrl = address, destination = AppDestination.CHECKLIST) }
    }

    fun newChat() {
        updateState {
        it.copy(
            destination = AppDestination.CHAT,
            activeChatId = null,
            messages = listOf(ChatMessageUi(text = WELCOME, fromWorker = false)),
            input = "",
            inputError = null,
        )
        }
        prepareIdleModel()
    }

    fun loadChat(id: String) {
        val session = history.find(id) ?: return
        updateState {
            it.copy(
                destination = AppDestination.CHAT,
                activeChatId = session.id,
                messages = session.turns.map { turn ->
                    ChatMessageUi(text = turn.text, fromWorker = turn.role == ChatHistoryStore.Role.USER, sourceIds = turn.sourceIds, suggestions=turn.suggestions, resolvedQuestion=turn.resolvedQuestion, topicQuestion=turn.topicQuestion, answerStatus=turn.answerStatus, sourceLabels = turn.sourceIds.mapNotNull { id -> repository.chunk(id)?.let { id to (it.sourceKind+" · "+it.pageNumber+"쪽") } }.toMap())
                },
                input = "",
                inputError = null,
                histories = history.list(),
            )
        }
        prepareIdleModel()
    }

    fun changeInput(input: String) = updateState { it.copy(input = input, inputError = null) }

    fun askPreset(question: String) {
        changeInput(question)
        submitQuestion()
    }

    /** 인식된 발화를 공통 질문 경로로 보낸다. 완료 콜백은 음성 상태 머신의 TTS/다음 듣기로 이어진다. */
    fun submitVoiceQuestion(question: String, onAnswer: ((String) -> Unit)? = null, onFailure: ((String) -> Unit)? = null) {
        if (_uiState.value.isAnswering || _uiState.value.isComparing) { onFailure?.invoke("진행 중인 답변이나 모델 비교가 끝난 뒤 다시 시작해 주세요."); return }
        if (_uiState.value.destination != AppDestination.VOICE) navigate(AppDestination.CHAT)
        changeInput(normalizeVoiceQuestion(question))
        submitQuestion(compactVoiceAnswer = true, speakAnswer = true, onAnswer = onAnswer, onFailure = onFailure)
    }

    fun showInputError(message: String) = updateState { it.copy(inputError = message) }

    fun openSource(id: Long) {
        repository.chunk(id)?.toSourceUi()?.let { source ->
            updateState {
                it.copy(
                    selectedSource = source,
                    sourceReturnDestination = when(it.destination){AppDestination.LIBRARY->AppDestination.LIBRARY;AppDestination.VOICE->AppDestination.VOICE;else->AppDestination.CHAT},
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
        if (_uiState.value.isAnswering || _uiState.value.isComparing) return
        history.clear()
        updateState { it.copy(histories = emptyList(), activeChatId = null, messages = listOf(ChatMessageUi(text = WELCOME, fromWorker = false))) }
    }

    /**
     * 텍스트·프리셋·음성 질문의 공통 처리 파이프라인.
     *
     * 1. 중복 전송을 막고 완료된 이전 턴으로 후속 질문의 대상/초점을 복원한다.
     * 2. IO 코루틴에서 검색하고 문서 본문·실제 출처를 먼저 uiState에 반영한다. 주제 선택 메뉴는 만들지 않는다.
     * 3. 검색이 완료된 미확인 주제만 일반 생성으로 연결한다. 검색 오류를 문서 부재로 취급하지 않는다.
     * 4. 일반 생성의 검증된 중간 본문을 갱신하거나, 명시적으로 요청한 문서 보충을 별도 검사한다.
     * 5. 최종 본문과 문맥을 저장하고 pending을 해제한다. 음성이면 완료 본문을 onAnswer로 돌려준다.
     *
     * UI의 글자 표시 버퍼는 별도 StreamingTextReveal이 담당한다. 이 함수는 최종 답변을 자르지 않는다.
     */
    fun submitQuestion(compactVoiceAnswer: Boolean = false, speakAnswer: Boolean = false, onAnswer: ((String) -> Unit)? = null, onFailure: ((String) -> Unit)? = null) {
        if (_uiState.value.isAnswering || _uiState.value.isComparing) return
        val asked = _uiState.value.input.trim()
        if (asked.isBlank()) {
            updateState { it.copy(inputError = "확인할 내용을 입력해 주세요") }
            onFailure?.invoke("질문을 정확히 인식하지 못했어요. 다시 시작해 주세요.")
            return
        }
        // 표시 중인 미완성 답변은 문맥에 넣지 않는다. 사용자가 쓴 원문과 검색용으로 복원한 질문을 구분한다.
        val conversation = _uiState.value.messages.filterNot { it.pending }.map { ConversationTurn(it.fromWorker, it.text, it.resolvedQuestion, it.topicQuestion, it.answerStatus, it.sourceIds, it.suggestions) }
        val request = ConversationContext.resolve(asked, conversation)
        val resolved = request.query
        val chatId = _uiState.value.activeChatId ?: history.create(asked).id
        val pending = ChatMessageUi(
            text = "",
            fromWorker = false,
            pending = true,
            startedAtElapsedRealtime = SystemClock.elapsedRealtime(),
        )
        updateState {
            it.copy(
                activeChatId = chatId,
                messages = it.messages + ChatMessageUi(text = asked, fromWorker = true, resolvedQuestion=resolved, topicQuestion=request.topic) + pending,
                input = "",
                inputError = null,
                isAnswering = true,
                histories = history.list(),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
            val firstBodyRecorded=java.util.concurrent.atomic.AtomicBoolean(false)
            fun recordFirstBody(origin:String){if(firstBodyRecorded.compareAndSet(false,true))android.util.Log.i("FieldGuardAnswerLatency","firstBodyStateMs=${SystemClock.elapsedRealtime()-pending.startedAtElapsedRealtime} origin=$origin")}
            history.append(chatId, ChatHistoryStore.Role.USER, asked, resolvedQuestion=resolved, topicQuestion=request.topic)
            val searchStarted=SystemClock.elapsedRealtime()
            // 정확한 제목/대상은 빠른 경로를 사용한다. 의미 벡터가 필요할 때만 인코더와 추론 메모리를 조정한다.
            val resolution = repository.searchQuestion(request,questionSearchIndex){query->
                semanticEncoder.cachedVector(query) ?: run {
                    // 이 동기 콜백은 IO에서 실행한다. 의미 벡터가 필요할 때만 native 준비 종료를
                    // 기다린다. 제목/직접 문서 응답은 모델 준비가 진행 중이어도 바로 반환한다.
                    kotlinx.coroutines.runBlocking { idlePreparation?.cancelAndJoin() }
                    runtime.prepareForSemanticSearch()
                    semanticEncoder.encode(query)
                }
            }
            android.util.Log.i("FieldGuardAnswerLatency","searchMs=${SystemClock.elapsedRealtime()-searchStarted} method=${resolution.method} status=${resolution.status} complete=${resolution.searchComplete}")
            val sources = resolution.sources
            val basePlan = DocumentAnswerPlan.create(request.answerQuestion, resolution, repository.documentCount() > 0)
            val plan = if(sources.isEmpty()) basePlan.copy(body=resolution.message) else if(resolution.message.isNotBlank())basePlan.copy(body=basePlan.body+"\n\n"+resolution.message) else basePlan
            val sourceIds = plan.sourceIds
            withContext(Dispatchers.Main) {
                updateState { state -> state.copy(messages = state.messages.map { message -> if (message.id == pending.id) message.copy(text = plan.body, sourceIds = sourceIds, sourceLabels = plan.sourceLabels, suggestions = resolution.suggestions, topicQuestion = request.topic, answerStatus = resolution.status) else message }) }
                if(sources.isNotEmpty())recordFirstBody("document")
            }
            // generalQuestion은 검색 조정기가 허용한 주제다. 단순히 sources가 비었다는 이유로 생성하지 않는다.
            val generalQuestion=resolution.generalQuestion.takeIf{resolution.searchComplete}?.let { if(sources.isEmpty() && request.answerQuestion != request.query) request.answerQuestion else it }
            val general = if(generalQuestion!=null){
                if(modelPreferences.status().phase!=ModelPhase.READY)GeneralKnowledgeAnswer.NOTICE+"\n\n일반 지식 답변에 사용할 로컬 AI 모델이 필요해요. 설정에서 모델을 다운로드해 주세요."
                else {
                var lastGeneralBody: String? = null
                var outputLimitReached = false
                try{
                    semanticEncoder.release()
                    val raw = coroutineScope {
                        // Snapshots contain the entire prefix. A newer one can replace any
                        // waiting preview without losing tokens from the final answer.
                        val previews = Channel<String>(Channel.CONFLATED)
                        launch(Dispatchers.Default) {
                            val preview = GeneralKnowledgeAnswer.StreamingPreview(generalQuestion)
                            for (partial in previews) {
                                val body = preview.next(partial) ?: continue
                                lastGeneralBody = body
                                val combined = if (sources.isEmpty()) body else plan.body + "\n\n" + body
                                updateState { state ->
                                    state.copy(messages = state.messages.map {
                                        if (it.id == pending.id) it.copy(text = combined, animateAnswer = true) else it
                                    })
                                }
                                recordFirstBody("general")
                            }
                        }
                        try {
                            runtime.generate(
                                generalQuestion, plan.context(), compactVoiceAnswer,
                                generalKnowledge = true,
                                recentConversation = request.turns,
                                latestUserQuestion = request.asked,
                                onMetrics = ::recordInferenceMetrics,
                                onOutputLimit = { outputLimitReached = it },
                                onPartialAnswer = { previews.trySend(it) },
                            )
                        } finally {
                            previews.close()
                        }
                    }
                    GeneralKnowledgeAnswer.finish(raw,generalQuestion,outputLimitReached)
                }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){(lastGeneralBody ?: GeneralKnowledgeAnswer.NOTICE)+"\n\n> 답변 생성이 중단됐어요. 위 내용은 완료된 답변의 일부예요. 모델 상태를 확인한 뒤 다시 질문해 주세요."}
                }
            }else ""
            // 문서 보충은 일반 지식과 별도 계약이다. 근거 ID/수치/조건을 검사한 짧은 설명만 본문 뒤에 붙인다.
            val supplement = if (generalQuestion==null && resolution.status=="answered" && plan.supplementRequested && modelPreferences.status().phase == ModelPhase.READY) {
                try {
                    semanticEncoder.release()
                    val raw = runtime.generate(request.answerQuestion, plan.context(), compactVoiceAnswer, supplementOnly = true, recentConversation = request.turns, latestUserQuestion = request.asked)
                    AnswerSupplementVerifier.fromJson(raw, plan)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { "" }
            } else ""
            val response = if(general.isNotBlank()){if(sources.isEmpty())general else plan.body+"\n\n"+general} else AnswerSupplementVerifier.compose(plan, supplement)
            android.util.Log.i("FieldGuardAnswerLatency","answerMs=${SystemClock.elapsedRealtime()-searchStarted} origin=${if(general.isNotBlank())"general-or-mixed" else "document"}")
            // 저장·복사·TTS가 같은 최종 본문을 쓰도록 애니메이션용 일부 텍스트 대신 완성된 response를 저장한다.
            history.append(chatId, ChatHistoryStore.Role.ASSISTANT, response, sourceIds, suggestions=resolution.suggestions, topicQuestion=request.topic, answerStatus=resolution.status)
            val savedHistories = history.list()
            withContext(Dispatchers.Main) {
                updateState { state ->
                    state.copy(
                        messages = state.messages.map { if (it.id == pending.id) it.copy(text = response, pending = false, sourceIds = sourceIds, sourceLabels = plan.sourceLabels, suggestions = resolution.suggestions, topicQuestion = request.topic, answerStatus = resolution.status) else it },
                        isAnswering = false,
                        histories = savedHistories,
                    )
                }
                if (speakAnswer) onAnswer?.invoke(response)
                prepareIdleModel()
            }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { withContext(Dispatchers.Main) {
                val message = "문서 답변 처리에 실패했어요. 다시 시작해 주세요."
                updateState { state -> state.copy(isAnswering = false, inputError = message, messages = state.messages.map { if (it.id == pending.id) it.copy(text = message, pending = false) else it }) }
                onFailure?.invoke(message)
            } } finally { if(!appForeground) { semanticEncoder.release();runtime.release() } }
        }
    }

    fun updateBackend(backend: InferenceBackend) = updateSettings { it.copy(backend = backend) }

    fun updateContextTokens(tokens: Int) = updateSettings { current ->
        val limit = ContextTokenPolicy.deviceLimit(appContext, current.model)
        val context = tokens.coerceIn(ContextTokenPolicy.MIN_CONTEXT_TOKENS, limit)
        current.copy(
            contextTokens = context,
            responseTokens = current.responseTokens.coerceAtMost(ContextTokenPolicy.responseLimit(context)),
        )
    }

    fun updateResponseTokens(tokens: Int) = updateSettings { current ->
        current.copy(responseTokens = tokens.coerceIn(ContextTokenPolicy.MIN_RESPONSE_TOKENS, ContextTokenPolicy.responseLimit(current.contextTokens)))
    }

    fun updateAutomaticAnswerLength(enabled: Boolean) = updateSettings { it.copy(automaticAnswerLength = enabled) }

    fun saveSettings() {
        if (_uiState.value.isAnswering || _uiState.value.isComparing) return
        val settings = _uiState.value.settings
        val previous = inferencePreferences.load()
        inferencePreferences.save(settings)
        if (EngineReusePolicy.requiresReload(previous, settings) && idlePreparation?.isActive != true) runtime.release()
        updateState { it.copy(settingsSaved = true) }
    }

    fun selectModel(model: LocalModel) {
        if (_uiState.value.isAnswering || _uiState.value.isComparing) return
        val current = _uiState.value.settings
        val context = current.contextTokens.coerceAtMost(ContextTokenPolicy.deviceLimit(appContext, model))
        val selected = current.copy(model = model, contextTokens = context,
            responseTokens = current.responseTokens.coerceAtMost(ContextTokenPolicy.responseLimit(context)))
        inferencePreferences.save(selected)
        runtime.release()
        updateState { it.copy(settings = selected, settingsSaved = true, lastInference = null) }
        refreshModelStatus()
    }

    private fun recordInferenceMetrics(metrics: GenerationMetrics) {
        android.util.Log.i("FieldGuardGenerationLatency", metrics.toString())
        updateState { it.copy(lastInference = metrics) }
    }

    /** 저장 공간/접근 토큰을 확인하고 모델별 고유 WorkManager 작업을 예약한다. HTTP 전송은 Worker가 맡는다. */
    fun downloadModel(token: String = "", useNpu: Boolean = false) {
        val model = _uiState.value.settings.model
        val npu = if (useNpu) _uiState.value.npuModel ?: return else null
        val profile = DeviceProfile.read(appContext)
        val requiredGb = if (model == LocalModel.GEMMA4_E2B) 4 else 1
        if (profile.availableStorageGb < requiredGb) {
            val message = "## 저장 공간이 부족합니다\n\n${model.title} 설치에는 **${requiredGb}GB** 이상의 여유 공간이 필요합니다."
            updateState { it.copy(destination = AppDestination.CHAT, messages = it.messages + ChatMessageUi(text = message, fromWorker = false)) }
            return
        }
        if (token.isNotBlank()) {
            val saved = runCatching { ModelDownloadAccess(appContext).save(token) }
            if (saved.isFailure) {
                updateState { it.copy(modelStatus = ModelStatus(ModelPhase.FAILED, 0, "접근 토큰을 안전하게 저장하지 못했어요. 다시 시도해 주세요.")) }
                return
            }
        }
        ModelDownloadScheduler.enqueue(appContext, model, npu)
        updateState { if (useNpu) it.copy(npuStatus = ModelStatus(ModelPhase.DOWNLOADING, 0), npuProgress = null)
            else it.copy(modelStatus = ModelStatus(ModelPhase.DOWNLOADING, 0), modelProgress = null) }
    }

    /** 채팅/음성과 겹치지 않게 비교를 시작한다. 문항별 실제 생성과 보고서 저장은 ModelComparisonRunner가 맡는다. */
    fun startModelComparison() {
        if (_uiState.value.isAnswering || _uiState.value.isComparing) return
        val models = LocalModel.entries.filter { modelPreferences.status(it).phase == ModelPhase.READY }
        if (models.size < 2) {
            updateState { it.copy(comparisonProgress = "모델을 두 개 이상 다운로드한 뒤 비교해 주세요.") }
            return
        }
        val backend = _uiState.value.settings.backend
        stopComparison = false
        updateState { it.copy(isComparing = true, comparisonStopRequested = false, comparisonPreview = "", comparisonProgress = "현장 상세 비교를 준비하고 있어요.") }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                idlePreparation?.cancelAndJoin()
                runtime.release()
                semanticEncoder.release()
                val report = ModelComparisonRunner(appContext).run(models, backend, { stopComparison },
                    onPartial = { preview -> updateState { it.copy(comparisonPreview = preview) } }) { report, progress ->
                    comparisonStore.save(report)
                    updateState { it.copy(comparison = report, comparisonProgress = progress) }
                }
                comparisonStore.save(report)
                updateState { it.copy(comparison = report, comparisonProgress = if (report.phase == "complete") "모델 비교를 완료했어요." else "모델 비교를 중지했어요.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val report = _uiState.value.comparison?.copy(phase = if (stopComparison) "stopped" else "interrupted")
                report?.let { runCatching { comparisonStore.save(it) } }
                updateState { it.copy(comparison = report, comparisonProgress = if (stopComparison) "모델 비교를 중지했어요." else "모델 비교가 중단됐어요. 완료된 결과를 확인하고 다시 실행해 주세요.") }
            } finally {
                updateState { it.copy(isComparing = false, comparisonPreview = "") }
            }
        }
    }

    fun stopModelComparison() {
        stopComparison = true
        updateState { it.copy(comparisonStopRequested = true, comparisonProgress = "현재 답변이 끝나면 중지해요.") }
    }

    fun reviewModelComparison(index: Int, correct: Boolean) {
        if (_uiState.value.isComparing) return
        val current = _uiState.value.comparison ?: return
        if (index !in current.rows.indices) return
        val report = current.copy(rows = current.rows.mapIndexed { i, row -> if (i == index) row.copy(correct = correct) else row })
        val saved = runCatching { comparisonStore.save(report) }.isSuccess
        updateState { it.copy(comparison = report,
            comparisonProgress = if (saved) it.comparisonProgress else "평가를 기기에 저장하지 못했어요. JSON 복사로 보관해 주세요.") }
    }

    fun startAp() {
        runCatching { GasMonitoringService.start(appContext) }.onFailure {
            WifiApHub.update { it.copy(phase = ApPhase.ERROR, status = "AP를 시작하지 못했어요. 앱을 열어 다시 시도해 주세요.") }
        }
    }
    fun wifiPermissionDenied() {
        WifiApHub.update { it.copy(phase = ApPhase.ERROR, status = "AP 연결에 주변 기기 권한(이전 Android는 위치 권한)이 필요해요.") }
    }
    fun selectMeter(id: String) = WifiApHub.select(id)
    fun stopAp() = GasMonitoringService.stop(appContext)

    fun onAppForeground() { appForeground = true; prepareIdleModel() }
    fun releaseQuestionSearchMemory():kotlinx.coroutines.Job {
        appForeground = false
        attemptedPreparation = null
        val preparation = idlePreparation
        return viewModelScope.launch(Dispatchers.Default) {
            preparation?.cancelAndJoin()
            if (appForeground) return@launch
            semanticEncoder.release()
            if (!_uiState.value.isAnswering) runtime.release()
        }
    }

    fun persistSettings() {
        val current = _uiState.value.settings
        val previous = inferencePreferences.load()
        if (current != previous) {
            inferencePreferences.save(current)
            if (!_uiState.value.isAnswering && idlePreparation?.isActive != true && EngineReusePolicy.requiresReload(previous, current)) runtime.release()
        }
    }

    /** 번들 문서 버전을 ObjectBox에 반영하고 검색 인덱스·보관함 상태를 준비한다. UI 스레드를 점유하지 않는다. */
    private fun seedKnowledge() = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching { KnowledgeSeeder(appContext).seedIfNeeded() }
        val documents = runCatching { repository.documents().map { DocumentUi(it.id, it.title, it.chunkCount) } }
            .getOrDefault(emptyList())
        if(result.isSuccess)runCatching{repository.prepareQuestionIndex(questionSearchIndex)}
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
        if (it.isAnswering || it.isComparing) it else it.copy(settings = transform(it.settings), settingsSaved = false)
    }

    private fun updateState(transform: (FieldGuardUiState) -> FieldGuardUiState) {
        _uiState.update(transform)
    }

    override fun onCleared() {
        stopComparison = true
        persistSettings()
        modelWork.removeObserver(workObserver)
        semanticEncoder.release()
        runtime.release()
        super.onCleared()
    }

    private fun gasStatusFor(reading: GasReading): String = when {
        reading.source == GasReadingSource.SIMULATION -> "테스트 송신기 · 실제 측정값 아님"
        reading.receivedAtMillis > 0L -> "${reading.deviceName ?: "Wi-Fi 감지기"} · ${DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.KOREA).format(Date(reading.receivedAtMillis))}"
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
