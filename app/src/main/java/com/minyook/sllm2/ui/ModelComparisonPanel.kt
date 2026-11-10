/* 소스 안내
 * 모델 비교 진행·문항 원문·지표·검토/공유를 표시한다. 실제 생성은 ViewModel이 Runner로 위임한다.
 * 연결 모듈: com.minyook.sllm2.model.LocalModel, com.minyook.sllm2.model.FieldComparisonSuite.
 */
package com.minyook.sllm2.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minyook.sllm2.model.LocalModel
import com.minyook.sllm2.model.FieldComparisonSuite
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable internal fun WorkspaceModelComparison(state: FieldGuardUiState, actions: FieldGuardActions) {
    val report = state.comparison
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember(report?.startedAt) { mutableStateOf<Int?>(null) }
    var showQuestions by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf("") }
    val reportJson = remember(report) { report?.json()?.toString(2).orEmpty() }
    Text("모델 성능 비교", fontSize=16.sp, fontWeight=FontWeight.Bold)
    Text("현장 복합 상황 ${FieldComparisonSuite.cases.size}문항을 같은 근거로 상세 설명하게 해요. 공통 문맥 ${FieldComparisonSuite.CONTEXT_TOKENS} · 출력 최대 ${FieldComparisonSuite.OUTPUT_TOKENS}토큰이에요. 모든 요구 항목과 조건을 Markdown 표·절차·체크리스트로 작성하게 하며 전체 비교는 수십 분 이상 걸릴 수 있어요.",
        fontSize=12.sp, lineHeight=20.sp, color=WorkspaceDesign.Muted)
    TextButton(onClick={showQuestions=!showQuestions}) { Text(if(showQuestions) "현장 질문 접기" else "현장 복합 질문 ${FieldComparisonSuite.cases.size}개 보기") }
    if (showQuestions) FieldComparisonSuite.cases.forEachIndexed { index, scenario ->
        Text("${index + 1}. ${scenario.title}",fontWeight=FontWeight.Bold,fontSize=13.sp)
        Text(scenario.question,fontSize=12.sp,lineHeight=20.sp)
    }
    WorkspaceButton("다운로드한 모델 상세 비교 시작", actions.onStartModelComparison, Modifier.fillMaxWidth(),
        enabled=!state.isAnswering && !state.isComparing)
    if (state.isComparing) {
        WorkspaceButton(if(state.comparisonStopRequested) "현재 답변 완료 후 중지 예정" else "현재 답변 완료 후 중지",
            actions.onStopModelComparison, Modifier.fillMaxWidth(), primary=false, enabled=!state.comparisonStopRequested)
    }
    if (state.comparisonProgress.isNotBlank()) Text(state.comparisonProgress, fontSize=12.sp, color=WorkspaceDesign.Muted)
    if (state.isComparing && state.comparisonPreview.isNotBlank()) {
        Text("현재 생성 중인 답변",fontSize=13.sp,fontWeight=FontWeight.Bold)
        Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            MarkdownText(state.comparisonPreview)
        }
    }
    if (report == null) return
    Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(report.startedAt)) +
        " · ${report.device} · 요청 ${report.requestedBackend}", fontSize=11.sp, color=WorkspaceDesign.Muted)
    Text("이 결과의 조건 · ${report.questionCount}문항 · 문맥 ${report.contextTokens} · 출력 최대 ${report.outputTokens}토큰",
        fontSize=12.sp,color=WorkspaceDesign.Muted)
    if (report.suiteId != FieldComparisonSuite.ID)
        WorkspaceNotice("이전에 저장한 간단 질문 비교 결과예요. 상세 비교를 새로 시작하면 현장 복합 질문으로 측정해요.")
    if (report.phase == "interrupted") WorkspaceNotice("이전 비교가 중단됐어요. 완료된 질문의 결과를 확인할 수 있어요.", warning=true)
    Text("모델마다 답변 길이가 달라지며 출력 한도에서 멈출 수 있어요. 답변과 기준 문서를 확인하고 정확도를 직접 평가하세요.",
        fontSize=12.sp, lineHeight=20.sp, color=WorkspaceDesign.Muted)
    for ((modelId, rows) in report.rows.groupBy { it.modelId }) {
        val valid = rows.filter { it.error.isBlank() }
        val first = valid.mapNotNull { it.firstSentenceMs }
        val reviewed = rows.filter { it.correct != null }
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(LocalModel.fromId(modelId).title, fontSize=14.sp, fontWeight=FontWeight.Bold)
            Text("실제 실행 ${rows.map { it.backend }.distinct().joinToString()} · 응답 ${valid.size}/${report.questionCount}",
                fontSize=12.sp, color=WorkspaceDesign.Muted)
            Text("첫 완결 문장 평균 ${if(first.isEmpty()) "미측정" else seconds(first.average())} · 전체 평균 ${if(valid.isEmpty()) "미측정" else seconds(valid.map{it.totalMs}.average())}",
                fontSize=12.sp)
            val warmed = valid.filter { it.reusedEngine }
            if (warmed.isNotEmpty()) Text("모델 재사용 시 전체 평균 ${seconds(warmed.map{it.totalMs}.average())}", fontSize=12.sp, color=WorkspaceDesign.Muted)
            if (valid.isNotEmpty()) Text("평균 출력 ${valid.map { it.rawAnswer.length }.average().toInt()}자",fontSize=12.sp,color=WorkspaceDesign.Muted)
            Text("직접 평가 ${reviewed.count { it.correct == true }}/${reviewed.size} 통과 · 미평가 ${rows.size - reviewed.size}",
                fontSize=12.sp, color=WorkspaceDesign.Muted)
        }
        HorizontalDivider(color=WorkspaceDesign.Border)
    }
    for ((index, row) in report.rows.withIndex()) {
        TextButton(onClick={expanded=if(expanded==index)null else index}) {
            Text("${LocalModel.fromId(row.modelId).title} · ${row.caseTitle.ifBlank { row.question }} · ${seconds(row.totalMs.toDouble())}")
        }
        if (expanded != index) continue
        Text(row.question,fontSize=13.sp,lineHeight=21.sp)
        Text("모델 준비 ${seconds(row.initializationMs.toDouble())} · 출력 ${row.rawAnswer.length}자", fontSize=11.sp, color=WorkspaceDesign.Muted)
        Text("요청한 출력 한도 ${row.requestedOutputTokens.takeIf { it > 0 } ?: report.outputTokens}토큰",fontSize=11.sp,color=WorkspaceDesign.Muted)
        if (row.outputLimitReached == true) WorkspaceNotice("최대 출력 한도에 도달했어요. 마지막 절차와 조건의 완결 여부를 확인해 주세요.",warning=true)
        if (row.outputLimitReached == null) Text("SDK의 출력 토큰·종료 원인은 미측정이에요.",fontSize=11.sp,color=WorkspaceDesign.Muted)
        if (row.effectiveContextTokens > 0) Text("실제 실행 ${row.backend} · 문맥 ${row.effectiveContextTokens}토큰",fontSize=11.sp,color=WorkspaceDesign.Muted)
        if (row.fallbackReason.isNotBlank()) WorkspaceNotice(row.fallbackReason)
        if (row.error.isNotBlank()) WorkspaceNotice(row.error, warning=true)
        if (row.missingEvidenceQueries.isNotEmpty())
            WorkspaceNotice("공통 근거 준비에서 확인이 필요한 항목 · ${row.missingEvidenceQueries.joinToString(" / ")}",warning=true)
        if (row.reviewChecklist.isNotEmpty()) {
            Text("품질 확인 항목",fontSize=13.sp,fontWeight=FontWeight.Bold)
            MarkdownText(row.reviewChecklist.joinToString("\n") { "- $it" })
        }
        Text("생성한 답변", fontWeight=FontWeight.Bold, fontSize=13.sp)
        MarkdownText(row.answer.ifBlank { "완료된 답변이 없어요." })
        Text("비교할 문서 근거", fontWeight=FontWeight.Bold, fontSize=13.sp)
        MarkdownText(row.reference)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            WorkspaceButton(if(row.correct==true) "✓ 정확함" else "정확함",
                { actions.onReviewModelComparison(index, true) }, Modifier.weight(1f), enabled=!state.isComparing && row.error.isBlank())
            WorkspaceButton(if(row.correct==false) "✓ 수정 필요" else "수정 필요",
                { actions.onReviewModelComparison(index, false) }, Modifier.weight(1f), primary=false, enabled=!state.isComparing)
        }
    }
    WorkspaceButton("비교 결과 JSON 파일 공유", { scope.launch {
        exportError = runCatching { shareComparison(context, report.startedAt, reportJson) }.exceptionOrNull()
            ?.let { "결과 파일을 공유하지 못했어요. 저장 공간과 공유할 앱을 확인해 주세요." }.orEmpty()
    } }, Modifier.fillMaxWidth(),primary=false,enabled=!state.isComparing)
    WorkspaceButton("비교 결과 JSON 복사", { scope.launch {
        exportError = runCatching {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("모델 비교 결과", reportJson)))
        }.exceptionOrNull()?.let { "복사하지 못했어요. JSON 파일 공유로 보관해 주세요." }.orEmpty()
    } }, Modifier.fillMaxWidth(), primary=false, enabled=!state.isComparing && reportJson.length <= 180_000)
    if (reportJson.length > 180_000) Text("긴 비교 결과는 JSON 파일 공유로 전체를 보관해 주세요.",fontSize=12.sp,color=WorkspaceDesign.Muted)
    if (exportError.isNotBlank()) WorkspaceNotice(exportError,warning=true)
}

private fun seconds(milliseconds: Double) = String.format(Locale.KOREA, "%.2f초", milliseconds / 1000.0)

private suspend fun shareComparison(context: Context, startedAt: Long, json: String) {
    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "model-comparisons").apply { mkdirs() }
        File(directory, "model-comparison-$startedAt-${System.currentTimeMillis()}.json").apply { writeText(json) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.comparison-files", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("모델 비교 결과", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "모델 비교 결과 저장·공유"))
}
