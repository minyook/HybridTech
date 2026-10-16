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
import androidx.compose.ui.test.performScrollTo
import com.minyook.sllm2.ui.ChatMessageUi
import com.minyook.sllm2.data.ChatHistoryStore
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.ui.AppDestination
import com.minyook.sllm2.ui.DocumentUi
import com.minyook.sllm2.ui.FieldGuardActions
import com.minyook.sllm2.ui.FieldGuardUiState
import com.minyook.sllm2.ui.TossFieldGuardApp
import com.minyook.sllm2.ui.VoiceUiState
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Compose rendering with inert callbacks; never starts a model, BLE or real work. */
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
    private fun show(destination: AppDestination, server: String = "", phone: Boolean = false, state: FieldGuardUiState = FieldGuardUiState(destination=destination,documents=documents,workServerUrl=server)) {
        compose.setContent {
            if(phone) {
                val configuration=Configuration(LocalConfiguration.current).apply{screenWidthDp=390;screenHeightDp=844}
                CompositionLocalProvider(LocalConfiguration provides configuration) {
                    Box(Modifier.width(390.dp).height(844.dp).testTag("phonePreview")) { TossFieldGuardApp(state,VoiceUiState(available=false),inertActions()) }
                }
            } else TossFieldGuardApp(state,VoiceUiState(available=false),inertActions())
        }
        compose.waitForIdle()
    }
    @Test fun homeUsesTheSameHeroAndWorkspaceAsWeb() {
        show(AppDestination.HOME)
        compose.onNodeWithText("안전한 작업의 시작,\n근거부터 확인하세요.").assertIsDisplayed()
        capture("android-workspace-home.png")
    }
    @Test fun settingsExposeDailySupervisorAccessAndNativeFunctions() {
        show(AppDestination.SETTINGS)
        compose.onNodeWithText("기기 내 Gemma").assertIsDisplayed()
        capture("android-workspace-settings.png")
        compose.onNodeWithText("감독관 화면으로 전환").performScrollTo().assertIsDisplayed()
    }
    @Test fun libraryKeepsTheOriginalDocuments() {
        show(AppDestination.LIBRARY)
        compose.onNodeWithText("밀폐공간 안전작업 가이드").assertIsDisplayed()
        capture("android-workspace-library.png")
    }
    @Test fun chatUsesWebEmptyStateAndComposer() {
        show(AppDestination.CHAT)
        compose.onNodeWithText("질문은 편하게,\n근거는 정확하게.").assertIsDisplayed()
        compose.onNodeWithTag("chatInput").assertIsDisplayed()
        capture("android-workspace-chat.png")
    }
    @Test fun answerUsesMarkdownAndSourceCards() {
        show(AppDestination.CHAT,state=FieldGuardUiState(destination=AppDestination.CHAT,documents=documents,activeChatId="design",messages=listOf(ChatMessageUi(text="작업 전 어떤 내용을 확인해야 하나요?",fromWorker=true),ChatMessageUi(text="## 작업 전 확인\n현장의 작업 절차와 제공 문서를 함께 확인하세요.\n\n- **작업 위치**와 출입 경로 확인\n- 측정 장비와 보호구 확인\n- 감시인과 연락 방법 확인",fromWorker=false,sourceIds=listOf(1L)))))
        compose.onNodeWithText("작업 전 확인").assertIsDisplayed()
        capture("android-workspace-answer.png")
    }
    @Test fun historyUsesWebSavedRows() {
        show(AppDestination.HISTORY,state=FieldGuardUiState(destination=AppDestination.HISTORY,documents=documents,histories=listOf(ChatHistoryStore.Session("design","산소 및 유해가스 측정방법 알려줘",System.currentTimeMillis(),emptyList()))))
        compose.onNodeWithText("이전에 확인한 내용을 다시 봐요").assertIsDisplayed()
        capture("android-workspace-history.png")
    }
    @Test fun tokenSettingsUseTheSameFieldsAsWeb() {
        show(AppDestination.INFERENCE)
        compose.onNodeWithText("최대 답변 토큰").assertIsDisplayed()
        compose.onNodeWithText("문맥 토큰 한도").assertIsDisplayed()
        capture("android-workspace-tokens.png")
    }
    @Test fun phoneHomeKeepsIllustrationAndBottomNavigationInsideViewport() {
        show(AppDestination.HOME,phone=true)
        compose.onNodeWithText("안전한 작업의 시작,\n근거부터 확인하세요.").assertIsDisplayed()
        capturePhone("android-workspace-phone-home.png")
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
    @Test fun supervisorPageUsesTheSameSixDigitWebScreen() {
        show(AppDestination.SUPERVISOR,"http://localhost:4173")
        val rendered=java.util.concurrent.atomic.AtomicReference("")
        compose.waitUntil(30_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                findWebView(compose.activity.window.decorView)?.evaluateJavascript("document.body ? document.body.innerText : ''") { rendered.set(it) }
            }
            rendered.get().contains("오늘의 암호코드")
        }
        capture("android-workspace-supervisor.png")
    }
    private fun findWebView(view:android.view.View):android.webkit.WebView? {
        if(view is android.webkit.WebView)return view
        if(view is android.view.ViewGroup)for(index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let{return it}
        }
        return null
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
    private fun inertActions()=FieldGuardActions(onNavigate={},onNewChat={},onLoadChat={},onInputChange={},onSubmit={},onPreset={},onDownloadModel={},onBackend={},onContextTokens={},onResponseTokens={},onSaveSettings={},onScanBluetooth={},onConnectDevice={},onDisconnectDevice={},onVoiceClick={},onVoiceLongClick={})
}
