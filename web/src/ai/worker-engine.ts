import { Engine, getGlobalLiteRtLm, loadLiteRtLm, SamplerType, setupDefaultWebGpuDevice } from '@litert-lm/core';
import { verifyStoredModel } from './model';
import type { AIModelSource, AIReply, AIRequest, GenerationResult, TokenUsage } from './protocol';
import { boundedModelStream, localModelStream,localModelReadStats } from './model-stream';
import { executorSettings } from './execution';
import { WorkerResources } from './worker-resources';
import type { AIDiagnostic } from './diagnostics';

async function openModel(source:AIModelSource):Promise<Blob>{
  if(source instanceof Blob)return source;
  const root=await navigator.storage.getDirectory();
  const file=await(await root.getFileHandle(source.storageKey)).getFile();
  if(file.size!==source.size)throw new Error('저장된 모델 크기가 달라요. 모델 파일을 확인해 주세요.');
  return file;
}

// Runs in a classic Web Worker. Normal Apple retirement deletes the engine and
// GPU device before runtime terminates this worker's WASM execution scope.
export class WorkerEngine {
  private engine?:Engine;
  private loaded=false;
  private busy=false;
  private freshEnginePerAnswer=false;
  private diagnosticReply?:(sample:AIDiagnostic)=>void;
  private readonly resources=new WorkerResources(getGlobalLiteRtLm,sample=>this.diagnosticReply?.(sample));
  async handle(request:AIRequest,reply:(message:AIReply)=>void) {
    if(this.busy){reply({id:request.id,type:'error',message:'AI가 앞선 작업을 처리하고 있어요.'});return;}
    this.busy=true;
    this.diagnosticReply=sample=>reply({id:request.id,type:'diagnostic',sample});
    const resources=this.resources;
    try {
      if(request.type==='initialize') {
        this.freshEnginePerAnswer=request.profile==='apple-mobile';
        const started=performance.now();
        reply({id:request.id,type:'progress',stage:'verification',message:'저장된 모델의 변경 여부 확인 중 · 다시 다운로드하지 않아요'});
        const model=await openModel(request.model);
        const verification=await verifyStoredModel(model,request.sha256,message=>reply({id:request.id,type:'progress',stage:'verification',message}));
        const verifiedAt=performance.now();
        reply({id:request.id,type:'progress',stage:'runtime',message:'AI 실행 파일 불러오는 중'});
        if(!this.loaded){
          // Emscripten otherwise resolves WASM beside ai-worker.js instead of
          // beside the imported official loader in runtime/.
          const scope=globalThis as typeof globalThis&{Module?:{locateFile:(filename:string)=>string}};
          scope.Module={locateFile:filename=>new URL(filename,request.runtimeUrl).href};
          await loadLiteRtLm(request.runtimeUrl);this.loaded=true;
        }
        const runtimeLoadedAt=performance.now();
        reply({id:request.id,type:'progress',stage:'engine',message:'저장된 모델을 기기 메모리에 불러오는 중'});
        if(this.freshEnginePerAnswer){await setupDefaultWebGpuDevice();resources.watch();}
        resources.sample('engine');
        const input=request.profile==='apple-mobile'?localModelStream(model):boundedModelStream(model);
        this.engine=await Engine.create({model:input,benchmarkEnabled:true,mainExecutorSettings:executorSettings(request.settings.contextTokens,request.profile)});
        resources.sample('engine-ready');
        const completedAt=performance.now();
        const reads=localModelReadStats(input);
        const initialization={verification,verificationMs:Math.round(verifiedAt-started),runtimeMs:Math.round(runtimeLoadedAt-verifiedAt),engineMs:Math.round(completedAt-runtimeLoadedAt),totalMs:Math.round(completedAt-started),...(reads?{modelRead:{mode:'file-range' as const,...reads}}:{})};
        console.info('AI initialization timing',initialization);
        reply({id:request.id,type:'done',initialization});
      } else {
        if(!this.engine)throw new Error('AI 준비를 완료하지 못했어요. 질문을 다시 시도해 주세요.');
        const currentEngine=this.engine;
        resources.watch();resources.sample('conversation');
        let result:GenerationResult;
        try{
          reply({id:request.id,type:'progress',stage:'conversation',message:'답변에 필요한 작업 공간을 준비하고 있어요'});
          const conversation=await currentEngine.createConversation({
            preface:{messages:request.systemInstruction?[{role:'system',content:request.systemInstruction}]:[],extra_context:{enable_thinking:false}},
            prefillPrefaceOnInit:false,
            // Document guidance benefits from choosing the highest-scoring token.
            // This does not compensate for device-specific GPU numerical errors.
            sessionConfig:{audioModalityEnabled:false,visionModalityEnabled:false,maxOutputTokens:request.settings.maxOutputTokens,samplerParams:{type:SamplerType.TOP_K,k:1,temperature:1,seed:0}},
          });
          let answer='';
          try {
            reply({id:request.id,type:'progress',stage:'prefill',message:'질문과 문서 근거를 읽고 있어요. 첫 답변까지 시간이 걸릴 수 있어요'});
            resources.sample('prefill');
            const reader=conversation.sendMessageStreaming(request.prompt).getReader();
            let decoding=false;
            let chunks=0;
            try {
              while(true){
                const {value,done}=await reader.read();if(done)break;
                if(typeof value.content==='string')answer+=value.content;
                else for(const part of value.content??[])if('text'in part&&typeof part.text==='string')answer+=part.text;
                if(answer&&!decoding){decoding=true;reply({id:request.id,type:'progress',stage:'decode',message:'Gemma가 답변을 작성하고 있어요'});}
                if(++chunks===1||chunks%32===0)resources.sample('decode');
                reply({id:request.id,type:'text',answer});
              }
            } finally { await reader.cancel().catch(()=>{}); }
            if(!answer.trim())throw new Error('AI가 답변을 반환하지 않았어요.');
            const usage:TokenUsage={maxOutputTokens:request.settings.maxOutputTokens,contextTokens:request.settings.contextTokens};
            resources.sample('metrics');
            // These are SDK measurements, never estimates from character lengths.
            try {
              const info=await conversation.getBenchmarkInfo();
              if(Number.isSafeInteger(info.lastPrefillTokenCount)&&info.lastPrefillTokenCount>0)usage.inputTokens=info.lastPrefillTokenCount;
              if(Number.isSafeInteger(info.lastDecodeTokenCount)&&info.lastDecodeTokenCount>0)usage.outputTokens=info.lastDecodeTokenCount;
            } catch { /* Metrics are optional; a valid answer is still usable. */ }
            try{const total=await conversation.getTokenCount();if(Number.isSafeInteger(total)&&total>0)usage.totalTokens=total;}catch{/* Optional SDK metric. */}
            result={answer:answer.trim(),usage};
          } finally { await conversation.delete(); }
        }finally{
          // Deleting a conversation does not release the worker's WASM heap or
          // engine allocations. Apple mobile retires the entire engine before
          // acknowledging the answer; runtime then replaces this worker.
          if(this.freshEnginePerAnswer){
            this.engine=undefined;
            reply({id:request.id,type:'progress',stage:'cleanup',message:'답변을 마무리하고 AI 메모리를 정리하고 있어요'});
            try{await currentEngine.delete();}finally{resources.sample('cleanup');resources.destroyGPU();}
          }
        }
        reply({id:request.id,type:'done',result});
      }
    } catch(error){
      if(this.freshEnginePerAnswer)resources.destroyGPU();
      console.error('AI worker failed:',error);
      const detail=error instanceof Error?error.message:'';
      const message=/[가-힣]/.test(detail)?detail:request.type==='initialize'?'이 기기에서 AI를 준비하지 못했어요. 문맥 토큰을 줄이고 다시 질문해 주세요.':/token|context|length|capacity/i.test(detail)?'질문과 문서 근거가 문맥 한도를 넘었어요. 문맥 토큰을 늘리거나 질문을 나누어 주세요.':'AI 답변을 완성하지 못했어요. 다시 질문해 주세요.';
      reply({id:request.id,type:'error',message});
    }
    finally{this.busy=false;}
  }
}
