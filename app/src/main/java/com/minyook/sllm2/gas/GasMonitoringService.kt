package com.minyook.sllm2.gas

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiSsid
import android.net.wifi.WifiManager
import android.util.SparseIntArray
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.minyook.sllm2.MainActivity
import com.minyook.sllm2.R
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.concurrent.Executors

/** User-started, offline AP receiver. Transport does not depend on the AI runtime. */
class GasMonitoringService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var wifi: WifiManager
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var receiver: WifiMeterServer? = null
    private var generation = 0
    private var active = false
    private var before = emptySet<String>()
    private var lastPersisted = 0L
    private val alarmStates = mutableMapOf<String, String>()
    private val tick = object : Runnable {
        override fun run() {
            val server = receiver ?: return
            if (!server.isRunning) { fail("AP 수신이 종료됐어요. AP를 다시 시작해 주세요."); return }
            val meters = server.registry.snapshot()
            WifiApHub.update { it.copy(meters = meters, selectedDeviceId = it.selectedDeviceId ?: meters.firstOrNull()?.deviceId) }
            val selected = WifiApHub.state.value.selected
            val now = SystemClock.elapsedRealtime()
            if (now - lastPersisted >= 1000L) {
                lastPersisted = now
                val reading = selected?.reading()
                if (reading?.hasValues == true) GasReadingStore.update(this@GasMonitoringService, reading)
                else GasReadingStore.clear(this@GasMonitoringService)
                val label = selected?.let { "${it.name} · ${it.link.label()}${if(it.test) " · 테스트" else ""}" }
                    ?: "AP 수신 대기 · ${meters.count { it.link != MeterLink.DISCONNECTED }}대 연결"
                manager().notify(NOTIFICATION_ID, notification(label, reading))
            }
            alarmStates.keys.retainAll(meters.map { it.deviceId }.toSet())
            meters.forEach { meter ->
                val alarm = meter.sample?.alarm ?: "unknown"
                if (meter.hasRecentSample && alarm in setOf("warning", "danger") && alarmStates[meter.deviceId] != alarm) {
                    manager().notify(5000 + meter.deviceId.hashCode().and(0x0fffffff),
                        NotificationCompat.Builder(this@GasMonitoringService, ALARM_CHANNEL)
                            .setSmallIcon(R.drawable.ic_stat_safety)
                            .setContentTitle(if (meter.test) "테스트 송신기 경보" else "감지기 경보 수신")
                            .setContentText("${meter.name} · ${if(alarm == "danger") "위험" else "주의"} · 장비 경보를 확인해 주세요")
                            .setContentIntent(appIntent()).setAutoCancel(true).build())
                }
                alarmStates[meter.deviceId] = if (meter.hasRecentSample) alarm else "unknown"
            }
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wifi = applicationContext.getSystemService(WifiManager::class.java)
        manager().createNotificationChannel(NotificationChannel(CHANNEL, "Wi-Fi AP 수신", NotificationManager.IMPORTANCE_LOW))
        manager().createNotificationChannel(NotificationChannel(ALARM_CHANNEL, "감지기 전달 경보", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (active) return START_NOT_STICKY
        startForeground(NOTIFICATION_ID, notification("휴대폰 AP 준비 중", null))
        active = true
        val attempt = ++generation
        before = addresses().map { it.second.hostAddress.orEmpty() }.toSet()
        GasReadingStore.clear(this)
        WifiApHub.update { WifiApUiState(phase = ApPhase.STARTING, status = "인터넷 없이 사용할 휴대폰 AP를 준비하고 있어요.") }
        handler.postDelayed({
            if (active && generation == attempt && reservation == null) fail("AP 준비 응답이 없어요. 기기 Wi-Fi 설정을 확인한 뒤 다시 시작해 주세요.")
        }, 20_000L)
        val callback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                if (!active || generation != attempt) { value.close(); return }
                reservation = value
                @Suppress("DEPRECATION")
                val ssid = if (Build.VERSION.SDK_INT >= 30) value.softApConfiguration.ssid.orEmpty() else value.wifiConfiguration?.SSID.orEmpty()
                @Suppress("DEPRECATION")
                val password = if (Build.VERSION.SDK_INT >= 30) value.softApConfiguration.passphrase.orEmpty() else value.wifiConfiguration?.preSharedKey.orEmpty()
                if (ssid.isBlank() || password.isBlank()) { fail("AP 연결 정보를 읽지 못했어요."); return }
                val token = randomHex(16)
                findAddress(attempt, ssid, password, token, SystemClock.elapsedRealtime() + 8000L)
            }
            override fun onStopped() { if (active && generation == attempt) fail("휴대폰 AP가 종료됐어요. 다시 시작해 주세요.") }
            override fun onFailed(reason: Int) {
                if (active && generation == attempt) fail(when(reason) {
                    ERROR_NO_CHANNEL -> "사용 가능한 Wi-Fi 채널이 없어요. 기존 핫스팟을 끄고 다시 시작해 주세요."
                    ERROR_TETHERING_DISALLOWED -> "이 기기의 정책에서 AP 사용을 제한하고 있어요."
                    ERROR_INCOMPATIBLE_MODE -> "기존 핫스팟과 함께 실행할 수 없어요. 기존 핫스팟을 끄고 다시 시작해 주세요."
                    else -> "AP를 시작하지 못했어요. Wi-Fi·주변 기기 권한과 기기 설정을 확인해 주세요. (코드 $reason)"
                })
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                val config = SoftApConfiguration.Builder().setWifiSsid(WifiSsid.fromBytes("FieldGuard-${randomHex(3)}".toByteArray()))
                    .setPassphrase(randomHex(8), SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                    .setChannels(SparseIntArray().apply { put(SoftApConfiguration.BAND_2GHZ, 0) }).build()
                wifi.startLocalOnlyHotspotWithConfiguration(config, mainExecutor, callback)
            } else {
                // Older Android chooses credentials/band. Never enable internet tethering as a fallback.
                wifi.startLocalOnlyHotspot(callback, handler)
            }
        } catch (_: SecurityException) { fail("AP 권한이 없어요. 주변 기기 권한(이전 Android는 위치 권한)을 허용해 주세요.") }
        catch (_: Exception) { fail("AP 시작 중 오류가 났어요. 기존 핫스팟을 끄고 다시 시도해 주세요.") }
        return START_NOT_STICKY
    }

    private fun findAddress(attempt: Int, ssid: String, password: String, token: String, deadline: Long) {
        if (!active || generation != attempt) return
        val candidates = addresses().filter { (name, address) ->
            Regex("(?i).*(wlan|swlan|wifi|^ap[0-9]).*").matches(name.orEmpty()) && address.hostAddress.orEmpty() !in before
        }
        if (candidates.size != 1) {
            if (SystemClock.elapsedRealtime() >= deadline) { fail("AP 주소를 확인하지 못했어요. 기존 핫스팟을 끄고 다시 시작해 주세요."); return }
            handler.postDelayed({ findAddress(attempt, ssid, password, token, deadline) }, 200L)
            return
        }
        val address = candidates.single().second
        io.execute {
            val result = runCatching { WifiMeterServer(address, WifiMeterProtocol.DEFAULT_PORT, token) }
            handler.post {
                if (!active || generation != attempt) { result.getOrNull()?.close(); return@post }
                result.fold(onSuccess = { server ->
                    receiver = server
                    WifiApHub.update { WifiApUiState(phase = ApPhase.RUNNING, status = "AP가 켜졌어요. 감지기에서 아래 Wi-Fi에 연결해 주세요.", ssid = ssid, password = password, token = token, host = address.hostAddress.orEmpty(), port = server.localPort, band = if(Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) "2.4 GHz" else "기기 자동 선택") }
                    tick.run()
                }, onFailure = { fail("AP 수신 포트를 열지 못했어요. AP를 다시 시작해 주세요.") })
            }
        }
    }

    private fun addresses(): List<Pair<String, Inet4Address>> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { net ->
            net.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress && !it.isLoopbackAddress }.map { net.name to it }
        }
    }.getOrDefault(emptyList())

    private fun fail(message: String) {
        shutdown()
        WifiApHub.update { it.copy(phase = ApPhase.ERROR, status = message) }
        stopSelf()
    }

    private fun shutdown() {
        active = false; generation++
        // Keep late hotspot callbacks: onStarted must close a reservation after cancellation.
        handler.removeCallbacks(tick)
        receiver?.close(); receiver = null
        reservation?.close(); reservation = null
        WifiApHub.update { state -> WifiApUiState(status = "AP가 꺼졌어요. 다시 켜면 새로운 연결 정보가 만들어져요.", meters = state.meters.map { it.copy(link = MeterLink.DISCONNECTED, reason = "AP 종료") }, selectedDeviceId = state.selectedDeviceId) }
        GasReadingStore.clear(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        if (active || receiver != null || reservation != null) shutdown()
        io.shutdownNow()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun manager() = getSystemService(NotificationManager::class.java)
    private fun appIntent() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(status: String, reading: GasReading?): Notification {
        val fresh = reading?.isFresh() == true
        val text = if (fresh) "O₂ ${reading.oxygenPercent ?: "—"}% · H₂S ${reading.h2sPpm ?: "—"} ppm · CO ${reading.carbonMonoxidePpm ?: "—"} ppm · LEL ${reading.lelPercent ?: "—"}%" else "현재 측정값 없음"
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_safety)
            .setContentTitle(status).setContentText(text).setContentIntent(appIntent())
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
    }
    companion object {
        private const val CHANNEL = "wifi_ap_gas_monitoring_v1"
        private const val ALARM_CHANNEL = "wifi_meter_alarm_v1"
        private const val NOTIFICATION_ID = 4012
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, GasMonitoringService::class.java))
        fun stop(context: Context) { context.stopService(Intent(context, GasMonitoringService::class.java)) }
        private fun randomHex(bytes: Int): String = ByteArray(bytes).apply { SecureRandom().nextBytes(this) }.joinToString("") { "%02x".format(it.toInt().and(255)) }
    }
}

fun MeterLink.label(): String = when(this) {
    MeterLink.WAITING -> "첫 측정 대기"
    MeterLink.RECEIVING -> "수신 중"
    MeterLink.STALE -> "수신 지연 · 현재값 없음"
    MeterLink.FAULT -> "센서 오류 · 현재값 없음"
    MeterLink.DISCONNECTED -> "연결 끊김 · 현재값 없음"
}
