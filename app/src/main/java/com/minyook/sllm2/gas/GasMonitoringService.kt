package com.minyook.sllm2.gas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.widget.RemoteViews
import com.minyook.sllm2.MainActivity
import com.minyook.sllm2.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Keeps a user-requested BLE connection alive and visualizes its last valid measurement in the notification shade. */
class GasMonitoringService : Service() {
    private lateinit var client: BleGasClient

    override fun onCreate() {
        super.onCreate()
        createChannel()
        client = BleGasClient(applicationContext, object : BleGasClient.Listener {
            override fun onScanResult(device: android.bluetooth.BluetoothDevice, rssi: Int) = Unit

            override fun onConnectionStatus(message: String) {
                sendStatus(message)
                updateNotification(message)
            }

            override fun onReading(reading: GasReading) {
                GasReadingStore.update(applicationContext, reading)
                updateNotification("실시간 측정 중 · ${reading.deviceName ?: "BLE 측정기"}")
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                GasSimulationService.stop(this)
                GasReadingStore.beginBleSession(applicationContext)
                startForeground(NOTIFICATION_ID, notification("가스 측정기 연결 준비 중"))
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                if (address.isNullOrBlank()) {
                    sendStatus("연결할 가스 측정기 주소가 없습니다.")
                    stopSelf()
                } else {
                    client.connect(address)
                }
            }
            ACTION_DISCONNECT -> {
                client.close()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (::client.isInitialized) client.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendStatus(message: String) {
        sendBroadcast(
            Intent(ACTION_CONNECTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, message),
        )
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(message))
    }

    private fun notification(message: String): android.app.Notification {
        val reading = GasReadingStore.current(this)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_safety)
            .setContentIntent(appIntent())
            .setCustomContentView(gasPanel(reading, message))
            .setCustomBigContentView(gasPanel(reading, message))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun gasPanel(reading: GasReading, status: String): RemoteViews =
        RemoteViews(packageName, R.layout.notification_gas_monitoring).apply {
            setTextViewText(R.id.notification_gas_status, status)
            setTextViewText(R.id.notification_o2, format(reading.oxygenPercent, "%"))
            setTextViewText(R.id.notification_h2s, format(reading.h2sPpm, "ppm"))
            setTextViewText(R.id.notification_co, format(reading.carbonMonoxidePpm, "ppm"))
            setTextViewText(R.id.notification_lel, format(reading.lelPercent, "%LEL"))
            setTextViewText(
                R.id.notification_updated_at,
                if (reading.receivedAtMillis > 0L) {
                    "최근 수신 ${DateFormat.getTimeInstance(DateFormat.SHORT, Locale.KOREA).format(Date(reading.receivedAtMillis))}"
                } else {
                    "아직 측정값을 받지 못했습니다"
                },
            )
        }

    private fun format(value: Double?, unit: String): String = value?.let {
        "${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.US, it)}$unit"
    } ?: "—"

    private fun appIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "실시간 가스 모니터링", NotificationManager.IMPORTANCE_LOW).apply {
                description = "연결된 가스 검출기의 최신 측정값을 표시합니다."
            },
        )
    }

    companion object {
        const val ACTION_CONNECTION_STATUS = "com.minyook.sllm2.gas.CONNECTION_STATUS"
        const val EXTRA_STATUS = "status"
        private const val ACTION_CONNECT = "com.minyook.sllm2.gas.CONNECT"
        private const val ACTION_DISCONNECT = "com.minyook.sllm2.gas.DISCONNECT"
        private const val EXTRA_ADDRESS = "address"
        private const val CHANNEL_ID = "ble_gas_monitoring_v2"
        private const val NOTIFICATION_ID = 4012

        fun connect(context: Context, address: String) {
            val intent = Intent(context, GasMonitoringService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_ADDRESS, address)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun disconnect(context: Context) {
            context.startService(Intent(context, GasMonitoringService::class.java).setAction(ACTION_DISCONNECT))
        }
    }
}
