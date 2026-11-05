package com.minyook.sllm2

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minyook.sllm2.data.*
import com.minyook.sllm2.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real corpus + production renderer. No production ViewModel/model/history side effects. */
@RunWith(AndroidJUnit4::class)
class SourceTableRenderingTest {
    @get:Rule val compose=createAndroidComposeRule<WorkspacePreviewActivity>()
    private fun show(page:Int):MarkdownBlock.Table {
        val raw=JSONObject(compose.activity.assets.open("knowledge/seed_chunks_v1.json").bufferedReader().use{it.readText()}).getJSONArray("chunks")
        val c=(0 until raw.length()).map{raw.getJSONObject(it)}.first{it.getString("documentId")=="confined_space_safety_guide_2022"&&it.getInt("pageNumber")==page}
        val chunk=KnowledgeChunk(id=page.toLong(),documentId=c.getString("documentId"),documentTitle=c.getString("documentTitle"),pageNumber=page,body=c.getString("body"))
        val blocks=markdownBlocks(FocusedEvidence.format(listOf(RetrievedChunk(chunk,1.0)))).filterIsInstance<MarkdownBlock.Table>()
        val table=if(page==13)blocks.last() else blocks.single()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(390.dp).fillMaxHeight().background(Color(0xFFF7F9FE)).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    MarkdownText("## ${if(page==13)"유해가스별 위험과 외관·냄새" else "연도별 질식재해 발생현황"}\n\n안전작업 가이드 · ${page}쪽")
                    MarkdownBlockText(table)
                }
            }
        }
        compose.waitForIdle()
        return table
    }
    private fun screenshot(name:String) {
        val dir=File(compose.activity.getExternalFilesDir(null),"table-checks").apply{mkdirs()}
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun gasRowsStayAlignedAndReadableAtPhoneWidth() {
        val table=show(13);val key=table.rows.hashCode()
        compose.onAllNodes(hasTestTag("tableScroll:$key:0")).assertCountEquals(0)
        compose.onNodeWithText("주된 위험").assertIsDisplayed()
        compose.onNodeWithText("외관 및 냄새").assertIsDisplayed()
        val gas=compose.onNodeWithTag("tableCell:$key:4:0").fetchSemanticsNode().boundsInRoot
        val risk=compose.onNodeWithTag("tableCell:$key:4:1").fetchSemanticsNode().boundsInRoot
        val odor=compose.onNodeWithTag("tableCell:$key:4:2").fetchSemanticsNode().boundsInRoot
        assertTrue(gas.right<risk.left);assertTrue(risk.right<odor.left)
        screenshot("gas-table-phone-top")
        compose.onNodeWithText("메탄(CH4)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("화재와 폭발\n상부에 축적 가능").assertIsDisplayed()
        screenshot("gas-table-phone")
    }
    @Test fun wideTableScrollsAllRowsTogetherWithPinnedFirstColumn() {
        val table=show(14);val key=table.rows.hashCode()
        val first=compose.onNodeWithTag("tableCell:$key:1:0").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("tableScroll:$key:1").performTouchInput { swipeLeft(durationMillis=500) }
        compose.onNodeWithTag("tableScroll:$key:1").performTouchInput { swipeLeft(durationMillis=500) }
        compose.onNodeWithTag("tableScroll:$key:1").performTouchInput { swipeLeft(durationMillis=500) }
        compose.onNodeWithTag("tableScroll:$key:1").performTouchInput { swipeLeft(durationMillis=500) }
        compose.onNodeWithText("2021").assertIsDisplayed()
        assertEquals(first,compose.onNodeWithTag("tableCell:$key:1:0").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("발생건수").assertIsDisplayed()
        val header=compose.onNodeWithTag("tableCell:$key:0:11").fetchSemanticsNode().boundsInRoot
        val value=compose.onNodeWithTag("tableCell:$key:1:11").fetchSemanticsNode().boundsInRoot
        assertEquals(header.left,value.left,1f)
        screenshot("statistics-table-phone")
    }
}
