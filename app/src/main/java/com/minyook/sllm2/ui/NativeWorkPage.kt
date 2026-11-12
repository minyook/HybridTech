package com.minyook.sllm2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.minyook.sllm2.model.ModelPhase
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun NativeWorkPage(
    state: FieldGuardUiState,
    actions: FieldGuardActions,
    work: WorkScenarioState,
    step: ScenarioStep?,
    openStep: (ScenarioStep?) -> Unit,
) {
    val order = work.currentOrder
    WorkspacePage {
        WorkspaceHeading(step?.title ?: "작업 진행", step?.instruction)
        WorkspaceBadge("기기 내 시연 · 작업자", blue = true)
        if (step == ScenarioStep.ALARM) {
            WorkspaceButton("측정값·장비 경보 보기", { actions.onNavigate(AppDestination.SENSOR) }, icon = WorkspaceSymbol.WIFI)
        }
        work.actionError?.let { WorkspaceNotice(it, warning = true) }
        if (step == ScenarioStep.REQUEST || work.requestOpen || (step == null && work.orders.isEmpty())) {
            WorkRequestForm(work, onCreated = { openStep(null) })
            if (work.orders.isNotEmpty()) TextButton(onClick = { openStep(null) }) { Text("요청 작성 닫기") }
            return@WorkspacePage
        }
        val orders = work.orders
        if (order != null) key(order.id) {
            WorkspaceCard {
                Text(order.status.label, style = MaterialTheme.typography.titleLarge)
                Text("${order.site} · ${order.location}")
                Text(order.task)
                Text("작업자 ${order.worker}", style = MaterialTheme.typography.bodySmall, color = WorkspaceDesign.Muted)
                if (order.reason.isNotBlank()) Text(order.reason)
                if (order.exitLocation.isNotBlank()) Text("확인 위치  ${order.exitLocation}")
                WorkerActions(order, state, work, actions, step, openStep)
            }
            val events = order.events
            if (events.isNotEmpty()) WorkspaceListGroup("진행 기록") {
                events.asReversed().forEach { (_, message) ->
                    ListItem(headlineContent = { Text(message, style = MaterialTheme.typography.bodySmall) })
                }
            }
        }
        if (orders.isNotEmpty()) WorkspaceListGroup("작업 목록") {
            orders.forEach { item ->
                    WorkspaceActionRow(WorkspaceSymbol.CHECK, item.task, "${item.site} · ${item.status.label}") {
                    work.selectedId = item.id
                    work.requestOpen = false
                }
            }
        } else {
            Text("아직 시연 작업이 없어요. 작업자 화면에서 요청을 먼저 기록해 주세요.")
        }
        if (step == null || orders.isEmpty()) WorkspaceButton("새 작업 요청", {
            work.openRequest(true)
            openStep(ScenarioStep.REQUEST)
        }, icon = WorkspaceSymbol.MIC)
    }
}

private val ScenarioStep.instruction: String?
    get() = when (this) {
        ScenarioStep.REQUEST, ScenarioStep.MONITORING -> null
        ScenarioStep.INSPECTION -> "요청된 시연 작업의 점검 항목 5개를 확인하고 저장해 주세요."
        ScenarioStep.APPROVAL -> "감독관이 저장된 점검 내용을 확인한 뒤 투입 승인을 기록하는 단계입니다."
        ScenarioStep.ENTRY -> "감독관 승인이 기록되면 작업자가 승인 내용을 읽고 투입을 확인할 수 있어요."
        ScenarioStep.ASSISTANCE -> "현장 투입을 확인한 작업에서 음성 문서 지원을 열 수 있어요."
        ScenarioStep.ALARM -> "장비가 보낸 경보를 확인하거나, 진행 중인 작업의 위험 상황을 직접 신고할 수 있어요."
        ScenarioStep.EVACUATION -> "위험 신고 후 실제 탈출한 작업자가 집결 위치를 기록하고, 감독관이 종료를 확인합니다."
    }

@Composable
private fun WorkRequestForm(work: WorkScenarioState, onCreated: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var site by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var task by rememberSaveable { mutableStateOf("") }
    WorkspaceCard {
        Text("작업 점검 요청", style = MaterialTheme.typography.titleLarge)
        Text("현장, 위치, 작업 내용을 말한 뒤 입력 내용을 확인해 주세요.")
        Text("같은 기기의 감독관 앱에서 이 요청을 확인할 수 있어요.", style = MaterialTheme.typography.bodySmall)
        WorkRequestVoice(work.startRequestMic, { work.startRequestMic = false }) { transcript ->
            val draft = parseSpokenWorkDraft(transcript)
            draft.site?.let { site = it.take(100) }
            draft.location?.let { location = it.take(150) }
            task = draft.task.take(2000)
        }
        WorkField("작업자 이름", name, 60, "workName") { name = it }
        WorkField("현장", site, 100, "workSite") { site = it }
        WorkField("작업 위치", location, 150, "workLocation") { location = it }
        WorkField("작업 내용", task, 2000, "workTask", singleLine = false) { task = it }
        WorkspaceButton(
            "시연 요청 등록",
            { work.create(name.trim(), site.trim(), location.trim(), task.trim(), onCreated) },
            Modifier.fillMaxWidth().testTag("workSubmit"),
            enabled = !work.busy && listOf(name, site, location, task).all(String::isNotBlank),
        )
    }
}

@Composable
private fun WorkField(
    label: String,
    value: String,
    maxLength: Int,
    tag: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value, { onChange(it.take(maxLength)) },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun WorkerActions(
    order: ScenarioOrder,
    state: FieldGuardUiState,
    work: WorkScenarioState,
    actions: FieldGuardActions,
    step: ScenarioStep?,
    openStep: (ScenarioStep?) -> Unit,
) {
    val view = WorkView.WORKER
    when (order.status) {
        WorkStatus.RETURNED -> if (step == null || step == ScenarioStep.ENTRY) WorkTextAction("보완 내용", "다시 요청", 2000) {
            work.advance(view, order, "resubmit", JSONObject().put("note", it))
        }
        WorkStatus.APPROVED -> if (step == null || step == ScenarioStep.ENTRY) {
            ApprovedInspection(order)
            var acknowledged by rememberSaveable(order.revision) { mutableStateOf(false) }
            WorkCheck("시연 승인 내용을 확인했습니다.", acknowledged) { acknowledged = it }
            WorkspaceButton("현장 투입 확인", {
                work.advance(view, order, "enter", JSONObject().put("acknowledged", true)) {
                    if (step == ScenarioStep.ENTRY) openStep(ScenarioStep.ASSISTANCE)
                }
            }, Modifier.testTag("workEnter"), enabled = acknowledged)
        }
        WorkStatus.ENTERED, WorkStatus.SUPPORTING -> {
            val aiReady = state.modelStatus.phase == ModelPhase.READY
            val supporting = order.status == WorkStatus.SUPPORTING
            val openVoice = {
                work.voiceFromWork = true
                actions.onNavigate(AppDestination.VOICE)
            }
            if (step == null || step == ScenarioStep.ASSISTANCE) {
                WorkspaceButton("음성 문서 지원 열기", {
                    if (supporting) openVoice() else work.advance(view, order, "start-ai", onSaved = openVoice)
                }, Modifier.testTag("workOpenAssistance"))
                if (!aiReady) TextButton(onClick = { actions.onNavigate(AppDestination.MODEL) }) { Text("EXAONE 모델 준비") }
                WorkspaceButton("가스 측정 열기", { openStep(ScenarioStep.MONITORING) },
                    Modifier.testTag("workOpenMonitoring"), icon = WorkspaceSymbol.WIFI)
            }
            if (step == null || step == ScenarioStep.ALARM) WorkTextAction("시연 위험 상황", "위험 신고", 1000) {
                work.advance(view, order, "report-danger", JSONObject().put("reason", it)) {
                    if (step == ScenarioStep.ALARM) openStep(ScenarioStep.EVACUATION)
                }
            }
            if (step == null || step == ScenarioStep.EVACUATION) WorkTextAction("정상 퇴장 확인 위치", "퇴장 기록", 200) {
                work.advance(view, order, "exit", JSONObject().put("location", it))
            }
        }
        WorkStatus.ALARM -> if (step == null || step == ScenarioStep.EVACUATION) {
            Text("시연에서 탈출을 마친 뒤 집결 위치를 기록해 주세요.")
            WorkTextAction("집결 위치", "탈출 확인 기록", 200) {
                work.advance(view, order, "evacuate", JSONObject().put("location", it))
            }
        } else if (step == ScenarioStep.ALARM) WorkspaceButton("탈출 확인 단계 열기", { openStep(ScenarioStep.EVACUATION) })
        else -> Unit
    }
}

@Composable
internal fun SupervisorWorkspace(work: WorkScenarioState) {
    val order = work.currentOrder
    WorkspacePage {
        WorkspaceHeading("감독관 작업 관리", "같은 기기에 있는 작업자의 요청을 확인하고 점검과 종료를 기록해요.")
        WorkspaceBadge("기기 내 시연 · 감독관", blue = true)
        if (work.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        work.actionError?.let { WorkspaceNotice(it, warning = true) }
        WorkspaceButton("작업 목록 새로고침", work::refresh, primary = false, enabled = !work.busy)
        if (work.orders.isNotEmpty()) WorkspaceListGroup("작업 목록") {
            work.orders.forEach { item ->
                WorkspaceActionRow(WorkspaceSymbol.CHECK, item.task, "${item.site} · ${item.status.label}") {
                    work.clearError()
                    work.selectedId = item.id
                }
            }
        } else if (!work.busy && work.actionError == null) {
            Text("아직 요청이 없어요. 작업자 앱에서 시연 요청을 먼저 등록해 주세요.")
        }
        if (order != null) key(order.id) {
            WorkspaceCard {
                Text(order.status.label, style = MaterialTheme.typography.titleLarge)
                Text(order.task, style = MaterialTheme.typography.titleMedium)
                Text("${order.site} · ${order.location} · ${order.worker}")
                if (order.reason.isNotBlank()) Text(order.reason)
                if (order.exitLocation.isNotBlank()) Text("확인 위치  ${order.exitLocation}")
                SupervisorActions(order, work)
            }
        }
    }
}

@Composable
private fun SupervisorActions(order: ScenarioOrder, work: WorkScenarioState) {
    val view = WorkView.SUPERVISOR
    when (order.status) {
        WorkStatus.REQUESTED, WorkStatus.REVIEWING -> {
            var dirty by rememberSaveable(order.revision) { mutableStateOf(false) }
            key(order.revision) {
                InspectionForm(order.inspection, { dirty = true }) {
                    work.advance(view, order, "save-inspection", JSONObject().put("inspection", it))
                }
            }
            if (order.status == WorkStatus.REVIEWING) WorkspaceButton("점검 완료 · 투입 승인 기록", {
                work.advance(view, order, "approve")
            }, Modifier.testTag("workApprove"), enabled = !dirty && !work.busy)
            WorkTextAction("보완 사유", "보완 요청", 1000) {
                work.advance(view, order, "return", JSONObject().put("reason", it))
            }
        }
        WorkStatus.APPROVED -> {
            ApprovedInspection(order)
            order.inspection.optString("note").takeIf(String::isNotBlank)?.let { Text("점검 메모  $it") }
            WorkTextAction("승인 취소 사유", "승인 취소", 1000) {
                work.advance(view, order, "revoke", JSONObject().put("reason", it))
            }
        }
        WorkStatus.ENTERED, WorkStatus.SUPPORTING -> WorkTextAction("시연 위험 상황", "위험 신고", 1000) {
            work.advance(view, order, "report-danger", JSONObject().put("reason", it))
        }
        WorkStatus.EVACUATED, WorkStatus.EXITED -> WorkspaceButton("감독관 종료 확인", {
            work.advance(view, order, "close", JSONObject().put("note", ""))
        }, Modifier.testTag("workClose"))
        else -> Unit
    }
}

@Composable
private fun ApprovedInspection(order: ScenarioOrder) {
    val inspection = order.inspection
    val gas = inspection.optJSONObject("gas")
    val values = inspectionGases.mapNotNull { (key, label) ->
        gas?.takeUnless { it.isNull(key) }?.optString(key)?.takeIf(String::isNotBlank)?.let { "$label $it" }
    }
    if (order.approvedBy.isNotBlank()) Text("승인 담당  ${order.approvedBy}")
    Text("확인한 점검 항목 ${inspection.optJSONArray("checked")?.length() ?: 0}개")
    if (values.isNotEmpty()) Text("측정값  ${values.joinToString(" · ")}")
    inspection.optString("device").takeIf(String::isNotBlank)?.let { Text("측정 장비  $it") }
    inspection.optString("measuredAt").takeIf(String::isNotBlank)?.let { Text("측정 시각  ${displayWorkTime(it)}") }
    inspection.optString("validUntil").takeIf(String::isNotBlank)?.let { Text("시연 기록 유효기한  ${displayWorkTime(it)}") }
    val contacts = listOf(inspection.optString("watcher"), inspection.optString("contact")).filter(String::isNotBlank)
    if (contacts.isNotEmpty()) Text("감시인·연락 방법  ${contacts.joinToString(" · ")}")
}

private val inspectionGases = listOf("o2" to "O₂ (%)", "h2s" to "H₂S (ppm)", "co" to "CO (ppm)", "co2" to "CO₂ (%)")
private val workTimeFormat = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")

@Composable
private fun InspectionForm(initial: JSONObject, onEdit: () -> Unit, save: (JSONObject) -> Unit) {
    val initialChecks = initial.optJSONArray("checked") ?: JSONArray()
    val checks = remember { mutableStateListOf(*Array(initialChecks.length()) { initialChecks.getString(it) }) }
    val gas = initial.optJSONObject("gas")
    val readings = remember { mutableStateListOf(*inspectionGases.map { (key, _) ->
        gas?.takeUnless { it.isNull(key) }?.optString(key).orEmpty()
    }.toTypedArray()) }
    var device by rememberSaveable { mutableStateOf(initial.optString("device")) }
    var measured by rememberSaveable { mutableStateOf(displayWorkTime(initial.optString("measuredAt"))) }
    var expires by rememberSaveable { mutableStateOf(displayWorkTime(initial.optString("validUntil"))) }
    var watcher by rememberSaveable { mutableStateOf(initial.optString("watcher")) }
    var contact by rememberSaveable { mutableStateOf(initial.optString("contact")) }
    var note by rememberSaveable { mutableStateOf(initial.optString("note")) }
    var timeError by remember { mutableStateOf(false) }
    Text("감독관 시연 점검", style = MaterialTheme.typography.titleMedium)
    scenarioInspectionItems.forEach { (key, label) ->
        WorkCheck(label, key in checks) { if (it) checks.add(key) else checks.remove(key); onEdit() }
    }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { showDetails = !showDetails }) {
        Text(if (showDetails) "측정·감시인 항목 접기" else "측정·감시인 정보 입력 (선택)")
    }
    if (showDetails) {
    inspectionGases.forEachIndexed { index, (key, label) ->
        WorkField(label, readings[index], 16, "inspection-$key", keyboardType = KeyboardType.Decimal) { readings[index] = it; onEdit() }
    }
    WorkField("측정 장비와 출처", device, 120, "inspection-device") { device = it; onEdit() }
    WorkField("측정 시각 (YYYY-MM-DD HH:mm)", measured, 16, "inspection-measured") { measured = it; onEdit() }
    TextButton(onClick = { measured = LocalDateTime.now().format(workTimeFormat); onEdit() }) { Text("현재 시각 입력") }
    WorkField("시연 기록 유효기한 (YYYY-MM-DD HH:mm)", expires, 16, "inspection-expires") { expires = it; onEdit() }
    WorkField("감시인 또는 현장 관리인", watcher, 100, "inspection-watcher") { watcher = it; onEdit() }
    WorkField("연락 방법", contact, 120, "inspection-contact") { contact = it; onEdit() }
    WorkField("점검 메모", note, 1000, "inspection-note") { note = it; onEdit() }
    }
    if (timeError) WorkspaceNotice("가스 측정값과 시각 입력 형식을 확인해 주세요.", warning = true)
    WorkspaceButton("점검 내용 저장", {
        val measuredIso = inputWorkTime(measured)
        val expiresIso = inputWorkTime(expires)
        val parsed = readings.map { it.trim().toDoubleOrNull()?.takeIf(Double::isFinite) }
        timeError = measuredIso == null || expiresIso == null || readings.indices.any { readings[it].isNotBlank() && parsed[it] == null }
        if (!timeError) {
            val values = JSONObject()
            inspectionGases.forEachIndexed { index, (key, _) -> values.put(key, parsed[index] ?: JSONObject.NULL) }
            save(JSONObject().put("checked", JSONArray(checks.toList())).put("gas", values)
                .put("device", device.trim()).put("measuredAt", measuredIso).put("validUntil", expiresIso)
                .put("watcher", watcher.trim()).put("contact", contact.trim()).put("note", note.trim()))
        }
    }, Modifier.testTag("workSaveInspection"))
}

private fun displayWorkTime(value: String): String =
    runCatching { ZonedDateTime.parse(value).withZoneSameInstant(ZoneId.systemDefault()).format(workTimeFormat) }.getOrDefault(value)

private fun inputWorkTime(value: String): String? =
    if (value.isBlank()) "" else runCatching {
        LocalDateTime.parse(value.trim(), workTimeFormat.withResolverStyle(java.time.format.ResolverStyle.STRICT))
            .atZone(ZoneId.systemDefault()).toInstant().toString()
    }.getOrNull()

@Composable
private fun WorkCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = null)
        Text(label)
    }
}

@Composable
private fun WorkTextAction(label: String, button: String, limit: Int, action: (String) -> Unit) {
    var text by rememberSaveable(label) { mutableStateOf("") }
    WorkField(label, text, limit, "work-action-$button") { text = it }
    WorkspaceButton(button, { action(text.trim()) }, primary = false, enabled = text.isNotBlank())
}
