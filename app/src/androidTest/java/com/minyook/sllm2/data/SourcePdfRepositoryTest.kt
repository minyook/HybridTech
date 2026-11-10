/* 소스 안내
 * Android PdfRenderer·판본·쪽 렌더을 검증하는 Android 계측 검사다.
 * 검사 예: bundledOfficialPagesRenderFromAppAssets
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
package com.minyook.sllm2.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourcePdfRepositoryTest {
    @Test
    fun bundledOfficialPagesRenderFromAppAssets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SourcePdfRepository(context)
        val guide = repository.renderPage("confined_space_safety_guide_2022", 45, 480)
        val checklist = repository.renderPage("confined_space_checklist_2026", 3, 480)
        assertTrue(guide.width > 0 && guide.height > 0)
        assertTrue(checklist.width > 0 && checklist.height > 0)
        guide.recycle()
        checklist.recycle()
    }
}
