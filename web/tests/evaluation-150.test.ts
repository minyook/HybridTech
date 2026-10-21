import {readFileSync} from 'node:fs';
import {expect,it} from 'vitest';
import dataset from './evaluation-150.json';
import {retrieve} from '../src/rag/search';
import {createAnswerPlan} from '../src/rag/answer-plan';
import type {Chunk} from '../src/types';
const raw=JSON.parse(readFileSync(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8'));
const chunks:Chunk[]=raw.chunks.map((chunk:Chunk)=>({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}`}));
const norm=(text:string)=>text.normalize('NFKC').replace(/[\s\\*_`#]/g,'').toLowerCase();
it('동결한 150문항은 문서 출처·필수 구절·금지 표현을 갖는다',()=>{
  expect(dataset.cases).toHaveLength(150);expect(new Set(dataset.cases.map(item=>item.id)).size).toBe(150);
  for(const item of dataset.cases)for(const clause of item.required)expect(item.pages.flat().some(page=>chunks.some(chunk=>chunk.documentId===dataset.documentId&&chunk.pageNumber===page&&norm(chunk.body).includes(norm(clause))))).toBe(true);
});
it.each(dataset.cases)('$id · $question',item=>{
  const sources=retrieve(item.question,chunks),plan=createAnswerPlan(item.question,sources);
  const pages=sources.filter(source=>source.chunk.documentId===dataset.documentId).map(source=>source.chunk.pageNumber);
  if(item.pages.length)for(const alternatives of item.pages)expect(alternatives.some(page=>pages.includes(page))).toBe(true);
  else expect(sources).toEqual([]);
  for(const clause of item.required)expect(norm(plan.body)).toContain(norm(clause));
  for(const clause of item.forbidden)expect(norm(plan.body)).not.toContain(norm(clause));
});
