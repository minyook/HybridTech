import { expect, it } from 'vitest';
import { cleanLightSummary, visibleLightAnswer } from '../src/ai/light-output';
it('부분 템플릿과 빈 thinking 표시를 숨기고 Markdown 답변을 보존한다',()=>{
  for(const partial of ['<','<thi','<think>\n'])expect(visibleLightAnswer(partial)).toBe('');
  expect(visibleLightAnswer('<think>\n\n</think>\n\n### 절차\n\n1. 점검')).toBe('### 절차\n\n1. 점검');
  expect(visibleLightAnswer('**안전 문서**')).toBe('**안전 문서**');
});
it('thinking이 생성돼도 본문만 노출한다',()=>{
  expect(visibleLightAnswer('<think>internal prelude</think>\n본문')).toBe('본문');
});
it('같은 첫 단계로 다시 시작하는 반복 목록과 남은 제목을 표시하지 않는다',()=>{
  const first='1. 사전준비: 가스농도 측정기에 이상이 없는지 확인하십시오.\n2. 표준가스로 확인합니다.\n3. 수직 및 수평 방향으로 측정합니다.';
  expect(cleanLightSummary(`${first}\n\n질문의 절차:\n1. 사전준비: 가스농도 측정기에 이상이 없는지 확인\n2. 표준가스로 확인합니다.`)).toBe(first);
});
it('주제가 다른 정상적인 두 목록은 그대로 두고 길이 종료의 미완성 문장만 제외한다',()=>{
  const answer='1. 가스농도 측정기에 이상이 없는지 확인합니다.\n2. 여러 지점을 측정합니다.\n3. 보호구를 착용합니다.\n\n주의사항:\n1. 밀폐공간 외부에 감시인을 배치합니다.';
  expect(cleanLightSummary(answer)).toBe(answer);
  expect(cleanLightSummary(answer+'\n2. 다음 단계는 미완',true)).toBe(answer);
});
