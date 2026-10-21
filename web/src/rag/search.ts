import type { Chunk, Source } from '../types';
import { compactText, domainTerms, genericTerms, pageBody, queryTerms } from './text';

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
  const parts=question.trim().split(/(?<![결효성경교학분])(?<=[0-9a-z가-힣])과\s+|(?<=[0-9a-z가-힣])와\s+|\s+(?:그리고|및)\s+|[,，]/).map(value => value.trim()).filter(Boolean);
  if(parts.length>1&&parts.slice(1).some(part=>queryTerms(part).every(term=>genericTerms.has(term)||['체적','길이','시간','수치','깊이','횟수'].includes(term))))return [question.trim()];
  return parts;
}
export const terms=queryTerms;
const textCache=new WeakMap<Chunk,{text:string;opening:string;body:string}>();
function indexed(chunk:Chunk){let value=textCache.get(chunk);if(!value){const body=pageBody(chunk);value={body,text:compactText(body),opening:compactText(body.slice(0,180))};textCache.set(chunk,value);}return value;}
export function rank(question: string, chunks: Chunk[]): Source[] {
  const tokens = terms(question);
  if (!tokens.length) return [];
  const pages=chunks.map(chunk=>({chunk,...indexed(chunk)}));
  // Colloquial endings and words absent from the corpus cannot dominate the
  // coverage denominator merely because their document frequency is zero.
  const anchored=tokens.some(token=>domainTerms.has(token)&&!genericTerms.has(token));
  const weights=tokens.map(token=>{const frequency=pages.filter(page=>page.text.includes(token)).length;return Math.log(1+chunks.length/(1+frequency))*(frequency?genericTerms.has(token)?.35:1:anchored?.05:1);});
  const total=weights.reduce((a,b)=>a+b,0);
  const subjects=tokens.filter(token=>!genericTerms.has(token));
  return pages.flatMap(({chunk,text,opening,body}) => {
    const matched=tokens.filter(token=>text.includes(token));
    // Hash collisions alone cannot establish a document citation.
    if (!matched.length) return [];
    if(subjects.length&&!subjects.some(token=>text.includes(token)))return [];
    const coverage=tokens.reduce((sum,token,i)=>sum+(text.includes(token)?weights[i]:0),0)/total;
    if(coverage<.48)return [];
    let score=0;
    for(let i=0;i<tokens.length;i++){
      const occurrences=text.split(tokens[i]).length-1;if(!occurrences)continue;
      score+=weights[i]*(occurrences/(occurrences+.8*(.3+.7*text.length/700))+(opening.includes(tokens[i])?.7:0));
      if(i&&new RegExp(`${tokens[i-1]}[가-힣]{0,8}${tokens[i]}`).test(text))score+=(weights[i-1]+weights[i])*.7;
    }
    if(/기준|범위|수치|몇|얼마/.test(question)&&/적정\s*공기/.test(body)&&/\d+(?:\.\d+)?\s*(?:%|ppm)\s*(?:이상|이하|미만)/i.test(body))score*=1.8;
    if((body.match(/제\d장/g)?.length??0)>=3&&!question.includes('목차'))score*=.15;
    if((body.match(/\(\s*\)/g)?.length??0)>=4&&!/허가|양식|신청|작성/.test(question))score*=.6;
    if(/점검표/.test(chunk.sourceKind)&&!/점검표|체크리스트|준수사항/.test(question))score*=.65;
    if(/확인\s*부탁|(?:^|\s)질의(?:\s|[:：])|문의드립니다/.test(body)&&!/사례|질의|해석|여부|반드시|항상|무조건/.test(question))score*=.65;
    // A contents page naming a topic is not a substitute for its procedures.
    return [{chunk,score:score*coverage*(text.length<100?.3:1)}];
  }).sort((a,b)=>b.score-a.score||a.chunk.pageNumber-b.chunk.pageNumber);
}
export function retrieve(question: string, chunks: Chunk[], limit = 3): Source[] {
  if(/(?:우리|현재|지금).*(?:승인|허가).*(?:됐|되었|받았|상태)/.test(question))return [];
  let subjects = aspects(question).slice(0,8);
  if(subjects.length>1&&subjects.slice(0,-1).every(subject=>/^(?:산소|유해가스|일산화탄소|이산화탄소|황화수소)$/.test(compactText(subject)))&&terms(subjects.at(-1)!).some(term=>['측정','농도','기준'].includes(term)))subjects=[question];
  const rankings = subjects.map(subject => rank(subject,chunks));
  const selected = new Map<string, Source>();
  for (const ranking of rankings) if (ranking[0]) selected.set(ranking[0].chunk.id,ranking[0]);
  const table=(source:Source)=>/^순\s*서\s*실\s*시\s*방\s*법/.test(pageBody(source.chunk));
  for(const ranking of rankings){
    const first=ranking[0];if(!first||!table(first))continue;
    const continuation=ranking.find(source=>source.chunk.documentId===first.chunk.documentId&&Math.abs(source.chunk.pageNumber-first.chunk.pageNumber)===1&&table(source));
    if(continuation&&selected.size<Math.max(limit,subjects.length))selected.set(continuation.chunk.id,continuation);
  }
  // Near ties can be complementary definitions/timing pages. Preserve them
  // before unrelated global matches displace the second half of a condition.
  for(const ranking of rankings){const alternative=ranking[1];if(alternative&&alternative.score>=ranking[0].score*.98&&selected.size<Math.max(limit,subjects.length))selected.set(alternative.chunk.id,alternative);}
  for (const source of [...rank(question,chunks),...rankings.flatMap(ranking=>ranking.slice(1))]) {
    if (selected.size >= Math.max(limit,subjects.length)) break;
    const best=rankings.find(ranking=>ranking.some(item=>item.chunk.id===source.chunk.id))?.[0]?.score??source.score;
    if(source.score<best*.65||indexed(source.chunk).text.length<100)continue;
    selected.set(source.chunk.id,source);
  }
  const result=[...selected.values()];
  // Keep a procedure's consecutive pages in reading order even when the shorter
  // continuation page ranks first. Do not globally reorder separate subjects.
  for(let i=0;i<result.length;i++){
    const previous=table(result[i])?result.findIndex((source,j)=>j>i&&table(source)&&source.chunk.documentId===result[i].chunk.documentId&&source.chunk.pageNumber===result[i].chunk.pageNumber-1):-1;
    if(previous>=0)[result[i],result[previous]]=[result[previous],result[i]];
  }
  return result;
}
export function missingAspects(question: string, chunks: Chunk[]) { return aspects(question).filter(subject => rank(subject,chunks).length === 0); }
export function sourceAnswer(sources: Source[]): string {
  if (!sources.length) return '제공 문서에서 질문에 맞는 근거를 찾지 못했어요. 문서에 있는 용어로 다시 검색해 주세요.';
  const escape = (text: string) => text.replace(/[\\`*_{}\[\]<>#|~]/g,'\\$&');
  return sources.map(source => `### ${source.chunk.documentTitle} · ${source.chunk.pageNumber}쪽\n\n${escape(source.chunk.body)}`).join('\n\n');
}
export function contradictsSources(question: string, answer: string, sources: Source[]): boolean {
  if (!sources.length) return false;
  if (terms(question).includes('심폐소생술') && !/기도|인공호흡|회복자세|호흡 확인|신고|정의|뜻/.test(question) && sources.some(source => compactText(source.chunk.body).includes('심폐소생술')) && !/압박|가슴을|가슴 중앙|흉부/.test(answer)) return true;
  return aspects(question).length > 1 && sources.length >= aspects(question).length && /(?:문서|자료)[^.!?\n]{0,40}(?:찾을 수 없|확인할 수 없|확인되지 않|없습니다|없어요)/.test(answer);
}
