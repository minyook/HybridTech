package com.minyook.sllm2.model

/** Keep a ready GPU engine only when a separate query encoder has ample RAM headroom. */
object EngineReusePolicy {
    fun retainForSearch(backend:String,totalRamGb:Int,availableBytes:Long,lowMemory:Boolean) =
        backend == "GPU" && totalRamGb >= 8 && !lowMemory && availableBytes >= 2L * 1024 * 1024 * 1024
}
