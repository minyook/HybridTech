/* 소스 안내
 * 질문만 ONNX CPU로 인코딩해 768차원 정규화 벡터를 반환한다. 세션/최근 질의를 재사용하고 생성 전 호출자가 해제한다.
 */
package com.minyook.sllm2.data

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import kotlin.math.sqrt

/**
 * 검색용 질문을 EmbeddingGemma ONNX CPU 세션으로 인코딩해 정규화된 768차원 벡터를 반환한다.
 * 문서 벡터는 번들 인덱스에 있으므로 휴대폰에서 다시 만들지 않는다. 질문별 임시 tensor/result는 use로 닫는다.
 * 최근 16개 질문 벡터와 세션을 재사용하며, ViewModel이 Gemma 생성 전 또는 화면 이탈 시 release를 호출한다.
 * 생성 모델(LocalModelRuntime)과 독립적인 자원이며 인코딩 실패는 Coordinator가 검색 오류로 처리한다.
 */
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
    /** ONNX 외부 데이터 파일을 APK 자산에서 noBackupFilesDir로 복사해 경로 기반 CPU 세션을 연다. */
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
