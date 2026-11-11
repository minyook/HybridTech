/* 소스 안내
 * 기기 자원 정보·모델 파일 위치·설치 상태 저장 계약. READY는 엔진 적재가 아닌 파일 저장 상태다.
 */
package com.minyook.sllm2.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File

/** 설치 파일 상태. READY를 native engine 적재 완료나 해당 backend의 실행 성공으로 해석하지 않는다. */
enum class ModelPhase { NOT_INSTALLED, DOWNLOADING, READY, FAILED }

data class ModelStatus(
    val phase: ModelPhase,
    val installedBytes: Long,
    val failureMessage: String? = null,
)

data class DeviceProfile(
    val totalRamGb: Int,
    val availableStorageGb: Int,
    val lowRamDevice: Boolean,
    val arm64: Boolean,
) {
    companion object {
        fun read(context: Context): DeviceProfile {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            val storage = StatFs(context.noBackupFilesDir.path)
            return DeviceProfile(
                totalRamGb = (info.totalMem / (1024L * 1024L * 1024L)).toInt(),
                availableStorageGb = (storage.availableBytes / (1024L * 1024L * 1024L)).toInt(),
                lowRamDevice = manager.isLowRamDevice,
                arm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" },
            )
        }
    }
}

/** Worker와 추론 실행기가 공유하는 파일 위치. 모델별 GGUF 파일과 미완성 .part 파일을 구분한다. */
object ModelFiles {
    fun modelDirectory(context: Context): File = File(context.noBackupFilesDir, "models").apply { mkdirs() }
    fun finalFile(context: Context): File =
        File(modelDirectory(context), LocalModel.fileName)
    fun partialFile(context: Context): File =
        File(modelDirectory(context), "${LocalModel.fileName}.part")
}

/** 파일 존재/크기와 저장된 진행 상태를 합쳐 설정 화면의 모델별 설치 상태를 반환한다. */
class ModelPreferences(private val context: Context) {
    private val preferences = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)

    fun status(): ModelStatus {
        val finalFile = ModelFiles.finalFile(context)
        val bytes = finalFile.takeIf { it.exists() }?.length() ?: 0L
        if (LocalModel.isInstalled(finalFile)) return ModelStatus(ModelPhase.READY, bytes)
        val savedPhase = runCatching {
            ModelPhase.valueOf(preferences.getString(key("phase"), ModelPhase.NOT_INSTALLED.name).orEmpty())
        }.getOrDefault(ModelPhase.NOT_INSTALLED)
        return ModelStatus(
            // A stale preference must never make a removed or corrupt model look runnable.
            phase = if (savedPhase == ModelPhase.READY) ModelPhase.NOT_INSTALLED else savedPhase,
            installedBytes = ModelFiles.partialFile(context).takeIf { it.exists() }?.length() ?: bytes,
            failureMessage = preferences.getString(key("failure"), null),
        )
    }

    fun markDownloading() = update(ModelPhase.DOWNLOADING, null)
    fun markReady() = update(ModelPhase.READY, null)
    fun markFailed(message: String?) = update(ModelPhase.FAILED, message?.take(180))

    private fun key(field: String) = "${LocalModel.id}_$field"

    private fun update(phase: ModelPhase, failure: String?) {
        preferences.edit()
            .putString(key("phase"), phase.name)
            .putString(key("failure"), failure)
            .commit()
    }
}
