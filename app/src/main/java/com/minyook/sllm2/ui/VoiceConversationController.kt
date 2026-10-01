package com.minyook.sllm2.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
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
)

/**
 * Keeps framework-only speech APIs outside Compose.  It is intentionally
 * foreground-only: the explicit "오투야" listener is stopped with the activity.
 */
class VoiceConversationController(
    private val context: Context,
    private val hasMicrophonePermission: () -> Boolean,
    private val requestMicrophonePermission: () -> Unit,
    private val launchSystemRecognizer: (Intent) -> Unit,
    private val onQuestion: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val _state = MutableStateFlow(VoiceUiState(available = SpeechRecognizer.isRecognitionAvailable(context)))
    val state: StateFlow<VoiceUiState> = _state

    private var recognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var suppressNextError = false
    private var fallbackInFlight = false
    private var pendingWakeWordPermission = false
    private var inForeground = true

    init {
        if (_state.value.available) configureRecognizer()
        configureTts()
    }

    fun startQuestion() {
        if (!_state.value.available) {
            onError("이 기기에서는 음성 인식을 사용할 수 없습니다.")
            return
        }
        if (!hasMicrophonePermission()) {
            pendingWakeWordPermission = false
            requestMicrophonePermission()
            return
        }
        _state.value = _state.value.copy(wakeWordEnabled = false)
        fallbackInFlight = false
        startListening(wakeWordOnly = false)
    }

    fun toggleWakeWord() {
        if (!_state.value.available) {
            onError("이 기기에서는 음성 인식을 사용할 수 없습니다.")
            return
        }
        val enable = !_state.value.wakeWordEnabled
        if (!enable) {
            stopListening()
            _state.value = _state.value.copy(wakeWordEnabled = false, awaitingWakeWord = false)
            return
        }
        if (!hasMicrophonePermission()) {
            pendingWakeWordPermission = true
            requestMicrophonePermission()
            return
        }
        _state.value = _state.value.copy(wakeWordEnabled = true)
        startListening(wakeWordOnly = true)
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        if (!granted) {
            pendingWakeWordPermission = false
            onError("음성 질문에는 마이크 권한이 필요합니다.")
            return
        }
        if (pendingWakeWordPermission) {
            pendingWakeWordPermission = false
            _state.value = _state.value.copy(wakeWordEnabled = true)
            startListening(wakeWordOnly = true)
        } else {
            startQuestion()
        }
    }

    fun onSystemRecognizerResult(spoken: String) {
        fallbackInFlight = false
        if (spoken.isBlank()) onError("음성 입력이 취소되었거나 인식되지 않았습니다.") else consumeResult(spoken)
    }

    fun onResume(chatVisible: Boolean) {
        inForeground = true
        if (chatVisible && _state.value.wakeWordEnabled && hasMicrophonePermission()) {
            startListening(wakeWordOnly = true)
        }
    }

    fun onPause() {
        inForeground = false
        stopListening()
    }

    fun destroy() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    fun speak(markdown: String) {
        if (!ttsReady) return
        val plain = markdown
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            .replace("**", "")
            .replace("`", "")
            .replace(Regex("(?m)^>\\s*출처:.*$"), "")
            .replace(Regex("\\n{2,}"), ". ")
            .trim()
            .take(3_500)
        if (plain.isNotBlank()) textToSpeech?.speak(plain, TextToSpeech.QUEUE_FLUSH, null, ANSWER_UTTERANCE)
    }

    private fun configureRecognizer() {
        recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    _state.value = _state.value.copy(listening = false)
                    consumeResult(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }

                override fun onError(error: Int) {
                    _state.value = _state.value.copy(listening = false)
                    if (!inForeground) return
                    if (suppressNextError) {
                        suppressNextError = false
                        return
                    }
                    if (_state.value.wakeWordEnabled && _state.value.awaitingWakeWord && inForeground) {
                        // The platform recognizer ends after a short silence. Re-arm only while visible.
                        android.os.Handler(context.mainLooper).postDelayed({
                            if (inForeground && _state.value.wakeWordEnabled && hasMicrophonePermission()) startListening(wakeWordOnly = true)
                        }, RETRY_DELAY_MS)
                    } else {
                        onError(errorMessage(error))
                        launchFallback()
                    }
                }
            })
        }
    }

    private fun configureTts() {
        textToSpeech = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.language = Locale.KOREAN
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onError(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = Unit
                })
            }
        }
    }

    private fun startListening(wakeWordOnly: Boolean) {
        if (_state.value.listening || !hasMicrophonePermission()) return
        val activeRecognizer = recognizer ?: return
        _state.value = _state.value.copy(listening = true, awaitingWakeWord = wakeWordOnly)
        val intent = recognizerIntent(wakeWordOnly)
        runCatching { activeRecognizer.startListening(intent) }
            .onFailure {
                _state.value = _state.value.copy(listening = false)
                onError("음성 인식을 시작하지 못했습니다. 다시 눌러 주세요.")
                launchFallback()
            }
    }

    private fun stopListening() {
        if (_state.value.listening) {
            suppressNextError = true
            runCatching { recognizer?.cancel() }
        }
        _state.value = _state.value.copy(listening = false, awaitingWakeWord = false)
    }

    private fun consumeResult(spoken: String) {
        val clean = spoken.trim()
        if (clean.isBlank()) {
            if (_state.value.wakeWordEnabled) startListening(wakeWordOnly = true) else onError("음성을 인식하지 못했습니다. 다시 말씀해 주세요.")
            return
        }
        if (_state.value.awaitingWakeWord) {
            if (WAKE_WORD.containsMatchIn(clean)) {
                _state.value = _state.value.copy(wakeWordEnabled = false, awaitingWakeWord = false)
                startListening(wakeWordOnly = false)
            } else if (_state.value.wakeWordEnabled) {
                startListening(wakeWordOnly = true)
            }
        } else {
            _state.value = _state.value.copy(awaitingWakeWord = false)
            onQuestion(clean)
        }
    }

    private fun launchFallback() {
        if (fallbackInFlight) return
        fallbackInFlight = true
        runCatching { launchSystemRecognizer(recognizerIntent(wakeWordOnly = false)) }
            .onFailure {
                fallbackInFlight = false
                onError("시스템 음성 인식기를 열 수 없습니다.")
            }
    }

    private fun recognizerIntent(wakeWordOnly: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PROMPT, if (wakeWordOnly) "오투야" else "질문을 말씀하세요")
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "마이크 입력을 확인해 주세요."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "음성 인식 연결이 없습니다. 한국어 오프라인 음성팩을 설치하거나 네트워크를 확인해 주세요."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성을 인식하지 못했습니다. 다시 말씀해 주세요."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기가 준비 중입니다. 잠시 후 다시 눌러 주세요."
        else -> "음성 인식에 실패했습니다. 다시 눌러 주세요."
    }

    private companion object {
        const val RETRY_DELAY_MS = 550L
        const val ANSWER_UTTERANCE = "voice_answer"
        val WAKE_WORD = Regex("(?i)(오\\s*투\\s*야|o\\s*2\\s*야)")
    }
}
