package com.minyook.sllm2.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
internal fun WorkRequestVoice(autoStart: Boolean, consumeAutoStart: () -> Unit, onTranscript: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val publish by rememberUpdatedState(onTranscript)
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun stop() {
        val current = recognizer
        recognizer = null
        current?.destroy()
    }

    fun listen() {
        if (Build.VERSION.SDK_INT < 31 || !VoiceConversationController.hasLocalRecognizer(context)) {
            message = "이 기기에서는 로컬 음성 인식을 사용할 수 없어요. 아래에 직접 입력해 주세요."
            return
        }
        if (recognizer != null) return
        message = null
        runCatching {
            val candidate = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = candidate
            candidate.setRecognitionListener(object : EmptyRecognitionListener() {
                override fun onResults(results: Bundle?) {
                    if (recognizer !== candidate) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    stop()
                    if (text.isNullOrBlank()) message = "인식된 내용이 없어요. 다시 말하거나 직접 입력해 주세요."
                    else publish(text)
                }

                override fun onError(error: Int) {
                    if (recognizer !== candidate) return
                    stop()
                    message = when (error) {
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "로컬 한국어 음성 데이터가 필요해요. 직접 입력하거나 음성 설정에서 한국어를 준비해 주세요."
                        else -> "작업 내용을 인식하지 못했어요. 다시 말하거나 직접 입력해 주세요."
                    }
                }
            })
            candidate.startListening(koreanRecognizerIntent("현장, 위치, 작업 내용을 말씀해 주세요"))
        }.onFailure {
            stop()
            message = "마이크를 시작하지 못했어요. 아래에 직접 입력해 주세요."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else message = "마이크 권한이 필요해요. 아래에 직접 입력할 수도 있어요."
    }
    fun start() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(autoStart) {
        if (autoStart) {
            consumeAutoStart()
            start()
        }
    }
    LaunchedEffect(recognizer) {
        if (recognizer != null) {
            delay(25_000)
            stop()
            message = "음성 입력 시간이 지났어요. 다시 말하거나 직접 입력해 주세요."
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
    WorkspaceButton(
        if (recognizer == null) "작업 내용 말하기" else "듣는 중 · 음성 입력 취소",
        { if (recognizer == null) start() else stop() },
        Modifier.fillMaxWidth().testTag("workRequestMic"),
        icon = WorkspaceSymbol.MIC,
    )
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

internal data class SpokenWorkDraft(val site: String?, val location: String?, val task: String)

internal fun parseSpokenWorkDraft(transcript: String): SpokenWorkDraft {
    val labels = Regex("(?:^|[\\s,，])(?:현장|위치|작업\\s*내용)(?:은|는|[:：])?\\s+")
    val matches = labels.findAll(transcript).toList()
    val values = matches.mapIndexed { index, match ->
        val key = match.value.trim().trimStart(',', '，')
        key to transcript.substring(match.range.last + 1, matches.getOrNull(index + 1)?.range?.first ?: transcript.length)
            .trim(' ', ',', '，').takeIf(String::isNotBlank)
    }
    return SpokenWorkDraft(
        site = values.firstOrNull { it.first.startsWith("현장") }?.second,
        location = values.firstOrNull { it.first.startsWith("위치") }?.second,
        task = values.firstOrNull { it.first.startsWith("작업") }?.second ?: transcript.trim(),
    )
}
