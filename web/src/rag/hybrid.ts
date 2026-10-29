import type {Chunk,Source} from '../types';
import {retrieve,terms,aspects} from './search';
import {aliases,compactText,genericTerms} from './text';
import {evidenceUnits,selectedEvidence} from './evidence';
import type {SemanticPassage} from './semantic-passages';
export interface SemanticIndex {version:number;corpusSha256:string;modelRevision:string;dimensions:number;passages:SemanticPassage[]}
export interface SearchSuggestion {label:string;question:string}
export interface SearchResolution {question:string;sources:Source[];status:'answered'|'partial'|'clarify'|'unconfirmed'|'live-state';message:string;suggestions:SearchSuggestion[];semantic:boolean}
export function contextualQuestion(question:string,previous=''){
 const value=question.trim();
 if(!previous||previous.length>500)return value;
 // Carry context only for explicit anaphora. A new self-contained topic wins.
 if(!/^(?:그거|그건|그것|그러면|그럼|이 경우|그 경우)/.test(value))return value;
 const subjects=terms(value).filter(t=>!genericTerms.has(t)&&!/^(그거|그건|그것|그러면|그럼|경우)/.test(t));
 if(subjects.length>1)return value;
 return `${previous}\n후속 질문: ${value}`;
}
const specification=/방수|IP\s*등급|배터리|전압|볼트|가격|구매|무게|제조사|모델명|충전|유효기간|유효\s*기간/gi;
function completeScope(p:SemanticPassage,chunk:Chunk){
 const units=evidenceUnits({chunk,score:0}),first=units.findIndex(u=>u.section===p.section),previous=units[first-1];
 const topic=previous?.text.match(/^\(([^)]+)\)/)?.[1];
 return topic&&compactText(p.section).includes(compactText(topic))?[{...previous,section:p.section},...p.units]:p.units;
}
export function resolveHybrid(question:string,chunks:Chunk[],index?:SemanticIndex,vector?:number[]):SearchResolution{
 const result:SearchResolution={question,sources:[],status:'unconfirmed',message:'질문에 직접 답할 근거를 아직 확인하지 못했어요. 궁금한 대상과 알고 싶은 내용을 함께 알려 주세요.',suggestions:[],semantic:!!vector};
 if(/(?:우리|현재|지금).*(?:승인|허가).*(?:됐|되었|받았|완료|상태)/.test(question))return {...result,status:'live-state',message:'현재 현장의 승인 상태는 문서로 확인할 수 없어요. 작업 전 점검 화면에서 요청과 감독관 승인 상태를 확인해 주세요.'};
 const parts=aspects(question).slice(0,8);
 const sharedGas=parts.length>1&&parts.slice(0,-1).every(p=>/^(산소|유해가스|일산화탄소|이산화탄소|황화수소)$/.test(compactText(p)))&&terms(parts.at(-1)!).some(t=>['측정','농도','기준'].includes(t));
 if(parts.length>1&&!sharedGas){
  const each=parts.map(part=>resolveHybrid(part,chunks,index));const found=each.filter(r=>r.status==='answered');const missing=each.filter(r=>r.status!=='answered');
  if(found.length&&missing.length){
   const sources:Source[]=[];for(const r of found)for(const source of r.sources){const old=sources.find(s=>s.chunk.id===source.chunk.id);if(old)old.evidence=[...selectedEvidence(old),...selectedEvidence(source)].filter((u,i,all)=>all.findIndex(v=>v.text===u.text)===i);else sources.push({...source});}
   return {...result,status:'partial',sources,message:`질문 중 ‘${missing.map(r=>r.question).join(' / ')}’ 부분은 아직 직접 근거를 확인하지 못했어요. 이 부분에서 궁금한 대상이나 조건을 더 알려 주세요.`,suggestions:missing.slice(0,3).map(r=>({label:r.question,question:r.question}))};
  }
 }
 const tokens=terms(question).filter(t=>!/^(그거|그건|그것|그러면|그럼|후속|질문)/.test(t));
 const lexical=retrieve(question,chunks);
 const supportedSpecs=[...new Set(question.match(specification)??[])];
 // Do not erase an explicitly requested property to make a topic match.
 const unknownSpecs=supportedSpecs.filter(s=>!chunks.some(c=>compactText(c.body).includes(compactText(s))));
 const vague=tokens.length<=1&&!tokens.some(t=>!genericTerms.has(t));
 const heading=compactText(question.replace(/(?:에\s*대해|관련|내용|알려줘|알려주세요|설명해줘|설명해주세요|정리해줘|주세요|좀|\?)/g,''));
 const exact=index?.passages.filter(p=>compactText(p.section)===heading&&heading&&chunks.some(c=>c.documentId===p.documentId&&c.pageNumber===p.page&&c.chunkIndex===p.chunkIndex&&c.body===p.sourceBody))??[];
 if(!unknownSpecs.length&&exact.length&&new Set(exact.map(p=>`${p.documentId}/${p.page}`)).size<=2){
  const selected=new Map<string,typeof exact[number]>();for(const p of exact){const key=`${p.documentId}/${p.page}`;if((selected.get(key)?.orders.length??0)<p.orders.length)selected.set(key,p);}
  return {...result,status:'answered',message:'',semantic:false,sources:[...selected.values()].map(p=>{const chunk=chunks.find(c=>c.documentId===p.documentId&&c.pageNumber===p.page&&c.chunkIndex===p.chunkIndex)!;return {chunk,score:1,evidence:completeScope(p,chunk)};})};
 }
 if(!index||!vector||vector.length!==index.dimensions||!vector.every(Number.isFinite)){
  if(lexical.length&&!unknownSpecs.length&&!vague)return {...result,sources:lexical,status:'answered',message:'',semantic:false};
  const headings=new Map<string,string>();if(vague&&tokens.length&&index)for(const p of index.passages)if(p.section.length<50&&tokens.some(t=>compactText(p.section).includes(t)))headings.set(compactText(p.section),p.section.replace(/^\((.*)\)$/,'$1'));
  const suggestions=[...headings.values()].slice(0,3).map(label=>({label,question:`${label}에 대해 알려줘`}));
  return {...result,status:vague?'clarify':'unconfirmed',message:vague?'어떤 대상의 방법이나 조건이 궁금한가요? 대상과 궁금한 점을 함께 알려 주세요.':result.message,suggestions,semantic:false};
 }
 const passageIndex=index.passages.filter(p=>chunks.some(c=>c.documentId===p.documentId&&c.pageNumber===p.page&&c.chunkIndex===p.chunkIndex&&c.body===p.sourceBody));
 const texts=passageIndex.map(p=>aliases(p.searchText));
 const frequencies=new Map(tokens.map(t=>[t,texts.filter(text=>text.includes(t)).length]));
 const candidates=passageIndex.map((p,index)=>{
  let dense=p.vector.reduce((sum,x,i)=>sum+x*vector[i],0);
  if(/사망\s*\d+명|\[답변|┃사례/.test(p.text)&&!/사례|사고사례|질의|해석/.test(question))dense-=.035;
  if(p.text.length<40)dense-=.025;
  const text=texts[index],lex=tokens.reduce((sum,t)=>{
   const df=frequencies.get(t)!;
   return sum+(text.includes(t)?Math.log(1+passageIndex.length/(1+df))*(genericTerms.has(t)?.2:1):0);
  },0)/Math.sqrt(1+p.text.length/500);
  return {p,dense,lex,fused:0};
 });
 const dense=[...candidates].sort((a,b)=>b.dense-a.dense),sparse=candidates.filter(x=>x.lex>0).sort((a,b)=>b.lex-a.lex);
 dense.slice(0,30).forEach((c,i)=>c.fused+=1/(20+i+1));sparse.slice(0,30).forEach((c,i)=>c.fused+=1/(20+i+1));
 const fused=[...candidates].filter(c=>c.dense>=(dense[0]?.dense??0)-.075).sort((a,b)=>b.fused-a.fused);
 const semanticLead=dense[0],pageGap=semanticLead?semanticLead.dense-(dense.find(c=>c.p.page!==semanticLead.p.page||c.p.documentId!==semanticLead.p.documentId)?.dense??semanticLead.dense):0;
 const minimumGap=semanticLead?.dense>=.5?.04:.06;
 const top=semanticLead?.dense>=.40&&pageGap>=minimumGap?semanticLead:fused[0];
 const suggest=(items:typeof candidates)=>{
  const seen=new Set<string>();return items.flatMap(({p})=>{
   const label=p.section;
   const key=compactText(label);if(seen.has(key)||!label.trim()||label.length>50)return [];seen.add(key);
   return [{label,question:`${label}에 대해 알려줘`}];
  }).slice(0,3);
 };
 result.suggestions=(dense[0]?.dense??0)>=.4?suggest(fused):[];
 if(vague)return {...result,status:'clarify',message:'어떤 내용을 확인하려는 건가요? 관련 문서에서 찾은 항목 중 선택하거나 궁금한 대상을 알려 주세요.'};
 if(unknownSpecs.length)return {...result,message:`관련 주제는 찾았지만 요청한 ‘${unknownSpecs.join('·')}’ 정보는 현재 문서 근거로 확인하지 못했어요. 아래 항목은 다른 관련 내용이에요.`};
 // Cosine is a ranking signal, never a probability or proof of answerability.
 const semanticClear=top&&top.dense>=.40&&dense.indexOf(top)<5&&top.dense-(dense.find(c=>c.p.page!==top.p.page||c.p.documentId!==top.p.documentId)?.dense??top.dense)>=(top.dense>=.5?.04:.06);
 if(!lexical.length&&!semanticClear)return {...result,status:'clarify',message:'질문의 뜻을 한 가지로 좁히지 못했어요. 아래 관련 항목 중 어떤 내용인지 알려 주세요. 다른 뜻이라면 대상이나 상황을 조금 더 설명해 주세요.'};
 if(lexical.length){
  // Keep complete procedural/conditional context from established retrieval;
  // semantic candidates participate independently in discovery and clarification.
  result.sources=lexical;result.status='answered';result.message='';result.suggestions=[];
  return result;
 }
 if(!top)return result;
 const p=top.p,chunk=chunks.find(c=>c.documentId===p.documentId&&c.pageNumber===p.page&&c.chunkIndex===p.chunkIndex)!;
 const units=evidenceUnits({chunk,score:top.fused});
 const selected=completeScope(p,chunk);
 const section=p.section;
 const context=units.filter(u=>(u.section===section||/반드시|금지|없다면/.test(u.text))&&!compactText(p.text).includes(compactText(u.text)));
 const evidence=[...selected,...context].filter((u,i,all)=>all.findIndex(x=>x.order===u.order)===i);
 result.sources=[{chunk,score:top.fused,evidence}];result.status='answered';result.message='';result.suggestions=[];
 return result;
}
