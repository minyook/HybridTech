import {readFileSync} from 'node:fs';
import {describe,it,expect} from 'vitest';
import {resolveHybrid,contextualQuestion,type SemanticIndex} from '../src/rag/hybrid';
import {createAnswerPlan} from '../src/rag/answer-plan';
import type {Chunk} from '../src/types';
const asset=(name:string)=>JSON.parse(readFileSync(new URL(`../../app/src/main/assets/knowledge/${name}`,import.meta.url),'utf8'));
const chunks:Chunk[]=asset('seed_chunks_v1.json').chunks.map((c:Chunk)=>({...c,id:`${c.documentId}:${c.pageNumber}:${c.chunkIndex}`}));
const index:SemanticIndex=asset('semantic-index-v1.json');
const cases=JSON.parse(readFileSync(new URL('../../app/src/test/resources/semantic-deep-vectors.json',import.meta.url),'utf8')) as {question:string;vector:number[];expectedPages:number[];expectedStatus:string}[];
describe('hybrid local document discovery',()=>{
 it.each(cases)('preserves the measured decision: $question',item=>{
  const r=resolveHybrid(item.question,chunks,index,item.vector);
  expect(r.status).toBe(item.expectedStatus);expect(r.sources.map(s=>s.chunk.pageNumber)).toEqual(item.expectedPages);
  if(!r.sources.length)expect(r.message).not.toContain('문서에 없');
 });
 it('finds a colloquial lamp question missed by strict lexical retrieval',()=>{
  const item=cases.find(c=>c.question.includes('휴대용 불빛'))!,r=resolveHybrid(item.question,chunks,index,item.vector),plan=createAnswerPlan(item.question,r.sources);
  expect(r.sources.map(s=>s.chunk.pageNumber)).toContain(27);expect(plan.body).toContain('방폭구조');expect(plan.body).not.toContain('수직 및 수평');
 });
 it('retains linked conditions and the original source in semantic-only answers',()=>{
  const item=cases.find(c=>c.question.includes('입으로 숨 넣는'))!,r=resolveHybrid(item.question,chunks,index,item.vector),plan=createAnswerPlan(item.question,r.sources);
  expect(plan.body).toContain('인공호흡 방법을 모르거나');expect(plan.body).toContain('지속적으로');
  expect(r.sources.every(s=>chunks.find(c=>c.id===s.chunk.id)?.body===s.chunk.body)).toBe(true);
 });
 it('does not carry an old topic into a new explicit question',()=>{
  expect(contextualQuestion('그럼 언제 해야 해?','가스 측정방법')).toContain('가스 측정방법');
  expect(contextualQuestion('심폐소생술 알려줘','가스 측정방법')).toBe('심폐소생술 알려줘');
 });
 it('a clarification heading returns only that section without loading the encoder',()=>{
  const r=resolveHybrid('측정 시기에 대해 알려줘',chunks,index);
  const body=createAnswerPlan(r.question,r.sources).body;
  expect(r.status).toBe('answered');expect(r.semantic).toBe(false);expect(body).toContain('점심시간');expect(body).not.toContain('교육과정');
 });
 it('a scoped qualification answer keeps its preceding qualification requirement',()=>{
  const r=resolveHybrid('산소 유해가스 농도 측정자의 자격',chunks,index),body=createAnswerPlan(r.question,r.sources).body;
  expect(body).toContain('올바른 해석');expect(body).toContain('관리감독자');
 });
 it('answers the supported half of a compound question and names the unresolved half',()=>{
  const r=resolveHybrid('심폐소생술 그리고 전등 방수 IP 등급 알려줘',chunks,index);
  expect(r.status).toBe('partial');expect(r.sources.map(s=>s.chunk.pageNumber)).toContain(45);expect(r.message).toContain('IP 등급');expect(r.message).not.toContain('문서에 없');
 });
 it('does not use stale passages as evidence',()=>{
  const item=cases.find(c=>c.question.includes('휴대용 불빛'))!;
  expect(resolveHybrid(item.question,chunks,{...index,passages:index.passages.map(p=>({...p,sourceBody:'different edition'}))},item.vector).sources).toEqual([]);
 });
});
