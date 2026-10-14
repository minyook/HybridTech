import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import evaluation from './evaluation.json';
import { retrieve } from '../src/rag/search';
import type { Chunk } from '../src/types';

const raw = JSON.parse(readFileSync(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8'));
const chunks: Chunk[] = raw.chunks.map((chunk: Chunk) => ({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}`}));
it('18개 단일·8개 복합·4개 문서 밖 질문의 원문 페이지 회귀 평가',() => {
  const results = evaluation.map(item => {
    const result=retrieve(item.question,chunks).slice(0,3);
    const pages=result.filter(source=>source.chunk.documentId==='confined_space_safety_guide_2022').map(source=>source.chunk.pageNumber);
    return {question:item.question,pages,passed:item.pages.length ? item.pages.every(group=>group.some(page=>pages.includes(page))) : result.length===0};
  });
  const failed=results.filter(item=>!item.passed);
  console.log(JSON.stringify({total:30,passed:30-failed.length,failed},null,2));
  expect(results.slice(0,26).filter(item=>item.passed).length/26).toBeGreaterThanOrEqual(.9);
  expect(results.slice(26).every(item=>item.passed)).toBe(true);
});
