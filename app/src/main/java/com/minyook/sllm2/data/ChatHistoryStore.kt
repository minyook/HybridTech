package com.minyook.sllm2.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A small, local-only transcript store for the navigation drawer. It deliberately
 * stays separate from the knowledge base: saved chats never alter RAG documents
 * or embeddings.
 */
class ChatHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    data class Turn(val role: Role, val text: String)
    enum class Role { USER, ASSISTANT }
    data class Session(
        val id: String,
        val title: String,
        val updatedAtMillis: Long,
        val turns: List<Turn>,
    )

    @Synchronized
    fun create(firstQuestion: String): Session {
        val session = Session(
            id = UUID.randomUUID().toString(),
            title = firstQuestion.replace(Regex("\\s+"), " ").take(36),
            updatedAtMillis = System.currentTimeMillis(),
            turns = emptyList(),
        )
        save(list() + session)
        return session
    }

    @Synchronized
    fun list(): List<Session> = read().sortedByDescending { it.updatedAtMillis }

    @Synchronized
    fun find(id: String): Session? = read().firstOrNull { it.id == id }

    @Synchronized
    fun append(id: String, role: Role, text: String) {
        val updated = read().map { session ->
            if (session.id == id) {
                session.copy(
                    updatedAtMillis = System.currentTimeMillis(),
                    turns = (session.turns + Turn(role, text)).takeLast(MAX_TURNS),
                )
            } else session
        }
        save(updated)
    }

    private fun read(): List<Session> = runCatching {
        val stored = JSONArray(preferences.getString(KEY_SESSIONS, "[]"))
        buildList {
            for (index in 0 until stored.length()) {
                val raw = stored.getJSONObject(index)
                val turns = raw.optJSONArray("turns") ?: JSONArray()
                add(
                    Session(
                        id = raw.getString("id"),
                        title = raw.optString("title", "새 대화"),
                        updatedAtMillis = raw.optLong("updatedAt"),
                        turns = buildList {
                            for (turnIndex in 0 until turns.length()) {
                                val turn = turns.getJSONObject(turnIndex)
                                val role = runCatching { Role.valueOf(turn.getString("role")) }
                                    .getOrDefault(Role.ASSISTANT)
                                add(Turn(role, turn.optString("text")))
                            }
                        },
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun save(sessions: List<Session>) {
        val json = JSONArray()
        sessions
            .sortedByDescending { it.updatedAtMillis }
            .take(MAX_SESSIONS)
            .forEach { session ->
                json.put(
                    JSONObject().apply {
                        put("id", session.id)
                        put("title", session.title)
                        put("updatedAt", session.updatedAtMillis)
                        put("turns", JSONArray().apply {
                            session.turns.forEach { turn ->
                                put(JSONObject().apply {
                                    put("role", turn.role.name)
                                    put("text", turn.text)
                                })
                            }
                        })
                    },
                )
            }
        preferences.edit().putString(KEY_SESSIONS, json.toString()).apply()
    }

    private companion object {
        const val PREFERENCES = "local_chat_history"
        const val KEY_SESSIONS = "sessions"
        const val MAX_SESSIONS = 20
        const val MAX_TURNS = 80
    }
}
