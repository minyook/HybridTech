import { expect, it, vi } from 'vitest';
import type { MLCEngine } from '@mlc-ai/web-llm';
import { LightWorkerEngine } from '../src/ai/light-engine';
import type { AIReply, AIRequest } from '../src/ai/protocol';
const initialize:AIRequest={id:1,type:'initialize',provider:'light',model:new Blob(),sha256:'unused',runtimeUrl:'unused',settings:{contextTokens:2048,maxOutputTokens:256}};
function fixture(){
  const device={destroy:vi.fn(),addEventListener:vi.fn(),lost:new Promise<{reason:string}>(()=>{})};
  const bridge={__fieldguardCountTokens:(text:string)=>text.length,__fieldguardWasmBytes:()=>64*1024*1024,__fieldguardDevice:device};
  const create=vi.fn(async function*(){yield {choices:[{delta:{content:'안전 '}}]};yield {choices:[{delta:{content:'답변'}}]};yield {choices:[],usage:{prompt_tokens:123,completion_tokens:2,total_tokens:125}};});
  const engine={reload:vi.fn(),unload:vi.fn(async()=>{}),chat:{completions:{create}}};
  const factory=vi.fn(()=>engine as unknown as MLCEngine),installed=vi.fn(async()=>true),replies:AIReply[]=[];
  return {worker:new LightWorkerEngine(factory,installed,bridge),replies,reply:(r:AIReply)=>replies.push(r),engine,bridge,device,installed,factory};
}
it('모델이 누락되거나 문맥이 늘어난 경우 GPU 엔진을 만들지 않는다',async()=>{
  const f=fixture();f.installed.mockResolvedValue(false);await f.worker.handle(initialize,f.reply);expect(f.factory).not.toHaveBeenCalled();expect(f.replies.at(-1)?.type).toBe('error');
  await f.worker.handle({...initialize,settings:{contextTokens:8192,maxOutputTokens:256}},f.reply);expect(f.factory).not.toHaveBeenCalled();
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
