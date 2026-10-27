package com.minyook.sllm2.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

data class VoiceUiState(
    val available: Boolean,
    val listening: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val awaitingWakeWord: Boolean = false,
    val conversationEnabled: Boolean = false,
    val speaking: Boolean = false,
    val waitingForAnswer: Boolean = false,
    val readAnswers: Boolean = true,
    val ttsAvailable: Boolean = false,
    val languagePreparing: Boolean = false,
    val localSpeechStatus: String = "로컬 한국어 음성 지원 확인 중",
    val status: String = "한 번 시작하면 말을 마칠 때 자동으로 질문해요.",
    val lastHeard: String = "",
    val error: String? = null,
)

/** Native on-device recognition only. No network recognizer or external voice-input activity. */
class VoiceConversationController(
    private val context: Context,
    private val hasMicrophonePermission: () -> Boolean,
    private val requestMicrophonePermission: () -> Unit,
    private val onQuestion: (String, (String) -> Unit, (String) -> Unit) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val _state = MutableStateFlow(VoiceUiState(available = hasLocalRecognizer(context)))
    val state: StateFlow<VoiceUiState> = _state
    private val handler = Handler(context.mainLooper)
    private var recognizer: SpeechRecognizer? = null
    private var languageRecognizer: SpeechRecognizer? = null
    private var languageEpoch = 0L
    private var languageTimeout: Runnable? = null
    private var tts: TextToSpeech? = null
    private var inForeground = true
    private var destroyed = false
    private var pendingPermission: Boolean? = null
    private var retry: Runnable? = null
    private var recognitionTimeout: Runnable? = null
    private var answerTimeout: Runnable? = null
    private var answerId: Long? = null
    private var speechTimeout: Runnable? = null
    private var speechId: Long? = null
    private var speechIndex = 0
    private var speechChunks = emptyList<String>()

    private val loop: VoiceConversationLoop = VoiceConversationLoop(object : VoiceConversationLoop.Port {
        override fun listen(id: Long, wakeWord: Boolean) = startListening(id, wakeWord)
        override fun stopListening() = closeRecognizer()
        override fun answer(id: Long, question: String) {
            answerId = id
            answerTimeout?.let(handler::removeCallbacks)
            answerTimeout = Runnable { loop.answerFailed(id, "답변 완료를 확인하지 못했어요. 화면을 확인하고 다시 시작해 주세요.") }.also { handler.postDelayed(it, 300_000) }
            try { onQuestion(question, { answer -> handler.post {
                if (!destroyed && answerId == id) { answerId = null; answerTimeout?.let(handler::removeCallbacks); loop.answered(id, answer) }
            } }, { error -> handler.post { if (!destroyed && answerId == id) { answerId = null; loop.answerFailed(id, error) } } }) }
            catch (_: Exception) { loop.answerFailed(id, "음성 질문을 처리하지 못했어요. 화면을 확인하고 다시 시작해 주세요.") }
        }
        override fun speak(id: Long, text: String) = speakAnswer(id, text)
        override fun stopSpeaking() {
            speechId = null; speechChunks = emptyList()
            speechTimeout?.let(handler::removeCallbacks); speechTimeout = null
            answerTimeout?.let(handler::removeCallbacks); answerTimeout = null
            answerId = null
            runCatching { tts?.stop() }
        }
        override fun later(delayMillis: Long, action: () -> Unit) {
            retry = Runnable { retry = null; if (inForeground && !destroyed) action() }.also { handler.postDelayed(it, delayMillis) }
        }
        override fun cancelLater() { retry?.let(handler::removeCallbacks); retry = null }
        override fun changed(state: VoiceLoopState) {
            val oldError = _state.value.error
            _state.value = _state.value.copy(
                conversationEnabled = state.enabled, listening = state.phase == VoicePhase.LISTENING,
                wakeWordEnabled = state.enabled && state.wakeWord, awaitingWakeWord = state.enabled && state.wakeWord,
                waitingForAnswer = state.phase == VoicePhase.ANSWERING, speaking = state.phase == VoicePhase.SPEAKING,
                lastHeard = state.lastHeard, error = state.error,
                status = when (state.phase) {
                    VoicePhase.OFF -> "음성 대화가 꺼져 있어요."
                    VoicePhase.LISTENING -> if (state.wakeWord) "‘오투야’라고 부르면 연속 대화를 시작해요." else "듣고 있어요. 말을 마치면 자동으로 질문해요."
                    VoicePhase.WAITING -> "다음 질문을 들을 준비 중이에요."
                    VoicePhase.ANSWERING -> "인식한 질문으로 기기 안에서 문서 답변을 만들고 있어요."
                    VoicePhase.SPEAKING -> "답변을 읽고 있어요. 마이크는 쉬고 있어요."
                    VoicePhase.ERROR -> state.error.orEmpty()
                },
            )
            if (state.error != null && state.error != oldError) onError(state.error)
        }
    })

    init { configureTts(); refreshLanguageSupport() }
    fun startQuestion() = start(false)
    fun restartQuestion() = startQuestion()
    fun toggleWakeWord() { if (_state.value.wakeWordEnabled) stopQuestion() else start(true) }
    fun setReadAnswers(enabled: Boolean) { _state.value = _state.value.copy(readAnswers = enabled); loop.setReadAnswers(enabled) }
    private fun start(wakeWord: Boolean) {
        if (destroyed || !inForeground) return
        if (!_state.value.available) { report("로컬 음성 인식기를 사용할 수 없어요. Android 12 이상과 기기 내 음성 서비스를 확인해 주세요."); return }
        if (_state.value.languagePreparing) { report("한국어 음성 데이터 준비가 끝난 뒤 다시 시작해 주세요."); return }
        if (!hasMicrophonePermission()) { pendingPermission = wakeWord; requestMicrophonePermission(); return }
        loop.start(wakeWord)
    }
    fun onMicrophonePermissionResult(granted: Boolean) {
        val wake = pendingPermission; pendingPermission = null
        if (!granted) report("마이크 권한이 필요해요. 앱 권한에서 마이크를 허용해 주세요.")
        else if (wake != null && inForeground) start(wake)
        else _state.value = _state.value.copy(status = "마이크 권한을 허용했어요. 음성 대화 시작을 눌러 주세요.")
    }
    fun onScreenVisibility(voiceOrChatVisible: Boolean) { if (!voiceOrChatVisible) stopQuestion() }
    fun onResume(chatVisible: Boolean) { inForeground = true; if (!chatVisible) stopQuestion(); selectLocalVoice(); refreshLanguageSupport() }
    fun onPause() { inForeground = false; stopQuestion() }
    fun stopQuestion() { pendingPermission = null; loop.stop() }
    fun destroy() {
        destroyed = true; stopQuestion(); languageEpoch++; languageTimeout?.let(handler::removeCallbacks); languageRecognizer?.destroy(); languageRecognizer = null
        tts?.shutdown(); tts = null
    }
    private fun report(message: String) { loop.stop(); _state.value = _state.value.copy(error = message, status = message); onError(message) }

    private fun startListening(id: Long, wakeWord: Boolean) {
        if (!inForeground || destroyed || !hasMicrophonePermission()) { loop.recognitionError(id, "마이크 권한과 앱 화면을 확인해 주세요."); return }
        closeRecognizer()
        try {
            val candidate = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = candidate
            candidate.setRecognitionListener(object : EmptyRecognitionListener() {
                override fun onBeginningOfSpeech() { if (recognizer === candidate) recognitionWatchdog(id, 30_000, false) }
                override fun onEndOfSpeech() { if (recognizer === candidate) recognitionWatchdog(id, 12_000, false) }
                override fun onResults(results: Bundle?) {
                    if (recognizer === candidate) loop.recognized(id, results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onError(error: Int) {
                    if (recognizer !== candidate) return
                    loop.recognitionError(id, errorMessage(error), silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT, busy = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                }
            })
            recognitionWatchdog(id, 30_000, true)
            candidate.startListening(recognizerIntent(wakeWord))
        } catch (_: Exception) { loop.recognitionError(id, "로컬 음성 인식을 시작하지 못했어요. 한국어 음성 데이터와 음성 서비스 설정을 확인해 주세요.") }
    }
    private fun recognitionWatchdog(id: Long, delay: Long, silence: Boolean) {
        recognitionTimeout?.let(handler::removeCallbacks)
        recognitionTimeout = Runnable { loop.recognitionError(id, "음성 인식 완료를 확인하지 못했어요. 대화를 다시 시작해 주세요.", silence = silence) }.also { handler.postDelayed(it, delay) }
    }
    private fun closeRecognizer() {
        recognitionTimeout?.let(handler::removeCallbacks); recognitionTimeout = null
        val old = recognizer; recognizer = null
        runCatching { old?.cancel() }; runCatching { old?.destroy() }
    }
    private fun configureTts() {
        tts = TextToSpeech(context) { status -> handler.post {
            if (destroyed) return@post
            if (status == TextToSpeech.SUCCESS) {
                selectLocalVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { handler.post {
                        val id = speechId ?: return@post
                        if (utteranceId != "$id:$speechIndex") return@post
                        speechTimeout?.let(handler::removeCallbacks); speechIndex++
                        if (speechIndex < speechChunks.size) speakChunk(id) else { speechId = null; loop.spoken(id) }
                    } }
                    @Deprecated("Platform callback") override fun onError(utteranceId: String?) = speechError(utteranceId)
                    override fun onError(utteranceId: String?, errorCode: Int) = speechError(utteranceId)
                })
            } else _state.value = _state.value.copy(ttsAvailable = false)
        } }
    }
    private fun selectLocalVoice() {
        val engine = tts ?: return
        val voice = runCatching { engine.voices?.filter { it.locale.language == "ko" && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }?.maxByOrNull { it.quality } }.getOrNull()
        val ready = voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS
        _state.value = _state.value.copy(ttsAvailable = ready)
    }
    private fun speakAnswer(id: Long, text: String) {
        selectLocalVoice()
        if (!_state.value.ttsAvailable) { loop.speechFailed(id, "설치된 로컬 한국어 읽기 음성이 없어요. 기기 TTS에 한국어를 설치하거나 ‘답변 읽기’를 끄고 다시 시작해 주세요."); return }
        speechChunks = VoiceResponseFormatter.chunks(text, minOf(300, TextToSpeech.getMaxSpeechInputLength()))
        if (speechChunks.isEmpty()) { loop.spoken(id); return }
        speechId = id; speechIndex = 0; speakChunk(id)
    }
    private fun speakChunk(id: Long) {
        if (destroyed || !inForeground || speechId != id) return
        speechTimeout?.let(handler::removeCallbacks)
        speechTimeout = Runnable { loop.speechFailed(id, "답변 읽기 완료를 확인하지 못했어요. 화면을 확인하고 다시 시작해 주세요.") }.also { handler.postDelayed(it, 90_000) }
        if (tts?.speak(speechChunks[speechIndex], TextToSpeech.QUEUE_FLUSH, null, "$id:$speechIndex") != TextToSpeech.SUCCESS) loop.speechFailed(id, "한국어 음성 출력에 실패했어요. 읽기를 끄고 다시 시작할 수 있어요.")
    }
    private fun speechError(utteranceId: String?) { handler.post {
        val id = speechId ?: return@post
        if (utteranceId == "$id:$speechIndex") loop.speechFailed(id, "한국어 로컬 음성을 출력하지 못했어요. 읽기를 끄거나 음성 데이터를 확인해 주세요.")
    } }

    fun refreshLanguageSupport() {
        if (destroyed || _state.value.languagePreparing) return
        val available = hasLocalRecognizer(context)
        _state.value = _state.value.copy(available = available)
        if (!available) { _state.value = _state.value.copy(localSpeechStatus = "기기 내 음성 인식 서비스 없음 · 네트워크 인식으로 전환하지 않아요."); return }
        if (Build.VERSION.SDK_INT < 33) { _state.value = _state.value.copy(localSpeechStatus = "로컬 서비스 사용 가능 · 한국어 데이터 설치 여부는 시작 시 확인해요."); return }
        val epoch = ++languageEpoch; languageRecognizer?.destroy()
        languageTimeout?.let(handler::removeCallbacks)
        val candidate = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull() ?: return
        languageRecognizer = candidate; candidate.setRecognitionListener(EmptyRecognitionListener())
        languageTimeout = Runnable {
            if (epoch == languageEpoch && languageRecognizer === candidate && !destroyed) {
                _state.value = _state.value.copy(localSpeechStatus = "한국어 지원 조회가 지연됐어요. 잠시 후 다시 확인해 주세요.")
                candidate.destroy(); languageRecognizer = null
            }
        }.also { handler.postDelayed(it, 15_000) }
        runCatching { candidate.checkRecognitionSupport(recognizerIntent(false), context.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                if (epoch != languageEpoch || destroyed || languageRecognizer !== candidate) return
                languageTimeout?.let(handler::removeCallbacks)
                _state.value = _state.value.copy(localSpeechStatus = when {
                    support.installedOnDeviceLanguages.any(::isKorean) -> "한국어 기기 내 인식 데이터 준비됨"
                    support.pendingOnDeviceLanguages.any(::isKorean) -> "한국어 음성 데이터 다운로드 대기 중 · 잠시 후 다시 확인해 주세요."
                    support.supportedOnDeviceLanguages.any(::isKorean) -> "한국어 로컬 데이터 설치 필요 · ‘한국어 음성 준비’를 눌러 주세요."
                    else -> "현재 음성 서비스가 한국어 로컬 인식 지원을 보고하지 않았어요. 기기 음성 서비스 설정을 확인해 주세요."
                }); candidate.destroy(); if (languageRecognizer === candidate) languageRecognizer = null
            }
            override fun onError(error: Int) {
                if (epoch != languageEpoch || destroyed || languageRecognizer !== candidate) return
                languageTimeout?.let(handler::removeCallbacks)
                _state.value = _state.value.copy(localSpeechStatus = "한국어 지원 조회를 제공하지 않는 서비스예요. 시작 시 로컬 인식을 확인해요.")
                candidate.destroy(); if (languageRecognizer === candidate) languageRecognizer = null
            }
        }) }.onFailure { candidate.destroy(); languageRecognizer = null; _state.value = _state.value.copy(localSpeechStatus = "로컬 한국어 지원 상태를 조회하지 못했어요.") }
    }
    fun prepareLocalLanguage() {
        stopQuestion()
        if (Build.VERSION.SDK_INT < 33 || !hasLocalRecognizer(context)) { report("Android 13 이상 음성 서비스의 한국어 다운로드 지원이 필요해요. 기기 음성 서비스 설정에서 로컬 데이터를 확인해 주세요."); return }
        val epoch = ++languageEpoch; languageRecognizer?.destroy()
        languageTimeout?.let(handler::removeCallbacks)
        val candidate = SpeechRecognizer.createOnDeviceSpeechRecognizer(context); languageRecognizer = candidate; candidate.setRecognitionListener(EmptyRecognitionListener())
        _state.value = _state.value.copy(languagePreparing = true, localSpeechStatus = "한국어 로컬 음성 데이터 준비 요청 중 · 최초 준비에는 인터넷이 필요할 수 있어요.")
        fun complete(message: String) {
            if (epoch != languageEpoch || destroyed || languageRecognizer !== candidate) return
            languageTimeout?.let(handler::removeCallbacks)
            _state.value = _state.value.copy(languagePreparing = false, localSpeechStatus = message)
            candidate.destroy(); if (languageRecognizer === candidate) languageRecognizer = null
        }
        languageTimeout = Runnable { complete("한국어 준비 완료를 확인하지 못했어요. 자동 재시도하지 않으니 기기 음성 서비스 설정을 확인해 주세요.") }.also { handler.postDelayed(it, 120_000) }
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) candidate.triggerModelDownload(recognizerIntent(false), context.mainExecutor, object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) { if (epoch == languageEpoch && !destroyed) _state.value = _state.value.copy(localSpeechStatus = "한국어 로컬 음성 데이터 준비 $completedPercent%") }
                override fun onSuccess() { complete("한국어 음성 데이터를 준비했어요."); refreshLanguageSupport() }
                override fun onScheduled() { complete("한국어 음성 데이터 다운로드가 예약됐어요. 잠시 후 앱을 다시 열어 확인해 주세요.") }
                override fun onError(error: Int) { complete("한국어 준비를 완료하지 못했어요 (서비스 코드 $error). 기기 음성 서비스에서 한국어 로컬 데이터를 확인해 주세요.") }
            }) else { candidate.triggerModelDownload(recognizerIntent(false)); complete("한국어 음성 데이터 준비를 요청했어요. 완료 후 앱을 다시 열어 확인해 주세요.") }
        }.onFailure { complete("현재 음성 서비스에서 다운로드를 시작하지 못했어요. 기기 음성 서비스 설정을 확인해 주세요.") }
    }
    private fun recognizerIntent(wakeWord: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR"); putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false); putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PROMPT, if (wakeWord) "오투야" else "질문을 말씀해 주세요")
    }
    private fun errorMessage(error: Int) = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "로컬 한국어 음성 데이터가 없거나 지원되지 않아요. ‘한국어 음성 준비’ 또는 기기 음성 서비스 설정을 확인해 주세요."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "로컬 음성 서비스가 네트워크 오류를 보고했어요. 인식을 중단했으며 원격 인식으로 전환하지 않아요."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요해요."
        SpeechRecognizer.ERROR_AUDIO -> "마이크 입력을 확인해 주세요. 다른 앱의 녹음을 종료하고 다시 시작해 주세요."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 계속 사용 중이에요. 다른 음성 앱을 닫고 다시 시작해 주세요."
        else -> "로컬 음성 인식에 실패했어요 (서비스 코드 $error). 다시 시작해 주세요."
    }
    companion object {
        fun hasLocalRecognizer(context: Context): Boolean = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        private fun isKorean(tag: String) = Locale.forLanguageTag(tag.replace('_', '-')).language == "ko"
    }
}

private open class EmptyRecognitionListener : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onError(error: Int) = Unit
    override fun onResults(results: Bundle?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
