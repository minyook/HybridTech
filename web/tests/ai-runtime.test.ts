import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const fixture=vi.hoisted(()=>({settings:{contextTokens:8192,maxOutputTokens:512},installed:true,lightInstalled:true,runtime:true,backend:'idb',forceSupplement:true}));
vi.mock('../src/rag/answer-plan',async importOriginal=>{const actual=await importOriginal<typeof import('../src/rag/answer-plan')>();return {...actual,createAnswerPlan:(question:string,sources:Parameters<typeof actual.createAnswerPlan>[1])=>({...actual.createAnswerPlan(question,sources),supplementRequested:fixture.forceSupplement})};});
vi.mock('../src/ai/light-model',async importOriginal=>({...await importOriginal<typeof import('../src/ai/light-model')>(),lightModelInstalled:async()=>fixture.lightInstalled}));
vi.mock('../src/ai/preferences',()=>({loadAISettings:async()=>({...fixture.settings}),RECOVERY_AI_SETTINGS:{contextTokens:2048,maxOutputTokens:256},saveAISettings:async(value:typeof fixture.settings)=>{fixture.settings={...value};return {...value};}}));
vi.mock('../src/ai/model',()=>({installedModel:async()=>fixture.installed?{blob:new Blob(['model']),metadata:{sha256:'verified',backend:fixture.backend,storageKey:'stored-model',size:5}}:undefined}));
vi.mock('../src/offline/assets',()=>({assetUrl:(path:string)=>`https://local.invalid/${path}`,preparedGroups:async()=>({runtime:fixture.runtime})}));
import { aiIsReady, buildPrompt, generate, getAIStatus, prepareAI, releaseModel, retryAIWithReducedContext, stopAI, SYSTEM_INSTRUCTION } from '../src/ai/runtime';
import { AI_OPERATION_KEY, resetAIRecovery } from '../src/ai/recovery';
import { aiDiagnosticReport } from '../src/ai/diagnostics';
import type { AIReply, AIRequest } from '../src/ai/protocol';

class FakeWorker {
  static instances:FakeWorker[]=[];
  onmessage?:((event:{data:AIReply})=>void);onerror?:((event:Event)=>void);
  messages:AIRequest[]=[];terminated=false;ackStop=true;
  constructor(public url:string){FakeWorker.instances.push(this);}
  postMessage(message:AIRequest){this.messages.push(message);if(message.type==='stop'&&this.ackStop)queueMicrotask(()=>this.reply({id:message.id,type:'stopped',cleanupComplete:true}));}
  terminate(){this.terminated=true;}
  reply(message:AIReply){this.onmessage?.({data:message.type==='error'||message.type==='done'?{cleanupComplete:true,...message}:message});}
}
beforeEach(async()=>{
  await releaseModel();localStore();resetAIRecovery();FakeWorker.instances=[];fixture.settings={contextTokens:8192,maxOutputTokens:512};fixture.installed=true;fixture.lightInstalled=true;fixture.runtime=true;fixture.backend='idb';fixture.forceSupplement=true;
  vi.stubGlobal('Worker',FakeWorker);vi.stubGlobal('navigator',{gpu:{}});vi.stubGlobal('isSecureContext',true);
});
afterEach(async()=>{await releaseModel();vi.unstubAllGlobals();});
async function ready(){const promise=prepareAI();await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(1));const worker=FakeWorker.instances[0];worker.reply({id:worker.messages[0].id,type:'done'});await promise;return worker;}
function localStore(){
  const values=new Map<string,string>();vi.stubGlobal('localStorage',{getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value),removeItem:(key:string)=>values.delete(key)});return values;
}
it('Gemma도 선택한 문단만 프롬프트에 넣고 전체 원문을 다시 섞지 않는다',()=>{
  const source={score:1,evidenceOrders:[1],chunk:{id:'a',documentId:'g',documentTitle:'문서',sourceKind:'guide',pageNumber:27,chunkIndex:0,heading:'',body:'▶ 사전준비 • 측정기의 산소농도를 확인합니다. ▶ 주의사항 • 가연성가스가 있을 수 있으므로 방폭구조의 전등을 사용하십시오.'}};
  const prompt=buildPrompt('어떤 전등을 써야 해?',[source]);
  expect(prompt).toContain('방폭구조의 전등');expect(prompt).not.toContain('측정기의 산소농도');expect(source.chunk.body).toContain('측정기의 산소농도');
});
it('아이폰의 일반 질문은 모델 적재를 기다리지 않고 문서 본문과 0 생성 토큰을 반환한다',async()=>{
  fixture.forceSupplement=false;vi.stubGlobal('navigator',{gpu:{},userAgent:'iPhone'});
  const sources=[{score:1,chunk:{id:'a',documentId:'g',documentTitle:'문서',sourceKind:'guide',pageNumber:1,chunkIndex:0,heading:'',body:'• 채기관은 1m마다 작은 눈금을 표시합니다.'}}];
  const onText=vi.fn(),result=await generate('채기관 측정방법',sources,onText,new AbortController().signal);
  expect(result).toMatchObject({generated:false,usage:{outputTokens:0},answer:expect.stringContaining('1m')});
  expect(onText).toHaveBeenCalledWith(result.answer);expect(FakeWorker.instances).toHaveLength(0);
});
it('복구 상태에서도 아이폰 문서 본문을 즉시 반환하고 자동 적재를 반복하지 않는다',async()=>{
  const values=localStore();values.set(AI_OPERATION_KEY,JSON.stringify({version:1,id:'old',stage:'engine',contextTokens:2048,startedAt:Date.now()}));
  vi.stubGlobal('navigator',{gpu:{},userAgent:'iPhone'});
  const result=await generate('쉽게 설명해줘',[],vi.fn(),new AbortController().signal);
  expect(result).toMatchObject({generated:false,supplementSkipped:'recovery'});expect(FakeWorker.instances).toHaveLength(0);expect(values.has(AI_OPERATION_KEY)).toBe(true);
});
it('중단 시간 초과 후 완료 기록을 지우지 않고 재적재를 잠근다',async()=>{
  const values=localStore(),worker=await ready();worker.ackStop=false;
  const controller=new AbortController(),running=generate('질문',[],vi.fn(),controller.signal),rejected=expect(running).rejects.toThrow('중단');
  await vi.waitFor(()=>expect(worker.messages.at(-1)?.type).toBe('generate'));
  vi.useFakeTimers();
  try{controller.abort();await vi.advanceTimersByTimeAsync(2001);await rejected;
    expect(worker.terminated).toBe(true);expect(JSON.parse(values.get(AI_OPERATION_KEY)!)).toMatchObject({state:'uncertain',reason:'stop-timeout'});
    expect(await prepareAI()).toBe(false);expect(FakeWorker.instances).toHaveLength(1);expect(getAIStatus().phase).toBe('recovery');
  }finally{vi.useRealTimers();}
});
it('정상 정리 확인을 받은 종료는 잠그지 않고 다음 준비를 허용한다',async()=>{
  const values=localStore(),worker=await ready();expect(await stopAI()).toBe(true);
  expect(worker.terminated).toBe(true);expect(values.has(AI_OPERATION_KEY)).toBe(false);
  const loading=prepareAI();await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(2));const next=FakeWorker.instances[1];next.reply({id:next.messages[0].id,type:'done'});expect(await loading).toBe(true);
});
it('아이폰은 복구 기록을 저장할 수 없으면 GPU 초기화 요청을 보내지 않는다',async()=>{
  vi.stubGlobal('navigator',{gpu:{},userAgent:'iPhone'});vi.stubGlobal('localStorage',{getItem:()=>{throw Error('denied');},setItem:()=>{throw Error('denied');},removeItem:()=>{throw Error('denied');}});
  expect(await prepareAI()).toBe(false);expect(FakeWorker.instances[0].messages).toHaveLength(0);expect(FakeWorker.instances[0].terminated).toBe(true);expect(getAIStatus().phase).toBe('recovery');
});
it('복구를 눌러도 다른 화면이 엔진 잠금을 보유하면 그 작업 기록을 지우지 않는다',async()=>{
  const values=localStore(),record=JSON.stringify({version:1,id:'other',stage:'engine',contextTokens:2048,startedAt:Date.now()});values.set(AI_OPERATION_KEY,record);
  vi.stubGlobal('navigator',{gpu:{},locks:{request:async(_name:string,_options:unknown,callback:(lock:null)=>Promise<void>)=>callback(null)}});
  await retryAIWithReducedContext();expect(values.get(AI_OPERATION_KEY)).toBe(record);expect(FakeWorker.instances).toHaveLength(0);
});
it('워커의 메모리 진단을 설정값과 저장하고 생성 완료로 오인하지 않는다',async()=>{
  localStore();const worker=await ready();worker.reply({id:worker.messages[0].id,type:'diagnostic',sample:{stage:'engine-ready',kind:'gpu-lost'}});
  expect(aiDiagnosticReport().events.at(-1)).toMatchObject({stage:'engine-ready',kind:'gpu-lost'});
  const generation=generate('private question',[],vi.fn(),new AbortController().signal);
  await vi.waitFor(()=>expect(worker.messages).toHaveLength(2));const id=worker.messages[1].id;
  worker.reply({id,type:'diagnostic',sample:{stage:'decode',kind:'memory',wasmHeapBytes:987654}});
  expect(getAIStatus().phase).toBe('generating');expect(aiDiagnosticReport().events.at(-1)).toMatchObject({stage:'decode',wasmHeapBytes:987654,contextTokens:8192,maxOutputTokens:512});
  expect(JSON.stringify(aiDiagnosticReport())).not.toContain('private question');
  worker.reply({id,type:'done',result:{answer:'답변',usage:{maxOutputTokens:512,contextTokens:8192}}});expect((await generation).answer).toBe('답변');
});
it('미완료 적재 기록이 있으면 재접속과 질문에서 자동 재시도하지 않고 축소 설정으로만 복구한다',async()=>{
  const values=localStore();values.set(AI_OPERATION_KEY,JSON.stringify({version:1,id:'closed-page',stage:'engine',contextTokens:8192,startedAt:Date.now()}));
  expect(await prepareAI()).toBe(false);expect(getAIStatus().phase).toBe('recovery');expect(FakeWorker.instances).toHaveLength(0);
  await expect(generate('질문',[],vi.fn(),new AbortController().signal)).rejects.toThrow('자동 재시도');expect(values.has(AI_OPERATION_KEY)).toBe(true);
  const changed=vi.fn();const retry=retryAIWithReducedContext(changed);await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(1));const worker=FakeWorker.instances[0];
  expect(changed).toHaveBeenCalledWith({contextTokens:2048,maxOutputTokens:256});expect(worker.messages[0]).toMatchObject({type:'initialize',settings:{contextTokens:2048,maxOutputTokens:256}});worker.reply({id:worker.messages[0].id,type:'done'});await retry;
  expect(getAIStatus().phase).toBe('ready');expect(values.has(AI_OPERATION_KEY)).toBe(false);
});
it('OPFS 모델은 큰 Blob 대신 저장 위치만 워커에 전달한다',async()=>{
  fixture.backend='opfs';const worker=await ready();expect(worker.messages[0]).toMatchObject({model:{backend:'opfs',storageKey:'stored-model',size:5}});
});
it('아이폰은 기존 Gemma만 있어도 무거운 워커를 생성하지 않고 경량 모델 다운로드를 안내한다',async()=>{
  vi.stubGlobal('navigator',{gpu:{},userAgent:'iPhone'});fixture.lightInstalled=false;
  expect(await prepareAI()).toBe(false);expect(FakeWorker.instances).toHaveLength(0);expect(getAIStatus().message).toContain('경량 모델');
});
it('아이폰 경량 준비에는 LiteRT 파일이 필요 없고 Gemma Blob도 전달하지 않는다',async()=>{
  vi.stubGlobal('navigator',{gpu:{},userAgent:'iPhone'});fixture.runtime=false;
  const worker=await ready();expect(worker.url).toBe('https://local.invalid/light-ai-worker.js');
  const message=worker.messages[0];expect(message).toMatchObject({provider:'light'});if(message.type==='initialize')expect((message.model as Blob).size).toBe(0);
});
it('iPhone의 실행 프로필을 워커에 전달하고 실제 생성 단계만 중단 기록에 남긴다',async()=>{
  const values=localStore();vi.stubGlobal('navigator',{gpu:{},userAgent:'iPhone'});
  const worker=await ready();expect(worker.messages[0]).toMatchObject({profile:'apple-mobile'});
  const generating=generate('질문',[],vi.fn(),new AbortController().signal);
  await vi.waitFor(()=>expect(worker.messages).toHaveLength(2));const id=worker.messages[1].id;
  worker.reply({id,type:'progress',stage:'prefill',message:'문서 입력 처리 중'});
  expect(getAIStatus()).toMatchObject({phase:'generating',message:'문서 입력 처리 중'});
  expect(JSON.parse(values.get(AI_OPERATION_KEY)!)).toMatchObject({stage:'prefill',contextTokens:8192});
  worker.reply({id,type:'progress',stage:'decode',message:'첫 토큰 생성 중'});
  expect(JSON.parse(values.get(AI_OPERATION_KEY)!)).toMatchObject({stage:'decode'});
  worker.reply({id,type:'progress',stage:'cleanup',message:'AI 메모리 정리 중'});
  expect(getAIStatus()).toMatchObject({phase:'generating',message:'AI 메모리 정리 중'});
  expect(JSON.parse(values.get(AI_OPERATION_KEY)!)).toMatchObject({stage:'cleanup'});
  worker.reply({id,type:'done',result:{answer:'답변',usage:{maxOutputTokens:512,contextTokens:8192}}});await generating;
  expect(worker.terminated).toBe(true);
  await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(2));const next=FakeWorker.instances[1];
  next.reply({id:next.messages[0].id,type:'done'});await prepareAI();expect(values.has(AI_OPERATION_KEY)).toBe(false);
});
it('iPhone 연속 질문은 매번 한 워커만 준비하고 이전 워커의 지연 응답을 무시한다',async()=>{
  const values=localStore();vi.stubGlobal('navigator',{gpu:{},userAgent:'iPhone'});let worker=await ready();
  for(let round=0;round<3;round++){
    const generation=generate(`질문 ${round}`,[],vi.fn(),new AbortController().signal);
    await vi.waitFor(()=>expect(worker.messages.at(-1)?.type).toBe('generate'));const id=worker.messages.at(-1)!.id;
    worker.reply({id,type:'done',result:{answer:`답변 ${round}`,usage:{maxOutputTokens:512,contextTokens:8192}}});
    expect((await generation).answer).toBe(`답변 ${round}`);expect(worker.terminated).toBe(true);
    worker.reply({id,type:'progress',stage:'decode',message:'폐기된 응답'});expect(getAIStatus().message).not.toBe('폐기된 응답');
    await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(round+2));
    expect(FakeWorker.instances.filter(candidate=>!candidate.terminated)).toHaveLength(1);
    const retired=worker;retired.onerror?.(new Event('error'));expect(getAIStatus().phase).not.toBe('error');
    worker=FakeWorker.instances[round+1];expect(worker.messages).toHaveLength(1);expect(worker.messages[0]).toMatchObject({type:'initialize',profile:'apple-mobile'});
    expect(worker.terminated).toBe(false);
    worker.reply({id:worker.messages[0].id,type:'done'});await prepareAI();expect(aiIsReady()).toBe(true);expect(values.has(AI_OPERATION_KEY)).toBe(false);
  }
});
it('자동 준비를 기다리는 두 번째 질문을 취소해도 새 엔진의 준비를 중단하지 않는다',async()=>{
  const values=localStore();vi.stubGlobal('navigator',{gpu:{},userAgent:'iPhone'});const first=await ready();
  const generation=generate('첫 질문',[],vi.fn(),new AbortController().signal);await vi.waitFor(()=>expect(first.messages).toHaveLength(2));first.reply({id:first.messages[1].id,type:'done',result:{answer:'첫 답변',usage:{maxOutputTokens:512,contextTokens:8192}}});await generation;
  await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(2));const next=FakeWorker.instances[1],controller=new AbortController();
  const queued=generate('두 번째 질문',[],vi.fn(),controller.signal);const rejection=expect(queued).rejects.toThrow();controller.abort();await rejection;
  expect(next.terminated).toBe(false);expect(next.messages).toHaveLength(1);expect(values.has(AI_OPERATION_KEY)).toBe(true);
  next.reply({id:next.messages[0].id,type:'done'});await prepareAI();expect(aiIsReady()).toBe(true);expect(values.has(AI_OPERATION_KEY)).toBe(false);
});
it('데스크톱 연속 질문은 준비된 엔진과 워커를 유지한다',async()=>{
  const worker=await ready();
  for(let round=0;round<2;round++){
    const generation=generate(`질문 ${round}`,[],vi.fn(),new AbortController().signal);
    await vi.waitFor(()=>expect(worker.messages).toHaveLength(round+2));const id=worker.messages.at(-1)!.id;
    worker.reply({id,type:'done',result:{answer:'답변',usage:{maxOutputTokens:512,contextTokens:8192}}});await generation;
    expect(worker.terminated).toBe(false);expect(FakeWorker.instances).toHaveLength(1);expect(aiIsReady()).toBe(true);
  }
});
it('준비를 기다리는 질문을 중단해도 진행 중인 초기화의 복구 기록을 지우지 않는다',async()=>{
  const values=localStore();const controller=new AbortController();const generating=generate('질문',[],vi.fn(),controller.signal);const rejected=expect(generating).rejects.toThrow();
  await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(1));controller.abort();await rejected;expect(values.has(AI_OPERATION_KEY)).toBe(true);
  const worker=FakeWorker.instances[0];worker.reply({id:worker.messages[0].id,type:'done'});await vi.waitFor(()=>expect(values.has(AI_OPERATION_KEY)).toBe(false));
});
it('다른 탭이 GPU 엔진 잠금을 가진 경우 두 번째 엔진을 만들지 않는다',async()=>{
  vi.stubGlobal('navigator',{gpu:{},locks:{request:async(_name:string,_options:unknown,callback:(lock:null)=>Promise<void>)=>callback(null)}});
  expect(await prepareAI()).toBe(false);expect(getAIStatus().phase).toBe('recovery');expect(FakeWorker.instances).toHaveLength(0);
});
it('엔진 잠금은 준비 완료 후 유지하고 워커 종료 때 반환한다',async()=>{
  let released=false;vi.stubGlobal('navigator',{gpu:{},locks:{request:async(_name:string,_options:unknown,callback:(lock:object)=>Promise<void>)=>{await callback({});released=true;}}});
  await ready();expect(released).toBe(false);await releaseModel();await vi.waitFor(()=>expect(released).toBe(true));
});
it('문맥 변경 후 잠금 확인이 실패해도 준비 중 상태로 멈추지 않는다',async()=>{
  await ready();fixture.settings.contextTokens=4096;
  vi.stubGlobal('navigator',{gpu:{},locks:{request:async()=>{throw Error('lock denied');}}});
  await expect(prepareAI()).rejects.toThrow('lock denied');expect(getAIStatus().phase).toBe('error');
});

it('중복된 자동 준비 요청은 한 번만 초기화하고 화면에 단계를 전달한다',async()=>{
  const first=prepareAI();const second=prepareAI();await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(1));const worker=FakeWorker.instances[0];
  expect(worker.messages).toHaveLength(1);expect(worker.url).toBe('https://local.invalid/ai-worker.js');
  worker.reply({id:worker.messages[0].id,type:'progress',message:'GPU 준비 중'});expect(getAIStatus().message).toBe('GPU 준비 중');
  worker.reply({id:worker.messages[0].id,type:'done'});await Promise.all([first,second]);expect(aiIsReady()).toBe(true);
});
it('모델이 없으면 다운로드하거나 실행한 것처럼 표시하지 않는다',async()=>{
  fixture.installed=false;expect(await prepareAI()).toBe(false);expect(aiIsReady()).toBe(false);expect(FakeWorker.instances).toHaveLength(0);
});
it('아이폰에서 WebGPU가 없으면 모델이나 워커를 준비하지 않고 원인을 표시한다',async()=>{
  vi.stubGlobal('navigator',{userAgent:'iPhone; CPU iPhone OS 18_6_2 like Mac OS X'});
  expect(await prepareAI()).toBe(false);expect(FakeWorker.instances).toHaveLength(0);
  expect(getAIStatus()).toMatchObject({phase:'unavailable',message:'이 브라우저에서 WebGPU를 사용할 수 없어요'});
});
it('신뢰된 보안 연결이 아니면 별도의 원인으로 AI를 차단한다',async()=>{
  vi.stubGlobal('isSecureContext',false);
  expect(await prepareAI()).toBe(false);expect(FakeWorker.instances).toHaveLength(0);
  expect(getAIStatus().message).toContain('보안 연결');
});
it('워커의 실제 준비 시간과 검증 재사용 여부를 표시하고 해제 시 초기화한다',async()=>{
  const loading=prepareAI();await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(1));const worker=FakeWorker.instances[0];
  const initialization={verification:'cached' as const,verificationMs:20,runtimeMs:100,engineMs:800,totalMs:920};
  worker.reply({id:worker.messages[0].id,type:'done',initialization});await loading;
  expect(getAIStatus().timing).toEqual(initialization);await releaseModel();expect(getAIStatus().timing).toBeUndefined();
});
it('자동 준비 중 들어온 질문은 완료를 기다렸다가 같은 엔진으로 생성한다',async()=>{
  const loading=prepareAI();const onText=vi.fn();const generating=generate('질문',[],onText,new AbortController().signal);
  await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(1));const worker=FakeWorker.instances[0];worker.reply({id:worker.messages[0].id,type:'done'});await loading;
  await vi.waitFor(()=>expect(worker.messages).toHaveLength(2));const message=worker.messages[1];expect(message).toMatchObject({type:'generate',systemInstruction:SYSTEM_INSTRUCTION,settings:{maxOutputTokens:512}});
  worker.reply({id:message.id,type:'text',answer:'답변'});worker.reply({id:message.id,type:'done',result:{answer:'답변',usage:{maxOutputTokens:512,contextTokens:8192,totalTokens:123}}});
  expect((await generating).answer).toBe('답변');expect(onText).toHaveBeenCalledWith('답변');expect(aiIsReady()).toBe(true);
});
it('답변 중단 시 워커를 종료하고 다음 질문에서 새로 준비할 수 있다',async()=>{
  const worker=await ready();const controller=new AbortController();const generating=generate('질문',[],vi.fn(),controller.signal);const rejected=expect(generating).rejects.toThrow('중단');
  await vi.waitFor(()=>expect(worker.messages).toHaveLength(2));controller.abort();await rejected;expect(worker.terminated).toBe(true);expect(aiIsReady()).toBe(false);
  const loading=prepareAI();await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(2));const next=FakeWorker.instances[1];next.reply({id:next.messages[0].id,type:'done'});expect(await loading).toBe(true);
});
it('출력 한도만 바꾸면 엔진을 재사용하고 문맥 크기를 바꾸면 새 엔진에 적용한다',async()=>{
  const worker=await ready();fixture.settings.maxOutputTokens=1024;await prepareAI();expect(FakeWorker.instances).toHaveLength(1);
  fixture.settings.contextTokens=4096;const loading=prepareAI();await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(2));expect(worker.terminated).toBe(true);
  const next=FakeWorker.instances[1];expect(next.messages[0]).toMatchObject({type:'initialize',settings:{contextTokens:4096,maxOutputTokens:1024}});next.reply({id:next.messages[0].id,type:'done'});await loading;
});
it('초기화 실패 뒤에도 질문에서 다시 자동 준비할 수 있다',async()=>{
  const promise=prepareAI();const rejected=expect(promise).rejects.toThrow('GPU 오류');await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(1));const worker=FakeWorker.instances[0];worker.reply({id:worker.messages[0].id,type:'error',message:'GPU 오류'});await rejected;
  expect(getAIStatus().phase).toBe('error');const retry=prepareAI();await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(2));const next=FakeWorker.instances[1];next.reply({id:next.messages[0].id,type:'done'});expect(await retry).toBe(true);
});
it('준비 중 문맥 설정을 바꿔도 질문은 새 문맥 엔진이 준비될 때까지 기다린다',async()=>{
  const loading=prepareAI();const generating=generate('질문',[],vi.fn(),new AbortController().signal);
  await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(1));const first=FakeWorker.instances[0];fixture.settings.contextTokens=4096;
  first.reply({id:first.messages[0].id,type:'done'});await loading;
  await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(2));const next=FakeWorker.instances[1];expect(next.messages[0]).toMatchObject({type:'initialize',settings:{contextTokens:4096}});
  next.reply({id:next.messages[0].id,type:'done'});await vi.waitFor(()=>expect(next.messages).toHaveLength(2));const message=next.messages[1];
  next.reply({id:message.id,type:'done',result:{answer:'새 설정 답변',usage:{contextTokens:4096,maxOutputTokens:512}}});expect((await generating).answer).toBe('새 설정 답변');
});

it('손상된 스트리밍은 숨기고 최종 답변은 거부해 원문 전환 경로로 보낸다',async()=>{
  const worker=await ready();const onText=vi.fn();const generating=generate('산소 측정방법',[],onText,new AbortController().signal);
  const rejected=expect(generating).rejects.toThrow('언어가 섞여');
  await vi.waitFor(()=>expect(worker.messages).toHaveLength(2));const message=worker.messages[1];const answer='측 Supplement 측شون 측 Layerनुसार';
  worker.reply({id:message.id,type:'text',answer});
  expect(onText).toHaveBeenLastCalledWith('생성된 문장을 확인하고 있어요…');expect(onText).not.toHaveBeenCalledWith(answer);
  worker.reply({id:message.id,type:'done',result:{answer,usage:{contextTokens:8192,maxOutputTokens:512}}});
  await rejected;expect(aiIsReady()).toBe(true);
});
it('시스템 지시와 질문·원문 데이터를 분리한다',()=>{
  const prompt=buildPrompt('산소 측정방법',[]);
  expect(prompt).toContain('[질문]\n산소 측정방법');expect(prompt).not.toContain(SYSTEM_INSTRUCTION);
  expect(SYSTEM_INSTRUCTION).toContain('한국어');expect(SYSTEM_INSTRUCTION).toContain('수치·단위·조건');
});
it('경량 심폐소생술에서 핵심 압박 절차가 빠지면 스트림을 보류하고 원문 전환 오류로 반환한다',async()=>{
  vi.stubGlobal('navigator',{gpu:{},userAgent:'iPhone'});const worker=await ready(),onText=vi.fn();
  const sources=[{score:1,chunk:{id:'cpr',documentId:'guide',documentTitle:'문서',pageNumber:40,sourceKind:'guide',chunkIndex:0,heading:'',body:'심폐소생술 흉부 압박 절차'}}];
  const generation=generate('심폐소생술 알려줘',sources,onText,new AbortController().signal);const rejected=expect(generation).rejects.toThrow('핵심 절차');
  await vi.waitFor(()=>expect(worker.messages).toHaveLength(2));const id=worker.messages[1].id;
  worker.reply({id,type:'text',answer:'기도를 개방합니다.'});expect(onText).not.toHaveBeenCalledWith('기도를 개방합니다.');
  worker.reply({id,type:'done',result:{answer:'기도를 개방합니다.',usage:{contextTokens:2048,maxOutputTokens:256}}});await rejected;
});
