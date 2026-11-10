/* 소스 안내
 * 동시 송신·재접속·장기 프레임 스트레스을 검증하는 JVM 회귀 검사다.
 * 검사 예: optIn, record, awaitCondition
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.gas

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/** Opt-in PC transport checks. Uses the production receiver and actual Node sender, never RF. */
class WifiMeterStressTest {
    private val address = InetAddress.getByName("127.0.0.1")
    private val token = "synthetic-interop-token"
    private val root by lazy {
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "tools/wifi-ap-sender.mjs").isFile }
    }
    private val results by lazy { File(root, "app/build/reports/fieldguard/wifi-ap-stress").apply { mkdirs() } }

    @Before fun optIn() {
        assumeTrue("Run with FIELDGUARD_WIFI_AP_STRESS=1; requires Node.js", System.getenv("FIELDGUARD_WIFI_AP_STRESS") == "1")
    }
    private fun record(name: String, vararg fields: Pair<String, Any>) {
        val json = JSONObject().put("scope", "PC loopback transport, not smartphone AP/RF").put("passed", true)
        fields.forEach { (key, value) -> json.put(key, value) }
        File(results, "$name.json").writeText(json.toString(2))
    }
    private fun awaitCondition(message: String, milliseconds: Long = 4000, check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds)
        while (!check() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(message, check())
    }
    private class Client(port: Int) : AutoCloseable {
        val socket = Socket("127.0.0.1", port).apply { soTimeout = 20000; tcpNoDelay = true }
        val input = socket.getInputStream().buffered()
        fun send(value: JSONObject) { socket.getOutputStream().write((value.toString() + "\n").toByteArray()); socket.getOutputStream().flush() }
        fun next(): JSONObject = WifiMeterProtocol.json(WifiMeterProtocol.readFrame(input) ?: error("EOF"))
        fun hello(id: String): JSONObject {
            send(JSONObject().put("type", "hello").put("protocol", 1).put("token", "synthetic-interop-token").put("deviceId", id).put("test", true).put("intervalMs", 1000))
            return next()
        }
        fun reading(sequence: Int) { send(JSONObject().put("type", "reading").put("seq", sequence).put("sampleAgeMs", 0).put("o2Percent", 20.9).put("status", "ok").put("alarm", "none")) }
        fun ack(): JSONObject {
            while (true) {
                val frame = next()
                if (frame.optString("type") != "ping") return frame
                send(JSONObject().put("type", "pong").put("id", frame.getString("id")))
            }
        }
        override fun close() = socket.close()
    }
    private fun sender(port: Int, count: Int, seconds: Int, name: String): Process = ProcessBuilder(
        System.getenv("FIELDGUARD_NODE") ?: "node", "tools/wifi-ap-sender.mjs", "--host", "127.0.0.1",
        "--port", port.toString(), "--count", count.toString(), "--interval", "100", "--seconds", seconds.toString(),
    ).directory(root).apply {
        environment()["FIELDGUARD_AP_TOKEN"] = token
        redirectOutput(File(results, "$name-output.json")); redirectError(File(results, "$name-stderr.txt"))
    }.start()
    private fun completed(process: Process, name: String): JSONObject {
        assertTrue("Node sender timed out", process.waitFor(30, TimeUnit.SECONDS))
        assertEquals(File(results, "$name-stderr.txt").readText(), 0, process.exitValue())
        return JSONObject(File(results, "$name-output.json").readText())
    }

    @Test fun actualNodeSenderWithSixteenConcurrentIdentities() {
        WifiMeterServer(address, 0, token).use { server ->
            val process = sender(server.localPort, 16, 15, "sixteen")
            try {
                awaitCondition("16 senders did not connect", 10000) { server.registry.snapshot().count { it.current } == 16 }
                awaitCondition("heartbeat RTT missing", 10000) { server.registry.snapshot().all { it.rttMs != null } }
                val stats = completed(process, "sixteen")
                assertEquals(16, stats.getInt("connections")); assertEquals(0, stats.getInt("errors")); assertEquals(0, stats.getInt("rejected"))
                assertTrue(stats.getLong("accepted") >= 1000)
                awaitCondition("closed senders still current") { server.registry.snapshot().all { it.link == MeterLink.DISCONNECTED } }
                server.registry.snapshot().forEach { meter ->
                    val index = meter.deviceId.substringAfter('-').toInt()
                    assertEquals(20 + index / 100.0, meter.sample!!.oxygen!!, 0.000001)
                    assertEquals(index.toDouble(), meter.sample!!.co!!, 0.0)
                    assertEquals(0L, meter.missed); assertEquals(0L, meter.rejected); assertTrue(meter.test)
                }
                record("sixteen", "statistics" to stats, "isolatedIdentities" to 16, "heartbeatRttSeenForAll" to true)
            } finally { process.destroyForcibly() }
        }
    }

    @Test fun actualNodeSenderReconnectsAfterReceiverRestart() {
        var server = WifiMeterServer(address, 0, token)
        val port = server.localPort
        val process = sender(port, 3, 10, "restart")
        try {
            awaitCondition("initial senders missing") { server.registry.snapshot().count { it.current } == 3 }
            Thread.sleep(1500)
            server.close()
            server = WifiMeterServer(address, port, token)
            awaitCondition("senders failed to reconnect", 6000) { server.registry.snapshot().count { it.current } == 3 }
            val stats = completed(process, "restart")
            assertTrue(stats.getInt("connections") >= 6); assertTrue(stats.getInt("accepted") >= 100)
            assertEquals(0, stats.getInt("rejected"))
            record("restart", "statistics" to stats, "newServerIdentityCount" to server.registry.snapshot().size)
        } finally { process.destroyForcibly(); server.close() }
    }

    @Test fun seventeenthConnectionIsRejectedAndReleasedSlotCanBeUsed() {
        WifiMeterServer(address, 0, token).use { server ->
            val clients = mutableListOf<Client>()
            try {
                repeat(16) { index ->
                    clients += Client(server.localPort).also { assertEquals("welcome", it.hello("capacity-$index").getString("type")) }
                }
                Client(server.localPort).use { extra ->
                    val refused = runCatching { extra.hello("overflow"); false }.getOrDefault(true)
                    assertTrue("17th connection unexpectedly accepted", refused)
                }
                clients.removeAt(0).close()
                awaitCondition("closed device not disconnected") { server.registry.snapshot().first { it.deviceId == "capacity-0" }.link == MeterLink.DISCONNECTED }
                Client(server.localPort).use { replacement -> assertEquals("welcome", replacement.hello("replacement").getString("type")) }
                record("capacity", "acceptedLiveConnections" to 16, "rejectedSeventeenth" to true, "releasedSlotReused" to true)
            } finally { clients.forEach { it.close() } }
        }
    }

    @Test fun silentHandshakeExpiresWithoutRegisteringADevice() {
        WifiMeterServer(address, 0, token).use { server ->
            Client(server.localPort).use { client ->
                val began = System.nanoTime()
                assertEquals("error", client.next().getString("type"))
                val milliseconds = (System.nanoTime() - began) / 1_000_000
                assertTrue("handshake expiry outside expected interval: $milliseconds", milliseconds in 4500..9000)
                assertTrue(server.registry.snapshot().isEmpty())
                record("handshake-timeout", "milliseconds" to milliseconds)
            }
        }
    }

    @Test fun noPongCannotKeepOldMeasurementsCurrentAndSocketEventuallyCloses() {
        WifiMeterServer(address, 0, token).use { server ->
            Client(server.localPort).use { client ->
                client.hello("silent"); client.reading(0); assertTrue(client.next().getBoolean("accepted"))
                val began = System.nanoTime()
                assertEquals("ping", client.next().getString("type"))
                assertEquals(MeterLink.STALE, server.registry.snapshot().single().link)
                var message: JSONObject
                do { message = client.next() } while (message.optString("type") == "ping")
                assertEquals("error", message.getString("type"))
                val milliseconds = (System.nanoTime() - began) / 1_000_000
                awaitCondition("unresponsive socket not disconnected") { server.registry.snapshot().single().link == MeterLink.DISCONNECTED }
                assertFalse(server.registry.snapshot().single().current)
                assertTrue("socket timeout outside expected interval", milliseconds in 14000..19000)
                record("read-timeout", "milliseconds" to milliseconds, "staleBeforeDisconnect" to true)
            }
        }
    }

    @Test fun repeatedReconnectsResetReadingsAndBoundDeviceHistory() {
        WifiMeterServer(address, 0, token).use { server ->
            repeat(80) { cycle ->
                val id = "history-$cycle"
                Client(server.localPort).use { client ->
                    assertEquals("welcome", client.hello(id).getString("type"))
                    assertNull(server.registry.snapshot().first { it.deviceId == id }.sample)
                    client.reading(0); assertTrue(client.next().getBoolean("accepted"))
                }
                awaitCondition("old connection did not close") { server.registry.snapshot().first { it.deviceId == id }.link == MeterLink.DISCONNECTED }
                assertTrue(server.registry.snapshot().size <= 64)
            }
            repeat(20) { cycle ->
                Client(server.localPort).use { client ->
                    client.hello("repeated"); assertNull(server.registry.snapshot().first { it.deviceId == "repeated" }.sample)
                    client.reading(cycle); assertTrue(client.next().getBoolean("accepted"))
                }
                awaitCondition("repeated connection still alive") { server.registry.snapshot().first { it.deviceId == "repeated" }.link == MeterLink.DISCONNECTED }
            }
            record("reconnect-history", "uniqueIdentities" to 80, "sameIdentityReconnects" to 20, "retainedRecords" to server.registry.snapshot().size)
        }
    }

    @Test fun oversizedAndInvalidUtf8ClientsDoNotBreakHealthyPeer() {
        WifiMeterServer(address, 0, token).use { server ->
            Client(server.localPort).use { healthy ->
                healthy.hello("healthy"); healthy.reading(0); assertTrue(healthy.ack().getBoolean("accepted"))
                listOf("x".repeat(8193).toByteArray(), byteArrayOf(0xc3.toByte(), 10)).forEachIndexed { index, bytes ->
                    Client(server.localPort).use { bad ->
                        bad.hello("bad-$index"); bad.socket.getOutputStream().write(bytes)
                        assertEquals("error", bad.next().getString("type"))
                    }
                }
                healthy.reading(1); assertTrue(healthy.ack().getBoolean("accepted"))
                assertTrue(server.registry.snapshot().first { it.deviceId == "healthy" }.current)
                record("invalid-peer-isolation", "malformedPeers" to 2, "healthyPeerContinues" to true)
            }
        }
    }
}
