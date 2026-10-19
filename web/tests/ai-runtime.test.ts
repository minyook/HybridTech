import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const fixture=vi.hoisted(()=>({settings:{contextTokens:8192,maxOutputTokens:512},installed:true,runtime:true,backend:'idb'}));
vi.mock('../src/ai/preferences',()=>({loadAISettings:async()=>({...fixture.settings}),RECOVERY_AI_SETTINGS:{contextTokens:2048,maxOutputTokens:256},saveAISettings:async(value:typeof fixture.settings)=>{fixture.settings={...value};return {...value};}}));
vi.mock('../src/ai/model',()=>({installedModel:async()=>fixture.installed?{blob:new Blob(['model']),metadata:{sha256:'verified',backend:fixture.backend,storageKey:'stored-model',size:5}}:undefined}));
vi.mock('../src/offline/assets',()=>({assetUrl:(path:string)=>`https://local.invalid/${path}`,preparedGroups:async()=>({runtime:fixture.runtime})}));
import { aiIsReady, buildPrompt, generate, getAIStatus, prepareAI, releaseModel, retryAIWithReducedContext, SYSTEM_INSTRUCTION } from '../src/ai/runtime';
import { AI_OPERATION_KEY } from '../src/ai/recovery';
import type { AIReply, AIRequest } from '../src/ai/protocol';

class FakeWorker {
  static instances:FakeWorker[]=[];
  onmessage?:((event:{data:AIReply})=>void);onerror?:((event:Event)=>void);
  messages:AIRequest[]=[];terminated=false;
  constructor(public url:string){FakeWorker.instances.push(this);}
  postMessage(message:AIRequest){this.messages.push(message);}
  terminate(){this.terminated=true;}
  reply(message:AIReply){this.onmessage?.({data:message});}
}
beforeEach(async()=>{
  await releaseModel();FakeWorker.instances=[];fixture.settings={contextTokens:8192,maxOutputTokens:512};fixture.installed=true;fixture.runtime=true;fixture.backend='idb';
  vi.stubGlobal('Worker',FakeWorker);vi.stubGlobal('navigator',{gpu:{}});vi.stubGlobal('isSecureContext',true);
});
afterEach(async()=>{await releaseModel();vi.unstubAllGlobals();});
async function ready(){const promise=prepareAI();await vi.waitFor(()=>expect(FakeWorker.instances).toHaveLength(1));const worker=FakeWorker.instances[0];worker.reply({id:worker.messages[0].id,type:'done'});await promise;return worker;}
function localStore(){
  const values=new Map<string,string>();vi.stubGlobal('localStorage',{getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value),removeItem:(key:string)=>values.delete(key)});return values;
}
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
  worker.reply({id,type:'done',result:{answer:'답변',usage:{maxOutputTokens:512,contextTokens:8192}}});await generating;
  expect(values.has(AI_OPERATION_KEY)).toBe(false);
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
