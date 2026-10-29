package com.minyook.sllm2.data

import org.json.JSONObject

/** Exact BPE for the pinned EmbeddingGemma tokenizer. No Korean stemming or
 * approximation is used here. Parity is checked against Transformers.js. */
internal class SemanticTokenizer(json: String) {
    private val root = JSONObject(json)
    private val model = root.getJSONObject("model")
    private val vocab = model.getJSONObject("vocab").let { data -> data.keys().asSequence().associateWith { data.getInt(it) } }
    private val ranks = model.getJSONArray("merges").let { data -> (0 until data.length()).associate { i -> val pair = data.getJSONArray(i); (pair.getString(0) + "\u0000" + pair.getString(1)) to i } }
    private val specials = root.getJSONArray("added_tokens").let { rows -> (0 until rows.length()).associate { val row=rows.getJSONObject(it);row.getString("content") to row.getInt("id") } }
    private val specialPattern = Regex(specials.keys.sortedByDescending{it.length}.joinToString("|"){Regex.escape(it)})
    fun encode(text: String): LongArray {
        require(text.length <= 2000) { "질문이 너무 길어요. 확인할 조건별로 나누어 주세요." }
        val ids = mutableListOf(2L)
        fun ordinary(text:String) {
            val word = text.replace(' ', '▁')
            val pieces = word.codePoints().toArray().map { String(Character.toChars(it)) }.toMutableList()
            while(pieces.size > 1) {
                var best = -1; var rank = Int.MAX_VALUE
                for(i in 0 until pieces.lastIndex) { val candidate = ranks[pieces[i] + "\u0000" + pieces[i+1]] ?: continue; if(candidate < rank) { best=i; rank=candidate } }
                if(best < 0) break
                pieces[best] = pieces[best] + pieces[best+1]; pieces.removeAt(best+1)
            }
            pieces.forEach { piece ->
                val id=vocab[piece]
                if(id!=null)ids.add(id.toLong())
                else piece.toByteArray(Charsets.UTF_8).forEach{byte->ids.add(requireNotNull(vocab["<0x%02X>".format(byte.toInt() and 255)]).toLong())}
            }
        }
        var offset=0
        for(match in specialPattern.findAll(text)) {ordinary(text.substring(offset,match.range.first));ids.add(specials.getValue(match.value).toLong());offset=match.range.last+1}
        ordinary(text.substring(offset));ids.add(1L)
        require(ids.size <= 256) { "질문이 길어 의미 검색을 생략했어요. 확인할 내용을 나누어 주세요." }
        return ids.toLongArray()
    }
}
