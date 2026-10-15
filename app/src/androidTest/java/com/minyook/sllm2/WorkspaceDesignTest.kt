package com.minyook.sllm2

import android.graphics.Bitmap
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepPreviewVisible() {
        compose.activity.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Show only this inert test fixture; the device remains locked.
            compose.activity.setShowWhenLocked(true)
            compose.activity.setTurnScreenOn(true)
        }
    }
    private val documents = listOf(DocumentUi("confined_space_safety_guide_2022", "밀폐공간 안전작업 가이드",84),DocumentUi("confined_space_checklist_2026","밀폐공간 자율점검표",3))
    private fun show(destination: AppDestination, server: String = "") {
        compose.setContent { TossFieldGuardApp(FieldGuardUiState(destination=destination,documents=documents,workServerUrl=server),VoiceUiState(available=false),inertActions()) }
        compose.waitForIdle()
    }
    @Test fun homeUsesTheSameHeroAndWorkspaceAsWeb() {
        show(AppDestination.HOME)
        compose.onNodeWithText("안전한 작업의 시작,\n근거부터 확인하세요.").assertIsDisplayed()
        capture("android-workspace-home.png")
    }
    @Test fun settingsExposeDailySupervisorAccessAndNativeFunctions() {
        show(AppDestination.SETTINGS)
        compose.onNodeWithText("감독관 화면으로 전환").assertIsDisplayed()
        capture("android-workspace-settings.png")
    }
    @Test fun libraryKeepsTheOriginalDocuments() {
        show(AppDestination.LIBRARY)
        compose.onNodeWithText("밀폐공간 안전작업 가이드").assertIsDisplayed()
        capture("android-workspace-library.png")
    }
    @Test fun supervisorPageUsesTheSameSixDigitWebScreen() {
        show(AppDestination.SUPERVISOR,"http://localhost:4173")
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(30_000) {
            val root=automation.rootInActiveWindow
            root!=null && containsText(root,"오늘의 암호코드")
        }
        capture("android-workspace-supervisor.png")
    }
    private fun containsText(node:android.view.accessibility.AccessibilityNodeInfo, expected:String):Boolean {
        if(node.text?.toString()?.contains(expected)==true)return true
        for(index in 0 until node.childCount) {
            val child=node.getChild(index)?:continue
            if(containsText(child,expected))return true
        }
        return false
    }
    private fun capture(name:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("화면 캡처 실패")
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"design-previews").apply{mkdirs()}
        File(directory,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    private fun inertActions()=FieldGuardActions(onNavigate={},onNewChat={},onLoadChat={},onInputChange={},onSubmit={},onPreset={},onDownloadModel={},onBackend={},onContextTokens={},onResponseTokens={},onSaveSettings={},onScanBluetooth={},onConnectDevice={},onDisconnectDevice={},onVoiceClick={},onVoiceLongClick={})
}
