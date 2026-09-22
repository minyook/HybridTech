package com.minyook.sllm2.gas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.minyook.sllm2.MainActivity
import com.minyook.sllm2.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round
import kotlin.random.Random

/**
 * Presentation-only data for first-run demonstrations. Every surface labels this
 * source as simulated, and a real BLE session stops this service before connecting.
 */
class GasSimulationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            publishSample()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (GasReadingStore.current(this).source == GasReadingSource.BLE) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, notification(GasReadingStore.current(this)))
                handler.removeCallbacks(updateRunnable)
                publishSample()
                handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishSample() {
        val sample = GasReading(
            oxygenPercent = randomDecimal(20.7, 20.9),
            h2sPpm = randomDecimal(0.0, 0.4),
            carbonMonoxidePpm = randomDecimal(0.0, 2.0),
            lelPercent = randomDecimal(0.0, 0.4),
            receivedAtMillis = System.currentTimeMillis(),
            deviceName = "데모 시뮬레이션 · 실제 측정값 아님",
            source = GasReadingSource.SIMULATION,
        )
        GasReadingStore.update(applicationContext, sample)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(sample))
    }

    private fun randomDecimal(minimum: Double, maximum: Double): Double =
        round((minimum + Random.nextDouble() * (maximum - minimum)) * 10.0) / 10.0

    private fun notification(reading: GasReading): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_safety)
            .setContentIntent(openAppIntent())
            .setCustomContentView(gasPanel(reading))
            .setCustomBigContentView(gasPanel(reading))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun gasPanel(reading: GasReading): RemoteViews =
        RemoteViews(packageName, R.layout.notification_gas_monitoring).apply {
            setTextViewText(R.id.notification_gas_status, "데모 시뮬레이션 · 실제 측정값 아님")
            setTextViewText(R.id.notification_o2, format(reading.oxygenPercent, "%"))
            setTextViewText(R.id.notification_h2s, format(reading.h2sPpm, "ppm"))
            setTextViewText(R.id.notification_co, format(reading.carbonMonoxidePpm, "ppm"))
            setTextViewText(R.id.notification_lel, format(reading.lelPercent, "%LEL"))
            setTextViewText(
                R.id.notification_updated_at,
                "시뮬레이션 갱신 ${DateFormat.getTimeInstance(DateFormat.SHORT, Locale.KOREA).format(Date(reading.receivedAtMillis))}",
            )
        }

    private fun format(value: Double?, unit: String): String = value?.let {
        "${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.US, it)}$unit"
    } ?: "—"

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "가스 농도 데모", NotificationManager.IMPORTANCE_LOW).apply {
                description = "실제 가스 측정기가 연결되기 전 UI 표시용 시뮬레이션입니다."
            },
        )
    }

    companion object {
        private const val ACTION_STOP = "com.minyook.sllm2.gas.SIMULATION_STOP"
        private const val CHANNEL_ID = "gas_demo_monitoring"
        private const val NOTIFICATION_ID = 4013
        private const val UPDATE_INTERVAL_MS = 20_000L

        fun start(context: Context) {
            if (GasReadingStore.current(context).source == GasReadingSource.BLE) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, GasSimulationService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GasSimulationService::class.java).setAction(ACTION_STOP))
        }
    }
}
