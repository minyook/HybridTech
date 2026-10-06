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
            source = GasReadingSource.BLE,
        )

        assertTrue(reading.isFresh(1_000_000L))
        assertTrue(reading.isFresh(1_059_999L))
        assertFalse(reading.isFresh(1_060_000L))
        assertFalse(reading.isFresh(999_999L))
        assertFalse(reading.copy(oxygenPercent = null).isFresh(1_000_000L))
    }
}
