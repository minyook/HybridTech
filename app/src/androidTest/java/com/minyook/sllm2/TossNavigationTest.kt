package com.minyook.sllm2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minyook.sllm2.ui.AppDestination
import com.minyook.sllm2.ui.ChatMessageUi
import com.minyook.sllm2.ui.FieldGuardActions
import com.minyook.sllm2.ui.FieldGuardUiState
import com.minyook.sllm2.ui.SourceUi
import com.minyook.sllm2.ui.TossFieldGuardApp
import com.minyook.sllm2.ui.VoiceUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose wiring tests use local state only; no BLE service or model download starts. */
@RunWith(AndroidJUnit4::class)
class TossNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun onboardingOpensHomeAndMainTabs() {
        var state by mutableStateOf(FieldGuardUiState(onboardingComplete = false, destination = AppDestination.HOME))
        compose.setContent {
            TossFieldGuardApp(state, VoiceUiState(available = true), actions(
                onNavigate = { state = state.copy(destination = it) },
                onCompleteOnboarding = { state = state.copy(onboardingComplete = true) },
            ))
        }

        compose.onNodeWithText("시작하기").performClick()
        compose.onNodeWithText("오늘 현장에서\n필요한 것").assertIsDisplayed()
        compose.onNodeWithText("질문", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("chatInput").assertIsDisplayed()
        compose.onNodeWithText("측정", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("scanBluetooth").assertIsDisplayed()
        compose.onNodeWithText("설정", useUnmergedTree = true).performClick()
        compose.onNodeWithText("로컬 AI 모델").assertIsDisplayed()
    }

    @Test
    fun chatSubmitKeepsComposerAndShowsPendingAnswer() {
        var state by mutableStateOf(FieldGuardUiState(destination = AppDestination.CHAT))
        compose.setContent {
            TossFieldGuardApp(state, VoiceUiState(available = true), actions(
                onInputChange = { state = state.copy(input = it) },
                onSubmit = {
                    state = state.copy(
                        input = "", isAnswering = true,
                        messages = state.messages +
                            ChatMessageUi(text = "산소 적정 농도는?", fromWorker = true) +
                            ChatMessageUi(text = "", fromWorker = false, pending = true),
                    )
                },
            ))
        }

        compose.onNodeWithTag("chatInput").performTextInput("산소 적정 농도는?")
        compose.onNodeWithContentDescription("음성 질문").assertIsDisplayed()
        compose.onNodeWithContentDescription("메시지 전송").assertIsDisplayed()
        compose.onNodeWithTag("sendMessage").performClick()
        compose.onNodeWithText("제공 문서에서 근거를 찾고 있어요.").assertIsDisplayed()
        compose.onNodeWithTag("chatInput").assertIsDisplayed()
    }

    @Test
    fun chatShowsPartialAnswerThenFinalMarkdownAndSources() {
        val answerId = "streaming-answer"
        var state by mutableStateOf(FieldGuardUiState(
            destination = AppDestination.CHAT,
            activeChatId = "chat-1",
            isAnswering = true,
            messages = listOf(ChatMessageUi(id = answerId, text = "", fromWorker = false, pending = true)),
        ))
        compose.setContent { TossFieldGuardApp(state, VoiceUiState(available = true), actions()) }

        compose.onNodeWithText("제공 문서에서 근거를 찾고 있어요.").assertIsDisplayed()
        compose.runOnIdle {
            state = state.copy(messages = listOf(ChatMessageUi(
                id = answerId, text = "### 산소 농도\n20.9%입니다.", fromWorker = false, pending = true,
            )))
        }
        compose.onNodeWithText("산소 농도").assertIsDisplayed()
        compose.onNodeWithText("답변 작성 중…").assertIsDisplayed()

        compose.runOnIdle {
            state = state.copy(
                isAnswering = false,
                messages = listOf(ChatMessageUi(
                    id = answerId, text = "### 산소 농도\n20.9%입니다.",
                    fromWorker = false, sourceIds = listOf(42L),
                )),
            )
        }
        compose.onNodeWithText("참조한 문서").assertIsDisplayed()
        compose.onNodeWithText("근거 1 원문 보기").assertIsDisplayed()
    }

    @Test
    fun sourceOpenedFromLibraryReturnsToLibrary() {
        val source = SourceUi(1L, "guide", "밀폐공간 안전작업 가이드", 12, "공기 측정", "원문 내용")
        var state by mutableStateOf(FieldGuardUiState(
            destination = AppDestination.SOURCE,
            selectedSource = source,
            sourceReturnDestination = AppDestination.LIBRARY,
        ))
        compose.setContent {
            TossFieldGuardApp(state, VoiceUiState(available = true), actions(
                onNavigate = { state = state.copy(destination = it) },
            ))
        }

        compose.onNodeWithText("원문 내용").assertIsDisplayed()
        compose.onNodeWithContentDescription("뒤로 가기").assertIsDisplayed()
        compose.onNodeWithText("홈").assertIsDisplayed()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(AppDestination.LIBRARY, state.destination) }
    }

    @Test
    fun bottomNavigationOnVoiceScreenStopsListeningWhenLeaving() {
        var state by mutableStateOf(FieldGuardUiState(destination = AppDestination.VOICE))
        var stopped = false
        compose.setContent {
            TossFieldGuardApp(state, VoiceUiState(available = true), actions(
                onNavigate = { state = state.copy(destination = it) },
                onStopVoice = { stopped = true },
            ))
        }

        compose.onNodeWithText("홈").performClick()
        compose.runOnIdle {
            assertEquals(AppDestination.HOME, state.destination)
            org.junit.Assert.assertTrue(stopped)
        }
    }

    @Test
    fun checklistSelectionUpdatesProgress() {
        var state by mutableStateOf(FieldGuardUiState(destination = AppDestination.CHECKLIST))
        compose.setContent {
            TossFieldGuardApp(state, VoiceUiState(available = true), actions(
                onToggleChecklist = { index -> state = state.copy(checklistChecked = state.checklistChecked + index) },
            ))
        }

        compose.onNodeWithText("0 / 5 확인").assertIsDisplayed()
        compose.onNodeWithText("작업 위치와 출입 경로").performClick()
        compose.onNodeWithText("1 / 5 확인").assertIsDisplayed()
    }

    private fun actions(
        onNavigate: (AppDestination) -> Unit = {},
        onInputChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onCompleteOnboarding: () -> Unit = {},
        onToggleChecklist: (Int) -> Unit = {},
        onStopVoice: () -> Unit = {},
    ) = FieldGuardActions(
        onNavigate = onNavigate,
        onNewChat = {}, onLoadChat = {}, onInputChange = onInputChange,
        onSubmit = onSubmit, onPreset = {}, onDownloadModel = {},
        onBackend = {}, onContextTokens = {}, onResponseTokens = {}, onSaveSettings = {},
        onScanBluetooth = {}, onConnectDevice = {}, onDisconnectDevice = {},
        onVoiceClick = {}, onVoiceLongClick = {},
        onCompleteOnboarding = onCompleteOnboarding,
        onToggleChecklist = onToggleChecklist,
        onStopVoice = onStopVoice,
    )
}
