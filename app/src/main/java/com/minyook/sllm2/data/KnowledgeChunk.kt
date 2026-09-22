package com.minyook.sllm2.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.VectorDistanceType

/**
 * One source-page chunk. Keeping the PDF page boundary makes every answer
 * traceable to the exact page a field worker can inspect offline.
 */
@Entity
data class KnowledgeChunk(
    @Id var id: Long = 0,
    @Index var documentId: String = "",
    var documentTitle: String = "",
    var sourceKind: String = "",
    var pageNumber: Int = 0,
    var chunkIndex: Int = 0,
    var heading: String = "",
    var body: String = "",
    @Index var searchableText: String = "",
    @HnswIndex(
        dimensions = OfflineHashEmbedding.DIMENSIONS_LONG,
        distanceType = VectorDistanceType.COSINE,
        neighborsPerNode = 16L,
        indexingSearchCount = 100L,
        vectorCacheHintSizeKB = 16_384L,
    )
    var embedding: FloatArray? = null,
)
