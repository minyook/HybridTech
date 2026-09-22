package com.minyook.sllm2.gas

import android.content.Context
import android.content.Intent
import org.json.JSONObject

enum class GasReadingSource { NONE, BLE, SIMULATION }

/** A validated reading; null means the connected meter did not report that channel. */
data class GasReading(
    val oxygenPercent: Double? = null,
    val h2sPpm: Double? = null,
    val carbonMonoxidePpm: Double? = null,
    val lelPercent: Double? = null,
    val receivedAtMillis: Long = 0L,
    val deviceName: String? = null,
    val source: GasReadingSource = GasReadingSource.NONE,
) {
    val hasValues: Boolean
        get() = oxygenPercent != null || h2sPpm != null || carbonMonoxidePpm != null || lelPercent != null
}

/**
 * One local source of truth for the in-app gas bar and home-screen widget.
 * A BLE protocol adapter calls [update] only after its packet has passed
 * [GasPacketParser]'s range checks; no placeholder reading is ever emitted.
 */
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

    /** Records the connection intent before the first measurement reaches the phone. */
    fun beginBleSession(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .clear()
            .putString(KEY_SOURCE, GasReadingSource.BLE.name)
            .apply()
        appContext.sendBroadcast(Intent(ACTION_READING_CHANGED).setPackage(appContext.packageName))
        GasWidgetProvider.updateAll(appContext)
    }

    private fun android.content.SharedPreferences.doubleOrNull(key: String): Double? =
        if (contains(key)) java.lang.Double.longBitsToDouble(getLong(key, 0L)) else null

    private fun android.content.SharedPreferences.Editor.putNullableDouble(key: String, value: Double?) = apply {
        if (value == null) remove(key) else putLong(key, java.lang.Double.doubleToRawLongBits(value))
    }
}

/** Accepts the common JSON, key=value and four-column CSV BLE meter payloads. */
object GasPacketParser {
    fun parse(payload: ByteArray, deviceName: String? = null, receivedAtMillis: Long = System.currentTimeMillis()): GasReading? {
        val text = payload.toString(Charsets.UTF_8).trim().trim('\u0000')
        if (text.isBlank()) return null
        val values = parseJson(text) ?: parseKeyValue(text) ?: parseCsv(text) ?: return null
        val reading = GasReading(
            oxygenPercent = values["o2"],
            h2sPpm = values["h2s"],
            carbonMonoxidePpm = values["co"],
            lelPercent = values["lel"],
            receivedAtMillis = receivedAtMillis,
            deviceName = deviceName,
            source = GasReadingSource.BLE,
        )
        return reading.takeIf(::isPlausible)
    }

    private fun parseJson(text: String): Map<String, Double>? = runCatching {
        val json = JSONObject(text)
        channelAliases.mapNotNull { (channel, aliases) ->
            aliases.firstNotNullOfOrNull { key -> json.optDouble(key, Double.NaN).takeUnless(Double::isNaN) }
                ?.let { channel to it }
        }.toMap().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun parseKeyValue(text: String): Map<String, Double>? {
        val result = mutableMapOf<String, Double>()
        keyValue.findAll(text).forEach { match ->
            canonical(match.groupValues[1])?.let { channel ->
                match.groupValues[2].toDoubleOrNull()?.let { result[channel] = it }
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun parseCsv(text: String): Map<String, Double>? {
        val values = text.split(',', ';').map { it.trim().toDoubleOrNull() ?: return null }
        if (values.size != 4) return null
        return mapOf("o2" to values[0], "h2s" to values[1], "co" to values[2], "lel" to values[3])
    }

    private fun isPlausible(reading: GasReading): Boolean =
        (reading.oxygenPercent == null || reading.oxygenPercent in 0.0..100.0) &&
            (reading.h2sPpm == null || reading.h2sPpm in 0.0..1_000.0) &&
            (reading.carbonMonoxidePpm == null || reading.carbonMonoxidePpm in 0.0..10_000.0) &&
            (reading.lelPercent == null || reading.lelPercent in 0.0..100.0)

    private fun canonical(value: String): String? = when (value.lowercase().replace("₂", "2")) {
        "o2", "oxygen", "oxygenpercent" -> "o2"
        "h2s", "hydrogensulfide" -> "h2s"
        "co", "carbonmonoxide" -> "co"
        "lel", "lfl" -> "lel"
        else -> null
    }

    private val keyValue = Regex("([a-zA-Z0-9₂]+)\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)")
    private val channelAliases = mapOf(
        "o2" to listOf("o2", "oxygen", "oxygenPercent"),
        "h2s" to listOf("h2s", "hydrogenSulfide"),
        "co" to listOf("co", "carbonMonoxide"),
        "lel" to listOf("lel", "lfl"),
    )
}
