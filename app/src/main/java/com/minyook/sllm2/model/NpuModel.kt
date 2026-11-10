/* 소스 안내
 * 모델·Qualcomm SoC에 맞는 전용 파일 크기/문맥/라이브러리를 선택한다. 범용 파일과 NPU 파일을 구분한다.
 */
package com.minyook.sllm2.model

/** Precompiled Qualcomm packages, paired with a portable CPU/GPU fallback. */
data class NpuModel(
    val model: LocalModel,
    val soc: String,
    val remoteFileName: String,
    val bytes: Long,
    val contextLimit: Int = 1280,
) {
    val id: String get() = "${model.id}-$soc"
    val fileName: String get() = remoteFileName
    val downloadUrl: String get() = model.downloadUrl.replace(model.remoteFileName, remoteFileName)
    val requiredLibraries: List<String> get() {
        val hexagon = when (soc) {
            "sm8550" -> 73
            "sm8650" -> 75
            "sm8750" -> 79
            else -> 81
        }
        return listOf("libLiteRtDispatch_Qualcomm.so", "libQnnHtp.so", "libQnnSystem.so",
            "libQnnHtpV${hexagon}Stub.so", "libQnnHtpV${hexagon}Skel.so")
    }

    companion object {
        internal fun isContextLimitError(message: String?): Boolean = Regex(
            "문맥|답변 길이|(?i:context.{0,40}(?:length|limit|exceed|overflow)|(?:tokens?|kv.?cache).{0,50}(?:limit|length|exceed|range|maximum)|max_num_tokens)",
        ).containsMatchIn(message.orEmpty())

        fun normalizeSoc(value: String): String? = Regex("(?i)\\bsm(?:8550|8650|8750|8850)\\b")
            .find(value)?.value?.lowercase()

        fun forDevice(model: LocalModel, socModel: String): NpuModel? {
            val soc = normalizeSoc(socModel) ?: return null
            val size = when (model) {
                LocalModel.GEMMA4_E2B -> return null
                LocalModel.GEMMA3_270M -> when (soc) {
                    "sm8550" -> 461_750_272L
                    "sm8650" -> 461_766_656L
                    "sm8750" -> 461_340_672L
                    else -> 463_011_840L
                }
                LocalModel.GEMMA3_1B -> when (soc) {
                    "sm8550" -> 690_143_232L
                    "sm8650" -> 690_094_080L
                    "sm8750" -> 689_291_264L
                    else -> 693_747_712L
                }
            }
            val file = if (model == LocalModel.GEMMA3_270M) "gemma3-270m-it-q8.qualcomm.$soc.litertlm"
                else "Gemma3-1B-IT_q4_ekv1280_$soc.litertlm"
            return NpuModel(model, soc, file, size)
        }
    }
}
