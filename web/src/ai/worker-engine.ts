import { Engine, loadLiteRtLm } from '@litert-lm/core';
import { digestBlob } from './model';
import type { AIReply, AIRequest, GenerationResult, TokenUsage } from './protocol';

// Runs exclusively in a classic Web Worker. Terminating the worker also releases
// WASM/GPU allocations, including an initialization that cannot otherwise cancel.
export class WorkerEngine {
  private engine?:Engine;
  private loaded=false;
  private busy=false;
  async handle(request:AIRequest,reply:(message:AIReply)=>void) {
    if(this.busy){reply({id:request.id,type:'error',message:'AI가 앞선 작업을 처리하고 있어요.'});return;}
    this.busy=true;
    try {
      if(request.type==='initialize') {
        reply({id:request.id,type:'progress',message:'저장된 모델 확인 중'});
        if(await digestBlob(request.model)!==request.sha256)throw new Error('저장된 모델 검증에 실패했어요. 모델을 다시 다운로드해 주세요.');
        reply({id:request.id,type:'progress',message:'AI 실행 파일 불러오는 중'});
        if(!this.loaded){
          // Emscripten otherwise resolves WASM beside ai-worker.js instead of
          // beside the imported official loader in runtime/.
          const scope=globalThis as typeof globalThis&{Module?:{locateFile:(filename:string)=>string}};
          scope.Module={locateFile:filename=>new URL(filename,request.runtimeUrl).href};
          await loadLiteRtLm(request.runtimeUrl);this.loaded=true;
        }
        reply({id:request.id,type:'progress',message:'기기에서 AI 준비 중 · 처음에는 시간이 걸릴 수 있어요'});
        this.engine=await Engine.create({model:request.model,benchmarkEnabled:true,mainExecutorSettings:{maxNumTokens:request.settings.contextTokens}});
        reply({id:request.id,type:'done'});
      } else {
        if(!this.engine)throw new Error('AI 준비를 완료하지 못했어요. 질문을 다시 시도해 주세요.');
        const conversation=await this.engine.createConversation({preface:{extra_context:{enable_thinking:false}},sessionConfig:{maxOutputTokens:request.settings.maxOutputTokens}});
        let answer='';let result:GenerationResult;
        try {
          const reader=conversation.sendMessageStreaming(request.prompt).getReader();
          try {
            while(true){
              const {value,done}=await reader.read();if(done)break;
              if(typeof value.content==='string')answer+=value.content;
              else for(const part of value.content??[])if('text'in part&&typeof part.text==='string')answer+=part.text;
              reply({id:request.id,type:'text',answer});
            }
          } finally { await reader.cancel().catch(()=>{}); }
          if(!answer.trim())throw new Error('AI가 답변을 반환하지 않았어요.');
          const usage:TokenUsage={maxOutputTokens:request.settings.maxOutputTokens,contextTokens:request.settings.contextTokens};
          // These are SDK measurements, never estimates from character lengths.
          try {
            const info=await conversation.getBenchmarkInfo();
            if(Number.isSafeInteger(info.lastPrefillTokenCount)&&info.lastPrefillTokenCount>0)usage.inputTokens=info.lastPrefillTokenCount;
            if(Number.isSafeInteger(info.lastDecodeTokenCount)&&info.lastDecodeTokenCount>0)usage.outputTokens=info.lastDecodeTokenCount;
          } catch { /* Metrics are optional; a valid answer is still usable. */ }
          try{const total=await conversation.getTokenCount();if(Number.isSafeInteger(total)&&total>0)usage.totalTokens=total;}catch{/* Optional SDK metric. */}
          result={answer:answer.trim(),usage};
        } finally { await conversation.delete(); }
        reply({id:request.id,type:'done',result});
      }
    } catch(error){
      console.error('AI worker failed:',error);
      const detail=error instanceof Error?error.message:'';
      const message=/[가-힣]/.test(detail)?detail:request.type==='initialize'?'이 기기에서 AI를 준비하지 못했어요. 문맥 토큰을 줄이고 다시 질문해 주세요.':/token|context|length|capacity/i.test(detail)?'질문과 문서 근거가 문맥 한도를 넘었어요. 문맥 토큰을 늘리거나 질문을 나누어 주세요.':'AI 답변을 완성하지 못했어요. 다시 질문해 주세요.';
      reply({id:request.id,type:'error',message});
    }
    finally{this.busy=false;}
  }
}
