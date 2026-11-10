/* 소스 안내
 * Android Activity 진입점. Compose 상태 구독·UI 이벤트·권한·음성 컨트롤러와 앱 생명주기를 연결한다.
 * 연결 모듈: com.minyook.sllm2.ui.AppDestination, com.minyook.sllm2.ui.FieldGuardActions, com.minyook.sllm2.ui.FieldGuardViewModel, com.minyook.sllm2.ui.TossFieldGuardApp, com.minyook.sllm2.ui.VoiceConversationController.
 */
package com.minyook.sllm2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minyook.sllm2.ui.AppDestination
import com.minyook.sllm2.ui.FieldGuardActions
import com.minyook.sllm2.ui.FieldGuardViewModel
import com.minyook.sllm2.ui.TossFieldGuardApp
import com.minyook.sllm2.ui.VoiceConversationController

/**
 * 설치형 앱의 UI 진입점. Android 권한·생명주기와 기능 조정 계층을 연결한다.
 *
 * [FieldGuardViewModel.uiState]와 음성 상태를 수명주기에 맞춰 구독하고 [TossFieldGuardApp]에 전달한다.
 * 화면의 [FieldGuardActions]는 ViewModel 또는 [VoiceConversationController]의 명시적인 명령으로 연결한다.
 * 검색·추론·소켓 처리는 Activity에서 수행하지 않는다. 음성으로 인식한 질문도 submitVoiceQuestion을 거쳐
 * 텍스트와 같은 검색 경로로 들어간다. 화면 이탈 시 음성을 중지하고 검색/추론 자원 정리를 요청한다.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: FieldGuardViewModel by viewModels()
    private lateinit var voiceController: VoiceConversationController
    private var notificationPermissionContinuation: ((Boolean) -> Unit)? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voiceController.onMicrophonePermissionResult(granted)
    }
    private val wifiPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasWifiPermissions()) requestNotificationPermission { viewModel.startAp() }
        else viewModel.wifiPermissionDenied()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionContinuation?.invoke(granted)
        notificationPermissionContinuation = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemNavigationBar()
        voiceController = VoiceConversationController(
            context = this,
            hasMicrophonePermission = { hasPermission(Manifest.permission.RECORD_AUDIO) },
            requestMicrophonePermission = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
            onQuestion = { spoken, answer, failure -> viewModel.submitVoiceQuestion(spoken, answer, failure) },
            onError = viewModel::showInputError,
        )
        // UI는 상태만 읽고 이벤트를 돌려준다. 기능별 콜백의 실제 소유자는 아래 actions에서 확인한다.
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val voice by voiceController.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.destination) { voiceController.onScreenVisibility(state.destination in setOf(AppDestination.CHAT, AppDestination.VOICE)) }
            TossFieldGuardApp(
                state = state,
                voice = voice,
                actions = FieldGuardActions(
                    onNavigate = viewModel::navigate,
                    onNewChat = { voiceController.stopQuestion(); viewModel.newChat() },
                    onLoadChat = { id -> voiceController.stopQuestion(); viewModel.loadChat(id) },
                    onInputChange = viewModel::changeInput,
                    onSubmit = { voiceController.stopQuestion(); viewModel.submitQuestion() },
                    onPreset = { question -> voiceController.stopQuestion(); viewModel.askPreset(question) },
                    onDownloadModel = { requestNotificationPermission { viewModel.downloadModel() } },
                    onDownloadWithToken = { token -> requestNotificationPermission { viewModel.downloadModel(token) } },
                    onDownloadNpu = { token -> requestNotificationPermission { viewModel.downloadModel(token, useNpu = true) } },
                    onSelectModel = viewModel::selectModel,
                    onStartModelComparison = { voiceController.stopQuestion(); viewModel.startModelComparison() },
                    onStopModelComparison = viewModel::stopModelComparison,
                    onReviewModelComparison = viewModel::reviewModelComparison,
                    onBackend = viewModel::updateBackend,
                    onContextTokens = viewModel::updateContextTokens,
                    onResponseTokens = viewModel::updateResponseTokens,
                    onAutomaticAnswerLength = viewModel::updateAutomaticAnswerLength,
                    onSaveSettings = viewModel::saveSettings,
                    onStartAp = {
                        if (hasWifiPermissions()) requestNotificationPermission { viewModel.startAp() }
                        else wifiPermissions.launch(wifiPermissionList())
                    },
                    onSelectMeter = viewModel::selectMeter,
                    onStopAp = viewModel::stopAp,
                    onVoiceClick = { if (state.isAnswering || state.isComparing) viewModel.showInputError("답변이나 모델 비교가 끝난 뒤 음성 대화를 시작해 주세요.") else voiceController.startQuestion() },
                    onVoiceLongClick = { if (!state.isAnswering && !state.isComparing) voiceController.toggleWakeWord() },
                    onStopVoice = voiceController::stopQuestion,
                    onPrepareVoice = voiceController::prepareLocalLanguage,
                    onVoiceReadAnswers = voiceController::setReadAnswers,
                    onCompleteOnboarding = viewModel::completeOnboarding,
                    onOpenSource = viewModel::openSource,
                    onOpenDocument = viewModel::openDocument,
                    onAdjacentSource = viewModel::adjacentSource,
                    onLibraryQuery = viewModel::changeLibraryQuery,
                    onToggleChecklist = viewModel::toggleChecklist,
                    onSaveChecklist = viewModel::saveChecklist,
                    onClearChatHistory = viewModel::clearChatHistory,
                    onSaveWorkServer = viewModel::saveWorkServer,
                ),
            )
        }
    }

    override fun onPause() {
        viewModel.persistSettings()
        voiceController.onPause()
        super.onPause()
    }

    override fun onStop() {
        viewModel.releaseQuestionSearchMemory()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForeground()
        hideSystemNavigationBar()
        voiceController.onResume(viewModel.uiState.value.destination in setOf(AppDestination.CHAT, AppDestination.VOICE))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigationBar()
    }

    override fun onDestroy() {
        voiceController.destroy()
        super.onDestroy()
    }

    private fun requestNotificationPermission(after: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            after(true)
        } else {
            notificationPermissionContinuation = after
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasWifiPermissions(): Boolean = wifiPermissionList().all(::hasPermission)
    /** AP 생성 권한과 로컬 소켓 접근 권한은 OS 버전에 따라 달라 한 곳에서 계산한다. */
    private fun wifiPermissionList(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        else { add(Manifest.permission.ACCESS_COARSE_LOCATION); add(Manifest.permission.ACCESS_FINE_LOCATION) }
        if (Build.VERSION.SDK_INT >= 37) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }.toTypedArray()

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hideSystemNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
