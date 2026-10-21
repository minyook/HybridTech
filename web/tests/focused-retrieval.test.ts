import {readFileSync} from 'node:fs';
import {expect,it} from 'vitest';
import dataset from './focused-retrieval.json';
import {aspects,retrieve,sourceAnswer} from '../src/rag/search';
import {createAnswerPlan,supplementPrompt} from '../src/rag/answer-plan';
import {selectedEvidence} from '../src/rag/evidence';
import type {Chunk} from '../src/types';
const raw=JSON.parse(readFileSync(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8'));
const chunks:Chunk[]=raw.chunks.map((chunk:Chunk)=>({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}`}));
const norm=(text:string)=>text.normalize('NFKC').replace(/[\s\\*_`#]/g,'').toLowerCase();

it('추가 질문의 기대 구절은 검색 결과가 아닌 원문에 존재한다',()=>{
  for(const item of dataset.cases)for(const clause of [item.first,...item.required].filter(Boolean)){
    expect(chunks.some(chunk=>chunk.documentId===dataset.documentId&&item.pages.includes(chunk.pageNumber)&&norm(chunk.body).includes(norm(clause)))).toBe(true);
  }
});
it.each(dataset.cases)('질문 초점·관련 조건·문서 밖 속성: $question',item=>{
  const sources=retrieve(item.question,chunks),plan=createAnswerPlan(item.question,sources);
  const pages=sources.filter(source=>source.chunk.documentId===dataset.documentId).map(source=>source.chunk.pageNumber);
  if(item.pages.length)for(const page of item.pages)expect(pages).toContain(page);else expect(sources).toEqual([]);
  // Require a direct response within the first 400 source characters, not
  // merely a correct word somewhere in a multi-page answer.
  if(item.first)expect(norm(plan.evidence.map(unit=>unit.text).join(' ').slice(0,400))).toContain(norm(item.first));
  for(const clause of item.required)expect(norm(plan.body)).toContain(norm(clause));
  for(const clause of item.forbidden)if(clause.startsWith('###'))expect(plan.body).not.toContain(clause);else expect(norm(plan.body)).not.toContain(norm(clause));
});
it('전등 질문은 첫 문장에서 방폭 조건을 직접 답하고 원본 쪽은 수정하지 않는다',()=>{
  const question='어두운 밀폐공간 측정 전등은 어떤 걸 써야해',sources=retrieve(question,chunks),plan=createAnswerPlan(question,sources);
  expect(plan.evidence[0].text).toContain('방폭구조의 전등');
  expect(plan.evidence[0].text).toContain('가연성가스가 차 있을 수 있으므로');
  expect(plan.body).not.toContain('20.9%');expect(plan.body).not.toContain('3개소');
  expect(sourceAnswer(sources)).toContain('방폭구조의 전등');expect(sourceAnswer(sources)).not.toContain('20.9%');
  expect(supplementPrompt(plan)).not.toContain('20.9%');
  for(const source of sources)expect(source.chunk.body).toBe(chunks.find(chunk=>chunk.id===source.chunk.id)!.body);
  const cloned=JSON.parse(JSON.stringify(sources));expect(createAnswerPlan(question,cloned)).toEqual(plan);
});
it('같은 쪽에 대한 두 질문의 문단 선택을 합쳐 조건을 누락하지 않는다',()=>{
  const question='측정기 사전준비 그리고 전등 종류 알려줘';
  const plan=createAnswerPlan(question,retrieve(question,chunks));
  for(const text of ['20.9%','표준가스','방폭구조의 전등','가연성가스'])expect(plan.body).toContain(text);
  expect(new Set(plan.evidence.map(unit=>unit.id)).size).toBe(plan.evidence.length);
});
it('안과 밖은 두 주제가 아니라 한 질문의 위치 조건이다',()=>{
  const question='감시인은 밀폐공간 안과 밖 중 어디에 배치해?';expect(aspects(question)).toEqual([question]);
});

const chunk=(id:string,body:string):Chunk=>({id,documentId:'test',documentTitle:'테스트 문서',sourceKind:'가이드',pageNumber:Number(id),chunkIndex:0,heading:'',body});
it('원래 주제 사전에 없는 명사도 새 문서의 실제 항목을 찾아 답한다',()=>{
  const corpus=[chunk('1','▶ 사전준비 • 장비를 점검합니다. ▶ 사용 주의사항 • 배수구를 막는 마개는 균열이 없는 것을 사용하십시오. 손상된 마개는 사용하지 마십시오.'),chunk('2','▶ 측정방법 • 산소 농도를 수직 및 수평 방향으로 측정하십시오.')];
  const question='배수구 마개는 어떤 걸 사용해야 해?',sources=retrieve(question,corpus),plan=createAnswerPlan(question,sources);
  expect(sources[0].chunk.id).toBe('1');expect(plan.evidence[0].text).toContain('마개');expect(plan.body).toContain('손상된 마개는 사용하지 마십시오.');expect(plan.body).not.toContain('수직 및 수평');
});
it('공백 양쪽의 정전 등/등 급기를 전등/등급으로 합쳐 매칭하지 않는다',()=>{
  const corpus=[chunk('1','정전 등으로 환기가 중단되면 즉시 대피하십시오. 작업장 등 급기로 공기를 공급합니다.'),chunk('2','전등은 방폭구조의 전등을 사용하십시오.')];
  expect(retrieve('전등 종류 알려줘',corpus).map(source=>source.chunk.id)).toEqual(['2']);
  expect(retrieve('방수 등급 알려줘',corpus)).toEqual([]);
});
it('새 질문의 결과는 앞서 질의한 주제나 검색 호출 순서에 의존하지 않는다',()=>{
  const question='전등 종류 알려줘',before=retrieve(question,chunks);
  for(const value of ['심폐소생술','산소 및 유해가스 측정방법','오늘 서울 날씨'])retrieve(value,chunks);
  expect(retrieve(question,chunks)).toEqual(before);
  expect(before.flatMap(selectedEvidence).every(unit=>unit.text)).toBe(true);
});
