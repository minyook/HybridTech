package com.minyook.sllm2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minyook.sllm2.model.ModelPhase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object WorkspaceDesign {
    val Blue = Color(0xFF3182F6)
    val BlueWeak = Color(0xFFEAF3FF)
    val Canvas = Color(0xFFF7F9FC)
    val Border = Color(0xFFE8EDF3)
    val Ink = Color(0xFF191F28)
    val Muted = Color(0xFF6B7684)
}

internal val workspaceNavigation = listOf(
    AppDestination.HOME to "홈", AppDestination.CHAT to "문서 질문",
    AppDestination.LIBRARY to "문서 보관함", AppDestination.CHECKLIST to "작업 전 점검",
    AppDestination.HISTORY to "대화 기록", AppDestination.SETTINGS to "설정",
)

@Composable
internal fun FieldWorkspaceSidebar(destination: AppDestination, navigate: (AppDestination) -> Unit) {
    Column(Modifier.width(242.dp).fillMaxHeight().background(Color.White).statusBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.padding(bottom = 28.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = WorkspaceDesign.Blue, shape = RoundedCornerShape(12.dp)) { Text("O₂", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp)) }
            Column(Modifier.padding(start = 10.dp)) { Text("Field Guard", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("HYBRIDTECH", fontSize = 9.sp, color = WorkspaceDesign.Muted, letterSpacing = 1.sp) }
        }
        Text("WORKSPACE", fontSize = 10.sp, color = WorkspaceDesign.Muted, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
        workspaceNavigation.forEach { (target, title) ->
            val selected = destination == target || (target == AppDestination.CHECKLIST && destination in setOf(AppDestination.LOCAL_CHECKLIST, AppDestination.SUPERVISOR))
            Surface(color = if (selected) WorkspaceDesign.BlueWeak else Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable { navigate(target) }.testTag("workspace-${target.name}")) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TossNavigationIcon(target, if (selected) WorkspaceDesign.Blue else WorkspaceDesign.Muted)
                    Text(title, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) WorkspaceDesign.Blue else WorkspaceDesign.Muted)
                }
            }
        }
        Text("ANDROID APP", fontSize = 9.sp, color = WorkspaceDesign.Muted, modifier = Modifier.padding(top = 20.dp))
        Surface(Modifier.fillMaxWidth().clickable { navigate(AppDestination.SENSOR) }.testTag("workspace-SENSOR"), color = Color.White) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("가스 측정", fontWeight = FontWeight.SemiBold); Text("측정기 연결과 현장 경보", fontSize = 11.sp, color = WorkspaceDesign.Muted) }
        }
        Spacer(Modifier.weight(1f))
        Text("현장 지식은 내 기기에", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("문서와 AI는 기기 안에서,\n작업 승인은 담당자 확인으로.", fontSize = 11.sp, lineHeight = 19.sp, color = WorkspaceDesign.Muted)
    }
}

@Composable
internal fun FieldWorkspaceHome(state: FieldGuardUiState, actions: FieldGuardActions) {
    val wide = LocalConfiguration.current.screenWidthDp >= 850
    TossScrollPage {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("HYBRIDTECH · FIELD WORKSPACE", fontSize = 9.sp, color = WorkspaceDesign.Muted)
            Text(SimpleDateFormat("M월 d일 E요일", Locale.KOREAN).format(Date()), fontSize = 9.sp, color = WorkspaceDesign.Muted)
        }
        Surface(color = Color.White, border = BorderStroke(1.dp, WorkspaceDesign.Border), shape = RoundedCornerShape(21.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 330.dp).padding(25.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
                    Text("현장의 질문에, 문서로 답해요", color = WorkspaceDesign.Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("안전한 작업의 시작,\n근거부터 확인하세요.", fontSize = if (wide) 34.sp else 28.sp, lineHeight = if (wide) 46.sp else 39.sp, fontWeight = FontWeight.Bold, color = WorkspaceDesign.Ink)
                    Text("밀폐공간 안전 문서를 내 기기에서 바로 검색하고\n필요한 절차와 기준을 원문으로 확인해요.", fontSize = 12.sp, lineHeight = 22.sp, color = WorkspaceDesign.Muted)
                    Button(onClick = { actions.onNavigate(AppDestination.CHAT) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = WorkspaceDesign.Blue), modifier = Modifier.padding(top = 5.dp).heightIn(min = 48.dp)) { Text("문서에 질문하기  →", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
                FieldWorkspaceShield(Modifier.size(if (wide) 120.dp else 65.dp).align(Alignment.BottomEnd))
            }
        }
        TossPanel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                WorkspaceReadiness("문서 검색", if (state.documents.isEmpty()) "준비 중" else "사용 가능")
                WorkspaceReadiness("PDF 원본", "오프라인 준비됨")
                WorkspaceReadiness("기기 내 AI", if (state.modelStatus.phase == ModelPhase.READY) "모델 저장됨" else "준비하기", { actions.onNavigate(AppDestination.MODEL) })
            }
        }
        Text("오늘의 현장 준비", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WorkspaceQuickCard("작업 전 점검", "요청·감독관 점검·승인부터\n투입과 퇴장까지 확인해요", AppDestination.CHECKLIST, Modifier.weight(1f), actions)
            WorkspaceQuickCard("문서 보관함", "안전작업 가이드와 점검표를\n원본 그대로 확인해요", AppDestination.LIBRARY, Modifier.weight(1f), actions)
            WorkspaceQuickCard("오프라인 준비", "통신이 어려운 현장에서도\n필요한 문서를 열어요", AppDestination.MODEL, Modifier.weight(1f), actions)
        } else {
            WorkspaceQuickCard("작업 전 점검", "작업자 요청 · 감독관 승인", AppDestination.CHECKLIST, Modifier, actions)
            WorkspaceQuickCard("문서 보관함", "안전작업 가이드와 점검표 원본", AppDestination.LIBRARY, Modifier, actions)
            WorkspaceQuickCard("오프라인 준비", "현장에 가기 전 문서와 AI 준비", AppDestination.MODEL, Modifier, actions)
        }
        TossPanel {
            Text("이렇게 질문해 보세요", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            listOf("산소 및 유해가스 측정방법 알려줘", "밀폐공간 작업허가 절차 알려줘", "심폐소생술과 산소측정법 알려줘").forEach { prompt -> TossRow(prompt, onClick = { actions.onPreset(prompt) }, trailing = "→") }
        }
        TossGasSummary(state.gasReading) { actions.onNavigate(AppDestination.SENSOR) }
        if (state.histories.isNotEmpty()) TossPanel {
            Text("최근 확인한 질문", style = MaterialTheme.typography.titleMedium)
            state.histories.take(3).forEach { session -> TossRow(session.title, "기기에 저장된 대화", { actions.onLoadChat(session.id) }) }
        }
        Text("질문과 답변은 이 기기에 보관해요.\nHybridTech O₂ Field Guard", fontSize = 10.sp, color = WorkspaceDesign.Muted, lineHeight = 18.sp)
    }
}

@Composable
private fun WorkspaceReadiness(title: String, value: String, onClick: () -> Unit = {}) {
    Column(Modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = WorkspaceDesign.Muted, fontSize = 10.sp)
        Text(value, color = WorkspaceDesign.Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WorkspaceQuickCard(title: String, description: String, destination: AppDestination, modifier: Modifier, actions: FieldGuardActions) {
    Surface(modifier = modifier.fillMaxWidth().clickable { actions.onNavigate(destination) }, color = Color.White, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, WorkspaceDesign.Border)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = WorkspaceDesign.BlueWeak, shape = RoundedCornerShape(14.dp)) { Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { TossNavigationIcon(destination, WorkspaceDesign.Blue) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(description, fontSize = 11.sp, lineHeight = 19.sp, color = WorkspaceDesign.Muted) }
            Text("→", color = WorkspaceDesign.Blue)
        }
    }
}

@Composable
internal fun FieldWorkspaceShield(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(Color(0xFFF2F7FF)); drawCircle(Color(0xFFE1EDFF), radius = size.minDimension * .4f, style = Stroke(1.dp.toPx()))
        val w = size.width; val h = size.height
        val shield = Path().apply { moveTo(w*.5f,h*.23f);lineTo(w*.74f,h*.32f);lineTo(w*.72f,h*.56f);quadraticTo(w*.7f,h*.7f,w*.5f,h*.8f);quadraticTo(w*.3f,h*.7f,w*.28f,h*.56f);lineTo(w*.26f,h*.32f);close() }
        drawPath(shield,Color(0xFF8BB8FF),style=Stroke(3.dp.toPx()))
        val check=Path().apply{moveTo(w*.38f,h*.5f);lineTo(w*.47f,h*.59f);lineTo(w*.64f,h*.42f)}
        drawPath(check,WorkspaceDesign.Blue,style=Stroke(3.dp.toPx()))
    }
}

@Composable
internal fun FieldWorkspaceDocument(document: DocumentUi, index: Int, actions: FieldGuardActions, modifier: Modifier = Modifier) {
    val checklist = document.id.contains("2026")
    TossPanel(modifier) {
        Surface(color = if (checklist) Color(0xFFEAF8F2) else Color(0xFFECF3FD), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().height(210.dp).padding(23.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("MINISTRY OF EMPLOYMENT AND LABOR", fontSize = 8.sp, color = WorkspaceDesign.Muted, letterSpacing = .6.sp)
                Text(if (checklist) "밀폐공간\n자율점검표" else "밀폐공간\n안전작업 가이드", fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = WorkspaceDesign.Ink)
                Text(if (checklist) "2026 · PDF" else "2022 · PDF", fontSize = 11.sp, color = WorkspaceDesign.Muted)
            }
        }
        Text("DOCUMENT 0${index + 1}", fontSize = 10.sp, color = WorkspaceDesign.Muted, letterSpacing = 1.sp)
        Text(document.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("${document.chunkCount}쪽 · PDF 원본 기기에 저장됨", fontSize = 12.sp, color = WorkspaceDesign.Muted)
        TossSecondaryButton("문서 열기  →", { actions.onOpenDocument(document.id) })
    }
}

@Composable
internal fun FieldWorkspaceSettings(state: FieldGuardUiState, actions: FieldGuardActions) {
    val wide = LocalConfiguration.current.screenWidthDp >= 850
    TossScrollPage {
        TossPageTitle("현장에 가기 전,\n준비해요", "문서와 AI를 이 기기에 저장하면 인터넷 없이 사용할 수 있어요.")
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            WorkspaceOfflinePanel(state, actions, Modifier.weight(1f))
            WorkspaceModelPanel(state, actions, Modifier.weight(1f))
        } else { WorkspaceOfflinePanel(state, actions); WorkspaceModelPanel(state, actions) }
        TossPanel {
            TossRow("AI 토큰 설정", "답변 길이와 문맥 크기 · 문맥 ${state.settings.contextTokens} 토큰", { actions.onNavigate(AppDestination.INFERENCE) })
            TossRow("감독관 화면으로 전환", "오늘의 암호코드 6자리 입력", { actions.onNavigate(AppDestination.SUPERVISOR) })
            TossRow("현장 작업 서버", state.workServerUrl.ifBlank { "앱과 웹에서 같은 작업 기록 사용" }, { actions.onNavigate(AppDestination.WORK_SERVER) })
        }
        TossPanel {
            TossRow("내 기록", "이 기기에 저장된 대화와 이전 점검", { actions.onNavigate(AppDestination.DATA) })
            TossRow("가스 측정", "측정기 연결과 현장 경보", { actions.onNavigate(AppDestination.SENSOR) })
            TossRow("위젯과 알림", "앱 밖에서 측정 상태 보기", { actions.onNavigate(AppDestination.SYSTEM) })
        }
    }
}

@Composable
private fun WorkspaceOfflinePanel(state: FieldGuardUiState, actions: FieldGuardActions, modifier: Modifier = Modifier) {
    TossPanel(modifier) {
        Text("오프라인 준비", style = MaterialTheme.typography.titleLarge)
        Text("앱에 포함된 문서와 원본 PDF를 사용해요.", style = MaterialTheme.typography.bodySmall)
        TossStatusPill("문서 ${state.documents.size}종 · PDF 원본 포함")
        TossRow("문서 검색", if (state.documents.isEmpty()) "준비 중" else "사용 가능", { actions.onNavigate(AppDestination.LIBRARY) })
        TossSecondaryButton("문서 보관함 열기", { actions.onNavigate(AppDestination.LIBRARY) })
    }
}

@Composable
private fun WorkspaceModelPanel(state: FieldGuardUiState, actions: FieldGuardActions, modifier: Modifier = Modifier) {
    TossPanel(modifier) {
        Text("기기 내 Gemma", style = MaterialTheme.typography.titleLarge)
        Text("질문과 답변은 기기 안에서 처리해요.", style = MaterialTheme.typography.bodySmall)
        TossStatusPill(when (state.modelStatus.phase) { ModelPhase.READY -> "모델 파일 저장됨"; ModelPhase.DOWNLOADING -> "모델 다운로드 중"; ModelPhase.FAILED -> "모델 준비 실패"; ModelPhase.NOT_INSTALLED -> "모델 다운로드 필요" })
        TossRow("Gemma 4 E2B", "Android용 모델 · 기기 내 실행", { actions.onNavigate(AppDestination.MODEL) })
        TossPrimaryButton("모델과 다운로드 관리", { actions.onNavigate(AppDestination.MODEL) })
    }
}
