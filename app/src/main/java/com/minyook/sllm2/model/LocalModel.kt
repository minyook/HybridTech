/* 소스 안내
 * 범용 생성 모델의 ID·파일·URL/revision·크기·문맥 한도 카탈로그. Worker와 Runtime이 공유한다.
 * 연결 모듈: com.minyook.sllm2.BuildConfig.
 */
package com.minyook.sllm2.model

import com.minyook.sllm2.BuildConfig

/**
 * 설정 화면·다운로드·추론이 공유하는 CPU/GPU용 모델 카탈로그.
 * ID를 저장 설정의 키로 쓰고 파일명·원격 revision·예상 크기·문맥 한도를 한 곳에서 정의한다.
 * 칩셋 전용 NPU 파일은 같은 범용 파일이 아니며 NpuModel에서 별도로 선택한다.
 * 이 목록에 존재한다는 사실은 기기별 생성 품질/속도 검증을 통과했다는 뜻이 아니다.
 */
enum class LocalModel(
    val id: String,
    val title: String,
    val fileName: String,
    val repository: String,
    val remoteFileName: String,
    val minimumBytes: Long,
    val downloadBytes: Long,
    val contextLimit: Int,
    val recommendedRamGb: Int,
    val requiresAccessToken: Boolean,
) {
    GEMMA4_E2B(
        "gemma4-e2b", "Gemma 4 E2B", "gemma-4-e2b-it.litertlm",
        "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm",
        BuildConfig.GEMMA4_E2B_MIN_BYTES, 2_590_000_000L, 32_768, 8, false,
    ),
    GEMMA3_270M(
        "gemma3-270m", "Gemma 3 270M", "gemma3-270m-it-q8.litertlm",
        "litert-community/gemma-3-270m-it", "gemma3-270m-it-q8.litertlm",
        304_005_120L, 304_005_120L, 4_096, 2, true,
    ),
    GEMMA3_1B(
        "gemma3-1b", "Gemma 3 1B", "gemma3-1b-it-int4.litertlm",
        "litert-community/Gemma3-1B-IT", "gemma3-1b-it-int4.litertlm",
        584_417_280L, 584_417_280L, 4_096, 4, true,
    );

    val modelPage: String get() = "https://huggingface.co/$repository"
    private val revision: String get() = when (this) {
        GEMMA4_E2B -> "main"
        GEMMA3_270M -> "9d2093270fb5aa49a986b49b5779d763dde7b630"
        GEMMA3_1B -> "aa49427ccd3bcacb1820d2bc3bb969eaffe632bf"
    }
    val downloadUrl: String get() = if (this == GEMMA4_E2B) BuildConfig.GEMMA4_E2B_URL
        else "$modelPage/resolve/$revision/$remoteFileName?download=true"
    fun acceptsBytes(bytes: Long): Boolean = if (this == GEMMA4_E2B) bytes >= minimumBytes else bytes == downloadBytes
    val sizeLabel: String get() = when (this) {
        GEMMA4_E2B -> "약 2.59 GB · 4비트"
        GEMMA3_270M -> "약 304 MB · 8비트"
        GEMMA3_1B -> "약 584 MB · 4비트"
    }

    companion object {
        fun fromId(id: String?): LocalModel = entries.find { it.id == id } ?: GEMMA4_E2B
    }
}
