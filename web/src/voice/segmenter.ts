/** Bounded, adaptive energy detector. Noise is not treated as semantic speech. */
export class SpeechSegmenter {
  private floor=0.004;private calibration=0;private voiced=0;private silence=0;private speaking=false;private frames:Float32Array[]=[];private before:Float32Array[]=[];
  reset(){this.floor=0.004;this.calibration=0;this.voiced=0;this.silence=0;this.speaking=false;this.frames=[];this.before=[];}
  push(frame:Float32Array,rate:number):{type:'speech'|'complete'|'too-long';audio?:Float32Array;rate?:number}|undefined{
    const ms=frame.length/rate*1000,rms=Math.sqrt(frame.reduce((sum,n)=>sum+n*n,0)/frame.length);
    if(this.calibration<600){this.calibration+=ms;this.floor=Math.min(0.025,this.floor*0.8+rms*0.2);return;}
    const audible=rms>Math.max(0.012,this.floor*3);
    if(!this.speaking){
      this.before.push(frame.slice());if(this.before.length>Math.ceil(rate*0.35/frame.length))this.before.shift();
      if(!audible){this.voiced=0;this.floor=this.floor*0.98+rms*0.02;return;}
      this.voiced+=ms;if(this.voiced<180)return;
      this.speaking=true;this.frames=this.before;this.before=[];this.silence=0;return {type:'speech'};
    }
    this.frames.push(frame.slice());this.voiced+=audible?ms:0;this.silence=audible?0:this.silence+ms;
    const length=this.frames.reduce((sum,part)=>sum+part.length,0);
    if(length>rate*20){this.reset();return {type:'too-long'};}
    if(this.silence<1200)return;
    const parts=this.frames,audio=new Float32Array(length);let offset=0;for(const part of parts){audio.set(part,offset);offset+=part.length;}
    const valid=this.voiced>=350;this.reset();return valid?{type:'complete',audio,rate}:undefined;
  }
}
export function resample16k(audio:Float32Array,rate:number){
  if(!Number.isFinite(rate)||rate<16000||!audio.length||audio.length>rate*21)throw Error('음성 길이·샘플레이트를 확인해 주세요.');
  const ratio=rate/16000,out=new Float32Array(Math.floor(audio.length/ratio));
  for(let i=0;i<out.length;i++){const start=Math.floor(i*ratio),end=Math.min(audio.length,Math.floor((i+1)*ratio));let value=0;for(let j=start;j<end;j++)value+=audio[j];out[i]=value/(end-start);}
  return out;
}
