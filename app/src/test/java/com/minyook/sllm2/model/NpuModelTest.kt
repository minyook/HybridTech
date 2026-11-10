/* 소스 안내
 * SoC 전용 파일·라이브러리·문맥 오류 분류을 검증하는 JVM 회귀 검사다.
 * 검사 예: socDetectionRequiresAnExplicitSupportedChipset, s24ArtifactsUseSeparateFilesAndTheMatchingChipset, anUnpublishedNpuPackageCannotBeAssumedFromThePortableFile
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.model

import org.junit.Assert.*
import org.junit.Test

class NpuModelTest {
    @Test fun socDetectionRequiresAnExplicitSupportedChipset() {
        assertEquals("sm8650", NpuModel.normalizeSoc("Qualcomm SM8650-AC"))
        assertEquals("sm8750", NpuModel.normalizeSoc("SM8750"))
        assertNull(NpuModel.normalizeSoc("SM86500"))
        assertNull(NpuModel.normalizeSoc("Exynos 2400"))
        assertNull(NpuModel.normalizeSoc(""))
    }

    @Test fun s24ArtifactsUseSeparateFilesAndTheMatchingChipset() {
        val small = NpuModel.forDevice(LocalModel.GEMMA3_270M, "SM8650")!!
        val medium = NpuModel.forDevice(LocalModel.GEMMA3_1B, "SM8650")!!
        assertEquals(461_766_656L, small.bytes)
        assertEquals("gemma3-270m-it-q8.qualcomm.sm8650.litertlm", small.fileName)
        assertEquals("Gemma3-1B-IT_q4_ekv1280_sm8650.litertlm", medium.fileName)
        assertNotEquals(small.model.fileName, small.fileName)
        assertTrue(small.downloadUrl.contains("/9d2093270fb5aa49a986b49b5779d763dde7b630/"))
        assertTrue(medium.downloadUrl.contains("/aa49427ccd3bcacb1820d2bc3bb969eaffe632bf/"))
        assertNotEquals(small.id, NpuModel.forDevice(small.model, "SM8750")!!.id)
        assertTrue(small.requiredLibraries.contains("libQnnHtpV75Skel.so"))
        assertFalse(small.requiredLibraries.contains("libQnnHtpV79Skel.so"))
    }

    @Test fun anUnpublishedNpuPackageCannotBeAssumedFromThePortableFile() {
        assertNull(NpuModel.forDevice(LocalModel.GEMMA4_E2B, "SM8650"))
        assertNull(NpuModel.forDevice(LocalModel.GEMMA4_E2B, "SM8750"))
        assertNull(NpuModel.forDevice(LocalModel.GEMMA3_270M, "Tensor G4"))
        assertNull(NpuModel.forDevice(LocalModel.GEMMA3_1B, "MT6989"))
    }

    @Test fun aLongQuestionDoesNotQuarantineTheNpuDriverForLaterShortQuestions() {
        assertTrue(NpuModel.isContextLimitError("KV-cache update out of range"))
        assertTrue(NpuModel.isContextLimitError("Context length exceeded"))
        assertTrue(NpuModel.isContextLimitError("요청한 답변 길이가 NPU 문맥을 초과해요."))
        assertFalse(NpuModel.isContextLimitError("NPU 실행 프로세스가 종료됐어요."))
        assertFalse(NpuModel.isContextLimitError("Failed to initialize QNN backend"))
    }
}
