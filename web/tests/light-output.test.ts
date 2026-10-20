import { expect, it } from 'vitest';
import { visibleLightAnswer } from '../src/ai/light-output';
it('부분 템플릿과 빈 thinking 표시를 숨기고 Markdown 답변을 보존한다',()=>{
  for(const partial of ['<','<thi','<think>\n'])expect(visibleLightAnswer(partial)).toBe('');
  expect(visibleLightAnswer('<think>\n\n</think>\n\n### 절차\n\n1. 점검')).toBe('### 절차\n\n1. 점검');
  expect(visibleLightAnswer('**안전 문서**')).toBe('**안전 문서**');
});
it('thinking이 생성돼도 본문만 노출한다',()=>{
  expect(visibleLightAnswer('<think>internal prelude</think>\n본문')).toBe('본문');
});
