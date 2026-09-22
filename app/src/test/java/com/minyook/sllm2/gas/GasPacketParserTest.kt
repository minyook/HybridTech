package com.minyook.sllm2.gas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GasPacketParserTest {
    @Test
    fun parsesKeyValueNotificationWithoutInventingChannels() {
        val reading = GasPacketParser.parse("O2=20.9,H2S=2,CO=4,LEL=0".toByteArray())

        requireNotNull(reading)
        assertEquals(20.9, reading.oxygenPercent!!, 0.001)
        assertEquals(2.0, reading.h2sPpm!!, 0.001)
        assertEquals(4.0, reading.carbonMonoxidePpm!!, 0.001)
        assertEquals(0.0, reading.lelPercent!!, 0.001)
    }

    @Test
    fun rejectsOutOfRangeOrUnknownPackets() {
        assertNull(GasPacketParser.parse("O2=120,H2S=0".toByteArray()))
        assertNull(GasPacketParser.parse("not a meter packet".toByteArray()))
    }
}
