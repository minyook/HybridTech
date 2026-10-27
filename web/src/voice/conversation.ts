import {SpeechSegmenter,resample16k} from './segmenter';
export type VoicePhase='off'|'preparing'|'listening'|'hearing'|'transcribing'|'answering'|'reading'|'error';
export interface VoiceState {phase:VoicePhase;message:string;heard?:string}
export interface Capture {pause:(value:boolean)=>void;close:()=>void}
export interface ConversationPorts {
  open:(frames:(audio:Float32Array,rate:number)=>void,ended:()=>void,signal:AbortSignal)=>Promise<Capture>;
  prepare:()=>Promise<void>;transcribe:(audio:Float32Array)=>Promise<string>;release:(clean?:boolean)=>void;
  answer:(text:string,signal:AbortSignal)=>Promise<string>;speak:(text:string,signal:AbortSignal)=>Promise<void>;
  update:(state:VoiceState)=>void;
}
/** One foreground conversation, half duplex. Epoch checks discard every late result. */
export class VoiceConversation {
  private epoch=0;private capture?:Capture;private abort?:AbortController;private detector=new SpeechSegmenter();private phase:VoicePhase='off';
  constructor(private ports:ConversationPorts){}
  private update(phase:VoicePhase,message:string,heard?:string){this.phase=phase;this.ports.update({phase,message,heard});}
  async start(){
    this.stop();const epoch=++this.epoch;this.abort=new AbortController();this.update('preparing','마이크·로컬 음성 인식을 준비해요.');
    try{
      const capture=await this.ports.open((audio,rate)=>this.frame(audio,rate),()=>this.fail(Error('마이크 연결이 끊겼어요. 다시 시작해 주세요.')),this.abort.signal);
      if(epoch!==this.epoch){capture.close();return;}this.capture=capture;capture.pause(true);
      await this.listen(epoch);
    }catch(error){if(epoch===this.epoch)this.fail(error);}
  }
  private async listen(epoch:number){
    this.update('preparing','로컬 음성 인식을 준비해요. 답변 모델은 잠시 정리해요.');
    await this.ports.prepare();if(epoch!==this.epoch)return;
    this.detector.reset();this.capture?.pause(false);this.update('listening','듣고 있어요. 말이 끝나면 자동으로 질문해요.');
  }
  private frame(audio:Float32Array,rate:number){
    if(!['listening','hearing'].includes(this.phase))return;
    const event=this.detector.push(audio,rate);if(!event)return;
    if(event.type==='speech')this.update('hearing','말씀을 듣고 있어요. 잠시 침묵하면 전송해요.');
    else if(event.type==='too-long')this.fail(Error('한 번에 20초 이내로 말씀해 주세요. 긴 질문은 자동 전송하지 않았어요.'));
    else if(event.audio){this.capture?.pause(true);void this.turn(resample16k(event.audio,rate),this.epoch);}
  }
  private async turn(audio:Float32Array,epoch:number){
    try{
      this.update('transcribing','이 기기에서 한국어로 바꾸고 있어요.');const text=(await this.ports.transcribe(audio)).trim();
      if(epoch!==this.epoch)return;this.ports.release();
      if(!text||text.length>1500)throw Error('질문을 정확히 인식하지 못했어요. 인식된 내용을 확인하고 다시 시작해 주세요.');
      if(/^(음성\s*)?대화\s*(종료|그만)[.!?\s]*$/.test(text)){this.stop();return;}
      this.update('answering','인식한 질문으로 문서 근거를 찾고 있어요.',text);
      const answer=await this.ports.answer(text,this.abort!.signal);if(epoch!==this.epoch)return;
      if(!answer)throw Error('질문을 처리하지 못했어요. 화면의 질문을 확인해 주세요.');
      this.update('reading','답변을 읽고 있어요. 마이크 인식은 쉬고 있어요.',text);
      await this.ports.speak(answer,this.abort!.signal);if(epoch!==this.epoch)return;
      await this.listen(epoch);
    }catch(error){if(epoch===this.epoch)this.fail(error);}
  }
  stop(){
    this.epoch++;this.abort?.abort();this.abort=undefined;this.capture?.close();this.capture=undefined;this.ports.release();this.detector.reset();this.update('off','음성 대화가 꺼져 있어요.');
  }
  private fail(error:unknown){this.stop();this.update('error',error instanceof Error?error.message:'음성 대화를 중단했어요. 다시 시작해 주세요.');}
}
