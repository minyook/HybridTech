package com.minyook.sllm2.data

import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class SemanticTokenizerTest {
    @Test fun matchesPinnedTransformersTokenizerOnKoreanNumbersNegationAndSpecialTokens() {
        val root=if(File("src/main/assets").exists())File(".") else File("app")
        val tokenizer=SemanticTokenizer(File(root,"src/main/assets/semantic-model/tokenizer.json").readText())
        val rows=JSONArray(File(root,"src/test/resources/semantic-tokenizer-parity.json").readText())
        for(i in 0 until rows.length()){
            val row=rows.getJSONObject(i);val ids=row.getJSONArray("ids")
            assertArrayEquals(row.getString("text"),LongArray(ids.length()){ids.getLong(it)},tokenizer.encode(row.getString("text")))
        }
    }
    @Test fun longQueriesAreRejectedInsteadOfDroppingConditions() {
        val root=if(File("src/main/assets").exists())File(".") else File("app")
        val tokenizer=SemanticTokenizer(File(root,"src/main/assets/semantic-model/tokenizer.json").readText())
        assertThrows(IllegalArgumentException::class.java){tokenizer.encode("조건 ".repeat(600))}
    }
}
