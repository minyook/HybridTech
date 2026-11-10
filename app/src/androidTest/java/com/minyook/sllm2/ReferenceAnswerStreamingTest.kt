/* 소스 안내
 * 실제 E2B 직접 생성의 첫 공개 본문·완료 지표와 임시 채팅 표시을 검증하는 Android 계측 검사다.
 * 검사 예: renderReferenceSizedNativeAnswers, publish
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
package com.minyook.sllm2

import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.minyook.sllm2.data.GeneralKnowledgeAnswer
import com.minyook.sllm2.model.*
import com.minyook.sllm2.ui.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Real native generation in the production chat UI, with a temporary history-free fixture. */
class ReferenceAnswerStreamingTest {
    @OptIn(ExperimentalApi::class)
    @Test fun renderReferenceSizedNativeAnswers() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runReferenceStream")=="true")
        val presentation=args.getString("presentation")?:"smooth"
        require(presentation in setOf("smooth","off"))
        val showUi=presentation=="smooth"
        val context=instrumentation.targetContext
        val original=InferenceSettingsPreferences(context).load()
        val benchmark=ExperimentalFlags.enableBenchmark
        ExperimentalFlags.enableBenchmark=true
        val settings=InferenceSettings(InferenceBackend.GPU,4096,1024,automaticAnswerLength=false,model=LocalModel.GEMMA4_E2B)
        val runtime=LocalModelRuntime(context){settings}
        val rows=JSONArray()
        val report=JSONObject().put("scope","Real S24 E2B GPU generation with optional production WorkspaceChat presentation in a temporary fixture. No RAG, user chat writes or saved-setting changes. Questions explicitly request a small field checklist; this is not a long-answer or factual-accuracy certification. Screen recording may add overhead. UI frame timestamps are not separately benchmarked.")
            .put("presentation",presentation).put("maxOutputTokens",1024).put("contextTokens",4096).put("phase","warming").put("rows",rows)
        val output=File(context.getExternalFilesDir(null),"generation-checks/reference-field-$presentation.json")
        val main=Handler(Looper.getMainLooper())
        fun publish(){output.parentFile?.mkdirs();output.writeText(report.toString(2))}
        var scenario:ActivityScenario<WorkspacePreviewActivity>?=null
        try {
            publish()
            val warmStarted=SystemClock.elapsedRealtime()
            runtime.generate("산소가 호흡에 필요한 이유를 세 문장으로 설명해줘.","",generalKnowledge=true)
            report.put("warmupMs",SystemClock.elapsedRealtime()-warmStarted);publish()
            val ui=mutableStateOf(FieldGuardUiState(activeChatId="reference-demo",messages=emptyList(),settings=settings,modelStatus=ModelStatus(ModelPhase.READY,2588147712)))
            val actions=FieldGuardActions(onNavigate={},onNewChat={},onLoadChat={},onInputChange={},onSubmit={},onPreset={},onDownloadModel={},onBackend={},onContextTokens={},onResponseTokens={},onSaveSettings={},onStartAp={},onSelectMeter={},onStopAp={},onVoiceClick={},onVoiceLongClick={},onOpenSource={})
            if(showUi) {
                scenario=ActivityScenario.launch(WorkspacePreviewActivity::class.java)
                scenario.onActivity { activity -> activity.setContent { MaterialTheme { WorkspaceChat(ui.value,VoiceUiState(available=false),actions,false) } } }
            }
            instrumentation.sendStatus(1,Bundle().apply{putString("referenceProgress","READY")})
            Thread.sleep(1500)
            val questions=listOf(
                "밀폐공간 작업을 중단했다가 다시 시작할 때 감독관이 확인할 사항을 핵심 5개로 정리해줘. 각 항목은 한 문장으로 설명해줘.",
                "밀폐공간 인명구조용 삼각대 준비 수량을 정할 때 확인할 조건을 핵심 4개로 설명해줘. 임의의 대수는 정하지 말고, 각 항목은 한 문장으로 정리해줘.",
            )
            for((index,question) in questions.withIndex()) {
                val id="reference-$index"
                instrumentation.runOnMainSync {
                    ui.value=ui.value.copy(messages=ui.value.messages+ChatMessageUi(id="q-$index",text=question,fromWorker=true)+ChatMessageUi(id=id,text="",fromWorker=false,pending=true),isAnswering=true)
                }
                val preview=GeneralKnowledgeAnswer.StreamingPreview(question)
                val chunks=JSONArray()
                var firstAccepted:Long?=null
                var metrics:GenerationMetrics?=null
                var limited=false
                val started=SystemClock.elapsedRealtime()
                report.put("phase",id);publish()
                instrumentation.sendStatus(1,Bundle().apply{putString("referenceProgress",question)})
                val raw=runtime.generate(question,"",generalKnowledge=true,onMetrics={metrics=it},onOutputLimit={limited=it},onPartialAnswer={partial ->
                    val accepted=preview.next(partial)
                    if(accepted!=null){
                        val elapsed=SystemClock.elapsedRealtime()-started
                        if(firstAccepted==null)firstAccepted=elapsed
                        chunks.put(JSONObject().put("ms",elapsed).put("validatedCharacters",accepted.length))
                        if(showUi)main.post {ui.value=ui.value.copy(messages=ui.value.messages.map{if(it.id==id)it.copy(text=accepted,animateAnswer=true)else it})}
                    }
                })
                val total=SystemClock.elapsedRealtime()-started
                val final=GeneralKnowledgeAnswer.finish(raw,question,limited)
                instrumentation.runOnMainSync {ui.value=ui.value.copy(messages=ui.value.messages.map{if(it.id==id)it.copy(text=final,pending=false)else it},isAnswering=false)}
                rows.put(JSONObject().put("question",question).put("totalMs",total).put("firstAcceptedBodyMs",firstAccepted?:JSONObject.NULL)
                    .put("firstNativeTextMs",metrics?.firstChunkMs).put("initializationMs",metrics?.initializationMs)
                    .put("decodeTokens",metrics?.decodeTokens).put("tokensPerSecond",metrics?.decodeTokensPerSecond)
                    .put("outputChars",raw.length).put("rawAnswer",raw).put("displayAnswer",final)
                    .put("outputLimitReached",limited).put("validatedChunkArrivals",chunks))
                publish();Thread.sleep(2500)
            }
            report.put("phase","complete")
            assertEquals(original,InferenceSettingsPreferences(context).load())
            instrumentation.sendStatus(1,Bundle().apply{putString("referenceProgress","FINISHED")})
            Thread.sleep(3500)
        } finally {
            runtime.releaseAndWait();scenario?.close();ExperimentalFlags.enableBenchmark=benchmark
            report.put("settingsUnchanged",original==InferenceSettingsPreferences(context).load());publish()
        }
    }
}
