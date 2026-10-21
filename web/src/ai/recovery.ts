export const AI_OPERATION_KEY='fieldguard-ai-operation';
export const AI_STAGES=['verification','runtime','engine','generation','conversation','prefill','decode','cleanup'] as const;
export type AIStage=typeof AI_STAGES[number];
export interface Operation {version:1;id:string;stage:AIStage;contextTokens:number;startedAt:number;state?:'uncertain';reason?:string;revision?:string;model?:string}
let activeId:string|undefined;
let uncertain:Operation|undefined;
function stored():Operation|undefined {
  try {
    const value=JSON.parse(localStorage.getItem(AI_OPERATION_KEY)??'null');
    if(value?.version===1&&typeof value.id==='string'&&AI_STAGES.includes(value.stage)&&Number.isFinite(value.startedAt))return value;
  } catch { /* Storage can be unavailable in restricted browser contexts. */ }
}
export function unfinishedAIOperation(){const value=uncertain??stored();return value?.state==='uncertain'||value?.id!==activeId?value:undefined;}
export function beginAIOperation(stage:AIStage,contextTokens:number,metadata:{revision?:string;model?:string}={}){
  activeId=crypto.randomUUID();
  try {localStorage.setItem(AI_OPERATION_KEY,JSON.stringify({version:1,id:activeId,stage,contextTokens,startedAt:Date.now(),...metadata}));return stored()?.id===activeId;}catch{return false;}
}
export function markAIUncertain(reason:string){
  const value=stored();
  // Never overwrite the operation of another page. A local latch still blocks us.
  uncertain={...(value??{version:1,id:activeId??crypto.randomUUID(),stage:'cleanup',contextTokens:0,startedAt:Date.now()}),state:'uncertain',reason};
  if(!value||value.id===activeId)try{localStorage.setItem(AI_OPERATION_KEY,JSON.stringify(uncertain));}catch{/* Local latch remains active. */}
  activeId=undefined;
}
export function updateAIStage(stage:AIStage){const value=stored();if(value&&value.id===activeId)try{localStorage.setItem(AI_OPERATION_KEY,JSON.stringify({...value,stage}));}catch{/* Optional diagnostics. */}}
export function finishAIOperation(){
  if(activeId&&stored()?.id===activeId&&stored()?.state!=='uncertain'&&!uncertain)try{localStorage.removeItem(AI_OPERATION_KEY);}catch{/* Best effort. */}
  activeId=undefined;
}
export function resetAIRecovery(){try{localStorage.removeItem(AI_OPERATION_KEY);}catch{/* Preparation separately verifies persistence. */}activeId=undefined;uncertain=undefined;}
export function recoveryMessage(stage:AIStage){
  const names={verification:'모델 검사',runtime:'실행 파일 준비',engine:'모델 메모리 적재',generation:'답변 생성',conversation:'대화 작업 공간 준비',prefill:'질문·문서 입력 처리',decode:'답변 토큰 생성',cleanup:'AI 메모리 정리'};
  return `이전 또는 다른 화면의 ${names[stage]} 완료를 확인하지 못해 자동 재시도를 멈췄어요. 저장된 모델은 유지돼요. 다른 AI 화면을 닫은 뒤 문맥을 줄여 다시 준비하거나 문서 기능을 이용해 주세요.`;
}
