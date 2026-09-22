package com.minyook.sllm2.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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
import com.minyook.sllm2.BuildConfig
import com.minyook.sllm2.MainActivity
import com.minyook.sllm2.R
import com.minyook.sllm2.gas.GasSimulationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object ModelDownloadScheduler {
    const val UNIQUE_WORK_NAME = "download_gemma_4_e2b"
    const val TAG = "model_download"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

class ModelDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val state = ModelPreferences(applicationContext)
        state.markDownloading()
        setForeground(foregroundInfo("Gemma 4 E2B 다운로드 준비 중"))

        try {
            val target = ModelFiles.finalFile(applicationContext)
            val part = ModelFiles.partialFile(applicationContext)
            val existingBytes = part.takeIf { it.exists() }?.length() ?: 0L
            val connection = (URL(BuildConfig.GEMMA4_E2B_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Accept-Encoding", "identity")
                if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
            }

            try {
                val code = connection.responseCode
                val append = code == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0
                if (code !in 200..299) throw IOException("모델 서버 응답: HTTP $code")
                val initialBytes = if (append) existingBytes else 0L
                val expectedBytes = connection.contentLengthLong.takeIf { it > 0 }?.plus(initialBytes)
                if (expectedBytes != null && expectedBytes < BuildConfig.GEMMA4_E2B_MIN_BYTES) {
                    throw IOException("예상치보다 작은 모델 파일입니다.")
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
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (downloaded - lastProgress >= 1_048_576) {
                                setProgress(workDataOf("downloadedBytes" to downloaded, "totalBytes" to (expectedBytes ?: -1L)))
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
                if (actualBytes < BuildConfig.GEMMA4_E2B_MIN_BYTES) {
                    throw IOException("모델 파일 크기 검증에 실패했습니다.")
                }
                if (target.exists() && !target.delete()) throw IOException("기존 모델 파일을 교체할 수 없습니다.")
                if (!part.renameTo(target)) throw IOException("다운로드 파일을 완성 상태로 바꾸지 못했습니다.")

                state.markReady()
                // The download itself is a foreground task, so the labelled demo card can
                // appear immediately after preparation even if the activity was closed.
                runCatching { GasSimulationService.start(applicationContext) }
                setProgress(workDataOf("downloadedBytes" to actualBytes, "totalBytes" to actualBytes))
                Result.success()
            } finally {
                connection.disconnect()
            }
        } catch (error: IOException) {
            state.markFailed(error.message)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (error: Exception) {
            state.markFailed(error.message)
            Result.failure()
        }
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
