import { expect, it, vi } from 'vitest';
import type { MLCEngine } from '@mlc-ai/web-llm';
import { LightWorkerEngine } from '../src/ai/light-engine';
import type { AIReply, AIRequest } from '../src/ai/protocol';
const initialize:AIRequest={id:1,type:'initialize',provider:'light',model:new Blob(),sha256:'unused',runtimeUrl:'unused',settings:{contextTokens:2048,maxOutputTokens:256}};
function fixture(){
  const device={destroy:vi.fn(),addEventListener:vi.fn(),lost:new Promise<{reason:string}>(()=>{})};
  const bridge={__fieldguardCountTokens:(text:string)=>text.length,__fieldguardWasmBytes:()=>64*1024*1024,__fieldguardDevice:device};
  const create=vi.fn(async function*(_request?:unknown){yield {choices:[{delta:{content:'안전 '}}]};yield {choices:[{delta:{content:'답변'}}]};yield {choices:[],usage:{prompt_tokens:123,completion_tokens:2,total_tokens:125}};});
  const engine={reload:vi.fn(),unload:vi.fn(async()=>{}),resetChat:vi.fn(async()=>{}),chat:{completions:{create}}};
  const factory=vi.fn(()=>engine as unknown as MLCEngine),installed=vi.fn(async()=>true),replies:AIReply[]=[];
  return {worker:new LightWorkerEngine(factory,installed,bridge),replies,reply:(r:AIReply)=>replies.push(r),engine,bridge,device,installed,factory};
}
it('모델이 누락되거나 문맥이 늘어난 경우 GPU 엔진을 만들지 않는다',async()=>{
  const f=fixture();f.installed.mockResolvedValue(false);await f.worker.handle(initialize,f.reply);expect(f.factory).not.toHaveBeenCalled();expect(f.replies.at(-1)?.type).toBe('error');
  await f.worker.handle({...initialize,settings:{contextTokens:8192,maxOutputTokens:256}},f.reply);expect(f.factory).not.toHaveBeenCalled();
});
it('한 답변의 근거 묶음 사이에 KV·대화를 순차 초기화하고 실제 사용량을 합산한다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);
  const sources=[{score:1,chunk:{id:'a',documentId:'g',documentTitle:'문서',sourceKind:'guide',pageNumber:1,chunkIndex:0,heading:'',body:'▶ 준비 • '+'장비 상태 확인 '.repeat(14)+'실시합니다.\n▶ 주의 • '+'외부 감시인 연락 '.repeat(14)+'유지합니다.'}}];
  let reset:()=>void=()=>{};f.engine.resetChat.mockImplementation(()=>new Promise<void>(resolve=>{reset=resolve;}));
  const generation=f.worker.handle({id:2,type:'generate',prompt:'',question:'작업 절차',sources,settings:{contextTokens:2048,maxOutputTokens:256}},f.reply);
  await vi.waitFor(()=>expect(f.engine.resetChat).toHaveBeenCalledTimes(1));expect(f.engine.chat.completions.create).toHaveBeenCalledTimes(1);
  reset();await generation;
  expect(f.engine.chat.completions.create).toHaveBeenCalledTimes(2);expect(f.factory).toHaveBeenCalledTimes(1);
  expect(f.replies.at(-1)).toMatchObject({type:'done',result:{usage:{inputTokens:246,outputTokens:4,totalTokens:250,maxOutputTokens:512,passes:2,perPassMaxOutputTokens:256,contextTokens:2048}}});
  for(const call of f.engine.chat.completions.create.mock.calls){const options=call[0] as unknown as {messages:{content:string}[]};expect(options.messages).toHaveLength(2);expect(options.messages[1].content).not.toContain('안전 답변');}
});
it('생성한 문단의 수치가 누락되면 해당 문단을 원문 표현으로 보완하고 별도 원문 답변은 붙이지 않는다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);
  const sources=[{score:1,chunk:{id:'a',documentId:'g',documentTitle:'문서',sourceKind:'guide',pageNumber:1,chunkIndex:0,heading:'',body:'▶ 측정 • 채기관은 1m, 5m 눈금으로 표시합니다.'}}];
  await f.worker.handle({id:2,type:'generate',prompt:'',question:'측정',sources,settings:{contextTokens:2048,maxOutputTokens:256}},f.reply);
  const result=f.replies.at(-1);expect(result).toMatchObject({type:'done',result:{sourceSupplemented:true,truncated:false}});
  if(result?.type==='done'){expect(result.result?.answer).toContain('1m');expect(result.result?.answer).toContain('5m');expect(result.result?.answer).not.toMatch(/AI 정리|문서에서 확인한 상세 내용/);}
});
it('생성의 실제 측정 토큰을 반환하고 GPU 정리가 끝난 뒤에만 완료를 알린다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);
  let finish:()=>void=()=>{};f.engine.unload.mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));
  const generation=f.worker.handle({id:2,type:'generate',prompt:'',question:'산소 기준',sources:[],systemInstruction:'한국어 안전 답변',settings:{contextTokens:2048,maxOutputTokens:256}},f.reply);
  await vi.waitFor(()=>expect(f.engine.unload).toHaveBeenCalled());expect(f.replies.filter(r=>r.id===2&&r.type==='done')).toHaveLength(0);
  expect(f.engine.chat.completions.create).toHaveBeenCalledWith(expect.objectContaining({extra_body:{enable_thinking:false},max_tokens:256,messages:[{role:'system',content:'한국어 안전 답변'},{role:'user',content:expect.stringContaining('산소 기준')}]}));
  finish();await generation;
  expect(f.device.destroy).toHaveBeenCalled();expect(f.replies.at(-1)).toMatchObject({type:'done',result:{answer:'안전 답변',usage:{inputTokens:123,outputTokens:2,totalTokens:125,contextTokens:2048,maxOutputTokens:256}}});
  expect(f.bridge.__fieldguardCountTokens).toBeUndefined();
});
it('GPU 제출 전에 질문 길이를 제한하고 실패 시에도 엔진을 정리한다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);await f.worker.handle({id:2,type:'generate',prompt:'질문'.repeat(2000),settings:{contextTokens:2048,maxOutputTokens:256}},f.reply);
  expect(f.engine.chat.completions.create).not.toHaveBeenCalled();expect(f.engine.unload).toHaveBeenCalled();expect(f.device.destroy).toHaveBeenCalled();expect(f.replies.at(-1)).toMatchObject({type:'error',message:expect.stringContaining('질문이')});
});
it('생성 실패 시 완료로 표시하지 않고 정리한다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);f.engine.chat.completions.create.mockRejectedValueOnce(Error('GPU failed'));
  await f.worker.handle({id:2,type:'generate',prompt:'질문',settings:{contextTokens:2048,maxOutputTokens:256}},f.reply);
  expect(f.replies.at(-1)?.type).toBe('error');expect(f.device.destroy).toHaveBeenCalled();
});
it('SDK가 길이 종료를 알리면 토큰 수가 한도 미만이어도 중단 표시와 완결 문장만 반환한다',async()=>{
  const f=fixture();await f.worker.handle(initialize,f.reply);
  f.engine.chat.completions.create.mockImplementation(async function*(){
    yield {choices:[{delta:{content:'1. 장비 상태를 확인합니다.\n2. 다음 단계는 미완'},finish_reason:'length'}]};
    yield {choices:[],usage:{prompt_tokens:1200,completion_tokens:508,total_tokens:1708}};
  });
  await f.worker.handle({id:2,type:'generate',prompt:'질문',settings:{contextTokens:2048,maxOutputTokens:512}},f.reply);
  expect(f.replies.at(-1)).toMatchObject({type:'done',result:{answer:'- 장비 상태를 확인합니다.',truncated:true,usage:{outputTokens:508,maxOutputTokens:512}}});
});
