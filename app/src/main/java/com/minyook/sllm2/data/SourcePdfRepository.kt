/* 소스 안내
 * 검증한 번들 PDF의 요청 쪽만 IO 스레드에서 렌더링한다. 결과 Bitmap은 SourcePdfPage에 전달한다.
 */
package com.minyook.sllm2.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * SourcePdfPage가 요청한 원본 PDF 한 쪽을 IO 스레드에서 Bitmap으로 렌더링한다.
 * SourcePdfCatalog로 문서/쪽을 확인하고 번들 사본의 해시·전체 쪽수를 검증한 뒤 PdfRenderer를 연다.
 * descriptor/renderer/page는 요청마다 닫고 렌더 폭을 제한한다. 반환 Bitmap의 화면 수명은 SourcePdfPage가 관리한다.
 * 온라인 PDF를 매번 다운로드하거나 추출한 텍스트를 원본 이미지처럼 표시하지 않는다.
 */
class SourcePdfRepository(private val context: Context) {
    suspend fun renderPage(documentId: String, pageNumber: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        val source = SourcePdfCatalog.find(documentId) ?: throw IOException("연결된 PDF 원본이 없습니다.")
        val pageIndex = source.pageIndex(pageNumber) ?: throw IOException("PDF에 해당 쪽이 없습니다.")
        val file = ensureBundled(source)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount != source.pageCount) throw IOException("PDF 쪽수가 저장된 근거와 다릅니다.")
                renderer.openPage(pageIndex).use { page ->
                    val width = widthPx.coerceIn(480, MAX_RENDER_WIDTH_PX)
                    val height = (width.toDouble() * page.height / page.width).roundToInt()
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }

    private fun ensureBundled(source: SourcePdf): File = synchronized(copyLock) {
        val directory = File(context.noBackupFilesDir, "source-pdfs").apply { mkdirs() }
        val file = File(directory, source.fileName)
        if (file.isFile && hashMatches(file, source.sha256)) return@synchronized file
        if (file.exists()) file.delete()

        val temporary = File(directory, "${source.fileName}.copying")
        try {
            context.assets.open("source-pdfs/${source.fileName}").use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_PDF_BYTES) throw IOException("PDF 파일이 예상보다 큽니다.")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (!hashMatches(temporary, source.sha256)) throw IOException("포함된 PDF가 검색에 사용한 판본과 다릅니다.")
            if (!temporary.renameTo(file)) throw IOException("PDF를 기기에 저장하지 못했습니다.")
            file
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun hashMatches(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') } == expected
    }

    private companion object {
        val copyLock = Any()
        const val MAX_PDF_BYTES = 12L * 1024 * 1024
        const val MAX_RENDER_WIDTH_PX = 1_800
    }
}
