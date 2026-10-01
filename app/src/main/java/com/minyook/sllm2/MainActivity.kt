package com.minyook.sllm2

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minyook.sllm2.ui.AppDestination
import com.minyook.sllm2.ui.FieldGuardActions
import com.minyook.sllm2.ui.FieldGuardViewModel
import com.minyook.sllm2.ui.HybridTechApp
import com.minyook.sllm2.ui.VoiceConversationController

/** Compose-only activity shell. RAG, model, BLE and voice work live behind observable state. */
class MainActivity : ComponentActivity() {
    private val viewModel: FieldGuardViewModel by viewModels()
    private lateinit var voiceController: VoiceConversationController
    private var notificationPermissionContinuation: ((Boolean) -> Unit)? = null

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voiceController.onMicrophonePermissionResult(granted)
    }
    private val bluetoothPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (hasBluetoothPermissions()) viewModel.scanBluetooth() else viewModel.bluetoothPermissionDenied()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionContinuation?.invoke(granted)
        notificationPermissionContinuation = null
    }
    private val systemVoiceInput = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        } else ""
        voiceController.onSystemRecognizerResult(spoken)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        voiceController = VoiceConversationController(
            context = this,
            hasMicrophonePermission = { hasPermission(Manifest.permission.RECORD_AUDIO) },
            requestMicrophonePermission = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
            launchSystemRecognizer = { systemVoiceInput.launch(it) },
            onQuestion = { spoken -> viewModel.submitVoiceQuestion(spoken, voiceController::speak) },
            onError = viewModel::showInputError,
        )
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val voice by voiceController.state.collectAsStateWithLifecycle()
            HybridTechApp(
                state = state,
                voice = voice,
                actions = FieldGuardActions(
                    onNavigate = viewModel::navigate,
                    onNewChat = viewModel::newChat,
                    onLoadChat = viewModel::loadChat,
                    onInputChange = viewModel::changeInput,
                    onSubmit = viewModel::submitQuestion,
                    onPreset = viewModel::askPreset,
                    onDownloadModel = { requestNotificationPermission { viewModel.downloadModel() } },
                    onBackend = viewModel::updateBackend,
                    onContextTokens = viewModel::updateContextTokens,
                    onResponseTokens = viewModel::updateResponseTokens,
                    onSaveSettings = viewModel::saveSettings,
                    onScanBluetooth = {
                        if (hasBluetoothPermissions()) viewModel.scanBluetooth()
                        else bluetoothPermissions.launch(bluetoothPermissionList())
                    },
                    onConnectDevice = { address ->
                        requestNotificationPermission { granted -> viewModel.connectDevice(address, granted) }
                    },
                    onDisconnectDevice = viewModel::disconnectDevice,
                    onVoiceClick = voiceController::startQuestion,
                    onVoiceLongClick = voiceController::toggleWakeWord,
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
        voiceController.onResume(viewModel.uiState.value.destination == AppDestination.CHAT)
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
}
