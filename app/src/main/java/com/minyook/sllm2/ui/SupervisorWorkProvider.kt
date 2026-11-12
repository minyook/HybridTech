package com.minyook.sllm2.ui

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject

internal val supervisorWorkUri: Uri = Uri.parse("content://com.minyook.sllm2.work/orders")

class SupervisorWorkProvider : ContentProvider() {
    override fun onCreate() = true

    private fun supervisorContext(): Context {
        val owner = requireNotNull(context)
        if (owner.packageName != "com.minyook.sllm2" || callingPackage != "com.minyook.sllm2.supervisor") {
            throw SecurityException("감독관 앱만 작업 기록에 접근할 수 있어요.")
        }
        owner.enforceCallingPermission("com.minyook.sllm2.permission.SUPERVISE_WORK", "감독관 앱 서명을 확인해 주세요.")
        return owner
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val owner = supervisorContext()
        require(uri == supervisorWorkUri)
        return MatrixCursor(arrayOf("record")).apply {
            LocalWorkScenario(owner).read().forEach { addRow(arrayOf(it.toJson().toString())) }
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val owner = supervisorContext()
        require(method == "advance")
        val input = requireNotNull(extras)
        val type = requireNotNull(input.getString("action"))
        require(type in setOf("save-inspection", "approve", "return", "revoke", "report-danger", "close"))
        val result = Bundle()
        try {
            val order = LocalWorkScenario(owner).advance(
                requireNotNull(arg), input.getInt("revision", -1), WorkView.SUPERVISOR, type,
                JSONObject(input.getString("payload") ?: "{}"),
            )
            result.putString("record", order.toJson().toString())
        } catch (error: IllegalArgumentException) {
            result.putString("error", error.message)
        }
        return result
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}

internal class SupervisorWorkScenario(private val resolver: ContentResolver) : WorkScenarioStore {
    override fun read(): List<ScenarioOrder> =
        requireNotNull(resolver.query(supervisorWorkUri, null, null, null, null)).use { records ->
            buildList { while (records.moveToNext()) add(ScenarioOrder.parse(JSONObject(records.getString(0)))) }
        }

    override fun advance(order: ScenarioOrder, type: String, payload: JSONObject): ScenarioOrder {
        val args = Bundle().apply {
            putString("action", type)
            putInt("revision", order.revision)
            putString("payload", payload.toString())
        }
        val response = requireNotNull(resolver.call(supervisorWorkUri, "advance", order.id, args))
        response.getString("error")?.let { throw IllegalArgumentException(it) }
        return ScenarioOrder.parse(JSONObject(requireNotNull(response.getString("record"))))
    }
}
