package com.minyook.sllm2

import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.data.DocumentAnswerPlan
import com.minyook.sllm2.data.DocumentGeneration
import com.minyook.sllm2.data.KnowledgeRepository
import com.minyook.sllm2.data.KnowledgeSeeder
import com.minyook.sllm2.data.SemanticEncoder
import com.minyook.sllm2.model.LocalModelRuntime
import com.minyook.sllm2.model.GenerationMetrics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RestartChecklistGenerationTest {
    @Test fun exaoneAnswersFromEvidenceRetrievedWithOnnx() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runDocumentGeneration") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val question = "밀폐공간 작업을 일시 중단했다가 다시 시작하기 전에 산소와 유해가스 농도를 측정해야 하나요?"
        KnowledgeSeeder(context).seedIfNeeded()
        val index = context.assets.open("knowledge/semantic-index-v1.json").bufferedReader().use { it.readText() }
        val encoder = SemanticEncoder(context)
        var usedOnnx = false
        val resolution = try {
            KnowledgeRepository().searchQuestion(question, index) { query ->
                usedOnnx = true
                encoder.encode(query)
            }
        } finally {
            encoder.release()
        }
        assertTrue("Expected ONNX query embedding", usedOnnx)
        assertEquals("Search method: ${resolution.method}", "answered", resolution.status)
        assertTrue("Retrieved pages: ${resolution.sources.map { it.chunk.pageNumber }}",
            resolution.sources.any { it.chunk.pageNumber == 24 })
        val plan = DocumentAnswerPlan.create(question, resolution)
        val runtime = LocalModelRuntime(context, maximizeOutput = true)
        try {
            var limited = false
            var metrics: GenerationMetrics? = null
            val raw = runtime.generate(question, plan.generationContext(), onOutputLimit = { limited = it }, onMetrics = { metrics = it })
            val stats = checkNotNull(metrics)
            assertEquals(stats.effectiveContextTokens - checkNotNull(stats.prefillTokens), stats.maxDecodeTokens)
            val answer = DocumentGeneration.accept(raw, plan, limited)
            assertNotNull("Generated document answer (limited=$limited):\n$raw", answer)
            assertTrue("Expected measurement timing:\n$raw",
                answer.orEmpty().contains("측정") && Regex("재개|다시\\s*시작").containsMatchIn(answer.orEmpty()))
        } finally {
            runtime.release()
        }
    }
}
