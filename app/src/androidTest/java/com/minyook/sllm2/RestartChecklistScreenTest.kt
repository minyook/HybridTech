package com.minyook.sllm2

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestartChecklistScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun productionChatShowsDocumentAnswerAndOpensBundledPdf() {
        ask("밀폐공간 작업을 일시 중단했다가 다시 시작하기 전에 산소와 유해가스 농도를 측정해야 하나요?")
        val source = waitForSource(24)
        compose.onNodeWithTag("conversationList").performScrollToNode(source)
        compose.onNode(source).performClick()
        val pdf = hasContentDescription("24쪽 PDF 원본", substring = true)
        compose.waitUntil(30_000) { compose.onAllNodes(pdf).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(pdf).assertIsDisplayed()
    }

    @Test fun supervisorQuestionShowsGroundedPermitAndMeasurementDetails() {
        ask("밀폐공간 작업을 잠깐 멈췄다가 재개할 때 관리감독자는 무엇을 확인해야하나요?")
        waitForSource(22)
        val list = compose.onNodeWithTag("conversationList")
        for (term in listOf("허가", "측정")) {
            list.performScrollToNode(hasText(term, substring = true))
        }
    }

    private fun ask(question: String) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runProductionChat") == "true")
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        if (compose.onAllNodesWithText("시작하기").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("시작하기").performClick()
        }
        val chat = compose.onNodeWithTag("workspace-CHAT")
        if (runCatching { chat.assertIsDisplayed() }.isFailure) {
            compose.onNodeWithTag("workspaceMenu").performClick()
        }
        chat.performClick()
        compose.onNodeWithTag("chatInput").performTextReplacement(question)
        compose.onNodeWithTag("sendMessage").performClick()
    }

    private fun waitForSource(page: Int): SemanticsMatcher {
        val source = hasText("${page}쪽", substring = true) and hasClickAction()
        compose.waitUntil(90_000) { compose.onAllNodes(source).fetchSemanticsNodes().isNotEmpty() }
        return source
    }
}
