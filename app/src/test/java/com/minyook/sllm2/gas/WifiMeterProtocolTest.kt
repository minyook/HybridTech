package com.minyook.sllm2.gas

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class WifiMeterProtocolTest {
    private fun hello() = JSONObject("""{"type":"hello","protocol":1,"token":"secret","deviceId":"meter-1","test":true,"intervalMs":1000}""")
    private fun reading() = JSONObject("""{"type":"reading","seq":1,"sampleAgeMs":0,"o2Percent":20.9,"status":"ok","alarm":"none"}""")
    private fun rejected(block: () -> Unit) { try { block(); fail("invalid frame accepted") } catch (_: MeterProtocolException) {} }
    @Test fun readsCoalescedFramesAndCrLf() {
        val input = ByteArrayInputStream("가스\r\n두번째\n".toByteArray())
        assertEquals("가스", WifiMeterProtocol.readFrame(input)); assertEquals("두번째", WifiMeterProtocol.readFrame(input)); assertNull(WifiMeterProtocol.readFrame(input))
    }
    @Test fun rejectsOversizedIncompleteAndMalformedUtf8() {
        rejected { WifiMeterProtocol.readFrame(ByteArrayInputStream("x".repeat(8193).toByteArray())) }
        rejected { WifiMeterProtocol.readFrame(ByteArrayInputStream("missing LF".toByteArray())) }
        rejected { WifiMeterProtocol.readFrame(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 10))) }
    }
    @Test fun authenticatesExplicitVersionAndTestIdentity() {
        assertTrue(WifiMeterProtocol.hello(hello(), "secret").test)
        rejected { WifiMeterProtocol.hello(hello(), "wrong") }
        rejected { WifiMeterProtocol.hello(hello().put("protocol", 2), "secret") }
        rejected { WifiMeterProtocol.hello(hello().apply { remove("test") }, "secret") }
        rejected { WifiMeterProtocol.hello(hello().put("deviceId", "../bad"), "secret") }
        rejected { WifiMeterProtocol.hello(hello().put("intervalMs", 10), "secret") }
    }
    @Test fun validatesUnitsAndNeverInventsAbsentChannels() {
        val value = WifiMeterProtocol.sample(reading().put("h2sPpm", 0))
        assertEquals(0.0, value.h2s!!, 0.0); assertNull(value.co); assertNull(value.lel)
        rejected { WifiMeterProtocol.sample(reading().put("o2Percent", "20.9")) }
        rejected { WifiMeterProtocol.sample(reading().put("o2Percent", -1)) }
        rejected { WifiMeterProtocol.sample(reading().put("h2sPpm", 1001)) }
        rejected { WifiMeterProtocol.sample(reading().put("oxygen", 20.9)) }
        rejected { WifiMeterProtocol.sample(reading().put("seq", 1.5)) }
        rejected { WifiMeterProtocol.sample(reading().put("sampleAgeMs", -1)) }
        rejected { WifiMeterProtocol.sample(reading().put("status", "safe")) }
        rejected { WifiMeterProtocol.sample(reading().put("alarm", "clear")) }
    }
    @Test fun acceptsSensorFaultWithoutNumbersButNotHealthyEmptyFrame() {
        val empty = reading().apply { remove("o2Percent") }
        rejected { WifiMeterProtocol.sample(empty) }
        assertFalse(WifiMeterProtocol.sample(empty.put("status", "fault")).healthy)
    }
}

class WifiMeterRegistryTest {
    private val registry = WifiMeterRegistry()
    private fun hello(id: String) = MeterHello(id, id, true, 1000)
    private fun sample(seq: Long = 1, age: Long = 0, o2: Double = 20.9) = MeterSample(seq, age, o2, null, null, null, true, "none")
    @Test fun isolatesMetersAndRejectsDuplicateLiveIdentity() {
        assertTrue(registry.connect(hello("one"), "a")); assertTrue(registry.connect(hello("two"), "b"))
        assertFalse(registry.connect(hello("one"), "c"))
        registry.reading("one", "a", sample(o2 = 19.0), 10000, 0)
        registry.reading("two", "b", sample(o2 = 21.0), 10000, 0)
        assertFalse(registry.reading("one", "b", sample(o2 = 99.0), 10000, 0))
        assertEquals(listOf(19.0, 21.0), registry.snapshot(0).map { it.sample!!.oxygen })
    }
    @Test fun staleMeasurementsDoNotBecomeFreshWithHeartbeatOrDuplicates() {
        registry.connect(hello("one"), "a")
        registry.reading("one", "a", sample(age = 2000), 10000, 0)
        registry.rtt("one", "a", 5.0)
        assertTrue(registry.snapshot(999_000_000).single().current)
        assertFalse(registry.reading("one", "a", sample(), 11000, 999_000_000))
        val view = registry.snapshot(1_000_000_000).single()
        assertEquals(MeterLink.STALE, view.link); assertEquals(1L, view.rejected); assertEquals(5.0, view.rttMs!!, 0.0)
        assertFalse(view.reading().isFresh(11000))
    }
    @Test fun countsMissingFramesAndRejectsOldSequence() {
        registry.connect(hello("one"), "a")
        registry.reading("one", "a", sample(1), 10000, 0)
        registry.reading("one", "a", sample(5), 10000, 0)
        assertFalse(registry.reading("one", "a", sample(3), 10000, 0))
        val view = registry.snapshot(0).single()
        assertEquals(3L, view.missed); assertEquals(1L, view.rejected); assertEquals(5L, view.sample!!.sequence)
    }
    @Test fun reconnectResetsNumbersAndOldSocketCannotDisconnectNewOne() {
        registry.connect(hello("one"), "a"); registry.reading("one", "a", sample(), 10000, 0)
        registry.disconnect("one", "a", "closed")
        assertFalse(registry.snapshot(0).single().reading().isFresh(10000))
        assertTrue(registry.connect(hello("one"), "b")); registry.disconnect("one", "a", "late close")
        val view = registry.snapshot(0).single()
        assertEquals(MeterLink.WAITING, view.link); assertNull(view.sample)
    }
    @Test fun faultAndStopImmediatelyInvalidateValues() {
        registry.connect(hello("one"), "a")
        registry.reading("one", "a", sample().copy(healthy = false), 10000, 0)
        assertEquals(MeterLink.FAULT, registry.snapshot(0).single().link)
        assertFalse(registry.snapshot(0).single().reading().isFresh(10000))
        assertTrue(registry.snapshot(0).single().hasRecentSample)
        assertEquals(MeterLink.STALE, registry.snapshot(3_000_000_000).single().link)
        assertFalse(registry.snapshot(3_000_000_000).single().hasRecentSample)
        registry.disconnectAll("stopped")
        assertEquals(MeterLink.DISCONNECTED, registry.snapshot(0).single().link)
    }
    @Test fun senderSampleAgeIsIncludedInReadingExpiry() {
        registry.connect(hello("one"), "a"); registry.reading("one", "a", sample(age = 2000), 10000, 0)
        val reading = registry.snapshot(0).single().reading()
        assertTrue(reading.isFresh(10999)); assertFalse(reading.isFresh(11000))
    }
}
