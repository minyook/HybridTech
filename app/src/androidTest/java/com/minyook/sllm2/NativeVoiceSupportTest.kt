package com.minyook.sllm2

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Capability-only: never grants RECORD_AUDIO, starts recognition, speaks, or downloads. */
@RunWith(AndroidJUnit4::class)
class NativeVoiceSupportTest {
    @Test fun reportInstalledLocalKoreanSupportWithoutRecording() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val details = StringBuffer("model=${Build.MODEL}\napi=${Build.VERSION.SDK_INT}\n")
        val recognition = AtomicReference<SpeechRecognizer?>(); val recognitionDone = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val available = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            details.append("onDeviceRecognition=$available\n")
            if (available && Build.VERSION.SDK_INT >= 33) {
                val service = SpeechRecognizer.createOnDeviceSpeechRecognizer(context); recognition.set(service)
                service.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) = Unit
                    override fun onResults(results: Bundle?) = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                service.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        details.append("installed=${support.installedOnDeviceLanguages}\npending=${support.pendingOnDeviceLanguages}\nsupported=${support.supportedOnDeviceLanguages}\n")
                        recognitionDone.countDown()
                    }
                    override fun onError(error: Int) { details.append("supportQueryError=$error\n"); recognitionDone.countDown() }
                })
            } else recognitionDone.countDown()
        }
        if (!recognitionDone.await(15, TimeUnit.SECONDS)) details.append("supportQueryTimeout=true\n")
        val synthesis = AtomicReference<TextToSpeech?>(); val synthesisDone = CountDownLatch(1)
        instrumentation.runOnMainSync {
            recognition.get()?.destroy()
            synthesis.set(TextToSpeech(context) { status ->
                val voices = synthesis.get()?.voices.orEmpty().filter { it.locale.language == "ko" && !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
                details.append("ttsInit=$status\ninstalledLocalKoreanVoices=${voices.map { it.name }}\n")
                synthesisDone.countDown()
            })
        }
        if (!synthesisDone.await(15, TimeUnit.SECONDS)) details.append("ttsQueryTimeout=true\n")
        instrumentation.runOnMainSync { synthesis.get()?.shutdown() }
        File(context.getExternalFilesDir(null), "voice-checks").apply { mkdirs() }.resolve("local-support.txt").writeText(details.toString())
        assertTrue("See voice-checks/local-support.txt", details.contains("onDeviceRecognition="))
    }
}
