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
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** ViewModel의 다운로드 요청을 모델/파일별 고유 WorkManager 작업으로 예약한다. 중복 요청은 KEEP으로 합친다. */
object ModelDownloadScheduler {
    const val UNIQUE_WORK_NAME = "download_gemma_4_e2b"
    const val TAG = "model_download"

    fun uniqueName(model: LocalModel, npu: NpuModel? = null) = if (npu != null) "download_${npu.id}"
        else if (model == LocalModel.GEMMA4_E2B) UNIQUE_WORK_NAME else "download_${model.id}"
    fun modelTag(model: LocalModel) = "model:${model.id}"
    fun artifactTag(npu: NpuModel?) = "artifact:${npu?.id ?: "portable"}"

    fun enqueue(context: Context, model: LocalModel = InferenceSettingsPreferences(context).load().model, npu: NpuModel? = null) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(modelTag(model))
            .addTag(artifactTag(npu))
            .setInputData(workDataOf("modelId" to model.id, "npuSoc" to npu?.soc))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(model, npu), ExistingWorkPolicy.KEEP, request)
    }
}

/**
 * 대형 생성 모델의 HTTP 다운로드와 디스크 확정을 담당하는 백그라운드 Worker.
 * LocalModel/NpuModel의 URL을 읽고 .part 파일에 이어받은 뒤 응답 길이·모델별 크기를 검사한다.
 * 완성 파일로 rename한 후 ModelPreferences에 READY를 기록하며 ViewModel은 WorkInfo와 이 상태를 합쳐 표시한다.
 * READY는 파일이 저장됐다는 뜻이다. 추론 엔진은 이후 LocalModelRuntime.generate에서 필요할 때 적재한다.
 * 인증 토큰은 WorkManager 입력 데이터에 넣지 않고 ModelDownloadAccess에서 읽는다.
 */
class ModelDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    private val model = LocalModel.fromId(inputData.getString("modelId"))
    private val npu = inputData.getString("npuSoc")?.let { NpuModel.forDevice(model, it) }
    private fun acceptsBytes(bytes: Long) = npu?.let { bytes == it.bytes } ?: model.acceptsBytes(bytes)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val state = ModelPreferences(applicationContext)
        if (inputData.getString("npuSoc") != null && npu == null) return@withContext Result.failure()
        state.markDownloading(model, npu)

        try {
            setForeground(foregroundInfo("${model.title} ${npu?.soc.orEmpty()} 다운로드 준비 중"))
            val target = ModelFiles.finalFile(applicationContext, model, npu)
            if (acceptsBytes(target.length())) {
                state.markReady(model, npu)
                return@withContext Result.success()
            }
            val part = ModelFiles.partialFile(applicationContext, model, npu)
            val existingBytes = part.takeIf { it.exists() }?.length() ?: 0L
            val connection = openConnection(existingBytes)

            try {
                val code = connection.responseCode
                if (code == 401 || code == 403) {
                    state.markFailed("Hugging Face 모델 페이지에서 이용 약관에 동의한 계정의 읽기 토큰을 입력해 주세요.", model, npu)
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
                if (expectedBytes != null && expectedBytes < (npu?.bytes ?: model.minimumBytes)) {
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
                if (!acceptsBytes(actualBytes)) {
                    throw IOException("모델 파일 크기 검증에 실패했습니다.")
                }
                if (target.exists() && !target.delete()) throw IOException("기존 모델 파일을 교체할 수 없습니다.")
                if (!part.renameTo(target)) throw IOException("다운로드 파일을 완성 상태로 바꾸지 못했습니다.")

                state.markReady(model, npu)
                setProgress(workDataOf("downloadedBytes" to actualBytes, "totalBytes" to actualBytes))
                Result.success()
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            state.markFailed(error.message, model, npu)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (error: Exception) {
            state.markFailed("모델을 저장하지 못했어요. 저장 공간과 접근 권한을 확인해 주세요.", model, npu)
            Result.failure()
        }
    }

    /** HTTPS 리다이렉트와 Range 응답을 제어한다. 토큰 전달 대상은 아래 호스트 조건으로 제한한다. */
    private fun openConnection(existingBytes: Long): HttpURLConnection {
        var address = URL(npu?.downloadUrl ?: model.downloadUrl)
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
            ForegroundInfo(ModelNotifications.NOTIFICATION_ID + model.ordinal + if (npu != null) 10 else 0, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ModelNotifications.NOTIFICATION_ID + model.ordinal + if (npu != null) 10 else 0, notification)
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
