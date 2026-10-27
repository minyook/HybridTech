package com.minyook.sllm2.ui

import org.junit.Assert.*
import org.junit.Test

class VoiceConversationLoopTest {
    private class Fake : VoiceConversationLoop.Port {
        val loop = VoiceConversationLoop(this)
        var listenId = 0L; var answerId = 0L; var speechId = 0L
        var starts = 0; var closes = 0; var stops = 0
        val questions = mutableListOf<String>(); val answers = mutableListOf<String>()
        var scheduled: (() -> Unit)? = null
        override fun listen(id: Long, wakeWord: Boolean) { listenId = id; starts++ }
        override fun stopListening() { closes++ }
        override fun answer(id: Long, question: String) { answerId = id; questions.add(question) }
        override fun speak(id: Long, text: String) { speechId = id; answers.add(text) }
        override fun stopSpeaking() { stops++ }
        override fun later(delayMillis: Long, action: () -> Unit) { assertTrue(delayMillis >= 500); scheduled = action }
        override fun cancelLater() { scheduled = null }
        override fun changed(state: VoiceLoopState) = Unit
        fun again() { val next = scheduled; scheduled = null; next?.invoke() }
    }
    @Test fun oneStartSupportsSeveralCompleteTurns() {
        val f = Fake(); f.loop.start()
        repeat(3) { index ->
            f.loop.recognized(f.listenId, "질문 $index")
            assertEquals(VoicePhase.ANSWERING, f.loop.state.phase)
            f.loop.answered(f.answerId, "조건을 포함한 답변 $index")
            assertEquals(VoicePhase.SPEAKING, f.loop.state.phase)
            f.loop.spoken(f.speechId); f.again()
        }
        assertEquals(4, f.starts); assertEquals(3, f.questions.size)
        assertEquals(VoicePhase.LISTENING, f.loop.state.phase)
    }
    @Test fun speechPlaybackCannotTriggerAnotherQuestion() {
        val f = Fake(); f.loop.start(); val listen = f.listenId
        f.loop.recognized(listen, "산소 측정 방법"); f.loop.answered(f.answerId, "산소 측정 답변")
        f.loop.recognized(listen, "앱이 읽는 소리")
        assertEquals(listOf("산소 측정 방법"), f.questions); assertEquals(1, f.starts)
    }
    @Test fun stopIgnoresLateRecognitionAnswerTtsAndScheduledCallbacks() {
        val f = Fake(); f.loop.start(); val listen = f.listenId
        f.loop.recognized(listen, "질문"); val answer = f.answerId
        f.loop.stop(); f.loop.recognized(listen, "늦은 질문"); f.loop.answered(answer, "늦은 답변"); f.loop.spoken(answer); f.again()
        assertEquals(VoicePhase.OFF, f.loop.state.phase); assertTrue(f.answers.isEmpty()); assertEquals(1, f.starts)
    }
    @Test fun oldAnswerCannotAffectAReplacementConversation() {
        val f = Fake(); f.loop.start(); f.loop.recognized(f.listenId, "첫 질문"); val old = f.answerId
        f.loop.start(); f.loop.recognized(f.listenId, "새 질문"); f.loop.answered(old, "옛 답변")
        assertEquals(VoicePhase.ANSWERING, f.loop.state.phase); assertTrue(f.answers.isEmpty())
        f.loop.answered(f.answerId, "새 답변"); assertEquals(listOf("새 답변"), f.answers)
    }
    @Test fun quietTimeoutRearmsOnlyAfterDelayAndStopsWithScreen() {
        val f = Fake(); f.loop.start(); f.loop.recognitionError(f.listenId, "침묵", silence = true)
        assertEquals(1, f.starts); assertEquals(VoicePhase.WAITING, f.loop.state.phase)
        val late = f.scheduled; f.loop.stop(); late?.invoke(); assertEquals(1, f.starts)
        f.loop.start(); f.loop.recognitionError(f.listenId, "침묵", silence = true); f.again(); assertEquals(3, f.starts)
    }
    @Test fun persistentBusyIsBoundedAndFatalErrorsNeverRestart() {
        val f = Fake(); f.loop.start()
        repeat(3) { f.loop.recognitionError(f.listenId, "사용 중", busy = true); f.again() }
        assertFalse(f.loop.state.enabled); assertEquals(3, f.starts); assertEquals("사용 중", f.loop.state.error)
        f.loop.start(); f.loop.recognitionError(f.listenId, "한국어 데이터 없음"); f.again()
        assertEquals(VoicePhase.ERROR, f.loop.state.phase); assertEquals(4, f.starts)
    }
    @Test fun wakeWordWithQuestionKeepsTheQuestionAndContinuesWithoutAnotherWakeWord() {
        val f = Fake(); f.loop.start(wakeWord = true); f.loop.recognized(f.listenId, "오투야 산소 측정 방법 알려줘")
        assertEquals(listOf("산소 측정 방법 알려줘"), f.questions); assertFalse(f.loop.state.wakeWord)
        f.loop.answered(f.answerId, "답변"); f.loop.spoken(f.speechId); f.again(); f.loop.recognized(f.listenId, "두 번째 질문")
        assertEquals(2, f.questions.size)
    }
    @Test fun bareWakeWordStartsListeningForTheQuestionWithoutSubmittingEmptyText() {
        val f = Fake(); f.loop.start(true); f.loop.recognized(f.listenId, "다른 대화"); f.again(); assertTrue(f.questions.isEmpty())
        f.loop.recognized(f.listenId, "오투야"); f.again(); assertTrue(f.questions.isEmpty()); assertFalse(f.loop.state.wakeWord)
    }
    @Test fun readOffSkipsTtsAndDisablingDuringPlaybackResumesListening() {
        val f = Fake(); f.loop.setReadAnswers(false); f.loop.start(); f.loop.recognized(f.listenId, "질문"); f.loop.answered(f.answerId, "답변"); assertTrue(f.answers.isEmpty()); f.again()
        f.loop.setReadAnswers(true); f.loop.recognized(f.listenId, "다음 질문"); f.loop.answered(f.answerId, "답변"); val id = f.speechId
        f.loop.setReadAnswers(false); f.loop.spoken(id); f.again(); assertEquals(3, f.starts)
    }
    @Test fun exactEndPhraseStopsWithoutCreatingAQuestion() {
        val f = Fake(); f.loop.start(); f.loop.recognized(f.listenId, "음성 대화 종료."); assertFalse(f.loop.state.enabled); assertTrue(f.questions.isEmpty())
    }
    @Test fun answerAndPlaybackFailuresStopWithAnExplanation() {
        val f = Fake(); f.loop.start(); f.loop.recognized(f.listenId, "질문"); f.loop.answerFailed(f.answerId, "검색 오류"); assertEquals("검색 오류", f.loop.state.error)
        f.loop.start(); f.loop.recognized(f.listenId, "질문"); f.loop.answered(f.answerId, "답변"); f.loop.speechFailed(f.speechId, "로컬 음성 없음"); f.again(); assertFalse(f.loop.state.enabled); assertEquals("로컬 음성 없음", f.loop.state.error)
    }
}
