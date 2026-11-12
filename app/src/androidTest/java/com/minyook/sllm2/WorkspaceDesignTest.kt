/* 소스 안내
 * 제품 공통 화면의 배치/반응형·콜백을 검증하는 Android 계측 검사다.
 * 검사 예: keepPreviewVisible, homeShowsSafetyTools, settingsExposeDailySupervisorAccessAndNativeFunctions
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
package com.minyook.sllm2

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.minyook.sllm2.ui.ChatMessageUi
import com.minyook.sllm2.data.ChatHistoryStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.ui.AppDestination
import com.minyook.sllm2.ui.DocumentUi
import com.minyook.sllm2.ui.FieldGuardActions
import com.minyook.sllm2.ui.FieldGuardUiState
import com.minyook.sllm2.ui.SourceUi
import com.minyook.sllm2.ui.TossFieldGuardApp
import com.minyook.sllm2.ui.VoiceUiState
import com.minyook.sllm2.ui.WorkspaceDesign
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Compose rendering with inert callbacks; never starts a model, AP or real work. */
@RunWith(AndroidJUnit4::class)
class WorkspaceDesignTest {
    @get:Rule val compose = createAndroidComposeRule<WorkspacePreviewActivity>()
    @Before fun keepPreviewVisible() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Show only this inert test fixture; the device remains locked.
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)
        }
    }
    private val documents = listOf(DocumentUi("confined_space_safety_guide_2022", "밀폐공간 안전작업 가이드",84),DocumentUi("confined_space_checklist_2026","밀폐공간 자율점검표",3))
    private fun show(destination: AppDestination, phone: Boolean = false, state: FieldGuardUiState = FieldGuardUiState(destination=destination,documents=documents), actions: FieldGuardActions = inertActions()) {
        compose.setContent {
            if(phone) {
                val configuration=Configuration(LocalConfiguration.current).apply{screenWidthDp=390;screenHeightDp=844}
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    Box(Modifier.width(390.dp).height(844.dp).testTag("phonePreview")) { TossFieldGuardApp(state,VoiceUiState(available=false),actions) }
                }
            } else TossFieldGuardApp(state,VoiceUiState(available=false),actions)
        }
        compose.waitForIdle()
    }
    @Test fun homeShowsSafetyTools() {
        show(AppDestination.HOME)
        compose.onNodeWithText("밀폐공간 안전").assertIsDisplayed()
        compose.onNodeWithTag("homeVoiceWorkRequest").assertIsDisplayed()
        capture("android-workspace-home.png")
        val visibleSteps = listOf(1, 4, 5, 6, 7, 8)
        visibleSteps.forEach { step -> compose.onNodeWithTag("workScenarioStep$step").performScrollTo().assertIsDisplayed() }
        capture("android-workspace-home-steps.png")
    }
    @Test fun settingsExposeModelAndDeviceFunctionsWithoutServerSetup() {
        var downloads = 0
        var destination: AppDestination? = null
        show(AppDestination.SETTINGS, actions = inertActions(
            navigate = { destination = it },
            downloadModel = { downloads++ },
        ))
        compose.onNodeWithText("기기 내 EXAONE").assertIsDisplayed()
        compose.onNodeWithTag("internetStatus").assertIsDisplayed()
        capture("android-workspace-settings.png")
        compose.onNodeWithTag("downloadExaone").assertIsDisplayed().performClick()
        compose.onNodeWithTag("modelInferenceSettings").assertIsDisplayed().performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(1, downloads)
            org.junit.Assert.assertEquals(AppDestination.INFERENCE, destination)
        }
        if (compose.activity.resources.configuration.screenWidthDp < WorkspaceDesign.SidebarBreakpoint) {
            compose.onNodeWithText("문서 보관함").assertIsNotDisplayed()
        }
        compose.onNodeWithText("가스 측정기").assertDoesNotExist()
        compose.onNodeWithText("한국어 음성").assertDoesNotExist()
        compose.onNodeWithText("작업 서버").assertDoesNotExist()
        compose.onNodeWithText("이전 기기 점검 기록").performScrollTo().assertIsDisplayed()
    }
    @Test fun libraryKeepsTheOriginalDocuments() {
        show(AppDestination.LIBRARY)
        compose.onNodeWithText("밀폐공간 안전작업 가이드").assertIsDisplayed()
        capture("android-workspace-library.png")
    }
    @Test fun wideDocumentViewKeepsLibraryNextToSource() {
        org.junit.Assume.assumeTrue(compose.activity.resources.configuration.screenWidthDp >= 1100)
        val guide = documents.first()
        show(AppDestination.SOURCE, state = FieldGuardUiState(
            destination = AppDestination.SOURCE,
            documents = documents,
            sourceReturnDestination = AppDestination.LIBRARY,
            selectedSource = SourceUi(1, guide.id, guide.title, 24, "산소·유해가스 농도 측정", ""),
        ))
        compose.onNodeWithText("전체 문서").assertIsDisplayed()
        compose.onNodeWithText("24쪽 · 산소·유해가스 농도 측정").assertIsDisplayed()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("${guide.title} 24쪽 PDF 원본").fetchSemanticsNodes().isNotEmpty()
        }
        capture("android-workspace-source.png")
    }
    @Test fun chatShowsExampleQuestionsAndComposer() {
        show(AppDestination.CHAT)
        compose.onNodeWithText("질문 예시").assertIsDisplayed()
        compose.onNodeWithTag("chatInput").assertIsDisplayed()
        capture("android-workspace-chat.png")
    }
    @Test fun answerUsesMarkdownAndSourceCards() {
        show(AppDestination.CHAT,state=FieldGuardUiState(destination=AppDestination.CHAT,documents=documents,activeChatId="design",messages=listOf(ChatMessageUi(text="작업 전 어떤 내용을 확인해야 하나요?",fromWorker=true),ChatMessageUi(text="## 작업 전 확인\n현장의 작업 절차와 제공 문서를 함께 확인하세요.\n\n- **작업 위치**와 출입 경로 확인\n- 측정 장비와 보호구 확인\n- 감시인과 연락 방법 확인",fromWorker=false,sourceIds=listOf(1L)))))
        compose.onNodeWithText("작업 전 확인").assertIsDisplayed()
        capture("android-workspace-answer.png")
    }
    @Test fun historyShowsSavedConversations() {
        show(AppDestination.HISTORY,state=FieldGuardUiState(destination=AppDestination.HISTORY,documents=documents,histories=listOf(ChatHistoryStore.Session("design","산소 및 유해가스 측정방법 알려줘",System.currentTimeMillis(),emptyList()))))
        compose.onNodeWithText("저장된 대화").assertIsDisplayed()
        capture("android-workspace-history.png")
    }
    @Test fun tokenSettingsShowOutputAndContext() {
        show(AppDestination.INFERENCE)
        compose.onNodeWithText("최대 답변 토큰").assertIsDisplayed()
        compose.onNodeWithText("문맥 토큰 한도").assertIsDisplayed()
        capture("android-workspace-tokens.png")
    }
    @Test fun phoneHomeKeepsTitleAndBottomNavigationInsideViewport() {
        show(AppDestination.HOME,phone=true)
        compose.onNodeWithText("밀폐공간 안전").assertIsDisplayed()
        compose.onNodeWithTag("bottom-SENSOR").assertIsDisplayed()
        compose.onNodeWithTag("bottom-LIBRARY").assertDoesNotExist()
        capturePhone("android-workspace-phone-home.png")
        compose.onNodeWithTag("homeLibrary").performScrollTo().assertIsDisplayed()
    }
    @Test fun phoneChatKeepsComposerAndMenuInsideViewport() {
        show(AppDestination.CHAT,phone=true)
        compose.onNodeWithTag("chatInput").assertIsDisplayed()
        capturePhone("android-workspace-phone-chat.png")
    }
    private fun capturePhone(name:String) {
        val bitmap=compose.onNodeWithTag("phonePreview").captureToImage().asAndroidBitmap()
        save(bitmap,name)
    }
    @Test fun workerBuildBlocksSupervisorScreensAndActions() {
        org.junit.Assume.assumeFalse(BuildConfig.SUPERVISOR_APP)
        val state = androidx.compose.runtime.mutableStateOf(FieldGuardUiState(destination = AppDestination.HOME))
        compose.setContent { TossFieldGuardApp(state.value, VoiceUiState(available = false), inertActions()) }
        compose.onNodeWithTag("homeSupervisorRole").assertDoesNotExist()
        compose.onNodeWithTag("workScenarioStep2").assertDoesNotExist()
        compose.onNodeWithTag("workScenarioStep3").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(destination = AppDestination.SUPERVISOR) }
        compose.onNodeWithText("작업 진행").assertIsDisplayed()
        compose.onNodeWithTag("workRoleSupervisor").assertDoesNotExist()
        compose.onNodeWithTag("workApprove").assertDoesNotExist()
    }
    private fun capture(name:String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        save(bitmap,name)
        bitmap.recycle()
    }
    private fun save(bitmap:Bitmap,name:String) {
        val directory=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"design-previews").apply{mkdirs()}
        File(directory,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun inertActions(navigate: (AppDestination) -> Unit = {}, downloadModel: () -> Unit = {}) =
        FieldGuardActions(onNavigate=navigate,onNewChat={},onLoadChat={},onInputChange={},onSubmit={},onPreset={},onDownloadModel=downloadModel,onContextTokens={},onResponseTokens={},onSaveSettings={},onStartAp={},onSelectMeter={},onStopAp={},onVoiceClick={},onVoiceLongClick={})
}
