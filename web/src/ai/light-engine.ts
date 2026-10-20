import type { MLCEngine } from '@mlc-ai/web-llm';
import { LIGHT_MODEL, lightModelInstalled } from './light-model';
import { boundedLightPrompt } from './light-prompt';
import { visibleLightAnswer } from './light-output';
import type { AIReply, AIRequest } from './protocol';

interface Device {destroy:()=>void;addEventListener:(type:string,listener:(event:{error?:{constructor?:{name?:string}}})=>void)=>void;lost:Promise<{reason:string}>}
interface Hooks {__fieldguardCountTokens?:(text:string)=>number;__fieldguardWasmBytes?:()=>number;__fieldguardDevice?:Device}
const hooks=globalThis as Hooks;
type Engine=Pick<MLCEngine,'reload'|'unload'|'chat'>;
export class LightWorkerEngine {
  private engine?:Engine;
  private busy=false;
  constructor(private create:()=>Engine,private installed=lightModelInstalled,private bridge:Hooks=hooks){}
  async handle(message:AIRequest,reply:(message:AIReply)=>void){
    const id=message.id;
    if(this.busy){reply({id,type:'error',message:'앞선 AI 작업을 처리하고 있어요.'});return;}
    this.busy=true;
    const progress=(stage:'engine'|'prefill'|'decode'|'cleanup',text:string)=>reply({id,type:'progress',stage,message:text});
    const memory=(stage:string)=>{const bytes=this.bridge.__fieldguardWasmBytes?.();if(bytes)reply({id,type:'diagnostic',sample:{stage,kind:'memory',wasmHeapBytes:bytes}});};
    try{
      if(message.type==='initialize'){
        if(message.provider!=='light')throw Error('경량 AI 실행 구성이 달라요. 앱을 다시 열어 주세요.');
        if(message.settings.contextTokens!==2048)throw Error('아이폰 경량 AI는 문맥 2,048 토큰을 사용해요.');
        const start=performance.now();if(!await this.installed())throw Error('경량 모델 파일이 없어요. 설정에서 다운로드해 주세요.');
        const verified=performance.now();progress('engine','경량 AI를 기기 메모리에 준비하고 있어요');
        const engine=this.create();this.engine=engine;
        await engine.reload(LIGHT_MODEL.id,{context_window_size:2048});
        const device=this.bridge.__fieldguardDevice;
        device?.addEventListener('uncapturederror',event=>{const name=event.error?.constructor?.name;reply({id,type:'diagnostic',sample:{stage:'light-gpu',kind:'gpu-error',gpuError:name==='GPUOutOfMemoryError'?'out-of-memory':name==='GPUValidationError'?'validation':'unknown'}});});
        void device?.lost.then(info=>{if(info.reason!=='destroyed')reply({id,type:'diagnostic',sample:{stage:'light-gpu',kind:'gpu-lost'}});});
        memory('light-ready');
        reply({id,type:'done',initialization:{verification:'cached',verificationMs:verified-start,runtimeMs:0,engineMs:performance.now()-verified,totalMs:performance.now()-start}});
      }else{
        const engine=this.engine,count=this.bridge.__fieldguardCountTokens;
        if(!engine||!count)throw Error('경량 AI 준비를 다시 확인해 주세요.');
        // No previous conversation is sent or retained. All requested output
        // tokens and chat template overhead are reserved before GPU prefill.
        const maxOutputTokens=Math.min(message.settings.maxOutputTokens,512);
        const system=message.systemInstruction??'';
        const budget=2048-maxOutputTokens-count(system)-96;
        const prompt=boundedLightPrompt(message.question??message.prompt,message.sources??[],count,budget);
        progress('prefill','문서 근거를 작은 묶음으로 처리하고 있어요');memory('light-prefill');
        const stream=await engine.chat.completions.create({messages:[{role:'system',content:system},{role:'user',content:prompt}],stream:true,stream_options:{include_usage:true},temperature:0,max_tokens:maxOutputTokens,extra_body:{enable_thinking:false}});
        let raw='',answer='',usage:import('@mlc-ai/web-llm').CompletionUsage|undefined,started=false;
        for await(const part of stream){
          if(part.usage)usage=part.usage;
          const text=part.choices[0]?.delta.content;
          if(text){if(!started){started=true;progress('decode','AI가 답변을 작성하고 있어요');memory('light-decode');}raw+=text;answer=visibleLightAnswer(raw);if(answer)reply({id,type:'text',answer});}
        }
        memory('light-complete');progress('cleanup','다음 질문을 위해 AI 메모리를 정리하고 있어요');
        await this.dispose();
        reply({id,type:'done',result:{answer,usage:{inputTokens:usage?.prompt_tokens,outputTokens:usage?.completion_tokens,totalTokens:usage?.total_tokens,maxOutputTokens,contextTokens:2048}}});
      }
    }catch(error){
      await this.dispose().catch(()=>{});
      const text=error instanceof Error?error.message:'';
      const message=/shader-f16|ShaderF16/i.test(text)?'이 기기에서 경량 모델에 필요한 16비트 그래픽 연산을 사용할 수 없어요. 문서 검색을 이용해 주세요.':/[가-힣]/u.test(text)?text:'경량 AI 실행을 완료하지 못했어요. 다른 AI 화면을 닫고 설정에서 진단 기록을 저장해 주세요.';
      reply({id,type:'error',message});
    }finally{this.busy=false;}
  }
  private async dispose(){
    try{await this.engine?.unload();}finally{this.engine=undefined;this.bridge.__fieldguardDevice?.destroy();this.bridge.__fieldguardDevice=undefined;this.bridge.__fieldguardCountTokens=undefined;this.bridge.__fieldguardWasmBytes=undefined;}
  }
}
