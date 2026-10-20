import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const sdk=vi.hoisted(()=>({create:vi.fn(),load:vi.fn(),verify:vi.fn()}));
vi.mock('@litert-lm/core',()=>({Engine:{create:sdk.create},loadLiteRtLm:sdk.load,SamplerType:{TOP_K:1}}));
vi.mock('../src/ai/model',()=>({verifyStoredModel:sdk.verify}));
import { WorkerEngine } from '../src/ai/worker-engine';
import type { AIReply, AIRequest } from '../src/ai/protocol';
import { localModelSource } from '../src/ai/model-stream';
const settings={contextTokens:4096,maxOutputTokens:512};
const init=():AIRequest=>({id:1,type:'initialize',model:new Blob(['model']),sha256:'verified',runtimeUrl:'https://local.invalid/runtime/',settings});
beforeEach(()=>{vi.clearAllMocks();sdk.verify.mockReset().mockResolvedValue('full');sdk.load.mockResolvedValue(undefined);});
afterEach(()=>vi.unstubAllGlobals());

it('무결성 검증 후 저장된 문맥 한도로 실제 SDK 엔진을 만든다',async()=>{
  sdk.create.mockResolvedValue({});const reply=vi.fn();
  await new WorkerEngine().handle(init(),reply);
  expect(sdk.verify).toHaveBeenCalledWith(expect.any(Blob),'verified',expect.any(Function));
  expect(sdk.load).toHaveBeenCalledWith('https://local.invalid/runtime/');
  expect((globalThis as typeof globalThis&{Module:{locateFile:(name:string)=>string}}).Module.locateFile('litertlm_wasm_internal.wasm')).toBe('https://local.invalid/runtime/litertlm_wasm_internal.wasm');
  expect(sdk.create).toHaveBeenCalledWith(expect.objectContaining({mainExecutorSettings:{maxNumTokens:4096}}));
  expect(sdk.create.mock.calls[0][0].model).toBeInstanceOf(ReadableStream);
  expect(localModelSource(sdk.create.mock.calls[0][0].model)).toBeUndefined();
  expect(reply).toHaveBeenLastCalledWith({id:1,type:'done',initialization:{verification:'full',verificationMs:expect.any(Number),runtimeMs:expect.any(Number),engineMs:expect.any(Number),totalMs:expect.any(Number)}});
});
it('OPFS 모델은 워커에서 열고 실제 파일 크기를 확인한 뒤 스트리밍 적재한다',async()=>{
  const file=new File(['model'],'saved.litertlm');const getFileHandle=vi.fn(async()=>({getFile:async()=>file}));
  vi.stubGlobal('navigator',{storage:{getDirectory:async()=>({getFileHandle})}});sdk.create.mockResolvedValue({});
  const request={...init(),model:{backend:'opfs' as const,storageKey:'saved-model',size:file.size}} as AIRequest;
  await new WorkerEngine().handle(request,vi.fn());expect(getFileHandle).toHaveBeenCalledWith('saved-model');expect(sdk.verify).toHaveBeenCalledWith(file,'verified',expect.any(Function));
  sdk.create.mockClear();await new WorkerEngine().handle({...request,model:{backend:'opfs',storageKey:'saved-model',size:file.size+1}} as AIRequest,vi.fn());expect(sdk.create).not.toHaveBeenCalled();
});
it('iPhone 실행은 입력 배치 1과 작은 ringbuffer를 사용하고 데스크톱의 기본 설정은 유지한다',async()=>{
  sdk.create.mockResolvedValue({});await new WorkerEngine().handle({...init(),profile:'apple-mobile'} as AIRequest,vi.fn());
  expect(sdk.create.mock.calls[0][0].mainExecutorSettings).toMatchObject({maxNumTokens:4096,backendConfig:{sequence_batch_size:1,use_autosized_ringbuffers:true,wait_for_weight_uploads:true,max_top_k:1}});
  expect(sdk.create.mock.calls[0][0].mainExecutorSettings.backendConfig.enable_external_embeddings).toBe(false);
  expect(localModelSource(sdk.create.mock.calls[0][0].model)).toBeInstanceOf(Blob);
});
it('모델이 손상된 경우 GPU 엔진을 만들지 않고 오류를 반환한다',async()=>{
  sdk.verify.mockRejectedValue(new Error('저장된 모델 검증에 실패했어요. 모델을 다시 다운로드해 주세요.'));const reply=vi.fn();await new WorkerEngine().handle(init(),reply);
  expect(sdk.create).not.toHaveBeenCalled();expect(reply).toHaveBeenLastCalledWith(expect.objectContaining({type:'error',message:expect.stringContaining('검증에 실패')}));
});
it('이전 파일 검증을 재사용해도 엔진 초기화를 끝내기 전에는 준비 완료로 표시하지 않는다',async()=>{
  sdk.verify.mockResolvedValue('cached');let complete!:(value:object)=>void;sdk.create.mockReturnValue(new Promise(resolve=>{complete=resolve;}));
  const replies:AIReply[]=[];const loading=new WorkerEngine().handle(init(),message=>replies.push(message));
  await vi.waitFor(()=>expect(sdk.create).toHaveBeenCalledOnce());
  expect(replies.some(message=>message.type==='done')).toBe(false);complete({});await loading;
  expect(replies.at(-1)).toMatchObject({id:1,type:'done',initialization:{verification:'cached'}});
});
it('저장한 답변 한도가 SDK에 전달되고 측정한 사용량만 반환한다',async()=>{
  const conversation={sendMessageStreaming:vi.fn(()=>new ReadableStream({start(controller){controller.enqueue({content:[{type:'text',text:'## 답변\n'}]});controller.enqueue({content:'원문 확인'});controller.close();}})),getBenchmarkInfo:vi.fn(async()=>({lastPrefillTokenCount:180,lastDecodeTokenCount:12})),getTokenCount:vi.fn(async()=>192),delete:vi.fn(async()=>{})};
  const createConversation=vi.fn(async()=>conversation);sdk.create.mockResolvedValue({createConversation});
  const engine=new WorkerEngine();await engine.handle(init(),vi.fn());const replies:AIReply[]=[];
  await engine.handle({id:2,type:'generate',prompt:'근거 질문',systemInstruction:'문서에 근거해 한국어로 답하세요.',settings},message=>replies.push(message));
  expect(createConversation).toHaveBeenCalledWith({preface:{messages:[{role:'system',content:'문서에 근거해 한국어로 답하세요.'}],extra_context:{enable_thinking:false}},prefillPrefaceOnInit:false,sessionConfig:{audioModalityEnabled:false,visionModalityEnabled:false,maxOutputTokens:512,samplerParams:{type:1,k:1,temperature:1,seed:0}}});
  expect(replies.filter(message=>message.type==='progress').map(message=>message.stage)).toEqual(['conversation','prefill','decode']);
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
