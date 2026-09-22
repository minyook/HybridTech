package com.minyook.sllm2.data

import java.util.Locale
import kotlin.math.sqrt

/**
 * A tiny, deterministic bootstrap embedder. It lets the bundled knowledge base
 * work immediately and stays below 2 KB of code/zero model bytes. A production
 * EmbeddingGemma adapter can replace this without changing ObjectBox's schema.
 */
object OfflineHashEmbedding {
    const val DIMENSIONS = 384
    const val DIMENSIONS_LONG = 384L

    fun embed(text: String): FloatArray {
        val vector = FloatArray(DIMENSIONS)
        val normalized = text.lowercase(Locale.KOREAN)
            .replace(Regex("[^0-9a-z가-힣]+"), " ")
            .trim()
        if (normalized.isEmpty()) return vector

        normalized.split(Regex("\\s+")).filter { it.length > 1 }.forEach { token ->
            add(vector, token, 1.5f)
            token.windowed(size = 2, step = 1, partialWindows = false)
                .forEach { bigram -> add(vector, bigram, 0.55f) }
        }
        normalize(vector)
        return vector
    }

    private fun add(vector: FloatArray, token: String, weight: Float) {
        val hash = token.hashCode()
        val index = (hash and Int.MAX_VALUE) % DIMENSIONS
        vector[index] += if ((hash ushr 31) == 0) weight else -weight
    }

    private fun normalize(vector: FloatArray) {
        val length = sqrt(vector.sumOf { value -> (value * value).toDouble() }).toFloat()
        if (length == 0f) return
        vector.indices.forEach { index -> vector[index] /= length }
    }
}
