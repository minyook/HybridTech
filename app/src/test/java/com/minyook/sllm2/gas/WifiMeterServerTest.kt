/* 소스 안내
 * 실제 loopback TCP의 분할/병합·동시 연결·정리을 검증하는 JVM 회귀 검사다.
 * 검사 예: send, next, hello
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.gas

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real TCP sockets on loopback: exercises transport, not Wi-Fi radio/AP firmware. */
class WifiMeterServerTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private class Sender(port: Int) : AutoCloseable {
        val socket = Socket("127.0.0.1", port).apply { soTimeout = 8000; tcpNoDelay = true }
        val input = socket.getInputStream().buffered()
        fun send(json: JSONObject) { socket.getOutputStream().write((json.toString() + "\n").toByteArray()); socket.getOutputStream().flush() }
        fun next(): JSONObject {
            while (true) {
                val json = WifiMeterProtocol.json(WifiMeterProtocol.readFrame(input) ?: error("connection closed"))
                if (json.optString("type") != "ping") return json
                send(JSONObject().put("type", "pong").put("id", json.getString("id")))
            }
        }
        fun hello(id: String, token: String = "test-token"): JSONObject {
            send(JSONObject().put("type", "hello").put("protocol", 1).put("token", token).put("deviceId", id).put("test", true).put("intervalMs", 1000))
            return next()
        }
        fun reading(sequence: Int, oxygen: Double = 20.9) = JSONObject().put("type", "reading").put("seq", sequence).put("sampleAgeMs", 0).put("o2Percent", oxygen).put("status", "ok").put("alarm", "none")
        override fun close() { socket.close() }
    }
    private fun awaitCondition(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!check() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(check())
    }
    @Test fun tenConcurrentSendersKeepTheirOwnValuesAndSequence() {
        WifiMeterServer(loopback, 0, "test-token").use { server ->
            val pool = Executors.newFixedThreadPool(10)
            try {
                val futures = (1..10).map { id -> pool.submit(Callable {
                    Sender(server.localPort).use { sender ->
                        assertEquals("welcome", sender.hello("meter-$id").getString("type"))
                        repeat(100) { seq ->
                            sender.send(sender.reading(seq, 10.0 + id))
                            val ack = sender.next(); assertEquals(seq, ack.getInt("seq")); assertTrue(ack.getBoolean("accepted"))
                        }
                    }
                }) }
                futures.forEach { it.get(10, TimeUnit.SECONDS) }
                awaitCondition { server.registry.snapshot().size == 10 && server.registry.snapshot().all { it.link == MeterLink.DISCONNECTED } }
                server.registry.snapshot().forEach { view ->
                    assertEquals(99L, view.sample!!.sequence)
                    assertEquals(10.0 + view.deviceId.substringAfter('-').toInt(), view.sample!!.oxygen!!, 0.0)
                    assertTrue(view.test); assertFalse(view.current)
                }
            } finally { pool.shutdownNow() }
        }
    }
    @Test fun rejectsWrongTokenAndDuplicateDeviceIdWithoutReplacingOwner() {
        WifiMeterServer(loopback, 0, "test-token").use { server ->
            Sender(server.localPort).use { wrong -> assertEquals("error", wrong.hello("wrong", "bad-token").getString("type")) }
            Sender(server.localPort).use { first ->
                assertEquals("welcome", first.hello("one").getString("type"))
                Sender(server.localPort).use { duplicate -> assertEquals("error", duplicate.hello("one").getString("type")) }
                first.send(first.reading(1)); assertTrue(first.next().getBoolean("accepted"))
                first.send(first.reading(1)); assertFalse(first.next().getBoolean("accepted"))
                assertEquals(1, server.registry.snapshot().size)
                assertEquals(1L, server.registry.snapshot().single().rejected)
            }
        }
    }
    @Test fun splitAndCoalescedFramesWorkButMalformedFrameClosesSocket() {
        WifiMeterServer(loopback, 0, "test-token").use { server ->
            Sender(server.localPort).use { sender ->
                sender.hello("split")
                val bytes = (sender.reading(1).toString() + "\n" + sender.reading(2).toString() + "\n").toByteArray()
                sender.socket.getOutputStream().write(bytes, 0, 7)
                sender.socket.getOutputStream().write(bytes, 7, bytes.size - 7)
                assertEquals(1, sender.next().getInt("seq")); assertEquals(2, sender.next().getInt("seq"))
                sender.socket.getOutputStream().write("{bad}\n".toByteArray())
                assertEquals("error", sender.next().getString("type"))
                awaitCondition { server.registry.snapshot().single().link == MeterLink.DISCONNECTED }
            }
        }
    }
    @Test fun serverStopClosesPeersReleasesPortAndMarksDisconnected() {
        val server = WifiMeterServer(loopback, 0, "test-token")
        val port = server.localPort
        Sender(port).use { sender ->
            sender.hello("one"); server.close()
            assertNull(WifiMeterProtocol.readFrame(sender.input))
            assertFalse(server.isRunning)
            assertEquals(MeterLink.DISCONNECTED, server.registry.snapshot().single().link)
        }
        WifiMeterServer(loopback, port, "different-token").close()
    }
    @Test fun heartbeatMeasuresRoundTripWithoutMakingOldSampleCurrent() {
        WifiMeterServer(loopback, 0, "test-token").use { server ->
            Sender(server.localPort).use { sender ->
                sender.hello("one"); sender.send(sender.reading(1)); sender.next()
                val ping = WifiMeterProtocol.json(WifiMeterProtocol.readFrame(sender.input)!!)
                assertEquals("ping", ping.getString("type"))
                sender.send(JSONObject().put("type", "pong").put("id", ping.getString("id")))
                awaitCondition { server.registry.snapshot().single().rttMs != null }
                val view = server.registry.snapshot().single()
                assertEquals(MeterLink.STALE, view.link); assertFalse(view.current); assertTrue(view.rttMs!! >= 0)
            }
        }
    }
}
