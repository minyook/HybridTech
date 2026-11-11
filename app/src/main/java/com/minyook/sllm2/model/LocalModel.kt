package com.minyook.sllm2.model

import java.io.File

/** Official EXAONE 4.0 1.2B GGUF used by the downloader and inference runtime. */
object LocalModel {
    const val id = "exaone-4.0-1.2b-q4-k-m"
    const val title = "EXAONE 4.0 1.2B"
    const val fileName = "EXAONE-4.0-1.2B-Q4_K_M.gguf"
    const val modelPage = "https://huggingface.co/LGAI-EXAONE/EXAONE-4.0-1.2B-GGUF"
    private const val revision = "b6a337b72841650a8ac7b09f799cd06055d90d24"
    const val downloadUrl = "$modelPage/resolve/$revision/$fileName?download=true"
    const val downloadBytes = 812_437_792L
    const val sha256 = "7b5e753540183ae4d56e6febd9b48cdd944de53386e6faa8f51c8f98cb2b47df"
    const val contextLimit = 32_768
    const val sizeLabel = "약 812 MB · Q4_K_M"

    fun acceptsBytes(bytes: Long): Boolean = bytes == downloadBytes

    fun isInstalled(file: File): Boolean = file.isFile && acceptsBytes(file.length()) && runCatching {
        file.inputStream().use { input ->
            "GGUF".all { input.read() == it.code }
        }
    }.getOrDefault(false)
}
