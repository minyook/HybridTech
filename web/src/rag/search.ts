import type { Chunk, Source } from '../types';

export function javaHash(text: string): number {
  let hash = 0;
  for (let i = 0; i < text.length; i++) hash = (Math.imul(31, hash) + text.charCodeAt(i)) | 0;
  return hash;
}
export function embed(text: string): Float32Array {
  const vector = new Float32Array(384);
  const add = (token: string, weight: number) => { const hash = javaHash(token); vector[(hash & 0x7fffffff) % 384] += hash >>> 31 === 0 ? weight : -weight; };
  for (const token of text.toLowerCase().replace(/[^0-9a-z가-힣]+/g, ' ').trim().split(/\s+/).filter(token => token.length > 1)) {
    add(token, 1.5);
    for (let i = 0; i < token.length - 1; i++) add(token.slice(i, i + 2), .55);
  }
  const length = Math.sqrt(vector.reduce((sum, value) => sum + value * value, 0));
  if (length) for (let i = 0; i < vector.length; i++) vector[i] /= length;
  return vector;
}
export function aspects(question: string): string[] {
  return question.trim().split(/(?<![결효성경교학분])(?<=[0-9a-z가-힣])과\s+|(?<=[0-9a-z가-힣])와\s+|\s+(?:그리고|및)\s+|[,，]/).map(value => value.trim()).filter(Boolean);
}
const requests = new Set(['알려줘','알려주세요','설명해줘','설명해주세요','말해줘','말해주세요','방법은','어떻게','뭐야','무엇인가요','궁금해','기준은']);
export function terms(question: string): string[] {
  return [...new Set(question.toLowerCase().replace(/측정법/g,' 측정 방법').replace(/산소측정/g,'산소 측정').split(/[^0-9a-z가-힣]+/).map(term => term.replace(/(에서|으로|은|는|이|가|을|를|의|에|로|도|만)$/,'')).filter(term => term.length >= 2 && !requests.has(term)))];
}
const textOf = (chunk: Chunk) => `${chunk.heading} ${chunk.body}`.toLowerCase();
const vectors = new WeakMap<Chunk, Float32Array>();
export function rank(question: string, chunks: Chunk[]): Source[] {
  const tokens = terms(question);
  if (!tokens.length) return [];
  const query = embed(question);
  return chunks.flatMap(chunk => {
    const text = textOf(chunk); const matched = tokens.filter(token => text.includes(token));
    // Hash collisions alone cannot establish a document citation.
    if (!matched.length) return [];
    const coverage = matched.length / tokens.length;
    if (coverage < .45) return [];
    let vector = vectors.get(chunk); if (!vector) { vector = embed(text); vectors.set(chunk, vector); }
    const semantic = query.reduce((sum,value,index) => sum + value * vector![index],0);
    const compact = text.replace(/[^0-9a-z가-힣]+/g,'');
    let phrase = 0;
    for (let i=0; i<tokens.length-1; i++) if (compact.includes(tokens[i]+tokens[i+1])) phrase += tokens[i+1] === '방법' ? 4 : 2;
    const repetitions = matched.reduce((sum,token) => sum + Math.min(text.split(token).length-1,3),0);
    // A subject near a page's opening is stronger evidence than the same phrase
    // appearing once in a long appendix or general checklist.
    const opening = chunk.body.slice(0,350).toLowerCase();
    const openingCoverage = matched.filter(token => opening.includes(token)).length / tokens.length;
    return [{chunk, score: coverage*10 + repetitions*.2 + phrase + semantic*.72 + openingCoverage*2}];
  }).sort((a,b) => b.score-a.score || b.chunk.body.length-a.chunk.body.length || a.chunk.pageNumber-b.chunk.pageNumber);
}
export function retrieve(question: string, chunks: Chunk[], limit = 3): Source[] {
  const subjects = aspects(question).slice(0,8);
  const rankings = subjects.map(subject => rank(subject,chunks));
  const selected = new Map<string, Source>();
  for (const ranking of rankings) if (ranking[0]) selected.set(ranking[0].chunk.id,ranking[0]);
  if (subjects.length > 1) return [...selected.values()];
  for (const source of rank(question,chunks)) {
    if (selected.size >= Math.max(limit,subjects.length)) break;
    selected.set(source.chunk.id,source);
  }
  return [...selected.values()];
}
export function missingAspects(question: string, chunks: Chunk[]) { return aspects(question).filter(subject => rank(subject,chunks).length === 0); }
export function sourceAnswer(sources: Source[]): string {
  if (!sources.length) return '제공 문서에서 질문에 맞는 근거를 찾지 못했어요. 문서에 있는 용어로 다시 검색해 주세요.';
  const escape = (text: string) => text.replace(/[\\`*_{}\[\]<>#|~]/g,'\\$&');
  return sources.map(source => `### ${source.chunk.documentTitle} · ${source.chunk.pageNumber}쪽\n\n${escape(source.chunk.body)}`).join('\n\n');
}
export function contradictsSources(question: string, answer: string, sources: Source[]): boolean {
  if (!sources.length) return false;
  if (question.includes('심폐소생술') && /알려|방법|어떻게|설명/.test(question) && sources.some(source => source.chunk.body.includes('심폐소생술')) && !/압박|가슴을|가슴 중앙|흉부/.test(answer)) return true;
  return aspects(question).length > 1 && sources.length >= aspects(question).length && /(?:문서|자료)[^.!?\n]{0,40}(?:찾을 수 없|확인할 수 없|확인되지 않|없습니다|없어요)/.test(answer);
}
