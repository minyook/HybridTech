import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import type { Chunk, Source } from '../src/types';
import { retrieve } from '../src/rag/search';
import { detailedEvidence, evidenceAnswer, evidenceUnits } from '../src/rag/evidence';
import { boundedLightPrompt } from '../src/ai/light-prompt';

const chunks:Chunk[]=JSON.parse(readFileSync(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8')).chunks.map((chunk:Chunk)=>({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}`}));
it('실제 CPR 원문에서 앞·중간·끝 절차와 수치를 빼먹지 않고 목록으로 표시한다',()=>{
  const sources=retrieve('심폐소생술',chunks),original=sources.map(source=>source.chunk.body);
  const text=detailedEvidence(sources).replaceAll('\\','');
  for(const required of ['45쪽','46쪽','119','10초','30회','5cm','100~120회','2회','회복자세'])expect(text).toContain(required);
  expect(text).toContain('- ');expect(text).not.toContain('제1장 질식과 밀폐공간제2장');
  expect(sources.map(source=>source.chunk.body)).toEqual(original);
});
it('실제 가스 측정 원문에서 준비·측정·주의사항과 장비 확인 수치를 보존한다',()=>{
  const text=evidenceAnswer(retrieve('산소 및 유해가스 측정방법',chunks));
  for(const required of ['사전준비','측정방법','측정시 주의사항','20.9%','3개소','1m','5m','방폭구조','감시인','①\\~③','ex. 점심시간','ex. 2시간'])expect(text).toContain(required);
  expect(text).not.toMatch(/AI 정리|문서에서 확인한 상세 내용|27쪽/);
});
it('긴 첫 문장이 예산을 초과해도 뒤쪽의 짧은 관련 조건을 버리지 않는다',()=>{
  const source={score:1,chunk:{id:'x',documentId:'guide',documentTitle:'문서',pageNumber:1,sourceKind:'guide',chunkIndex:0,heading:'',body:'▶ 사전준비 • '+'긴 설명 '.repeat(100)+'\n▶ 주의사항 • 반드시 감시인을 배치하십시오.'}} as Source;
  const prompt=boundedLightPrompt('측정 주의사항',[source],text=>text.length,130);
  expect(prompt).toContain('반드시 감시인');expect(prompt.length).toBeLessThanOrEqual(130);
});
it('한 페이지의 앞부분으로 채우기 전에 여러 페이지와 뒤쪽 절차를 함께 담는다',()=>{
  const source=(page:number,body:string)=>({score:1,chunk:{id:String(page),documentId:'g',documentTitle:'문서',pageNumber:page,sourceKind:'guide',chunkIndex:0,heading:'',body}} as Source);
  const sources=[source(1,'▶ 준비 • 장비 상태를 확인합니다. • 준비에 대한 추가 설명입니다. ▶ 주의 • 외부의 감시인과 연락합니다.'),source(2,'▶ 반복 • 119 구급대가 올 때까지 반복합니다.')];
  const prompt=boundedLightPrompt('절차',sources,text=>text.length,180);
  expect(prompt).toContain('1쪽');expect(prompt).toContain('2쪽');expect(prompt).toContain('감시인');expect(prompt.length).toBeLessThanOrEqual(180);
});
it('출처에 없는 문장은 만들지 않고 Markdown 문법과 외부 이미지는 이스케이프한다',()=>{
  const source={score:1,chunk:{id:'x',documentId:'g',documentTitle:'원문',pageNumber:1,sourceKind:'guide',chunkIndex:0,heading:'',body:'• ![remote](https://example.invalid/image.png) **점검** 실시.'}} as Source;
  expect(evidenceUnits(source)[0].text).toContain('점검');expect(detailedEvidence([source])).toContain('!\\[remote\\]');
});
