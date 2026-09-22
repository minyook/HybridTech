package com.minyook.sllm2.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.minyook.sllm2.BuildConfig
import java.io.File

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
    val recommendedForGemma4E2b: Boolean
        get() = arm64 && totalRamGb >= 8 && !lowRamDevice

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

object ModelFiles {
    const val MODEL_FILE_NAME = "gemma-4-e2b-it.litertlm"

    fun modelDirectory(context: Context): File = File(context.noBackupFilesDir, "models").apply { mkdirs() }
    fun finalFile(context: Context): File = File(modelDirectory(context), MODEL_FILE_NAME)
    fun partialFile(context: Context): File = File(modelDirectory(context), "$MODEL_FILE_NAME.part")
}

class ModelPreferences(private val context: Context) {
    private val preferences = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)

    fun status(): ModelStatus {
        val finalFile = ModelFiles.finalFile(context)
        val bytes = finalFile.takeIf { it.exists() }?.length() ?: 0L
        if (bytes >= BuildConfig.GEMMA4_E2B_MIN_BYTES) return ModelStatus(ModelPhase.READY, bytes)
        val savedPhase = runCatching {
            ModelPhase.valueOf(preferences.getString("phase", ModelPhase.NOT_INSTALLED.name).orEmpty())
        }.getOrDefault(ModelPhase.NOT_INSTALLED)
        return ModelStatus(
            // A stale preference must never make a removed or corrupt model look runnable.
            phase = if (savedPhase == ModelPhase.READY) ModelPhase.NOT_INSTALLED else savedPhase,
            installedBytes = ModelFiles.partialFile(context).takeIf { it.exists() }?.length() ?: bytes,
            failureMessage = preferences.getString("failure", null),
        )
    }

    fun markDownloading() = update(ModelPhase.DOWNLOADING, null)
    fun markReady() = update(ModelPhase.READY, null)
    fun markFailed(message: String?) = update(ModelPhase.FAILED, message?.take(180))

    private fun update(phase: ModelPhase, failure: String?) {
        preferences.edit()
            .putString("phase", phase.name)
            .putString("failure", failure)
            .commit()
    }
}
