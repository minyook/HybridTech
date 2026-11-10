/* 소스 안내
 * 최종 대화·출처·주제·선택지를 로컬 JSON으로 저장/복원한다. ViewModel의 기록 목록과 다음 질문 문맥에 연결된다.
 */
package com.minyook.sllm2.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * ViewModel의 최종 대화와 문맥 메타데이터를 SharedPreferences JSON으로 저장한다.
 * create/append 후 목록을 갱신하고 loadChat에서 턴을 복원해 ConversationContext에 다시 전달한다.
 * 대화 수/턴 수를 제한하며 문서 DB·임베딩·모델 가중치는 수정하지 않는다.
 * 화면 애니메이션의 부분 본문 대신 최종 Markdown·실제 출처·선택지를 저장한다.
 */
class ChatHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    data class Turn(val role: Role, val text: String, val sourceIds: List<Long> = emptyList(), val suggestions: List<SearchSuggestion> = emptyList(), val resolvedQuestion: String? = null, val topicQuestion: String? = null, val answerStatus: String? = null)
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
    fun append(id: String, role: Role, text: String, sourceIds: List<Long> = emptyList(), suggestions: List<SearchSuggestion> = emptyList(), resolvedQuestion: String? = null, topicQuestion: String? = null, answerStatus: String? = null) {
        val updated = read().map { session ->
            if (session.id == id) {
                session.copy(
                    updatedAtMillis = System.currentTimeMillis(),
                    turns = (session.turns + Turn(role, text, sourceIds, suggestions, resolvedQuestion, topicQuestion, answerStatus)).takeLast(MAX_TURNS),
                )
            } else session
        }
        save(updated)
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_SESSIONS).apply()
    }

    private fun read(): List<Session> = ChatHistoryJson.decode(preferences.getString(KEY_SESSIONS, "[]") ?: "[]")
    private fun save(sessions: List<Session>) {
        preferences.edit().putString(KEY_SESSIONS, ChatHistoryJson.encode(sessions, MAX_SESSIONS)).apply()
    }

    private companion object {
        const val PREFERENCES = "local_chat_history"
        const val KEY_SESSIONS = "sessions"
        const val MAX_SESSIONS = 20
        const val MAX_TURNS = 80
    }
}

/** Pure JSON codec: legacy chats and contextual metadata use the same local storage format. */
internal object ChatHistoryJson {
    fun decode(raw: String): List<ChatHistoryStore.Session> = runCatching {
        val stored = JSONArray(raw)
        buildList {
            for (index in 0 until stored.length()) {
                val raw = stored.getJSONObject(index)
                val turns = raw.optJSONArray("turns") ?: JSONArray()
                add(
                    ChatHistoryStore.Session(
                        id = raw.getString("id"),
                        title = raw.optString("title", "새 대화"),
                        updatedAtMillis = raw.optLong("updatedAt"),
                        turns = buildList {
                            for (turnIndex in 0 until turns.length()) {
                                val turn = turns.getJSONObject(turnIndex)
                                val role = runCatching { ChatHistoryStore.Role.valueOf(turn.getString("role")) }
                                    .getOrDefault(ChatHistoryStore.Role.ASSISTANT)
                                val sourceIds = turn.optJSONArray("sourceIds") ?: JSONArray()
                                add(ChatHistoryStore.Turn(role, turn.optString("text"), buildList {
                                    for (sourceIndex in 0 until sourceIds.length()) add(sourceIds.optLong(sourceIndex))
                                }.filter { it > 0L }, buildList {
                                    val choices=turn.optJSONArray("suggestions") ?: JSONArray()
                                    for(choiceIndex in 0 until choices.length()){val choice=choices.getJSONObject(choiceIndex);add(SearchSuggestion(choice.getString("label"),choice.getString("question")))}
                                },turn.optString("resolvedQuestion").takeIf{it.isNotBlank()},turn.optString("topicQuestion").takeIf{it.isNotBlank()},turn.optString("answerStatus").takeIf{it.isNotBlank()}))
                            }
                        },
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun encode(sessions: List<ChatHistoryStore.Session>, maxSessions: Int = 20): String {
        val json = JSONArray()
        sessions
            .sortedByDescending { it.updatedAtMillis }
            .take(maxSessions)
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
                                    turn.resolvedQuestion?.let{put("resolvedQuestion",it)}
                                    turn.topicQuestion?.let{put("topicQuestion",it)}
                                    turn.answerStatus?.let{put("answerStatus",it)}
                                    put("suggestions",JSONArray().apply{turn.suggestions.forEach{choice->put(JSONObject().put("label",choice.label).put("question",choice.question))}})
                                    put("sourceIds", JSONArray().apply { turn.sourceIds.forEach { put(it) } })
                                })
                            }
                        })
                    },
                )
            }
        return json.toString()
    }

 }
