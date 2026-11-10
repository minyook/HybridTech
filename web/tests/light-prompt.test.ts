/* 소스 안내
 * 웹 회귀 검사: 전체 문장이 들어가는 근거만 발췌하고 질문과 쪽수를 유지한다 / 문맥에 근거가 하나도 들어가지 않거나 질문이 너무 길면 GPU 작업 전에 중단한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다.
 * 검사 예: 전체 문장이 들어가는 근거만 발췌하고 질문과 쪽수를 유지한다, 문맥에 근거가 하나도 들어가지 않거나 질문이 너무 길면 GPU 작업 전에 중단한다
 * 실행: web/에서 npm test. 실제 네트워크/모델 검증과 mock·순수 검사의 범위는 각 fixture를 확인한다.
 */
import { expect, it } from 'vitest';
import { boundedLightPrompt } from '../src/ai/light-prompt';
import type { Source } from '../src/types';
const source=(body:string,pageNumber=3)=>({score:1,chunk:{id:'x',documentId:'test',documentTitle:'안전 문서',sourceKind:'guide',heading:'',chunkIndex:0,pageNumber,body}} as Source);
it('전체 문장이 들어가는 근거만 발췌하고 질문과 쪽수를 유지한다',()=>{
  const result=boundedLightPrompt('산소 기준',[source('산소는 18% 이상 23.5% 미만입니다.\n'+'긴 문장'.repeat(100))],text=>text.length,110);
  expect(result).toContain('18% 이상 23.5% 미만');expect(result).toContain('3쪽');expect(result).toContain('[질문]\n산소 기준');expect(result.length).toBeLessThanOrEqual(110);
});
it('문맥에 근거가 하나도 들어가지 않거나 질문이 너무 길면 GPU 작업 전에 중단한다',()=>{
  expect(()=>boundedLightPrompt('산소',[source('긴 문장'.repeat(100))],text=>text.length,100)).toThrow('문서 근거');
  expect(()=>boundedLightPrompt('질문'.repeat(100),[],text=>text.length,100)).toThrow('질문이');
});
