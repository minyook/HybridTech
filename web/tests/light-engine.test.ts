import { expect, it, vi } from 'vitest';
import type { MLCEngine } from '@mlc-ai/web-llm';
import { LightWorkerEngine } from '../src/ai/light-engine';
import { createAnswerPlan } from '../src/rag/answer-plan';
import type { AIReply, AIRequest } from '../src/ai/protocol';
const initialize:AIRequest={id:1,type:'initialize',provider:'light',model:new Blob(),sha256:'unused',runtimeUrl:'unused',settings:{contextTokens:2048,maxOutputTokens:256}};
const sources=[{score:1,chunk:{id:'a',documentId:'g',documentTitle:'문서',sourceKind:'guide',pageNumber:1,chunkIndex:0,heading:'',body:'▶ 측정 • 채기관은 1m, 5m 눈금으로 표시합니다.'}}];
function fixture(){
  const device={destroy:vi.fn(),addEventListener:vi.fn(),lost:new Promise<{reason:string}>(()=>{})};
  const bridge={__fieldguardCountTokens:(text:string)=>Math.ceil(text.length/3),__fieldguardWasmBytes:()=>64*1024*1024,__fieldguardDevice:device};
  const create=vi.fn(async function*(){yield {choices:[{delta:{content:'{"evidenceIds":[],"text":""}'}}]};yield {choices:[],usage:{prompt_tokens:123,completion_tokens:12,total_tokens:135}};});
  const engine={reload:vi.fn(),unload:vi.fn(async()=>{}),interruptGenerate:vi.fn(async()=>{}),chat:{completions:{create}}};
  const factory=vi.fn(()=>engine as unknown as MLCEngine),installed=vi.fn(async()=>true),replies:AIReply[]=[];
  return {worker:new LightWorkerEngine(factory,installed,bridge),replies,reply:(r:AIReply)=>replies.push(r),engine,bridge,device,installed,factory};
}
const request=(question='측정방법 쉽게 설명해줘'):AIRequest=>({id:2,type:'generate',prompt:'',question,sources,settings:{contextTokens:2048,maxOutputTokens:512}});
it('모델 누락·문맥 초과 시 엔진을 만들지 않는다',async()=>{
  const f=fixture();f.installed.mockResolvedValue(false);await f.worker.handle(initialize,f.reply);expect(f.factory).not.toHaveBeenCalled();
  await f.worker.handle({...initialize,settings:{contextTokens:8192,maxOutputTokens:256}},f.reply);expect(f.factory).not.toHaveBeenCalled();
});
it('일반 절차 질문은 본문을 먼저 표시하고 생성 없이 정확한 0 토큰을 반환한다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);await f.worker.handle(request('측정방법'),f.reply);
  expect(f.engine.chat.completions.create).not.toHaveBeenCalled();expect(f.replies.find(r=>r.id===2&&r.type==='text')).toMatchObject({answer:expect.stringContaining('1m')});
  expect(f.replies.at(-1)).toMatchObject({type:'done',cleanupComplete:true,result:{generated:false,supplementSkipped:'not-needed',usage:{inputTokens:0,outputTokens:0,totalTokens:0,maxOutputTokens:0}}});
});
it('추가 설명은 단 한 번 생성하고 실제 SDK 사용량을 보존한다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);await f.worker.handle(request(),f.reply);
  expect(f.engine.chat.completions.create).toHaveBeenCalledTimes(1);
  expect(f.engine.chat.completions.create).toHaveBeenCalledWith(expect.objectContaining({max_tokens:256,extra_body:{enable_thinking:false}}));
  expect(f.replies.at(-1)).toMatchObject({type:'done',result:{generated:true,supplementSkipped:'validation',answer:expect.stringContaining('5m'),usage:{inputTokens:123,outputTokens:12,totalTokens:135}}});
  // Incomplete or rejected model text never replaces the visible body.
  expect(f.replies.filter(r=>r.id===2&&r.type==='text')).toHaveLength(1);
});
it('필수 근거 전체가 입력 예산을 넘으면 자르거나 순차 생성하지 않는다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);f.bridge.__fieldguardCountTokens=()=>5000;
  await f.worker.handle(request(),f.reply);expect(f.engine.chat.completions.create).not.toHaveBeenCalled();
  expect(f.replies.at(-1)).toMatchObject({type:'done',result:{generated:false,supplementSkipped:'budget',answer:expect.stringContaining('1m')}});
});
it('생성 중 중단 요청은 busy 대기열을 우회하지만 unload 완료 전에는 정리 완료를 알리지 않는다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);
  let resume:()=>void=()=>{},unload:()=>void=()=>{};
  f.engine.chat.completions.create.mockImplementation(async function*(){await new Promise<void>(r=>{resume=r;});yield {choices:[{delta:{content:'unvalidated'}}]};});
  f.engine.unload.mockImplementation(()=>new Promise<void>(r=>{unload=r;}));
  const running=f.worker.handle(request(),f.reply);await vi.waitFor(()=>expect(f.engine.chat.completions.create).toHaveBeenCalled());
  await f.worker.handle({id:3,type:'stop'},f.reply);expect(f.engine.interruptGenerate).toHaveBeenCalledTimes(1);
  expect(f.replies.some(r=>r.type==='stopped')).toBe(false);resume();
  await vi.waitFor(()=>expect(f.engine.unload).toHaveBeenCalled());expect(f.replies.some(r=>r.type==='stopped')).toBe(false);
  unload();await running;expect(f.device.destroy).toHaveBeenCalled();expect(f.replies.at(-1)).toMatchObject({id:3,type:'stopped',cleanupComplete:true});
  expect(f.replies.some(r=>r.id===2&&r.type==='done')).toBe(false);
});
it('unload 실패는 두 번째 빈 dispose로 정상 종료로 둔갑시키지 않는다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);f.engine.unload.mockRejectedValue(Error('cleanup failed'));
  await f.worker.handle(request(),f.reply);expect(f.engine.unload).toHaveBeenCalledTimes(1);
  expect(f.replies.at(-1)).toMatchObject({type:'error',cleanupComplete:false});
});
it('길이 종료·미완성 JSON·본문과 같은 설명은 추가하지 않는다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);
  const plan=createAnswerPlan('쉽게 설명해줘',sources);
  f.engine.chat.completions.create.mockImplementation(async function*(){yield {choices:[{delta:{content:JSON.stringify({evidenceIds:[plan.evidence[0].id],text:plan.evidence[0].text})},finish_reason:'length'}]};});
  await f.worker.handle(request(),f.reply);expect(f.replies.at(-1)).toMatchObject({type:'done',result:{answer:plan.body,supplementSkipped:'validation'}});
});
