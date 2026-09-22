package com.minyook.sllm2.gas

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.minyook.sllm2.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Home-screen summary. It only renders the last validated BLE measurement. */
class GasWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { widgetId -> manager.updateAppWidget(widgetId, views(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, GasWidgetProvider::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(appContext))
        }

        private fun views(context: Context): RemoteViews {
            val reading = GasReadingStore.current(context)
            return RemoteViews(context.packageName, R.layout.widget_gas_reading).apply {
                setTextViewText(R.id.widget_o2, format(reading.oxygenPercent, "%"))
                setTextViewText(R.id.widget_h2s, format(reading.h2sPpm, "ppm"))
                setTextViewText(R.id.widget_co, format(reading.carbonMonoxidePpm, "ppm"))
                setTextViewText(R.id.widget_lel, format(reading.lelPercent, "%LEL"))
                setTextViewText(
                    R.id.widget_status,
                    if (reading.source == GasReadingSource.SIMULATION) {
                        "데모 시뮬레이션 · 실제 측정값 아님"
                    } else if (reading.receivedAtMillis > 0L) {
                        "${reading.deviceName ?: "BLE 측정기"} · ${DateFormat.getTimeInstance(DateFormat.SHORT, Locale.KOREA).format(Date(reading.receivedAtMillis))}"
                    } else {
                        "센서 연결 대기 · 실제 측정값만 표시"
                    },
                )
            }
        }

        private fun format(value: Double?, unit: String): String = value?.let {
            "${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.US, it)}$unit"
        } ?: "—"
    }
}
