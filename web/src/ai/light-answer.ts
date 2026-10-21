import type { Source } from '../types';
import { evidenceUnits, formatEvidenceUnits, type EvidenceUnit } from '../rag/evidence';
import { compactText } from '../rag/text';

export interface LightAnswerPart {units:EvidenceUnit[];prompt:string;fallback:string;generate:boolean}
function partPrompt(question:string,units:EvidenceUnit[]){
  return `[사용자 질문]\n${question}\n\n[설명할 항목]\n${units.map((unit,index)=>`[${index+1}] ${unit.section?unit.section+': ':''}${unit.text}`).join('\n')}\n\n[답변 형식]\n각 항목을 같은 [번호]로 시작하여 뜻을 바꾸지 않고 쉬운 한국어로 설명하세요. 번호마다 해당 항목만 설명하세요. 항목을 합치거나 제목·머리말을 추가하지 마세요.`;
}

/** Cover every selected evidence unit with small, independent generations.
 * Count with the live tokenizer; previous output is never added to a prompt. */
export function lightAnswerParts(question:string,sources:Source[],count:(text:string)=>number,inputBudget:number,maxOutputTokens:number):LightAnswerPart[]{
  if(count(partPrompt(question,[]))>inputBudget)throw Error('질문이 문맥 한도보다 길어요. 질문을 나누어 입력해 주세요.');
  const parts:LightAnswerPart[]=[];let selected:EvidenceUnit[]=[];
  const finish=(units:EvidenceUnit[],generate=true)=>parts.push({units,prompt:partPrompt(question,units),fallback:formatEvidenceUnits(units),generate});
  const fits=(units:EvidenceUnit[])=>count(partPrompt(question,units))<=inputBudget&&count(units.map(unit=>unit.text).join('\n'))<=Math.max(128,Math.floor(maxOutputTokens*.85));
  for(const unit of sources.flatMap(evidenceUnits)){
    if(selected.length&&!fits([...selected,unit])){finish(selected);selected=[];}
    if(!fits([unit])){finish([unit],false);continue;}
    selected.push(unit);
  }
  if(selected.length)finish(selected);
  return parts;
}

/** Preserve quantitative facts even when the small model compresses a part too
 * far. The affected paragraph uses the source wording in the same answer. */
export function missingLightFacts(answer:string,units:EvidenceUnit[]){
  const reference=units.map(unit=>unit.text).join('\n').normalize('NFKC');
  const pattern=/\d+(?:\.\d+)?(?:\s*[~～]\s*\d+(?:\.\d+)?)?\s*(?:%|ppm|cm|mmAq|m\b|초|분|시간|회|개소)(?:\s*(?:이상|미만|이하|초과))?|\b119\b/g;
  const facts=reference.match(pattern)??[];
  const normalized=(text:string)=>text.normalize('NFKC').replace(/\s|\\/g,'').toLowerCase();
  const text=normalized(answer);
  if(facts.some(fact=>!text.includes(normalized(fact))))return true;
  if((answer.normalize('NFKC').match(pattern)??[]).some(fact=>!normalized(reference).includes(normalized(fact))))return true;
  if(units.some(unit=>unit.text.includes('①~③'))&&!text.includes('1~3'))return true;
  const compact=compactText(answer);
  const required=['방폭','가연성','공기호흡기','송기마스크','감시인','개구면','환기','검교정','교정','센서','표준가스','노출기준','압박','기도','호흡','반응','산소','황화수소','이산화탄소','일산화탄소','차단','잠금','대피','신고','중앙','흉부부위','팔꿈치','어깨','손등','일직선','머리','턱','코','가슴','경우','까지'];
  if(required.some(term=>compactText(reference).includes(term)&&!compact.includes(term)))return true;
  const negative=/(?:않|금지|(?:지|면|서는|해선)\s*안\s*(?:되|됩|돼|된|해|하)|지\s*마|말[아고]|(?:^|\s)안\s*(?:되|됩|돼))/;
  if(negative.test(reference)&&!negative.test(answer))return true;
  // A generic sentence cannot stand in for a whole procedure. Conservatively
  // retain a unit's source wording when its content disappears in generation.
  return units.some(unit=>{
    const source=compactText(unit.text),grams=new Set(Array.from({length:Math.max(0,source.length-1)},(_,i)=>source.slice(i,i+2)));
    return grams.size>=8&&[...grams].filter(gram=>compact.includes(gram)).length/grams.size<.75;
  });
}

/** Align each generated explanation to its own evidence item, so one item's
 * condition cannot silently become a different step's condition. Unmatched or
 * incomplete items keep their wording at the same position in the answer. */
export function explainLightPart(raw:string,units:EvidenceUnit[],final=true,truncated=false){
  const matches=[...raw.matchAll(/^\s*\[(\d+)\]\s*/gm)];
  let supplemented=false;
  const explained:EvidenceUnit[]=[];
  for(let index=0;index<units.length;index++){
    const found=matches.filter(match=>Number(match[1])===index+1);
    const match=found.length===1?found[0]:undefined;
    const position=match?matches.indexOf(match):-1,next=matches[position+1];
    const complete=Boolean(match&&(next||final&&!truncated));
    let text=match?raw.slice(match.index!+match[0].length,next?.index).trim():'';
    text=text.replace(/^\s*(?:#{1,6}\s*|[-*]\s*|\d+[.)]\s*)/gm,'').trim();
    if(units[index].section&&text.startsWith(units[index].section+':'))text=text.slice(units[index].section.length+1).trim();
    const label=text.match(/^([^:\n]{4,90}):\s*/);
    if(label&&compactText(units[index].section).endsWith(compactText(label[1])))text=text.slice(label[0].length).trim();
    if(!complete||!text||missingLightFacts(text,[units[index]])){
      if(!final)continue;
      text=units[index].text;supplemented=true;
    }
    explained.push({...units[index],text});
  }
  return {answer:formatEvidenceUnits(explained),supplemented};
}

/** One answer, one reading order. Remove repeated headings/paragraphs across
 * independent generations without deleting different steps with shared words. */
export function joinLightAnswer(parts:string[]){
  const sections=new Map<string,{heading:string;blocks:string[];seen:Set<string>}>();
  sections.set('',{heading:'',blocks:[],seen:new Set()});
  for(const part of parts){
    let section='',lines:string[]=[];
    const flush=()=>{const block=lines.join('\n').trim();lines=[];const key=compactText(block),group=sections.get(section)!;if(key&&!group.seen.has(key)){group.seen.add(key);group.blocks.push(block);}};
    for(const line of part.trim().split('\n')){
      const heading=line.match(/^\s*(#{1,6})\s+(.+)$/);
      if(heading){
        flush();if(/^(?:AI\s*정리|문서에서 확인한 상세 내용|문서\s*발췌|원문\s*발췌)$/.test(heading[2].trim()))continue;
        section=compactText(heading[2]);if(!sections.has(section))sections.set(section,{heading:`${heading[1]} ${heading[2]}`,blocks:[],seen:new Set()});
      }else if(!line.trim())flush();else lines.push(line.replace(/^\s*\d+[.)]\s+/,'- '));
    }
    flush();
  }
  return [...sections.values()].filter(group=>group.blocks.length).map(group=>[group.heading,...group.blocks].filter(Boolean).join('\n\n')).join('\n\n');
}
