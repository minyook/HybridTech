/* 소스 안내
 * 모델별 고유 WorkManager 작업·HTTP Range 이어받기·크기 검증·완성 파일 확정과 진행 알림을 처리한다.
 * 연결 모듈: com.minyook.sllm2.MainActivity, com.minyook.sllm2.R.
 */
package com.minyook.sllm2.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import com.minyook.sllm2.MainActivity
import com.minyook.sllm2.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** ViewModel의 다운로드 요청을 모델/파일별 고유 WorkManager 작업으로 예약한다. 중복 요청은 KEEP으로 합친다. */
object ModelDownloadScheduler {
    const val TAG = "model_download"

    private const val UNIQUE_WORK_NAME = "download_exaone_4_q4"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

/**
 * 대형 생성 모델의 HTTP 다운로드와 디스크 확정을 담당하는 백그라운드 Worker.
 * LocalModel의 URL을 읽고 .part 파일에 이어받은 뒤 응답 길이·모델별 크기를 검사한다.
 * 완성 파일로 rename한 후 ModelPreferences에 READY를 기록하며 ViewModel은 WorkInfo와 이 상태를 합쳐 표시한다.
 * READY는 파일이 저장됐다는 뜻이다. 추론 엔진은 이후 LocalModelRuntime.generate에서 필요할 때 적재한다.
 * 인증 토큰은 WorkManager 입력 데이터에 넣지 않고 ModelDownloadAccess에서 읽는다.
 */
class ModelDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    companion object {
        internal const val AUTH_ERROR = "Hugging Face 인증이 필요해요. 읽기 토큰을 입력해 주세요."
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val state = ModelPreferences(applicationContext)
        state.markDownloading()

        try {
            setForeground(foregroundInfo("${LocalModel.title} 다운로드 준비 중"))
            val target = ModelFiles.finalFile(applicationContext)
            if (LocalModel.isInstalled(target)) {
                state.markReady()
                return@withContext Result.success()
            }
            val part = ModelFiles.partialFile(applicationContext)
            if (part.length() >= LocalModel.downloadBytes) {
                if (LocalModel.acceptsBytes(part.length()) && matchesChecksum(part)) {
                    install(part, target)
                    state.markReady()
                    return@withContext Result.success()
                }
                if (!part.delete()) throw IOException("손상된 임시 파일을 지우지 못했어요.")
            }
            val existingBytes = part.length()
            val connection = openConnection(existingBytes)

            try {
                val code = connection.responseCode
                if (code == 401 || code == 403) {
                    state.markFailed(AUTH_ERROR)
                    return@withContext Result.failure()
                }
                if (code == 416 && part.exists()) {
                    part.delete()
                    throw IOException("이전 다운로드 범위를 갱신합니다. 다시 시도해 주세요.")
                }
                val append = code == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0
                if (code !in 200..299) throw IOException("모델 서버 응답: HTTP $code")
                if (code == HttpURLConnection.HTTP_PARTIAL && !connection.getHeaderField("Content-Range").orEmpty().startsWith("bytes $existingBytes-")) {
                    throw IOException("다운로드 이어받기 범위가 일치하지 않습니다.")
                }
                val initialBytes = if (append) existingBytes else 0L
                val expectedBytes = connection.contentLengthLong.takeIf { it > 0 }?.plus(initialBytes)
                if (expectedBytes != null && !LocalModel.acceptsBytes(expectedBytes)) {
                    throw IOException("모델 서버의 파일 크기가 공식 GGUF와 일치하지 않아요.")
                }
                if (StatFs(part.parentFile!!.path).availableBytes < LocalModel.downloadBytes - initialBytes) {
                    state.markFailed("모델을 저장할 공간이 부족해요.")
                    return@withContext Result.failure()
                }

                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(part, append).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = initialBytes
                        var lastProgress = downloaded
                        while (true) {
                            if (isStopped) return@withContext Result.retry()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (downloaded + count > LocalModel.downloadBytes) throw IOException("모델 파일 크기를 초과했어요.")
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (downloaded - lastProgress >= 1_048_576) {
                                setProgress(workDataOf("downloadedBytes" to downloaded, "totalBytes" to LocalModel.downloadBytes))
                                lastProgress = downloaded
                            }
                        }
                        output.flush()
                    }
                }

                val actualBytes = part.length()
                if (expectedBytes != null && actualBytes != expectedBytes) {
                    throw IOException("다운로드가 완전하지 않습니다. 재시도합니다.")
                }
                if (!LocalModel.acceptsBytes(actualBytes)) {
                    throw IOException("다운로드가 완전하지 않아요. 재시도합니다.")
                }
                if (!matchesChecksum(part)) {
                    if (!part.delete()) throw IOException("손상된 임시 파일을 지우지 못했어요.")
                    throw IOException("공식 GGUF의 SHA-256과 일치하지 않아요. 다시 다운로드합니다.")
                }
                install(part, target)
                state.markReady()
                setProgress(workDataOf("downloadedBytes" to actualBytes, "totalBytes" to actualBytes))
                Result.success()
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            state.markFailed(error.message)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (error: Exception) {
            state.markFailed("모델을 저장하지 못했어요. 저장 공간과 접근 권한을 확인해 주세요.")
            Result.failure()
        }
    }

    private suspend fun matchesChecksum(file: java.io.File): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == LocalModel.sha256
    }

    private fun install(part: java.io.File, target: java.io.File) {
        if (!part.renameTo(target)) throw IOException("다운로드 파일을 완성 상태로 바꾸지 못했어요.")
    }

    /** HTTPS 리다이렉트와 Range 응답을 제어한다. 토큰 전달 대상은 아래 호스트 조건으로 제한한다. */
    private fun openConnection(existingBytes: Long): HttpURLConnection {
        var address = URL(LocalModel.downloadUrl)
        val token = ModelDownloadAccess(applicationContext).token()
        repeat(6) {
            check(address.protocol == "https")
            val connection = (address.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept-Encoding", "identity")
                if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
                if (address.host == "huggingface.co" && token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            val code = try { connection.responseCode } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            if (code !in listOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            address = URL(address, location ?: throw IOException("모델 서버의 이동 주소가 없습니다."))
        }
        throw IOException("모델 다운로드 주소를 확인하지 못했습니다.")
    }

    private fun foregroundInfo(message: String): ForegroundInfo {
        val openAppIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ModelNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_safety)
            .setContentTitle("오프라인 모델 다운로드")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ModelNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ModelNotifications.NOTIFICATION_ID, notification)
        }
    }
}

object ModelNotifications {
    const val CHANNEL_ID = "model_download"
    const val NOTIFICATION_ID = 4102

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "오프라인 모델 다운로드", NotificationManager.IMPORTANCE_LOW),
        )
    }
}
