package com.minyook.sllm2.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkScenarioTest {
    @Test fun explicitSpeechLabelsFillTheEditableDraft() {
        assertEquals(
            SpokenWorkDraft("A구역", "3번 맨홀", "내부 점검"),
            parseSpokenWorkDraft("현장은 A구역, 위치는 3번 맨홀, 작업 내용은 내부 점검"),
        )
        assertEquals(
            SpokenWorkDraft(null, null, "3번 맨홀 내부 점검을 요청해줘"),
            parseSpokenWorkDraft("3번 맨홀 내부 점검을 요청해줘"),
        )
    }

    @Test fun actualAlarmTakesPrecedenceOverMonitoring() {
        assertEquals(1, activeWorkStep(null, monitoring = false, alarm = false))
        assertEquals(4, activeWorkStep(WorkStatus.APPROVED, monitoring = false, alarm = false))
        assertEquals(5, activeWorkStep(WorkStatus.ENTERED, monitoring = false, alarm = false))
        assertEquals(6, activeWorkStep(WorkStatus.SUPPORTING, monitoring = true, alarm = false))
        assertEquals(7, activeWorkStep(WorkStatus.SUPPORTING, monitoring = true, alarm = true))
        assertEquals(8, activeWorkStep(WorkStatus.ALARM, monitoring = false, alarm = false))
    }
}
