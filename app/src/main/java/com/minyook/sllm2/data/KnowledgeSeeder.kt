package com.minyook.sllm2.data

import android.content.Context
import org.json.JSONObject

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
