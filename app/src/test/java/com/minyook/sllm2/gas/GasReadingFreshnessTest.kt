/* 소스 안내
 * 세션·시간·누락 채널에 따른 최신 측정 표시을 검증하는 JVM 회귀 검사다.
 * 검사 예: onlyRecentMeasurementsAreCurrent
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.gas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GasReadingFreshnessTest {
    @Test
    fun onlyRecentMeasurementsAreCurrent() {
        val reading = GasReading(
            oxygenPercent = 20.9,
            receivedAtMillis = 1_000_000L,
            source = GasReadingSource.WIFI_AP,
        )

        assertFalse(reading.copy(source = GasReadingSource.NONE).isFresh(1_000_000L))
        assertFalse(reading.copy(sessionActive = false).isFresh(1_000_000L))
        assertTrue(reading.isFresh(1_000_000L))
        assertTrue(reading.isFresh(1_059_999L))
        assertFalse(reading.isFresh(1_060_000L))
        assertFalse(reading.isFresh(999_999L))
        assertFalse(reading.copy(oxygenPercent = null).isFresh(1_000_000L))
    }
}
