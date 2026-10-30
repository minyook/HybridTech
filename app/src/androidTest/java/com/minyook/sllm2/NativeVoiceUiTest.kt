package com.minyook.sllm2

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.ui.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Inert UI state only. No microphone, recognizer, TTS, model or work request. */
class NativeVoiceUiTest {
    @get:Rule val compose = createAndroidComposeRule<WorkspacePreviewActivity>()
    @Before fun foregroundFixture() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setShowWhenLocked(true); compose.activity.setTurnScreenOn(true)
        }
    }
    private fun actions(start: () -> Unit = {}, stop: () -> Unit = {}, navigate: (AppDestination) -> Unit = {}) = FieldGuardActions(
        onNavigate = navigate, onNewChat = {}, onLoadChat = {}, onInputChange = {}, onSubmit = {}, onPreset = {}, onDownloadModel = {}, onBackend = {}, onContextTokens = {}, onResponseTokens = {}, onSaveSettings = {}, onScanBluetooth = {}, onConnectDevice = {}, onDisconnectDevice = {}, onVoiceClick = start, onVoiceLongClick = {}, onStopVoice = stop,
    )
    @Test fun microphoneOpensDedicatedConversationAndStartsOnce() {
        val voice = mutableStateOf(VoiceUiState(available = true))
        val state=mutableStateOf(FieldGuardUiState(destination=AppDestination.CHAT))
        var starts=0
        compose.setContent { TossFieldGuardApp(state.value, voice.value, actions(
            start = { starts++;voice.value = voice.value.copy(conversationEnabled = true, listening = true, status = "듣고 있어요. 말을 마치면 자동으로 질문해요.") },
            stop = { voice.value = voice.value.copy(conversationEnabled = false, listening = false, status = "음성 대화가 꺼져 있어요.") },
            navigate={state.value=state.value.copy(destination=it)},
        )) }
        compose.onNodeWithTag("voiceConversationBar").assertDoesNotExist()
        compose.onNodeWithTag("voiceQuestion").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(AppDestination.VOICE,state.value.destination);org.junit.Assert.assertEquals(1,starts) }
        compose.onNodeWithText("음성 대화 종료").assertIsDisplayed()
        compose.onNodeWithTag("chatInput").assertDoesNotExist()
        capture("android-native-voice-listening.png")
        compose.onNodeWithTag("returnToTextChat").performClick()
        compose.onNodeWithTag("chatInput").assertIsEnabled()
        compose.onNodeWithTag("voiceConversationBar").assertDoesNotExist()
        compose.onNodeWithTag("voiceQuestion").performClick()
        compose.onNodeWithTag("toggleVoiceConversation").performClick()
        compose.onNodeWithText("음성 대화 시작").assertIsDisplayed()
    }
    @Test fun readingStateKeepsStopButtonAvailable() {
        compose.setContent { TossFieldGuardApp(FieldGuardUiState(destination = AppDestination.VOICE), VoiceUiState(available = true, conversationEnabled = true, speaking = true, status = "답변을 읽고 있어요. 마이크는 쉬고 있어요.", lastHeard = "산소 측정 방법 알려줘"), actions()) }
        compose.onNodeWithText("음성 대화 종료").assertIsDisplayed()
        compose.onNodeWithTag("chatInput").assertDoesNotExist()
        compose.onNodeWithTag("voiceSettings").assertIsDisplayed()
    }
    @Test fun nativeVoicePageShowsOfflineReadinessAndPreparation() {
        compose.setContent { TossFieldGuardApp(FieldGuardUiState(destination = AppDestination.VOICE), VoiceUiState(available = true, ttsAvailable = true, localSpeechStatus = "한국어 기기 내 인식 데이터 준비됨"), actions()) }
        compose.onNodeWithTag("voiceSettings").performClick()
        compose.onNodeWithText("한국어 기기 내 인식 데이터 준비됨").assertIsDisplayed()
        compose.onNodeWithText("로컬 한국어 답변 읽기 준비됨").assertIsDisplayed()
        compose.onNodeWithText("한국어 음성 준비·다시 확인").assertIsDisplayed()
        capture("android-native-voice-settings.png")
    }
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "voice-checks").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
