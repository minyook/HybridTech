import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import { acceptedSupplement, composeAnswer, createAnswerPlan, supplementPrompt } from '../src/rag/answer-plan';
import { retrieve } from '../src/rag/search';
import type { Chunk, Source } from '../src/types';
const raw=JSON.parse(readFileSync(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8'));
const chunks:Chunk[]=raw.chunks.map((chunk:Chunk)=>({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}`}));
const planFor=(question:string)=>createAnswerPlan(question,retrieve(question,chunks));
it('실제 가스 측정 본문은 한 번에 표시되고 모델 입력과 동일한 조건·출처를 보존한다',()=>{
  const plan=planFor('산소 및 유해가스 측정방법');
  expect(plan.supplementRequested).toBe(false);
  for(const text of ['1m','5m','방폭','감시인','공기호흡기','개구면'])expect(plan.body).toContain(text);
  for(const unit of plan.evidence){expect(unit.page).toBeGreaterThan(0);expect(supplementPrompt(plan)).toContain(unit.text);}
  expect(plan.body).not.toMatch(/AI 정리|문서에서 확인한 상세 내용/);
});
it('실제 CPR의 조건·행동·반복 종료 시점과 회복자세를 한 답변에 유지한다',()=>{
  const plan=planFor('심폐소생술 알려줘');
  for(const text of ['30회','100','120','모르거나 꺼리는 경우','도착할 때까지','호흡이 회복되었다면'])expect(plan.body).toContain(text);
  expect(plan.supplementRequested).toBe(false);
  expect(plan.evidence.some(unit=>unit.page===46)).toBe(true);
  const headings=['### 반응확인','### 119 신고','### 호흡 확인','### 심폐소생술 흉부 압박 (30회)','### 기도 개방','### 인공 호흡 (2회)','### 심폐소생술 흉부 압박 & 인공 호흡 반복','### 회복자세'];
  const positions=headings.map(heading=>plan.body.indexOf(heading));
  expect(positions.every(position=>position>=0)).toBe(true);
  expect(positions).toEqual([...positions].sort((a,b)=>a-b));
});
it('일반 문서 질문에 현장 작업 판정을 만들지 않고 실시간 승인 질문은 문서에서 답하지 않는다',()=>{
  const plan=planFor('산소 농도 기준');expect(plan).not.toHaveProperty('decision');
  expect(plan.body).not.toMatch(/작업 가능합니다|승인되었습니다/);
  expect(retrieve('우리 현장 현재 작업 승인됐어?',chunks)).toEqual([]);
});
it('도착 시점과 산소 상한의 비교 연산자를 보존한다',()=>{
  const plan=planFor('적정 공기 산소농도 기준');
  expect(plan.evidence.flatMap(unit=>unit.quantitativeFacts)).toContain('23.5% 미만');
  expect(plan.body).not.toContain('23.5% 이하');
  expect(plan.evidence.flatMap(unit=>unit.criteria)).toContainEqual({literal:'23.5% 미만',value:'23.5',unit:'%',operator:'미만'});
});
it('이전 PDF 페이지의 교육 신청 항목이 다음 페이지 본문의 제목이 되지 않는다',()=>{
  const plan=planFor('산소 및 유해가스 측정방법');
  expect(plan.body).not.toMatch(/^### .*신청방법/m);
  expect(plan.body.indexOf('### 사전준비')).toBeLessThan(plan.body.indexOf('### 측정방법'));
});
it('보충은 본문 뒤에만 추가하고 기존 본문은 바꾸지 않는다',()=>{
  const plan=planFor('가스 측정이 왜 필요해');const answer=composeAnswer(plan,'추가 설명');
  expect(answer.slice(0,plan.body.length)).toBe(plan.body);expect(answer.endsWith('\n\n추가 설명')).toBe(true);
});
const example:Source={score:1,chunk:{id:'a',documentId:'guide',documentTitle:'문서',sourceKind:'guide',pageNumber:1,chunkIndex:0,heading:'',body:'▶ 환기 • 환기장치가 정지한 경우 작업자는 즉시 외부로 대피해야 합니다.'}};
it('본문 반복·잘못된 출처·새 수치·작업 허용·조건 생략 보충은 표시하지 않는다',()=>{
  const plan=createAnswerPlan('환기 중단의 의미를 쉽게 설명해줘',[example]),id=plan.evidence[0].id;
  for(const value of [
    {evidenceIds:[id],text:plan.evidence[0].text},
    {evidenceIds:['missing'],text:'환기장치가 정지한 경우 외부로 나갑니다.'},
    {evidenceIds:[id],text:'환기장치가 정지한 경우 30분 후 대피합니다.'},
    {evidenceIds:[id],text:'환기장치가 정지한 경우에도 작업 가능합니다.'},
    {evidenceIds:[id],text:'작업자는 즉시 외부로 대피해야 합니다.'},
  ])expect(acceptedSupplement(JSON.stringify(value),plan)).toBe('');
  expect(acceptedSupplement('not-json',plan)).toBe('');
});
