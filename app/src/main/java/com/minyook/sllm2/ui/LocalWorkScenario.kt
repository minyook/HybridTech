package com.minyook.sllm2.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal enum class WorkView { WORKER, SUPERVISOR }

internal enum class WorkStatus(val label: String) {
    REQUESTED("감독관 점검 대기"),
    REVIEWING("감독관 점검 기록됨"),
    RETURNED("보완 요청"),
    APPROVED("시연 투입 승인"),
    ENTERED("현장 투입 확인"),
    SUPPORTING("현장 지원 중"),
    ALARM("위험 신고 · 대피 중"),
    EVACUATED("탈출 확인"),
    EXITED("정상 퇴장 확인"),
    CLOSED("감독관 종료 확인"),
}

internal data class ScenarioOrder(
    val id: String,
    val revision: Int,
    val site: String,
    val location: String,
    val task: String,
    val worker: String,
    val status: WorkStatus = WorkStatus.REQUESTED,
    val inspection: JSONObject = JSONObject(),
    val approvedBy: String = "",
    val reason: String = "",
    val exitLocation: String = "",
    val events: List<Pair<String, String>> = listOf("request" to "작업자가 시연용 점검을 요청했어요."),
) {
    val fieldActive get() = status == WorkStatus.ENTERED || status == WorkStatus.SUPPORTING

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("revision", revision).put("site", site).put("location", location)
        .put("task", task).put("worker", worker).put("status", status.name).put("inspection", inspection)
        .put("approvedBy", approvedBy).put("reason", reason).put("exitLocation", exitLocation)
        .put("events", JSONArray(events.map { (action, message) -> JSONObject().put("action", action).put("message", message) }))

    companion object {
        fun parse(json: JSONObject) = ScenarioOrder(
            id = json.getString("id"),
            revision = json.getInt("revision"),
            site = json.getString("site"),
            location = json.getString("location"),
            task = json.getString("task"),
            worker = json.getString("worker"),
            status = WorkStatus.valueOf(json.getString("status")),
            inspection = json.getJSONObject("inspection"),
            approvedBy = json.getString("approvedBy"),
            reason = json.getString("reason"),
            exitLocation = json.getString("exitLocation"),
            events = json.getJSONArray("events").let { events ->
                List(events.length()) { index ->
                    events.getJSONObject(index).let { it.getString("action") to it.getString("message") }
                }
            },
        )
    }
}

internal val scenarioInspectionItems = listOf(
    "air" to "가스 상태",
    "ppe" to "개인보호구",
    "communication" to "통신 상태",
    "ventilation" to "환기 상태",
    "worker" to "작업자 상태",
)

internal interface WorkScenarioStore {
    fun read(): List<ScenarioOrder>
    fun advance(order: ScenarioOrder, type: String, payload: JSONObject): ScenarioOrder
}

internal class LocalWorkScenario(context: Context, preferencesName: String = "field_guard_worker_work") : WorkScenarioStore {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun read(): List<ScenarioOrder> = synchronized(lock) {
        val orders = JSONArray(preferences.getString("orders", "[]") ?: "[]")
        List(orders.length()) { ScenarioOrder.parse(orders.getJSONObject(it)) }
    }

    private fun save(orders: List<ScenarioOrder>) {
        check(preferences.edit().putString("orders", JSONArray(orders.map(ScenarioOrder::toJson)).toString()).commit())
    }

    fun create(name: String, site: String, location: String, task: String): ScenarioOrder = synchronized(lock) {
        require(listOf(name, site, location, task).all(String::isNotBlank)) { "작업자, 현장, 위치, 작업 내용을 입력해 주세요." }
        val order = ScenarioOrder(UUID.randomUUID().toString(), 0, site, location, task, name)
        save(listOf(order) + read())
        order
    }

    override fun advance(order: ScenarioOrder, type: String, payload: JSONObject): ScenarioOrder =
        advance(order.id, order.revision, WorkView.WORKER, type, payload)

    fun advance(id: String, revision: Int, view: WorkView, type: String, payload: JSONObject): ScenarioOrder = synchronized(lock) {
        val orders = read()
        val current = orders.firstOrNull { it.id == id }
        require(current != null && current.revision == revision) { "기록이 바뀌었어요. 현재 단계에서 다시 확인해 주세요." }
        val updated = current.transition(view, type, payload)
        save(listOf(updated) + orders.filterNot { it.id == id })
        updated
    }

    private companion object { val lock = Any() }
}

internal fun ScenarioOrder.transition(view: WorkView, action: String, payload: JSONObject): ScenarioOrder {
    fun requireView(expected: WorkView) {
        require(view == expected) { "해당 시연 역할에서 진행해 주세요." }
    }
    fun requireStatus(vararg valid: WorkStatus) {
        require(status in valid) { "현재 단계에서는 이 작업을 할 수 없어요." }
    }
    fun input(key: String) = payload.optString(key).trim().also {
        require(it.isNotEmpty()) { "기록 내용을 입력해 주세요." }
    }
    val (updated, message) = when (action) {
        "save-inspection" -> {
            requireView(WorkView.SUPERVISOR)
            requireStatus(WorkStatus.REQUESTED, WorkStatus.REVIEWING)
            copy(status = WorkStatus.REVIEWING, inspection = payload.getJSONObject("inspection")) to "시연 감독관이 점검 내용을 저장했어요."
        }
        "approve" -> {
            requireView(WorkView.SUPERVISOR)
            requireStatus(WorkStatus.REVIEWING)
            val checked = inspection.optJSONArray("checked") ?: JSONArray()
            val keys = List(checked.length()) { checked.getString(it) }
            require(scenarioInspectionItems.all { it.first in keys }) { "시연 점검 항목 5개를 모두 확인하고 저장해 주세요." }
            copy(status = WorkStatus.APPROVED, approvedBy = "시연 감독관", reason = "") to "시연 투입 승인을 기록했어요."
        }
        "return" -> {
            requireView(WorkView.SUPERVISOR)
            requireStatus(WorkStatus.REQUESTED, WorkStatus.REVIEWING)
            val reason = input("reason")
            copy(status = WorkStatus.RETURNED, reason = reason) to "보완 요청 · $reason"
        }
        "resubmit" -> {
            requireView(WorkView.WORKER)
            requireStatus(WorkStatus.RETURNED)
            val note = input("note")
            val inspection = JSONObject(inspection.toString()).put("checked", JSONArray())
            copy(status = WorkStatus.REQUESTED, reason = "", inspection = inspection) to "재점검 요청 · $note"
        }
        "revoke" -> {
            requireView(WorkView.SUPERVISOR)
            requireStatus(WorkStatus.APPROVED)
            val reason = input("reason")
            val inspection = JSONObject(inspection.toString()).put("checked", JSONArray())
            copy(status = WorkStatus.REVIEWING, approvedBy = "", reason = reason, inspection = inspection) to "시연 승인 취소 · $reason"
        }
        "enter" -> {
            requireView(WorkView.WORKER)
            requireStatus(WorkStatus.APPROVED)
            require(payload.optBoolean("acknowledged")) { "시연 승인 내용을 확인해 주세요." }
            copy(status = WorkStatus.ENTERED) to "작업자가 시연 투입을 확인했어요."
        }
        "start-ai" -> {
            requireView(WorkView.WORKER)
            requireStatus(WorkStatus.ENTERED)
            copy(status = WorkStatus.SUPPORTING) to "작업자가 문서 지원 화면을 열었어요."
        }
        "report-danger" -> {
            requireStatus(WorkStatus.ENTERED, WorkStatus.SUPPORTING)
            val reason = input("reason")
            copy(status = WorkStatus.ALARM, reason = reason) to "시연 위험 신고 · $reason"
        }
        "evacuate", "exit" -> {
            requireView(WorkView.WORKER)
            if (action == "evacuate") requireStatus(WorkStatus.ALARM) else requireStatus(WorkStatus.ENTERED, WorkStatus.SUPPORTING)
            val location = input("location")
            val evacuated = action == "evacuate"
            val status = if (evacuated) WorkStatus.EVACUATED else WorkStatus.EXITED
            copy(status = status, exitLocation = location) to "${if (evacuated) "시연 탈출" else "시연 퇴장"} 확인 · $location"
        }
        "close" -> {
            requireView(WorkView.SUPERVISOR)
            requireStatus(WorkStatus.EVACUATED, WorkStatus.EXITED)
            copy(status = WorkStatus.CLOSED) to "시연 감독관이 종료를 확인했어요."
        }
        else -> error("알 수 없는 시연 동작입니다.")
    }
    return updated.copy(revision = revision + 1, events = events + (action to message))
}
