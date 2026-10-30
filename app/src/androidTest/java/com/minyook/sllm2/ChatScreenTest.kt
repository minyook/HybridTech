package com.minyook.sllm2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minyook.sllm2.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Inert Compose fixture: no production ViewModel, model, microphone or BLE. */
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {
    @get:Rule val compose = createAndroidComposeRule<WorkspacePreviewActivity>()
    private val answer = ChatMessageUi(id = "answer", text = (1..120).joinToString("\n\n") { "- 문서 항목 $it: 사용 조건과 출처를 함께 확인합니다." }, fromWorker = false, pending = true)
    private val ui = mutableStateOf(FieldGuardUiState(activeChatId = "chat", messages = listOf(ChatMessageUi(id = "question", text = "장비 알려줘", fromWorker = true), answer), isAnswering = true))
    private val ime = mutableStateOf(false)

    private fun actions(submit: () -> Unit = {}, openSource: (Long) -> Unit = {}) = FieldGuardActions(onNavigate={},onNewChat={},onLoadChat={},onInputChange={value->ui.value=ui.value.copy(input=value)},onSubmit=submit,onPreset={},onDownloadModel={},onBackend={},onContextTokens={},onResponseTokens={},onSaveSettings={},onScanBluetooth={},onConnectDevice={},onDisconnectDevice={},onVoiceClick={},onVoiceLongClick={},onOpenSource=openSource)

    private fun show(actions: FieldGuardActions = actions(), voice: VoiceUiState = VoiceUiState(available=false)) {
        compose.setContent {
            androidx.compose.material3.MaterialTheme {
                Box(Modifier.width(390.dp).height(720.dp)) { WorkspaceChat(ui.value, voice, actions, ime.value) }
            }
        }
        compose.waitForIdle()
    }
    private fun position(): Float = compose.onNodeWithTag("conversationList").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun aLongAnswerStartsAtItsEndAndComposesOnlyAViewport() {
        show()
        compose.onNodeWithTag("conversationEnd").assertIsDisplayed()
        compose.onNodeWithText("문서 항목 120:", substring=true).assertIsDisplayed()
        compose.onNodeWithText("문서 항목 1:", substring=true).assertDoesNotExist()
        compose.onNodeWithTag("scrollLatest").assertDoesNotExist()
    }

    @Test fun readingOlderTextPausesFollowingWithoutMovingComposerAndCanResume() {
        show()
        val composer = compose.onNodeWithTag("chatComposer").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("conversationList").performTouchInput { swipeDown() }
        compose.onNodeWithTag("scrollLatest").assertIsDisplayed()
        val before = position()
        compose.runOnIdle { ui.value=ui.value.copy(messages=listOf(ui.value.messages.first(),answer.copy(text=answer.text+"\n\n추가된 답변"))) }
        assertEquals(before, position(), 1f)
        assertEquals(composer, compose.onNodeWithTag("chatComposer").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("scrollLatest").performClick()
        compose.onNodeWithTag("conversationEnd").assertIsDisplayed()
        compose.onNodeWithTag("scrollLatest").assertDoesNotExist()
        compose.runOnIdle { ui.value=ui.value.copy(messages=listOf(ui.value.messages.first(),answer.copy(text=answer.text+"\n\n다음 보충 답변"))) }
        compose.onNodeWithText("다음 보충 답변").assertIsDisplayed()
        compose.onNodeWithTag("conversationEnd").assertIsDisplayed()
    }

    @Test fun returningToTheBottomResumesFollowingAndChatSwitchResetsPosition() {
        show()
        compose.onNodeWithTag("conversationList").performTouchInput { swipeDown() }
        compose.onNodeWithTag("scrollLatest").assertIsDisplayed()
        compose.onNodeWithTag("conversationList").performScrollToNode(hasTestTag("conversationEnd"))
        compose.onNodeWithTag("scrollLatest").assertDoesNotExist()
        compose.runOnIdle { ui.value=ui.value.copy(activeChatId="other",messages=listOf(answer.copy(id="other-answer",text="다른 대화의 끝",pending=false))) }
        compose.onNodeWithText("다른 대화의 끝").assertIsDisplayed()
    }

    @Test fun imeSendUsesTheSameValidationAndHidesIdleVoicePanel() {
        var submits = 0
        ui.value=FieldGuardUiState()
        ime.value=true
        show(actions(submit={submits++;ui.value=ui.value.copy(input="",isAnswering=true)}))
        compose.onNodeWithTag("voiceConversationBar").assertDoesNotExist()
        compose.onNodeWithTag("sendMessage").assertIsNotEnabled()
        compose.onNodeWithTag("chatInput").performTextInput("작업장비")
        compose.onNodeWithTag("sendMessage").assertIsEnabled()
        compose.onNodeWithTag("chatInput").performImeAction()
        compose.runOnIdle { assertEquals(1,submits) }
        compose.onNodeWithTag("sendMessage").assertIsNotEnabled()
    }

    @Test fun activeVoiceKeepsStopAvailableAndBlocksTextSendWithImeOpen() {
        ime.value=true
        show(voice=VoiceUiState(available=true,conversationEnabled=true))
        compose.onNodeWithTag("chatInput").assertIsNotEnabled()
        compose.onNodeWithTag("sendMessage").assertIsNotEnabled()
        compose.onNodeWithTag("toggleVoiceConversation").assertIsEnabled()
        compose.onNodeWithTag("voiceConversationBar").assertIsDisplayed()
    }

    @Test fun sourceAndCopyUseTheOriginalFullAnswerAfterBlockSplitting() {
        var opened = 0L
        val text = "**확인 조건**\n\n- 장비와 연결된 사용 조건"
        ui.value=ui.value.copy(messages=listOf(answer.copy(text=text,pending=false,sourceIds=listOf(27),sourceLabels=mapOf(27L to "안전 가이드 · 27쪽"))),isAnswering=false)
        show(actions(openSource={opened=it}))
        compose.onNodeWithText("안전 가이드 · 27쪽").performClick()
        compose.runOnIdle { assertEquals(27L,opened) }
        compose.onNodeWithTag("copyAnswer:answer").performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard=compose.activity.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals(text,clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }
}
