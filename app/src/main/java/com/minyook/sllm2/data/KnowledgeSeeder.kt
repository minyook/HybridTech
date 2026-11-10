/* 소스 안내
 * 번들 JSON 버전이 바뀔 때 문서 엔티티를 트랜잭션으로 적재한다. 채팅 저장소와 별개이며 앱 초기 준비에서 호출된다.
 */
package com.minyook.sllm2.data

import android.content.Context
import org.json.JSONObject

/**
 * ViewModel 초기화에서 번들 seed_chunks_v1.json을 ObjectBox KnowledgeChunk로 적재한다.
 * 저장된 seed 버전과 DB가 유효하면 재적재하지 않고, 버전 변경 시 트랜잭션으로 문서 데이터를 교체한다.
 * 여기서 저장하는 384차원 해시 벡터는 기존 엔티티 필드이며, 실제 의미 검색은 별도 768차원 인덱스를 쓴다.
 * 사용자 대화는 ChatHistoryStore에 따로 있어 이 문서 적재 작업에 포함되지 않는다.
 */
class KnowledgeSeeder(private val context: Context) {
    companion object {
        private const val ASSET_PATH = "knowledge/seed_chunks_v1.json"
        private const val PREFS_NAME = "knowledge_seed"
        private const val KEY_VERSION = "seed_version"
    }

    fun seedIfNeeded(): Int {
        val payload = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(payload)
        val version = root.getInt("version")
        val box = ObjectBoxStore.store.boxFor(KnowledgeChunk::class.java)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_VERSION, -1) == version && box.count() > 0) return box.count().toInt()

        val chunks = root.getJSONArray("chunks")
        val entities = ArrayList<KnowledgeChunk>(chunks.length())
        for (index in 0 until chunks.length()) {
            val item = chunks.getJSONObject(index)
            val heading = item.getString("heading")
            val body = item.getString("body")
            val searchable = "$heading $body".lowercase()
            entities += KnowledgeChunk(
                documentId = item.getString("documentId"),
                documentTitle = item.getString("documentTitle"),
                sourceKind = item.getString("sourceKind"),
                pageNumber = item.getInt("pageNumber"),
                chunkIndex = item.getInt("chunkIndex"),
                heading = heading,
                body = body,
                searchableText = searchable,
                embedding = OfflineHashEmbedding.embed(searchable),
            )
        }

        ObjectBoxStore.store.runInTx {
            box.removeAll()
            box.put(entities)
        }
        prefs.edit().putInt(KEY_VERSION, version).commit()
        return entities.size
    }
}
