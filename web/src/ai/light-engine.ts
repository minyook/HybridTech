import type { MLCEngine } from '@mlc-ai/web-llm';
import { LIGHT_MODEL, lightModelInstalled } from './light-model';
import { acceptedSupplement, composeAnswer, createAnswerPlan, supplementPrompt, SUPPLEMENT_SYSTEM } from '../rag/answer-plan';
import type { AIReply, AIRequest } from './protocol';

interface Device {destroy:()=>void;addEventListener:(type:string,listener:(event:{error?:{constructor?:{name?:string}}})=>void)=>void;lost:Promise<{reason:string}>}
interface Hooks {__fieldguardCountTokens?:(text:string)=>number;__fieldguardWasmBytes?:()=>number;__fieldguardDevice?:Device}
type Engine=Pick<MLCEngine,'reload'|'unload'|'interruptGenerate'|'chat'>;
export class LightWorkerEngine {
  private engine?:Engine;
  private busy=false;
  private stopping=false;
  private disposal?:Promise<void>;
  private stops:{id:number;reply:(message:AIReply)=>void}[]=[];
  constructor(private create:()=>Engine,private installed=lightModelInstalled,private bridge:Hooks=globalThis as Hooks){}
  async handle(message:AIRequest,reply:(message:AIReply)=>void){
    // This bypasses the busy gate, not the Worker's event loop. The parent owns
    // the watchdog so a blocked Worker cannot disable its own timeout.
    if(message.type==='stop'){
      this.stopping=true;this.stops.push({id:message.id,reply});
      void this.engine?.interruptGenerate().catch(()=>{});
      if(!this.busy){let clean=false;try{await this.dispose();clean=true;}catch{/* Acknowledge uncertainty without a second unhandled rejection. */}finally{this.ackStops(clean);}}
      return;
    }
    const id=message.id;
    if(this.busy||this.stopping){reply({id,type:'error',message:'앞선 AI 작업을 정리하고 있어요.'});return;}
    this.busy=true;let clean=false;
    const progress=(stage:'engine'|'prefill'|'decode'|'cleanup',text:string)=>{if(!this.stopping)reply({id,type:'progress',stage,message:text});};
    const memory=(stage:string)=>{const bytes=this.bridge.__fieldguardWasmBytes?.();if(bytes)reply({id,type:'diagnostic',sample:{stage,kind:'memory',wasmHeapBytes:bytes}});};
    try{
      if(message.type==='initialize'){
        if(message.provider!=='light'||message.settings.contextTokens!==2048)throw Error('아이폰 경량 AI 실행 구성을 다시 확인해 주세요.');
        const start=performance.now();if(!await this.installed())throw Error('경량 모델 파일이 없어요. 설정에서 다운로드해 주세요.');
        if(this.stopping)return;
        const verified=performance.now();progress('engine','경량 AI를 기기 메모리에 준비하고 있어요');
        const engine=this.create();this.engine=engine;this.disposal=undefined;
        await engine.reload(LIGHT_MODEL.id,{context_window_size:2048});
        const device=this.bridge.__fieldguardDevice;
        device?.addEventListener('uncapturederror',event=>{const name=event.error?.constructor?.name;reply({id,type:'diagnostic',sample:{stage:'light-gpu',kind:'gpu-error',gpuError:name==='GPUOutOfMemoryError'?'out-of-memory':name==='GPUValidationError'?'validation':'unknown'}});});
        void device?.lost.then(info=>{if(info.reason!=='destroyed')reply({id,type:'diagnostic',sample:{stage:'light-gpu',kind:'gpu-lost'}});});
        memory('light-ready');
        if(!this.stopping)reply({id,type:'done',initialization:{verification:'cached',verificationMs:verified-start,runtimeMs:0,engineMs:performance.now()-verified,totalMs:performance.now()-start}});
      }else{
        const engine=this.engine,count=this.bridge.__fieldguardCountTokens;
        if(!engine||!count)throw Error('경량 AI 준비를 다시 확인해 주세요.');
        const plan=message.plan??createAnswerPlan(message.question??message.prompt,message.sources??[]);
        reply({id,type:'text',answer:plan.body});
        const maxOutputTokens=Math.min(message.settings.maxOutputTokens,256);
        const prompt=supplementPrompt(plan),budget=2048-maxOutputTokens-count(SUPPLEMENT_SYSTEM)-96;
        let supplement='',generated=false,skipped:'not-needed'|'budget'|'validation'|undefined;
        let usage:import('@mlc-ai/web-llm').CompletionUsage|undefined;
        if(!plan.supplementRequested)skipped='not-needed';
        else if(count(prompt)>budget)skipped='budget';
        else if(!this.stopping){
          generated=true;progress('prefill','표시된 문서 근거로 필요한 보충 설명을 확인하고 있어요');memory('light-prefill');
          const stream=await engine.chat.completions.create({messages:[{role:'system',content:SUPPLEMENT_SYSTEM},{role:'user',content:prompt}],stream:true,stream_options:{include_usage:true},temperature:0,max_tokens:maxOutputTokens,extra_body:{enable_thinking:false}});
          let raw='',started=false,truncated=false;
          for await(const chunk of stream){
            if(this.stopping)break;
            if(chunk.usage)usage=chunk.usage;
            if(chunk.choices[0]?.finish_reason==='length')truncated=true;
            const text=chunk.choices[0]?.delta.content;
            if(text){if(!started){started=true;progress('decode','보충 설명을 확인하고 있어요');memory('light-decode');}raw+=text;}
          }
          if(!this.stopping&&!truncated)supplement=acceptedSupplement(raw,plan);
          if(!supplement)skipped='validation';
        }
        memory('light-complete');progress('cleanup','AI 메모리를 정리하고 있어요');
        await this.dispose();clean=true;
        if(!this.stopping)reply({id,type:'done',cleanupComplete:true,result:{answer:composeAnswer(plan,supplement),generated,supplementSkipped:skipped,usage:{...(usage?{inputTokens:usage.prompt_tokens,outputTokens:usage.completion_tokens,totalTokens:usage.total_tokens}:generated?{}:{inputTokens:0,outputTokens:0,totalTokens:0}),maxOutputTokens:generated?maxOutputTokens:0,contextTokens:2048}}});
      }
    }catch(error){
      try{await this.dispose();clean=true;}catch{/* Never acknowledge failed unload as completed cleanup. */}
      if(!this.stopping)reply({id,type:'error',cleanupComplete:clean,message:error instanceof Error&&/[가-힣]/.test(error.message)?error.message:'경량 AI 실행을 완료하지 못했어요. 문서 본문은 계속 확인할 수 있어요.'});
    }finally{
      if(this.stopping&&!clean)try{await this.dispose();clean=true;}catch{/* Parent guard handles uncertain cleanup. */}
      this.busy=false;if(this.stopping)this.ackStops(clean);
    }
  }
  private ackStops(clean:boolean){for(const stop of this.stops.splice(0))stop.reply({id:stop.id,type:'stopped',cleanupComplete:clean});this.stopping=false;}
  private dispose(){
    return this.disposal??=(async()=>{try{await this.engine?.unload();}finally{this.engine=undefined;this.bridge.__fieldguardDevice?.destroy();this.bridge.__fieldguardDevice=undefined;this.bridge.__fieldguardCountTokens=undefined;this.bridge.__fieldguardWasmBytes=undefined;}})();
  }
}
