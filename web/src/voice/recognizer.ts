import {holdAIForAudio} from '../ai/runtime';
import {acquireEngineLease} from '../ai/engine-lease';
import {voiceInstalled} from './assets';
const RECOVERY='fieldguard-voice-incomplete-v1';
export const voiceRecovery=()=>{try{return localStorage.getItem(RECOVERY)!==null;}catch{return true;}};
export class LocalRecognizer {
  private worker?:Worker;private release?:()=>void;private unlock?:()=>void;private epoch=0;private quarantined=false;private ownsMarker=false;
  private pending?:{resolve:(value:string)=>void;reject:(error:Error)=>void;timer:ReturnType<typeof setTimeout>};
  // Do not erase a different tab's marker before acquiring the shared lease.
  // An explicit retry replaces it only after prepare() owns that lease.
  resetRecovery(){this.quarantined=false;}
  async prepare(){
    const epoch=++this.epoch;if(!await voiceInstalled())throw Error('먼저 로컬 음성 파일을 다운로드해 주세요.');
    const release=await holdAIForAudio();if(epoch!==this.epoch){release();throw Error('음성 준비를 취소했어요.');}this.release=release;
    try{
      const unlock=await acquireEngineLease();if(epoch!==this.epoch){unlock?.();throw Error('음성 준비를 취소했어요.');}if(!unlock)throw Error('다른 화면에서 AI를 사용 중이에요. 그 화면을 닫아 주세요.');this.unlock=unlock;
      localStorage.setItem(RECOVERY,JSON.stringify({stage:'loading',at:Date.now()}));
      this.ownsMarker=true;
      this.worker=new Worker(new URL('./recognizer.worker.ts',import.meta.url),{type:'module'});
      this.worker.onmessage=event=>{if(epoch!==this.epoch)return;const result=event.data;if(result.type==='error'){this.fail(Error(result.message));return;}if(result.type==='ready'||result.type==='text'){const pending=this.pending;if(!pending)return;clearTimeout(pending.timer);this.pending=undefined;pending.resolve(result.type==='text'?String(result.text??'').trim():'');}};
      this.worker.onerror=event=>{event.preventDefault();this.fail(Error('음성 인식 실행이 중단됐어요. 대화를 다시 시작해 주세요.'));};
      await this.request({type:'prepare'},120000);
    }catch(error){this.quarantined=true;this.stop(false);throw error;}
  }
  transcribe(audio:Float32Array){localStorage.setItem(RECOVERY,JSON.stringify({stage:'transcribing',at:Date.now()}));return this.request({type:'transcribe',audio},120000,[audio.buffer as ArrayBuffer]);}
  private request(message:unknown,timeout:number,transfer:Transferable[]=[]){
    if(!this.worker)return Promise.reject(Error('음성 인식기가 준비되지 않았어요.'));
    return new Promise<string>((resolve,reject)=>{const timer=setTimeout(()=>this.fail(Error('음성 인식 시간이 초과됐어요. 자동 재시도하지 않아요.')),timeout);this.pending={resolve,reject,timer};this.worker!.postMessage(message,transfer);});
  }
  private fail(error:Error){const pending=this.pending;this.pending=undefined;if(pending){clearTimeout(pending.timer);pending.reject(error);}this.quarantined=true;this.stop(false);}
  stop(clean=true){
    this.epoch++;this.worker?.terminate();this.worker=undefined;this.unlock?.();this.unlock=undefined;this.release?.();this.release=undefined;
    if(this.pending){clearTimeout(this.pending.timer);this.pending.reject(Error('음성 인식을 중단했어요.'));this.pending=undefined;}
    if(clean&&this.ownsMarker&&!this.quarantined)try{localStorage.removeItem(RECOVERY);}catch{/* No automatic restart. */}
    this.ownsMarker=false;
  }
}
