/* 소스 안내
 * 기존 엔티티 적재용 결정적 384차원 해시 벡터. 현재 질문의 신경망 의미 검색은 SemanticEncoder의 별도 768차원 경로다.
 */
package com.minyook.sllm2.data

import java.util.Locale
import kotlin.math.sqrt

/**
 * KnowledgeSeeder가 기존 KnowledgeChunk.embedding 필드에 기록하는 결정적 384차원 해시 벡터.
 * 현재 앱의 의미 검색 모델은 이 클래스가 아니라 SemanticEncoder + semantic-index-v1.json이다.
 * 해시 충돌이나 이 벡터의 유사도만으로 문서 출처를 결정하지 않는다.
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
