package com.minyook.sllm2.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReviewedRetrievalEvaluationTest {
    private val root=if(File("src/main/assets").exists())File(".") else File("app")
    @Test fun recordReviewedFrozenVectorsAndPreparedIndexLatency() {
        val json=File(root,"src/main/assets/knowledge/semantic-index-v1.json").readText()
        val seed=JSONObject(File(root,"src/main/assets/knowledge/seed_chunks_v1.json").readText()).getJSONArray("chunks")
        val chunks=(0 until seed.length()).map{i->val c=seed.getJSONObject(i);KnowledgeChunk(id=i+1L,documentId=c.getString("documentId"),documentTitle=c.getString("documentTitle"),sourceKind=c.getString("sourceKind"),pageNumber=c.getInt("pageNumber"),chunkIndex=c.getInt("chunkIndex"),body=c.getString("body"))}
        val started=System.nanoTime();val index=HybridRetrieval(chunks,json);val prepareMs=(System.nanoTime()-started)/1e6
        val vectors=JSONArray(File(root,"src/test/resources/semantic-deep-vectors.json").readText())
        val rows=JSONArray()
        for(i in 0 until vectors.length()){
            val row=vectors.getJSONObject(i);val q=row.getString("question");val v=row.getJSONArray("vector");val vector=FloatArray(v.length()){v.getDouble(it).toFloat()}
            val before=index.resolve(q,vector);val after=index.resolve(q,vector,review=true)
            assertTrue(q,after.sources.all{s->chunks.any{it.id==s.chunk.id&&it.body==s.chunk.body}})
            if(after.generalQuestion!=null)assertTrue(q,after.searchComplete)
            rows.put(JSONObject().put("question",q).put("beforeStatus",before.status).put("beforePages",JSONArray(before.sources.map{it.chunk.pageNumber})).put("reviewedStatus",after.status).put("reviewedPages",JSONArray(after.sources.map{it.chunk.pageNumber})).put("complete",after.searchComplete).put("method",after.method))
        }
        val questions=(0 until minOf(8,vectors.length())).map{vectors.getJSONObject(it).getString("question")}
        val cold=questions.map{q->val t=System.nanoTime();HybridRetrieval(chunks,json).resolve(q,null);(System.nanoTime()-t)/1e6}
        val warm=questions.map{q->val t=System.nanoTime();index.resolve(q,null);(System.nanoTime()-t)/1e6}
        fun median(values:List<Double>)=values.sorted().let{it[it.size/2]}
        val report=JSONObject().put("scope","PC JVM retrieval/index work only; frozen query vectors, no on-device encoder or Gemma inference; not full answer latency or an independent quality score")
            .put("prepareMs",prepareMs).put("rebuildEachQuestionMs",JSONArray(cold)).put("reusePreparedIndexMs",JSONArray(warm)).put("rebuildMedianMs",median(cold)).put("reuseMedianMs",median(warm)).put("cases",rows)
        File(root,"build/reports/reviewed-rag").apply{mkdirs()}.resolve("evaluation.json").writeText(report.toString(2))
    }
}
