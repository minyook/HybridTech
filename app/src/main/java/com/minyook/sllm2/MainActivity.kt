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

/** Compose-only activity shell. RAG, model, BLE and voice work live behind observable state. */
class MainActivity : ComponentActivity() {
    private val viewModel: FieldGuardViewModel by viewModels()
    private lateinit var voiceController: VoiceConversationController
    private var notificationPermissionContinuation: ((Boolean) -> Unit)? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voiceController.onMicrophonePermissionResult(granted)
    }
    private val bluetoothPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        if (hasBluetoothPermissions()) viewModel.scanBluetooth() else viewModel.bluetoothPermissionDenied()
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
                    onBackend = viewModel::updateBackend,
                    onContextTokens = viewModel::updateContextTokens,
                    onResponseTokens = viewModel::updateResponseTokens,
                    onAutomaticAnswerLength = viewModel::updateAutomaticAnswerLength,
                    onSaveSettings = viewModel::saveSettings,
                    onScanBluetooth = {
                        if (hasBluetoothPermissions()) viewModel.scanBluetooth()
                        else bluetoothPermissions.launch(bluetoothPermissionList())
                    },
                    onConnectDevice = { address ->
                        requestNotificationPermission { granted -> viewModel.connectDevice(address, granted) }
                    },
                    onDisconnectDevice = viewModel::disconnectDevice,
                    onVoiceClick = { if (state.isAnswering) viewModel.showInputError("앞선 답변이 끝난 뒤 음성 대화를 시작해 주세요.") else voiceController.startQuestion() },
                    onVoiceLongClick = { if (!state.isAnswering) voiceController.toggleWakeWord() },
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

    override fun onResume() {
        super.onResume()
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

    private fun hasBluetoothPermissions(): Boolean = bluetoothPermissionList().all(::hasPermission)

    private fun bluetoothPermissionList(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hideSystemNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
