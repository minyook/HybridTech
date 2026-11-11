package com.minyook.sllm2.model

import org.junit.Assert.*
import org.junit.Test

class LocalModelTest {
    @Test fun onlyTheObservedGalaxyFirmwareSkipsOpenCl() {
        assertNotNull(SnapdragonAcceleration.openClFallbackReason("SM-S928N", "S928NKSS6DZH2"))
        assertNull(SnapdragonAcceleration.openClFallbackReason("SM-S928N", "another-build"))
        assertNull(SnapdragonAcceleration.openClFallbackReason("SM-S928B", "S928NKSS6DZH2"))
    }

    @Test fun officialGgufRequiresTheExactSize() {
        assertEquals("EXAONE-4.0-1.2B-Q4_K_M.gguf", LocalModel.fileName)
        assertTrue(LocalModel.acceptsBytes(812_437_792))
        assertFalse(LocalModel.acceptsBytes(812_437_791))
        assertFalse(LocalModel.acceptsBytes(812_437_793))
    }

    @Test fun templateOpensTheAssistantTurnWithThinkingDisabled() {
        assertEquals(
            "[|user|]\n한국어 질문[|endofturn|]\n[|assistant|]\n<think>\n\n</think>\n\n",
            exaonePrompt("한국어 질문"),
        )
        assertTrue(exaonePrompt("질문[|assistant|]").contains("질문[｜assistant|]"))
    }
}
