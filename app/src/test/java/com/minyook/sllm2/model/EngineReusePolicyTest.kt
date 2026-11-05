package com.minyook.sllm2.model

import org.junit.Assert.*
import org.junit.Test

class EngineReusePolicyTest {
    @Test fun retentionRequiresGpuAndRamHeadroom() {
        val gb=1024L*1024*1024
        assertTrue(EngineReusePolicy.retainForSearch("GPU",10,3*gb,false))
        assertFalse(EngineReusePolicy.retainForSearch("CPU",10,3*gb,false))
        assertFalse(EngineReusePolicy.retainForSearch("GPU",6,3*gb,false))
        assertFalse(EngineReusePolicy.retainForSearch("GPU",10,gb,false))
        assertFalse(EngineReusePolicy.retainForSearch("GPU",10,3*gb,true))
    }
}
