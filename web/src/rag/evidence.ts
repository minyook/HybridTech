import type { Source } from '../types';
import { compactText, genericTerms, pageBody, queryTerms } from './text';

export interface EvidenceUnit {text:string;section:string;order:number}
export function evidenceUnits(source:Source):EvidenceUnit[]{
  const lines=pageBody(source.chunk)
    .replace(/([▶•◆※☞])/g,'\n$1').replace(/\s+([①-⑳])(?=\s)/g,'\n$1').replace(/\s+-\s+/g,'\n- ')
    .split(/\n+|(?<!ex\.)(?<!e\.g\.)(?<=[.!?])\s+/u).map(line=>line.trim()).filter(Boolean);
  let section='';const units:EvidenceUnit[]=[];
  for(let i=0;i<lines.length;i++){
    const line=lines[i];
    const marked=/^[•◆※☞①-⑳*\-]/.test(line);
    const text=line.replace(/^[▶•◆※☞*\-]\s*/,'').trim();
    if(!text)continue;
    const followedByItem=/^[•◆※☞①-⑳*\-]/.test(lines[i+1]??'');
    if(followedByItem&&(line.startsWith('▶')||(!marked&&text.length<55&&!/[.!?。]/.test(text)&&!/(?:이상|미만|이하|초과)/.test(text)))){section=text.replace(/^순\s*서\s*실\s*시\s*방\s*법\s*/,'');continue;}
    units.push({text,section,order:units.length});
  }
  if(!units.length){const text=pageBody(source.chunk);if(text)units.push({text,section:'',order:0});}
  return units;
}

/** Visit each section once before taking its next bullet; a long opening
 * paragraph cannot crowd out the later precautions or a continuation page. */
export function evidenceQueue(question:string,units:EvidenceUnit[]){
  const groups=new Map<string,EvidenceUnit[]>();
  for(const unit of units){const key=unit.section;const group=groups.get(key)??[];group.push(unit);groups.set(key,group);}
  const specific=queryTerms(question).filter(term=>!genericTerms.has(term));
  const sectionScore=(group:EvidenceUnit[])=>specific.filter(term=>compactText(group[0].section).includes(term)).length;
  const sections=[...groups.values()].sort((a,b)=>sectionScore(b)-sectionScore(a)||a[0].order-b[0].order);
  const result:EvidenceUnit[]=[];
  for(let i=0;i<units.length;i++)for(const group of sections)if(group[i])result.push(group[i]);
  return result;
}

const escapeMarkdown=(text:string)=>text.replace(/[\\`*_{}\[\]<>#|~]/g,'\\$&');
export function formatEvidenceUnits(units:EvidenceUnit[]){
  let section='';const lines:string[]=[];
  for(const unit of units){
    if(unit.section&&unit.section!==section){section=unit.section;lines.push(`### ${escapeMarkdown(section)}`);}
    lines.push(`- ${escapeMarkdown(unit.text)}`);
  }
  return lines.join('\n\n');
}
/** Display evidence outside the model's KV cache/output-token budget. Content
 * is extracted from the selected pages, never authored from a topic template. */
export function detailedEvidence(sources:Source[]){
  return sources.map(source=>{
    let section='';const lines:string[]=[];
    for(const unit of evidenceUnits(source)){
      if(unit.section&&unit.section!==section){section=unit.section;lines.push(`**${escapeMarkdown(section)}**`);}
      lines.push(`- ${escapeMarkdown(unit.text)}`);
    }
    return `#### ${escapeMarkdown(source.chunk.documentTitle)} · ${source.chunk.pageNumber}쪽\n\n${lines.join('\n\n')}`;
  }).join('\n\n');
}

export function evidenceAnswer(sources:Source[]){
  const details=formatEvidenceUnits(sources.flatMap(evidenceUnits));
  if(!details)return '제공 문서에서 질문에 맞는 근거를 찾지 못했어요. 다른 표현으로 검색해 주세요.';
  return details;
}
