package com.minyook.sllm2.data

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import kotlin.math.sqrt

/** One short CPU session per question, closed before optional Gemma generation.
 * Weights ship as pinned assets and no network access exists in this class. */
class SemanticEncoder(private val context: Context) {
    fun encode(question: String): FloatArray {
        val ids = SemanticTokenizer(context.assets.open("semantic-model/tokenizer.json").bufferedReader().use { it.readText() }).encode("task: search result | query: $question")
        val directory = File(context.noBackupFilesDir,"semantic-embeddinggemma-5090578d").apply{mkdirs()}
        for((name,size) in listOf("model_q4.onnx" to 519322L,"model_q4.onnx_data" to 196725760L)){
            val file=File(directory,name)
            if(!file.exists() || file.length()!=size){
                val temporary=File(directory,name+".part")
                context.assets.open("semantic-model/onnx/$name").use{input->temporary.outputStream().use{input.copyTo(it)}}
                check(temporary.renameTo(file))
            }
        }
        val env = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
            options.setMemoryPatternOptimization(false)
            env.createSession(File(directory,"model_q4.onnx").absolutePath, options).use { session ->
                OnnxTensor.createTensor(env, arrayOf(ids)).use { input ->
                    OnnxTensor.createTensor(env, arrayOf(LongArray(ids.size){1L})).use { mask ->
                        session.run(mapOf("input_ids" to input,"attention_mask" to mask)).use { output ->
                            @Suppress("UNCHECKED_CAST")
                            val vector = (output.get("sentence_embedding").get().value as Array<FloatArray>)[0].copyOf()
                            val norm = sqrt(vector.sumOf { it.toDouble()*it }).toFloat()
                            require(norm > 0 && vector.size == 768 && vector.all { it.isFinite() })
                            return vector.map { it/norm }.toFloatArray()
                        }
                    }
                }
            }
        }
    }
}
