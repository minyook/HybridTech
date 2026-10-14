import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { aspects, contradictsSources, embed, javaHash, rank, retrieve } from '../src/rag/search';
import type { Chunk } from '../src/types';

const raw = JSON.parse(readFileSync(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8'));
const chunks: Chunk[] = raw.chunks.map((chunk: Chunk) => ({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}`}));
describe('Android 검색 이식',() => {
  it('같은 Java UTF-16 해시와 384차원 정규화 벡터를 사용한다',() => {
    expect(javaHash('abc')).toBe(96354); expect(javaHash('산소')).toBe(1578716);
    const vector=embed('산소 유해가스 농도 측정방법');
    expect(vector).toHaveLength(384); expect(Math.hypot(...vector)).toBeCloseTo(1,6); expect([...embed('')].every(value=>value===0)).toBe(true);
  });
  it('Android와 같은 복합 질문과 결과 단어를 구분한다',() => {
    expect(aspects('심폐소생술과 산소측정법 알려줘')).toEqual(['심폐소생술','산소측정법 알려줘']);
    expect(aspects('측정 결과 알려줘')).toEqual(['측정 결과 알려줘']);
  });
  it('원문 전체와 문서 ID, PDF 쪽 연결을 보존한다',() => {
    expect(chunks).toHaveLength(87); expect(new Set(chunks.map(chunk=>chunk.id)).size).toBe(87);
    for (const source of retrieve('밀폐공간 작업허가 절차 알려줘',chunks)) expect(source.chunk.body).toBe(chunks.find(chunk=>chunk.id===source.chunk.id)?.body);
  });
  it('복합 질문에서 각 주제에 대한 근거를 확보한다',() => {
    const result=retrieve('심폐소생술과 산소측정법 알려줘',chunks);
    expect(result.some(source=>source.chunk.body.includes('흉부압박')||source.chunk.body.includes('가슴압박')||source.chunk.body.includes('가슴을'))).toBe(true);
    expect(result.some(source=>source.chunk.body.includes('측정방법'))).toBe(true);
  });
  it.each(['오늘 서울 날씨 알려줘','비트코인 투자 전망','파스타 레시피 추천','아이폰 배터리 교체 가격'])('문서 밖 질문에 근거를 붙이지 않는다: %s',question => {
    expect(retrieve(question,chunks)).toEqual([]);
  });
  it('근거가 있는 심폐소생술 절차를 부정하는 생성을 거부한다',() => {
    const sources=retrieve('심폐소생술 방법 알려줘',chunks);
    expect(contradictsSources('심폐소생술 방법 알려줘','문서에서 확인되지 않습니다.',sources)).toBe(true);
  });
  it('검색 결과는 질문마다 동일하다',() => {
    expect(rank('산소측정법 알려줘',chunks).map(source=>source.chunk.id)).toEqual(rank('산소측정법 알려줘',chunks).map(source=>source.chunk.id));
  });
});
