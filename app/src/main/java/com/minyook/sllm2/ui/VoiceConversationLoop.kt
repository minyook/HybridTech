package com.minyook.sllm2.ui

internal enum class VoicePhase { OFF, LISTENING, WAITING, ANSWERING, SPEAKING, ERROR }
internal data class VoiceLoopState(
    val enabled: Boolean = false,
    val phase: VoicePhase = VoicePhase.OFF,
    val wakeWord: Boolean = false,
    val lastHeard: String = "",
    val error: String? = null,
)

/** Foreground, half-duplex conversation. All callbacks carry the originating attempt ID. */
internal class VoiceConversationLoop(private val port: Port) {
    interface Port {
        fun listen(id: Long, wakeWord: Boolean)
        fun stopListening()
        fun answer(id: Long, question: String)
        fun speak(id: Long, text: String)
        fun stopSpeaking()
        fun later(delayMillis: Long, action: () -> Unit)
        fun cancelLater()
        fun changed(state: VoiceLoopState)
    }
    var state = VoiceLoopState(); private set
    private var sequence = 0L
    private var activeId = 0L
    private var busyRetries = 0
    var readAnswers = true; private set

    fun start(wakeWord: Boolean = false) {
        stop()
        busyRetries = 0
        update(state.copy(enabled = true, wakeWord = wakeWord, error = null))
        listen()
    }
    fun stop() {
        activeId = ++sequence
        port.cancelLater(); port.stopListening(); port.stopSpeaking()
        update(state.copy(enabled = false, phase = VoicePhase.OFF, wakeWord = false, error = null))
    }
    fun setReadAnswers(enabled: Boolean) {
        readAnswers = enabled
        if (!enabled && state.phase == VoicePhase.SPEAKING) { port.stopSpeaking(); again() }
    }
    fun recognized(id: Long, spoken: String) {
        if (!accept(id, VoicePhase.LISTENING)) return
        port.stopListening(); busyRetries = 0
        var text = spoken.trim()
        if (text.isBlank()) { again(); return }
        if (state.wakeWord) {
            val wake = WAKE_WORD.find(text)
            if (wake == null) { again(); return }
            text = text.substring(wake.range.last + 1).trim(' ', ',', '.', '!', '?')
            update(state.copy(wakeWord = false))
            if (text.isBlank()) { again(); return }
        }
        if (END_CONVERSATION.matches(text)) { stop(); return }
        if (text.length > 1500) { fail("질문이 너무 길어요. 짧게 나누어 말씀해 주세요."); return }
        activeId = ++sequence
        update(state.copy(phase = VoicePhase.ANSWERING, lastHeard = text))
        port.answer(activeId, text)
    }
    fun recognitionError(id: Long, message: String, silence: Boolean = false, busy: Boolean = false) {
        if (!accept(id, VoicePhase.LISTENING)) return
        port.stopListening()
        when {
            silence -> again(700)
            busy && busyRetries++ < 2 -> again(1000L * busyRetries)
            else -> fail(message)
        }
    }
    fun answered(id: Long, text: String) {
        if (!accept(id, VoicePhase.ANSWERING)) return
        if (text.isBlank()) { fail("답변을 받지 못했어요. 화면을 확인하고 다시 시작해 주세요."); return }
        if (readAnswers) {
            update(state.copy(phase = VoicePhase.SPEAKING))
            port.speak(id, text)
        } else again()
    }
    fun answerFailed(id: Long, message: String) { if (accept(id, VoicePhase.ANSWERING)) fail(message) }
    fun spoken(id: Long) { if (accept(id, VoicePhase.SPEAKING)) again() }
    fun speechFailed(id: Long, message: String) { if (accept(id, VoicePhase.SPEAKING)) fail(message) }
    private fun listen() {
        if (!state.enabled) return
        activeId = ++sequence
        update(state.copy(phase = VoicePhase.LISTENING))
        port.listen(activeId, state.wakeWord)
    }
    private fun again(delay: Long = 550) {
        if (!state.enabled) return
        val id = ++sequence; activeId = id
        update(state.copy(phase = VoicePhase.WAITING))
        port.cancelLater()
        port.later(delay) { if (state.enabled && activeId == id) listen() }
    }
    private fun accept(id: Long, phase: VoicePhase) = state.enabled && activeId == id && state.phase == phase
    private fun fail(message: String) { stop(); update(state.copy(phase = VoicePhase.ERROR, error = message)) }
    private fun update(next: VoiceLoopState) { state = next; port.changed(next) }
    companion object {
        val WAKE_WORD = Regex("(?i)(오\\s*투\\s*야|o\\s*2\\s*야)")
        private val END_CONVERSATION = Regex("(?:음성\\s*)?대화\\s*(?:종료|그만)[.!?\\s]*")
    }
}

/** Preserve all conditions and table values; split only for the OS TTS input limit. */
internal object VoiceResponseFormatter {
    fun forSpeech(markdown: String): String = markdown.lineSequence().map(String::trim)
        .filter { it.isNotBlank() && !it.startsWith("> 출처:") && !it.startsWith("출처:") && !Regex("\\|?\\s*[-:]+(?:\\s*\\|\\s*[-:]+)+\\s*\\|?").matches(it) }
        .map { it.replace(Regex("^#{1,6}\\s+"), "").removePrefix("- ").removePrefix("> ")
            .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1").replace("**", "").replace("`", "").replace('|', ' ') }
        .joinToString(" ").replace(Regex("\\s+"), " ").trim()

    fun chunks(markdown: String, limit: Int = 300): List<String> {
        require(limit >= 20)
        var remaining = forSpeech(markdown)
        return buildList {
            while (remaining.isNotEmpty()) {
                var end = minOf(limit, remaining.length)
                if (end < remaining.length) {
                    val space = remaining.lastIndexOf(' ', end)
                    if (space > limit / 2) end = space
                }
                add(remaining.take(end).trim()); remaining = remaining.drop(end).trim()
            }
        }
    }
}
