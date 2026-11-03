package com.minyook.sllm2.data

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import kotlin.math.sqrt

/** Reuse the query-only CPU session between document questions. The caller
 * releases it before Gemma generation/backgrounding. Tensors never outlive a query. */
class SemanticEncoder(private val context: Context) {
    private val tokenizer by lazy { SemanticTokenizer(context.assets.open("semantic-model/tokenizer.json").bufferedReader().use { it.readText() }) }
    private val vectors=object:LinkedHashMap<String,FloatArray>(16,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,FloatArray>?)=size>16}
    private var session:OrtSession?=null
    @Synchronized fun cachedVector(question:String):FloatArray?=vectors[question]?.copyOf()
    @Synchronized fun release(){session?.close();session=null}
    @Synchronized
    fun encode(question: String): FloatArray {
        cachedVector(question)?.let{return it}
        val ids = tokenizer.encode("task: search result | query: $question")
        val env=OrtEnvironment.getEnvironment()
        val active=session?:openSession(env).also{session=it}
        try {
            OnnxTensor.createTensor(env,arrayOf(ids)).use{input->
                OnnxTensor.createTensor(env,arrayOf(LongArray(ids.size){1L})).use{mask->
                    active.run(mapOf("input_ids" to input,"attention_mask" to mask)).use{output->
                        @Suppress("UNCHECKED_CAST")
                        val vector=(output.get("sentence_embedding").get().value as Array<FloatArray>)[0].copyOf()
                        val norm=sqrt(vector.sumOf{it.toDouble()*it}).toFloat()
                        require(norm>0&&vector.size==768&&vector.all{it.isFinite()})
                        return vector.map{it/norm}.toFloatArray().also{vectors[question]=it.copyOf()}
                    }
                }
            }
        }catch(error:Exception){release();throw error}
    }
    private fun openSession(env:OrtEnvironment):OrtSession {
        val directory = File(context.noBackupFilesDir,"semantic-embeddinggemma-5090578d").apply{mkdirs()}
        for((name,size) in listOf("model_q4.onnx" to 519322L,"model_q4.onnx_data" to 196725760L)){
            val file=File(directory,name)
            if(!file.exists() || file.length()!=size){
                val temporary=File(directory,name+".part")
                context.assets.open("semantic-model/onnx/$name").use{input->temporary.outputStream().use{input.copyTo(it)}}
                check(temporary.renameTo(file))
            }
        }
        return OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
            options.setMemoryPatternOptimization(false)
            env.createSession(File(directory,"model_q4.onnx").absolutePath, options)
        }
    }
}
