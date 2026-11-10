/* 소스 안내
 * 검증된 본문의 프레임 표시·완료·정정·이모지/표을 검증하는 JVM 회귀 검사다.
 * 검사 예: firstGeneratedSentenceReplacesTheSearchPlaceholderWithoutArrivingInOneChunk, validatedChunksAppearInSeveralSmallStepsAndEndWithoutExtraWaiting, documentAnswersAndCorrectedOrRejectedContentReplaceImmediately
 * 실행: :app:testDebugUnitTest. 외부 자산/평가/스트레스의 선택 조건은 본문의 환경 변수/Assume을 확인한다.
 */
package com.minyook.sllm2.ui

import org.junit.Assert.*
import org.junit.Test

class StreamingTextRevealTest {
    @Test fun firstGeneratedSentenceReplacesTheSearchPlaceholderWithoutArrivingInOneChunk() {
        val state=StreamingTextReveal("문서를 확인하고 있어요.")
        val answer=com.minyook.sllm2.data.GeneralKnowledgeAnswer.NOTICE+"\n\n질문에 필요한 조건을 먼저 설명합니다."
        state.begin(answer);state.update(answer,true,true,0);state.frame(16)
        assertTrue(state.visible.startsWith(com.minyook.sllm2.data.GeneralKnowledgeAnswer.NOTICE))
        assertFalse(state.visible.contains("문서를 확인"))
        assertTrue(state.visible.length<answer.length)
    }
    @Test fun validatedChunksAppearInSeveralSmallStepsAndEndWithoutExtraWaiting() {
        val state=StreamingTextReveal("")
        val text="작업 조건과 장비 배치를 함께 확인하세요. ".repeat(8)
        state.update(text,true,true,0)
        val lengths=mutableListOf<Int>()
        for(time in 16L..160L step 16L){state.frame(time);lengths+=state.visible.length}
        assertTrue(lengths.distinct().size>=5)
        assertTrue(state.visible.length in 1 until text.length)
        assertTrue(text.startsWith(state.visible))
        state.update(text,false,true,160)
        for(time in 176L..336L step 16L)state.frame(time)
        assertEquals(text,state.visible)
        assertFalse(state.running)
    }
    @Test fun documentAnswersAndCorrectedOrRejectedContentReplaceImmediately() {
        val state=StreamingTextReveal("")
        state.update("원문 수치와 조건입니다.",true,false,0)
        assertEquals("원문 수치와 조건입니다.",state.visible)
        state.update("새로 검증한 다른 설명입니다.",true,true,16)
        assertEquals("새로 검증한 다른 설명입니다.",state.visible)
        state.update("",false,true,32)
        assertEquals("",state.visible)
    }
    @Test fun restoredHistoryIsNotReplayedAndNewTargetsDoNotRestartVisibleText() {
        val state=StreamingTextReveal("이미 읽은 문장입니다.")
        state.update("이미 읽은 문장입니다. 새 조건도 확인하세요.",true,true,0)
        state.frame(32)
        val previous=state.visible
        state.update("이미 읽은 문장입니다. 새 조건도 확인하세요. 마지막 조건입니다.",true,true,48)
        state.frame(64)
        assertTrue(state.visible.startsWith(previous))
    }
    @Test fun emojiPairsAndTablesAreNeverPublishedHalfway() {
        val emoji=StreamingTextReveal("")
        emoji.update("👷 작업 준비를 확인하세요.",true,true,0)
        emoji.frame(16)
        assertFalse(Character.isHighSurrogate(emoji.visible.last()))
        val table="| 항목 | 조건 |\n| --- | --- |\n| 장비 | 확인 |\n\n다음 설명입니다."
        val state=StreamingTextReveal("")
        state.update(table,true,true,0);state.frame(16)
        assertTrue(state.visible.contains("| 장비 | 확인 |"))
        assertTrue(table.startsWith(state.visible))
    }
}
