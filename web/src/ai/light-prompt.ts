import type { Source } from '../types';

// Count with the *same live tokenizer* as WebLLM. Reserve control/template
// tokens and the entire output budget before any GPU prefill is submitted.
export function boundedLightPrompt(question:string,sources:Source[],count:(text:string)=>number,inputBudget:number){
  const questionPart=`[질문]\n${question}`;
  if(count(questionPart)>inputBudget)throw Error('질문이 문맥 한도보다 길어요. 질문을 나누어 입력해 주세요.');
  let context='';
  for(const {chunk} of sources){
    const heading=`\n[${chunk.documentTitle} ${chunk.pageNumber}쪽 · 문서 발췌]\n`;
    let excerpt='';
    for(const line of chunk.body.split(/\n|(?<=[.!?。])\s+/u).filter(Boolean)){
      if(count(`${context}${heading}${excerpt}${line}\n\n${questionPart}`)>inputBudget)break;
      excerpt+=line+'\n';
    }
    if(excerpt)context+=heading+excerpt;
  }
  if(sources.length&&!context)throw Error('문서 근거를 문맥 한도에 넣지 못했어요. 질문을 짧게 입력하거나 원문을 확인해 주세요.');
  return `${context}\n${questionPart}`;
}
