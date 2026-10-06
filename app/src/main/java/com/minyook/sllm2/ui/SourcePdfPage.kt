package com.minyook.sllm2.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.minyook.sllm2.data.SourcePdfCatalog
import com.minyook.sllm2.data.SourcePdfRepository
import kotlinx.coroutines.CancellationException

private sealed interface PdfViewState {
    data object Loading : PdfViewState
    data class Ready(val bitmap: Bitmap) : PdfViewState
    data class Failed(val reason: String) : PdfViewState
}

@Composable
internal fun SourcePdfPage(source: SourceUi) {
    val specification = SourcePdfCatalog.find(source.documentId)
    if (specification == null) {
        Text("이 문서에 연결된 PDF 원본이 없습니다. 추출 텍스트 보기를 이용해 주세요.")
        return
    }

    val context = LocalContext.current.applicationContext
    val repository = remember(context) { SourcePdfRepository(context) }
    var zoomPercent by remember(source.id) { mutableIntStateOf(100) }
    var retry by remember(source.id) { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("고용노동부 공식 PDF · ${source.pageNumber}/${specification.pageCount}쪽", style = MaterialTheme.typography.bodySmall)
        Text("처음 열 때 원본을 내려받아 기기에 보관합니다. 이후에는 오프라인에서도 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { zoomPercent = (zoomPercent - 25).coerceAtLeast(75) }, enabled = zoomPercent > 75) {
                Text("축소")
            }
            OutlinedButton(onClick = { zoomPercent = (zoomPercent + 25).coerceAtMost(200) }, enabled = zoomPercent < 200) {
                Text("확대")
            }
            Text("$zoomPercent%", modifier = Modifier.padding(top = 13.dp), style = MaterialTheme.typography.bodyMedium)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val displayWidth = maxWidth * (zoomPercent / 100f)
            val renderWidthPx = with(density) { displayWidth.roundToPx() }
            var pageState by remember(source.id, renderWidthPx, retry) { mutableStateOf<PdfViewState>(PdfViewState.Loading) }
            LaunchedEffect(source.id, renderWidthPx, retry) {
                pageState = try {
                    PdfViewState.Ready(repository.renderPage(source.documentId, source.pageNumber, renderWidthPx))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    PdfViewState.Failed(error.message ?: "PDF를 불러오지 못했습니다.")
                }
            }
            when (val current = pageState) {
                PdfViewState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("PDF 원본을 여는 중이에요.", style = MaterialTheme.typography.bodyMedium)
                }
                is PdfViewState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PDF를 열지 못했어요: ${current.reason}", style = MaterialTheme.typography.bodyMedium)
                    Text("인터넷 연결을 확인하거나 위의 추출 텍스트 보기를 이용해 주세요.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { retry++ }) { Text("다시 시도") }
                }
                is PdfViewState.Ready -> Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Image(
                            bitmap = current.bitmap.asImageBitmap(),
                            contentDescription = "${source.documentTitle} ${source.pageNumber}쪽 PDF 원본",
                            modifier = Modifier.width(displayWidth)
                                .aspectRatio(current.bitmap.width.toFloat() / current.bitmap.height),
                        )
                    }
                }
            }
        }
    }
}
