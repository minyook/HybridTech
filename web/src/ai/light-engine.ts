import type { MLCEngine } from '@mlc-ai/web-llm';
import { LIGHT_MODEL, lightModelInstalled } from './light-model';
import { boundedLightPrompt } from './light-prompt';
import { cleanLightSummary } from './light-output';
import { explainLightPart, joinLightAnswer, lightAnswerParts } from './light-answer';
import type { AIReply, AIRequest } from './protocol';

interface Device {destroy:()=>void;addEventListener:(type:string,listener:(event:{error?:{constructor?:{name?:string}}})=>void)=>void;lost:Promise<{reason:string}>}
interface Hooks {__fieldguardCountTokens?:(text:string)=>number;__fieldguardWasmBytes?:()=>number;__fieldguardDevice?:Device}
const hooks=globalThis as Hooks;
type Engine=Pick<MLCEngine,'reload'|'unload'|'resetChat'|'chat'>;
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
        const question=message.question??message.prompt,sources=message.sources??[];
        const parts=sources.length?lightAnswerParts(question,sources,count,budget,maxOutputTokens):[{units:[],prompt:boundedLightPrompt(question,[],count,budget),fallback:'',generate:true}];
        if(!parts.length)throw Error('답변할 문서 근거를 확인하지 못했어요.');
        const answers:string[]=[],usages:import('@mlc-ai/web-llm').CompletionUsage[]=[];
        let passes=0,truncated=false,sourceSupplemented=false;
        for(let index=0;index<parts.length;index++){
          const part=parts[index];
          if(!part.generate){answers.push(part.fallback);sourceSupplemented=true;reply({id,type:'text',answer:joinLightAnswer(answers)});continue;}
          // resetChat clears the conversation and KV sequences. Never carry a
          // prior paragraph's prompt or generated tokens into the next one.
          if(passes){await engine.resetChat();memory('light-context-reset');}
          passes++;
          progress('prefill',`문서 근거를 반영해 답변을 작성하고 있어요 (${index+1}/${parts.length})`);memory('light-prefill');
          const stream=await engine.chat.completions.create({messages:[{role:'system',content:system},{role:'user',content:part.prompt}],stream:true,stream_options:{include_usage:true},temperature:0,max_tokens:maxOutputTokens,extra_body:{enable_thinking:false}});
          let raw='',started=false,length=false,usage:import('@mlc-ai/web-llm').CompletionUsage|undefined;
          for await(const chunk of stream){
            if(chunk.usage)usage=chunk.usage;
            if(chunk.choices[0]?.finish_reason==='length')length=true;
            const text=chunk.choices[0]?.delta.content;
            if(text){if(!started){started=true;progress('decode','문서 근거를 반영해 답변을 작성하고 있어요');memory('light-decode');}raw+=text;const visible=cleanLightSummary(raw);const answer=part.units.length?explainLightPart(visible,part.units,false).answer:visible;if(answer)reply({id,type:'text',answer:joinLightAnswer([...answers,answer])});}
          }
          if(usage)usages.push(usage);
          let answer=cleanLightSummary(raw,length);
          if(part.units.length){const result=explainLightPart(cleanLightSummary(raw),part.units,true,length);answer=result.answer;sourceSupplemented||=result.supplemented;}
          else if(length)truncated=true;
          answers.push(answer);reply({id,type:'text',answer:joinLightAnswer(answers)});
        }
        const answer=joinLightAnswer(answers);
        const sum=(key:'prompt_tokens'|'completion_tokens'|'total_tokens')=>usages.length===passes&&passes?usages.reduce((total,usage)=>total+usage[key],0):undefined;
        memory('light-complete');progress('cleanup','다음 질문을 위해 AI 메모리를 정리하고 있어요');
        await this.dispose();
        reply({id,type:'done',result:{answer,truncated,sourceSupplemented,usage:{inputTokens:sum('prompt_tokens'),outputTokens:sum('completion_tokens'),totalTokens:sum('total_tokens'),maxOutputTokens:maxOutputTokens*passes,contextTokens:2048,...(sources.length?{passes,perPassMaxOutputTokens:maxOutputTokens}:{})}}});
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
