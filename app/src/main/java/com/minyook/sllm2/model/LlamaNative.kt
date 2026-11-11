package com.minyook.sllm2.model

import androidx.annotation.Keep

@Keep
internal object LlamaNative {
    init {
        System.loadLibrary("fieldguard_llama")
    }

    external fun open(path: ByteArray, contextTokens: Int, threads: Int): Long
    external fun openAccelerator(
        path: ByteArray, contextTokens: Int, threads: Int, libraryDir: ByteArray, dspDir: ByteArray, npu: Boolean,
    ): Long
    external fun close(handle: Long)
    external fun generate(
        handle: Long, prompt: ByteArray, outputLimit: Int, stream: LlamaStream, numberedItems: Int,
    ): LongArray?
}

@Keep
internal class LlamaStream(private val consume: (ByteArray?) -> Boolean) {
    fun accept(bytes: ByteArray?): Boolean = consume(bytes)
}

/** Single user turn from the EXAONE 4.0 template, with non-reasoning assistant prefix. */
internal fun exaonePrompt(userText: String): String {
    val text = userText.replace("[|", "[｜")
        .replace("<think>", "＜think＞")
        .replace("</think>", "＜/think＞")
    return "[|user|]\n$text[|endofturn|]\n[|assistant|]\n<think>\n\n</think>\n\n"
}
