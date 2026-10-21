import type { Chunk, Source } from '../types';
import { aliases, compactText, domainTerms, genericTerms, pageBody, queryTerms } from './text';
import { evidenceUnits, selectedEvidence, type EvidenceUnit } from './evidence';

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
  const parts=question.trim().split(/(?<![결효성경교학분안밖전후])(?<=[0-9a-z가-힣])과\s+|(?<![안밖전후])(?<=[0-9a-z가-힣])와\s+|\s+(?:그리고|및)\s+|[,，]/).map(value => value.trim()).filter(Boolean);
  if(parts.length>1&&parts.slice(1).some(part=>queryTerms(part).every(term=>genericTerms.has(term)||['체적','길이','시간','수치','깊이','횟수'].includes(term))))return [question.trim()];
  return parts;
}
export const terms=queryTerms;
const textCache=new WeakMap<Chunk,{text:string;opening:string;body:string;units:EvidenceUnit[]}>();
const searchable=(text:string)=>aliases(text).replace(/[^0-9a-z가-힣\s]/g,' ').replace(/\s+/g,' ');
function indexed(chunk:Chunk){let value=textCache.get(chunk);if(!value){const body=pageBody(chunk);value={body,text:searchable(body),opening:searchable(body.slice(0,180)),units:evidenceUnits({chunk,score:0})};textCache.set(chunk,value);}return value;}

function queryProfile(question:string,chunks:Chunk[]){
  const pages=chunks.map(chunk=>({chunk,...indexed(chunk)}));
  const supports=(token:string)=>pages.some(page=>page.text.includes(token));
  const tokens=[...new Set(terms(question).map(token=>{
    if(domainTerms.has(token))return token;
    // Resolve inflected, previously unseen query words from the corpus itself
    // (중단되면→중단, 표시해→표시), without a question-to-page lookup table.
    for(let length=2;length<token.length;length++){
      const stem=token.slice(0,length),ending=token.slice(length);
      if(/^(?:하|해|했|할|되|돼|이|은|는|을|를|의|에|로|과|와|만|마다|여야|까지|처럼|인가|야|어|었|졌|용|기)/.test(ending)&&supports(stem))return stem;
    }
    return token;
  }))];
  const frequencies=tokens.map(token=>pages.filter(page=>page.text.includes(token)).length);
  // Corpus frequency, rather than membership in a hand-written topic list,
  // decides specificity. An unknown noun must still lower confidence.
  const weights=tokens.map((token,i)=>Math.log(1+chunks.length/(1+frequencies[i]))*(genericTerms.has(token)?.25:1));
  const specific=tokens.filter((token,i)=>frequencies[i]>0&&!genericTerms.has(token));
  const rarest=Math.min(...specific.map(token=>frequencies[tokens.indexOf(token)]));
  const focus=specific.filter(token=>frequencies[tokens.indexOf(token)]<=Math.max(5,rarest*1.5));
  return {pages,tokens,weights,focus,total:weights.reduce((a,b)=>a+b,0)};
}

function passages(chunk:Chunk){
  const groups=new Map<number,EvidenceUnit[]>();
  for(const unit of indexed(chunk).units){const key=unit.group??unit.order;const group=groups.get(key)??[];group.push(unit);groups.set(key,group);}
  return [...groups.values()];
}
function passageScore(units:EvidenceUnit[],profile:ReturnType<typeof queryProfile>){
  const text=compactText(units.map(unit=>unit.text).join(' '));
  const heading=compactText(units[0].section);
  const local=searchable(units.map(unit=>unit.text).join(' '));
  return profile.tokens.reduce((score,token,i)=>score+profile.weights[i]*(local.includes(token)?1:heading.includes(token)?.35:0),0)/Math.sqrt(1+text.length/350);
}

/** Carry the answer scope alongside the untouched PDF page. A narrow question
 * leads with its matching bullet and keeps the surrounding safety section;
 * ordered procedure tables always retain their original order. */
function scopeSource(question:string,source:Source,chunks:Chunk[]):Source{
  if(/^순\s*서\s*실\s*시\s*방\s*법/.test(pageBody(source.chunk)))return source;
  const profile=queryProfile(question,chunks),groups=passages(source.chunk);
  if(!profile.focus.length||!groups.length)return source;
  const sorted=groups.map(group=>({group,score:passageScore(group,profile)})).sort((a,b)=>b.score-a.score||a.group[0].order-b.group[0].order);
  const best=sorted[0].group;
  if(!profile.focus.some(token=>compactText(best.map(unit=>unit.text).join(' ')).includes(token)))return source;
  const section=best[0].section;
  const root=(heading:string)=>compactText(heading.replace(/\([^)]*\)/g,''));
  const key=root(section);
  const units=indexed(source.chunk).units.filter(unit=>!section||(!unit.section&&unit.text.length>60)||unit.section===section||(unit.section&&(root(unit.section).startsWith(key)||key.startsWith(root(unit.section))))||/(?:반드시|금지|없다면)/.test(unit.text));
  const lead=[...best];
  // Dependent qualifiers retain the preceding bullet they refer to.
  if(/^(?:다만|이 경우|이 때|이때|그 밖|또한|그러)/.test(best[0].text)){
    const previous=groups[groups.indexOf(best)-1];
    if(previous?.[0].section===section)lead.unshift(...previous);
  }
  return {...source,evidenceOrders:[...lead,...units.filter(unit=>!lead.includes(unit))].map(unit=>unit.order)};
}
export function rank(question: string, chunks: Chunk[]): Source[] {
  const {pages,tokens,weights,focus,total}=queryProfile(question,chunks);
  if (!tokens.length) return [];
  const subjects=tokens.filter(token=>!genericTerms.has(token));
  return pages.flatMap(({chunk,text,opening,body}) => {
    const matched=tokens.filter(token=>text.includes(token));
    // Hash collisions alone cannot establish a document citation.
    if (!matched.length) return [];
    if(subjects.length&&!subjects.some(token=>text.includes(token)))return [];
    if(focus.length&&!focus.some(token=>text.includes(token)))return [];
    const coverage=tokens.reduce((sum,token,i)=>sum+(text.includes(token)?weights[i]:0),0)/total;
    if(coverage<.52)return [];
    let score=0;
    for(let i=0;i<tokens.length;i++){
      const occurrences=text.split(tokens[i]).length-1;if(!occurrences)continue;
      score+=weights[i]*(occurrences/(occurrences+.8*(.3+.7*text.length/700))+(opening.includes(tokens[i])?.7:0));
      if(i&&new RegExp(`${tokens[i-1]}[가-힣]{0,8}${tokens[i]}`).test(text))score+=(weights[i-1]+weights[i])*.7;
    }
    // Local co-occurrence matters: incidental words scattered across a long
    // page must not outrank a bullet that actually addresses the question.
    score=score*.8+Math.max(0,...passages(chunk).map(group=>passageScore(group,{pages,tokens,weights,focus,total})))*.8;
    if(/기준|범위|수치|몇|얼마/.test(question)&&/적정\s*공기/.test(body)&&/\d+(?:\.\d+)?\s*(?:%|ppm)\s*(?:이상|이하|미만)/i.test(body))score*=1.8;
    if((body.match(/제\d장/g)?.length??0)>=3&&!question.includes('목차'))score*=.15;
    if((body.match(/\(\s*\)/g)?.length??0)>=4&&!/허가|양식|신청|작성/.test(question))score*=.6;
    if(/점검표/.test(chunk.sourceKind)&&!/점검표|체크리스트|준수사항/.test(question))score*=.65;
    if(/확인\s*부탁|(?:^|\s)질의(?:\s|[:：])|문의드립니다/.test(body)&&!/사례|질의|해석|여부|반드시|항상|무조건/.test(question))score*=.65;
    if(/┃사례|\[답변|\(\d+\)[^()]{0,60}사망/.test(body)&&!/사례|질의|해석|여부/.test(question))score*=.4;
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
  const alternatives=rankings.flatMap(ranking=>ranking.slice(1).map(source=>({source,ratio:source.score/ranking[0].score}))).sort((a,b)=>b.ratio-a.ratio);
  for (const {source,ratio} of alternatives) {
    if (selected.size >= Math.max(limit,subjects.length)) break;
    if(rankings.some(ranking=>ranking[0]&&table(ranking[0])&&ranking[0].chunk.documentId===source.chunk.documentId)&&!table(source))continue;
    if(ratio<.65||indexed(source.chunk).text.length<100)continue;
    selected.set(source.chunk.id,source);
  }
  const result=[...selected.values()];
  // Keep a procedure's consecutive pages in reading order even when the shorter
  // continuation page ranks first. Do not globally reorder separate subjects.
  for(let i=0;i<result.length;i++){
    const previous=table(result[i])?result.findIndex((source,j)=>j>i&&table(source)&&source.chunk.documentId===result[i].chunk.documentId&&source.chunk.pageNumber===result[i].chunk.pageNumber-1):-1;
    if(previous>=0)[result[i],result[previous]]=[result[previous],result[i]];
  }
  return result.map(source=>{
    const primary=subjects.filter((_,i)=>rankings[i][0]?.chunk.id===source.chunk.id);
    const relevant=primary.length?primary:[subjects.find((_,i)=>rankings[i].some(item=>item.chunk.id===source.chunk.id))??question];
    const scoped=primary.length?relevant.map(subject=>scopeSource(subject,source,chunks)):[source];
    return scoped.some(item=>!item.evidenceOrders)?source:{...source,evidenceOrders:[...new Set(scoped.flatMap(item=>item.evidenceOrders!))]};
  });
}
export function missingAspects(question: string, chunks: Chunk[]) { return aspects(question).filter(subject => rank(subject,chunks).length === 0); }
export function sourceAnswer(sources: Source[]): string {
  if (!sources.length) return '제공 문서에서 질문에 맞는 근거를 찾지 못했어요. 문서에 있는 용어로 다시 검색해 주세요.';
  const escape = (text: string) => text.replace(/[\\`*_{}\[\]<>#|~]/g,'\\$&');
  return sources.map(source => `### ${source.chunk.documentTitle} · ${source.chunk.pageNumber}쪽\n\n${escape(source.evidenceOrders?selectedEvidence(source).map(unit=>unit.text).join('\n\n'):source.chunk.body)}`).join('\n\n');
}
export function contradictsSources(question: string, answer: string, sources: Source[]): boolean {
  if (!sources.length) return false;
  if (terms(question).includes('심폐소생술') && !/기도|인공호흡|회복자세|호흡 확인|신고|정의|뜻/.test(question) && sources.some(source => compactText(source.chunk.body).includes('심폐소생술')) && !/압박|가슴을|가슴 중앙|흉부/.test(answer)) return true;
  return aspects(question).length > 1 && sources.length >= aspects(question).length && /(?:문서|자료)[^.!?\n]{0,40}(?:찾을 수 없|확인할 수 없|확인되지 않|없습니다|없어요)/.test(answer);
}
