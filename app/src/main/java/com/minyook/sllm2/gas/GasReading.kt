package com.minyook.sllm2.gas

import android.content.Context
import android.content.Intent

enum class GasReadingSource { NONE, WIFI_AP, SIMULATION }

/** A validated reading; null means the connected meter did not report that channel. */
data class GasReading(
    val oxygenPercent: Double? = null,
    val h2sPpm: Double? = null,
    val carbonMonoxidePpm: Double? = null,
    val lelPercent: Double? = null,
    val receivedAtMillis: Long = 0L,
    val deviceName: String? = null,
    val source: GasReadingSource = GasReadingSource.NONE,
    val sessionActive: Boolean = true,
    val freshnessMs: Long = 60_000L,
) {
    val hasValues: Boolean
        get() = oxygenPercent != null || h2sPpm != null || carbonMonoxidePpm != null || lelPercent != null

    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean =
        source != GasReadingSource.NONE && sessionActive && hasValues && receivedAtMillis > 0L && nowMillis >= receivedAtMillis && nowMillis - receivedAtMillis < freshnessMs
}

/** Selected AP meter snapshot for the app, notification and widget. */
object GasReadingStore {
    const val ACTION_READING_CHANGED = "com.minyook.sllm2.gas.READING_CHANGED"
    private const val PREFERENCES = "gas_reading"
    private const val KEY_OXYGEN = "oxygen"
    private const val KEY_H2S = "h2s"
    private const val KEY_CO = "co"
    private const val KEY_LEL = "lel"
    private const val KEY_TIME = "time"
    private const val KEY_DEVICE = "device"
    private const val KEY_SOURCE = "source"

    fun current(context: Context): GasReading {
        val values = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return GasReading(
            oxygenPercent = values.doubleOrNull(KEY_OXYGEN),
            h2sPpm = values.doubleOrNull(KEY_H2S),
            carbonMonoxidePpm = values.doubleOrNull(KEY_CO),
            lelPercent = values.doubleOrNull(KEY_LEL),
            receivedAtMillis = values.getLong(KEY_TIME, 0L),
            deviceName = values.getString(KEY_DEVICE, null),
            sessionActive = values.getBoolean("active", false) && WifiApHub.state.value.phase == ApPhase.RUNNING,
            freshnessMs = values.getLong("freshness", 3000L),
            source = runCatching {
                GasReadingSource.valueOf(values.getString(KEY_SOURCE, GasReadingSource.NONE.name).orEmpty())
            }.getOrDefault(GasReadingSource.NONE),
        )
    }

    fun update(context: Context, reading: GasReading) {
        require(reading.hasValues) { "At least one gas channel must be present." }
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putNullableDouble(KEY_OXYGEN, reading.oxygenPercent)
            .putNullableDouble(KEY_H2S, reading.h2sPpm)
            .putNullableDouble(KEY_CO, reading.carbonMonoxidePpm)
            .putNullableDouble(KEY_LEL, reading.lelPercent)
            .putBoolean("active", reading.sessionActive)
            .putLong("freshness", reading.freshnessMs)
            .putLong(KEY_TIME, reading.receivedAtMillis)
            .putString(KEY_DEVICE, reading.deviceName)
            .putString(KEY_SOURCE, reading.source.name)
            .apply()
        appContext.sendBroadcast(Intent(ACTION_READING_CHANGED).setPackage(appContext.packageName))
        GasWidgetProvider.updateAll(appContext)
    }

    /** A new physical connection must never inherit an earlier meter's reading. */
    fun clear(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
        appContext.sendBroadcast(Intent(ACTION_READING_CHANGED).setPackage(appContext.packageName))
        GasWidgetProvider.updateAll(appContext)
    }

    private fun android.content.SharedPreferences.doubleOrNull(key: String): Double? =
        if (contains(key)) java.lang.Double.longBitsToDouble(getLong(key, 0L)) else null

    private fun android.content.SharedPreferences.Editor.putNullableDouble(key: String, value: Double?) = apply {
        if (value == null) remove(key) else putLong(key, java.lang.Double.doubleToRawLongBits(value))
    }
}
