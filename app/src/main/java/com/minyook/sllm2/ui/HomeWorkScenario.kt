package com.minyook.sllm2.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.minyook.sllm2.gas.ApPhase

internal enum class ScenarioStep(val title: String, val symbol: WorkspaceSymbol, val destination: AppDestination) {
    REQUEST("작업 지시 요청", WorkspaceSymbol.MIC, AppDestination.CHECKLIST),
    INSPECTION("감독관 점검", WorkspaceSymbol.CHECK, AppDestination.SUPERVISOR),
    APPROVAL("점검 완료 · 투입 승인", WorkspaceSymbol.SHIELD, AppDestination.SUPERVISOR),
    ENTRY("현장 투입 확인", WorkspaceSymbol.CHECK, AppDestination.CHECKLIST),
    ASSISTANCE("AI 문서 지원 전환", WorkspaceSymbol.CHAT, AppDestination.CHECKLIST),
    MONITORING("작업 중 실시간 모니터링", WorkspaceSymbol.WIFI, AppDestination.SENSOR),
    ALARM("위험 감지 · 경보", WorkspaceSymbol.INFO, AppDestination.CHECKLIST),
    EVACUATION("긴급 탈출 · 종료 확인", WorkspaceSymbol.HISTORY, AppDestination.CHECKLIST),
}

@Composable
internal fun HomeWorkScenario(state: FieldGuardUiState, work: WorkScenarioState, openStep: (ScenarioStep?) -> Unit) {
    val order = work.currentOrder
    val meter = state.wifiAp.selected
    val monitoring = order?.fieldActive == true && state.wifiAp.phase == ApPhase.RUNNING &&
        meter?.test == false && meter.current
    val alarm = order?.fieldActive == true && state.wifiAp.phase == ApPhase.RUNNING &&
        meter?.test == false && meter.hasRecentSample && meter.sample?.alarm in setOf("warning", "danger")
    val step = activeWorkStep(order?.status, monitoring, alarm)
    val recorded = order?.events.orEmpty().map { it.first }.toSet()
    fun request(voice: Boolean) {
        work.openRequest(voice)
        openStep(ScenarioStep.REQUEST)
    }
    WorkspaceCard(Modifier.testTag("homeWorkScenario")) {
        Text("현장 작업", style = MaterialTheme.typography.headlineSmall)
        WorkspaceBadge("기기 내 시연 · 작업자", blue = true)
        if (order != null) {
            Text(order.task, style = MaterialTheme.typography.titleMedium)
            Text("${order.site} · ${order.location}", color = WorkspaceDesign.Muted)
            WorkspaceBadge(order.status.label, blue = true)
            WorkspaceButton("작업 진행 보기", { openStep(null) }, Modifier.fillMaxWidth())
            TextButton(onClick = { request(true) }) { Text("새 작업 요청") }
        } else {
            Text("현장과 작업 내용을 말하면 요청 양식에 입력돼요.")
            WorkspaceButton(
                "음성으로 작업 요청", { request(true) },
                Modifier.fillMaxWidth().testTag("homeVoiceWorkRequest"), icon = WorkspaceSymbol.MIC,
            )
            TextButton(onClick = { request(false) }, modifier = Modifier.testTag("homeManualWorkRequest")) { Text("직접 입력") }
        }
        if (alarm) WorkspaceNotice("선택한 감지기에서 ${if (meter.sample?.alarm == "danger") "위험" else "주의"} 경보를 받았어요.", warning = true)
    }
    WorkspaceListGroup("작업 기능") {
        ScenarioStep.entries.forEachIndexed { index, item ->
            if (item.destination == AppDestination.SUPERVISOR) return@forEachIndexed
            val number = index + 1
            if (index > 0) WorkspaceRowDivider()
            val detail = when {
                number == 6 && monitoring -> "선택한 감지기 측정값 수신 중"
                number == 7 && alarm -> "선택한 감지기 경보 수신"
                number == 7 && "report-danger" in recorded -> "위험 신고 기록됨"
                number == 8 && order?.status == WorkStatus.CLOSED -> "감독관 종료 기록됨"
                number == step -> "현재 단계"
                number == 1 && "request" in recorded -> "요청 기록됨"
                number == 2 && "save-inspection" in recorded -> "점검 내용 저장됨"
                number == 3 && order?.approvedBy?.isNotBlank() == true -> "투입 승인 기록됨"
                number == 4 && "enter" in recorded -> "현장 투입 기록됨"
                number == 5 && "start-ai" in recorded -> "문서 지원 시작 기록됨"
                number == 2 -> "감독관 역할로 점검"
                number == 6 -> "Wi‑Fi AP 감지기 연결"
                number == 7 -> "장비 경보 확인 · 위험 상황 신고"
                number == 8 -> "탈출 후 집결 위치 기록"
                else -> null
            }
            WorkspaceActionRow(
                item.symbol, item.title, detail,
                Modifier.testTag("workScenarioStep$number"),
            ) {
                if (item == ScenarioStep.REQUEST) request(true) else openStep(item)
            }
        }
    }
}

internal fun activeWorkStep(status: WorkStatus?, monitoring: Boolean, alarm: Boolean): Int = when (status) {
    null -> 1
    WorkStatus.REQUESTED, WorkStatus.RETURNED -> 2
    WorkStatus.REVIEWING -> 3
    WorkStatus.APPROVED -> 4
    WorkStatus.ENTERED, WorkStatus.SUPPORTING -> when {
        alarm -> 7
        monitoring || status == WorkStatus.SUPPORTING -> 6
        else -> 5
    }
    WorkStatus.ALARM, WorkStatus.EVACUATED, WorkStatus.EXITED, WorkStatus.CLOSED -> 8
}
