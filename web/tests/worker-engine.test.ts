import { beforeEach, expect, it, vi } from 'vitest';
const sdk=vi.hoisted(()=>({create:vi.fn(),load:vi.fn(),digest:vi.fn()}));
vi.mock('@litert-lm/core',()=>({Engine:{create:sdk.create},loadLiteRtLm:sdk.load}));
vi.mock('../src/ai/model',()=>({digestBlob:sdk.digest}));
import { WorkerEngine } from '../src/ai/worker-engine';
import type { AIReply, AIRequest } from '../src/ai/protocol';
const settings={contextTokens:4096,maxOutputTokens:512};
const init=():AIRequest=>({id:1,type:'initialize',model:new Blob(['model']),sha256:'verified',runtimeUrl:'https://local.invalid/runtime/',settings});
beforeEach(()=>{vi.clearAllMocks();sdk.digest.mockResolvedValue('verified');sdk.load.mockResolvedValue(undefined);});

it('무결성 검증 후 저장된 문맥 한도로 실제 SDK 엔진을 만든다',async()=>{
  sdk.create.mockResolvedValue({});const reply=vi.fn();
  await new WorkerEngine().handle(init(),reply);
  expect(sdk.load).toHaveBeenCalledWith('https://local.invalid/runtime/');
  expect((globalThis as typeof globalThis&{Module:{locateFile:(name:string)=>string}}).Module.locateFile('litertlm_wasm_internal.wasm')).toBe('https://local.invalid/runtime/litertlm_wasm_internal.wasm');
  expect(sdk.create).toHaveBeenCalledWith(expect.objectContaining({mainExecutorSettings:{maxNumTokens:4096}}));
  expect(reply).toHaveBeenLastCalledWith({id:1,type:'done'});
});
it('모델이 손상된 경우 GPU 엔진을 만들지 않고 오류를 반환한다',async()=>{
  sdk.digest.mockResolvedValue('broken');const reply=vi.fn();await new WorkerEngine().handle(init(),reply);
  expect(sdk.create).not.toHaveBeenCalled();expect(reply).toHaveBeenLastCalledWith(expect.objectContaining({type:'error',message:expect.stringContaining('검증에 실패')}));
});
it('저장한 답변 한도가 SDK에 전달되고 측정한 사용량만 반환한다',async()=>{
  const conversation={sendMessageStreaming:vi.fn(()=>new ReadableStream({start(controller){controller.enqueue({content:[{type:'text',text:'## 답변\n'}]});controller.enqueue({content:'원문 확인'});controller.close();}})),getBenchmarkInfo:vi.fn(async()=>({lastPrefillTokenCount:180,lastDecodeTokenCount:12})),getTokenCount:vi.fn(async()=>192),delete:vi.fn(async()=>{})};
  const createConversation=vi.fn(async()=>conversation);sdk.create.mockResolvedValue({createConversation});
  const engine=new WorkerEngine();await engine.handle(init(),vi.fn());const replies:AIReply[]=[];
  await engine.handle({id:2,type:'generate',prompt:'근거 질문',settings},message=>replies.push(message));
  expect(createConversation).toHaveBeenCalledWith(expect.objectContaining({sessionConfig:{maxOutputTokens:512}}));
  expect(replies).toContainEqual({id:2,type:'text',answer:'## 답변\n원문 확인'});
  expect(replies).toContainEqual({id:2,type:'done',result:{answer:'## 답변\n원문 확인',usage:{inputTokens:180,outputTokens:12,totalTokens:192,maxOutputTokens:512,contextTokens:4096}}});
  expect(conversation.delete).toHaveBeenCalledOnce();
});
it('SDK 측정치가 없으면 글자 수로 토큰 사용량을 꾸며내지 않는다',async()=>{
  const conversation={sendMessageStreaming:()=>new ReadableStream({start(controller){controller.enqueue({content:'답변'});controller.close();}}),getBenchmarkInfo:async()=>{throw new Error('unsupported');},getTokenCount:async()=>NaN,delete:async()=>{}};
  sdk.create.mockResolvedValue({createConversation:async()=>conversation});const engine=new WorkerEngine();await engine.handle(init(),vi.fn());const reply=vi.fn();
  await engine.handle({id:2,type:'generate',prompt:'질문',settings},reply);
  expect(reply).toHaveBeenLastCalledWith({id:2,type:'done',result:{answer:'답변',usage:{maxOutputTokens:512,contextTokens:4096}}});
});
