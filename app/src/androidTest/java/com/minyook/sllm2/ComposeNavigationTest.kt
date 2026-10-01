package com.minyook.sllm2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.data.ChatHistoryStore
import com.minyook.sllm2.model.InferenceBackend
import com.minyook.sllm2.model.ContextTokenPolicy
import com.minyook.sllm2.model.InferenceSettings
import com.minyook.sllm2.model.InferenceSettingsPreferences
import com.minyook.sllm2.ui.AppDestination
import com.minyook.sllm2.ui.BleDeviceUi
import com.minyook.sllm2.ui.ChatMessageUi
import com.minyook.sllm2.ui.FieldGuardActions
import com.minyook.sllm2.ui.FieldGuardUiState
import com.minyook.sllm2.ui.HybridTechApp
import com.minyook.sllm2.ui.VoiceUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

/** UI wiring tests use inert callbacks, so they never download a model or start BLE services. */
@RunWith(AndroidJUnit4::class)
class ComposeNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun drawerNavigatesToSensorAndSettings() {
        var state by mutableStateOf(FieldGuardUiState())
        compose.setContent {
            HybridTechApp(
                state,
                VoiceUiState(available = true),
                actions(onNavigate = { state = state.copy(destination = it) }),
            )
        }
        compose.onNodeWithTag("openDrawer").performClick()
        compose.onNodeWithText("센서 연결 상태").performClick()
        compose.onNodeWithText("Bluetooth LE 가스 검출기").assertIsDisplayed()
        compose.onNodeWithTag("openDrawer").performClick()
        compose.onNodeWithText("설정").performClick()
        compose.onNodeWithText("모델 관리").assertIsDisplayed()
    }

    @Test
    fun chatInputShowsPendingAnswerAndKeepsComposerVisible() {
        var state by mutableStateOf(FieldGuardUiState())
        compose.setContent {
            HybridTechApp(
                state,
                VoiceUiState(available = true),
                actions(
                    onInputChange = { state = state.copy(input = it) },
                    onSubmit = {
                        state = state.copy(
                            input = "",
                            isAnswering = true,
                            messages = state.messages +
                                ChatMessageUi(text = "산소 적정 농도는?", fromWorker = true) +
                                ChatMessageUi(text = "_제공 문서에서 근거를 찾는 중입니다…_", fromWorker = false, pending = true),
                        )
                    },
                ),
            )
        }
        compose.onNodeWithTag("chatInput").performTextInput("산소 적정 농도는?")
        compose.onNodeWithTag("sendMessage").performClick()
        compose.onNodeWithText("제공 문서에서 근거를 찾는 중입니다…").assertIsDisplayed()
        compose.onNodeWithTag("chatInput").assertIsDisplayed()
    }

    @Test
    fun sensorShowsPermissionAndDiscoveredDeviceStates() {
        var state by mutableStateOf(
            FieldGuardUiState(
                destination = AppDestination.SENSOR,
                bluetoothStatus = "가스 측정기 검색에는 Bluetooth 권한이 필요합니다.",
            ),
        )
        compose.setContent {
            HybridTechApp(
                state,
                VoiceUiState(available = true),
                actions(onScan = {
                    state = state.copy(
                        bluetoothStatus = "주변 가스 측정기를 검색하는 중입니다…",
                        isScanning = true,
                        devices = listOf(BleDeviceUi("AA:BB:CC:DD:EE:FF", "현장 측정기", -58)),
                    )
                }),
            )
        }
        compose.onNodeWithText("가스 측정기 검색에는 Bluetooth 권한이 필요합니다.").assertIsDisplayed()
        compose.onNodeWithTag("scanBluetooth").performClick()
        compose.onNodeWithText("현장 측정기").assertIsDisplayed()
        compose.onNodeWithText("AA:BB:CC:DD:EE:FF · -58 dBm").assertIsDisplayed()
    }

    @Test
    fun backReturnsFromSettingsToChat() {
        var state by mutableStateOf(FieldGuardUiState(destination = AppDestination.SETTINGS))
        compose.setContent {
            HybridTechApp(state, VoiceUiState(available = true), actions(onNavigate = { state = state.copy(destination = it) }))
        }
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(AppDestination.CHAT, state.destination) }
    }

    @Test
    fun recentAnswerButtonReturnsToBottomAfterScrolling() {
        val messages = (0 until 30).map { index ->
            ChatMessageUi(text = "대화 메시지 $index — 밀폐공간 안전작업 안내를 확인합니다.", fromWorker = index % 2 == 0)
        }
        compose.setContent {
            HybridTechApp(FieldGuardUiState(messages = messages), VoiceUiState(available = true), actions())
        }
        compose.waitForIdle()
        compose.onNodeWithTag("conversationList").performScrollToIndex(0)
        compose.onNodeWithTag("scrollLatest").assertIsDisplayed().performClick()
        compose.onNodeWithText("대화 메시지 29 — 밀폐공간 안전작업 안내를 확인합니다.").assertIsDisplayed()
    }

    @Test
    fun inferenceSettingsSurvivePreferenceRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = InferenceSettingsPreferences(context)
        val original = preferences.load()
        val changed = InferenceSettings(
            backend = InferenceBackend.CPU,
            contextTokens = ContextTokenPolicy.MIN_CONTEXT_TOKENS,
            responseTokens = 256,
        )
        try {
            preferences.save(changed)
            assertEquals(changed, InferenceSettingsPreferences(context).load())
        } finally {
            preferences.save(original)
        }
    }

    private fun actions(
        onNavigate: (AppDestination) -> Unit = {},
        onInputChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onScan: () -> Unit = {},
    ) = FieldGuardActions(
        onNavigate = onNavigate,
        onNewChat = {},
        onLoadChat = {},
        onInputChange = onInputChange,
        onSubmit = onSubmit,
        onPreset = {},
        onDownloadModel = {},
        onBackend = {},
        onContextTokens = {},
        onResponseTokens = {},
        onSaveSettings = {},
        onScanBluetooth = onScan,
        onConnectDevice = {},
        onDisconnectDevice = {},
        onVoiceClick = {},
        onVoiceLongClick = {},
    )
}
