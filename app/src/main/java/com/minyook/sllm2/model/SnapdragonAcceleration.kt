package com.minyook.sllm2.model

import android.content.Context
import android.os.Build
import java.io.File

internal enum class LlamaBackend(val displayName: String) {
    NPU("NPU · Hexagon"),
    GPU("GPU · Adreno OpenCL"),
    CPU("CPU"),
}

internal object SnapdragonAcceleration {
    // The upstream Android plugins target API 34 and armv8.7-a.
    fun supports(sdk: Int, arm64: Boolean, socModel: String): Boolean =
        sdk >= 34 && arm64 && supportedSoc.containsMatchIn(socModel)

    fun supportedOnDevice(): Boolean = Build.VERSION.SDK_INT >= 34 &&
        supports(Build.VERSION.SDK_INT, "arm64-v8a" in Build.SUPPORTED_ABIS, Build.SOC_MODEL)

    fun openClFallbackReason(model: String, incremental: String): String? =
        if (model == "SM-S928N" && incremental == "S928NKSS6DZH2") {
            "이 Galaxy S24 Ultra 펌웨어에서 Adreno OpenCL 실행 중 앱 종료가 확인되어 CPU로 전환했어요."
        } else null

    private val supportedSoc = Regex("(?i)\\bSM(?:8650|8750|8850)(?:\\b|[-_])")
    private const val DSP_ASSETS = "llama-dsp-b12248"

    @Synchronized
    fun prepareDspLibraries(context: Context): File {
        val folder = File(context.noBackupFilesDir, DSP_ASSETS)
        check(folder.isDirectory || folder.mkdirs()) { "Could not create the DSP library directory." }
        val names = context.assets.list(DSP_ASSETS).orEmpty()
        check(names.isNotEmpty()) { "Hexagon DSP libraries are missing from the APK." }
        for (name in names) {
            val library = File(folder, name)
            if (library.isFile) continue
            val partial = File.createTempFile("dsp-", ".part", folder)
            try {
                context.assets.open("$DSP_ASSETS/$name").use { input ->
                    partial.outputStream().use { input.copyTo(it) }
                }
                check(partial.renameTo(library)) { "Could not install the Hexagon DSP library." }
            } finally {
                partial.delete()
            }
        }
        return folder
    }
}
