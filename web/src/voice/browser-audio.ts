import type {Capture} from './conversation';
export async function openMicrophone(frames:(audio:Float32Array,rate:number)=>void,ended:()=>void,signal:AbortSignal):Promise<Capture>{
  if(!window.isSecureContext||!navigator.mediaDevices?.getUserMedia)throw Error('마이크에는 HTTPS와 지원 브라우저가 필요해요.');
  const context=new AudioContext();let stream:MediaStream|undefined;let capture:AudioWorkletNode|undefined;let closed=false;
  const resume=context.resume();
  const close=()=>{if(closed)return;closed=true;signal.removeEventListener('abort',close);stream?.getTracks().forEach(track=>{track.onended=null;track.stop();});capture?.disconnect();void context.close().catch(()=>{});};
  signal.addEventListener('abort',close,{once:true});
  try{
    stream=await navigator.mediaDevices.getUserMedia({audio:{channelCount:1,echoCancellation:true,noiseSuppression:true,autoGainControl:true},video:false});
    if(signal.aborted||closed){stream.getTracks().forEach(track=>track.stop());throw Error('마이크 준비를 취소했어요.');}
    await resume;
    if(context.state!=='running')throw Error('오디오 실행이 제한됐어요. 화면을 연 상태에서 다시 시작해 주세요.');
    await context.audioWorklet.addModule(new URL('voice-capture.js',document.baseURI).href);
    signal.throwIfAborted();
    capture=new AudioWorkletNode(context,'fieldguard-voice-capture');
    const source=context.createMediaStreamSource(stream),gain=context.createGain();gain.gain.value=0;source.connect(capture);capture.connect(gain);gain.connect(context.destination);
    capture.port.onmessage=event=>{if(!closed)frames(event.data as Float32Array,context.sampleRate);};
    stream.getAudioTracks().forEach(track=>{track.onended=ended;});
    return {close,pause(value){stream?.getAudioTracks().forEach(track=>{track.enabled=!value;});capture?.port.postMessage(!value);}};
  }catch(error){close();throw error instanceof DOMException&&error.name==='NotAllowedError'?Error('마이크 사용이 허용되지 않았어요. 브라우저 권한을 확인해 주세요.'):error;}
}
export function speechParts(markdown:string){
  const plain=markdown.replace(/!\[[^\]]*\]\([^)]*\)/g,'').replace(/\[([^\]]+)\]\([^)]*\)/g,'$1').replace(/^\s*\|?\s*[-:]+(?:\s*\|\s*[-:]+)+\s*\|?\s*$/gm,'').replace(/^\s{0,3}#{1,6}\s+/gm,'').replace(/[*_`]/g,'').replace(/^\s*[-+]\s+/gm,'').replace(/\|/g,', ').trim();
  const parts:string[]=[];let rest=plain;
  while(rest.length){let end=Math.min(rest.length,180);if(end<rest.length){const boundary=Math.max(rest.lastIndexOf(' ',end),rest.lastIndexOf('\n',end));if(boundary>80)end=boundary;}parts.push(rest.slice(0,end).trim());rest=rest.slice(end).trim();}
  return parts.filter(Boolean);
}
export function localKoreanVoice(voices:Pick<SpeechSynthesisVoice,'lang'|'localService'>[]){return voices.find(voice=>voice.localService&&/^ko(?:-|$)/i.test(voice.lang));}
export async function speakLocal(text:string,signal:AbortSignal){
  if(!('speechSynthesis' in window))throw Error('이 브라우저에는 답변 읽기가 없어요. 읽기를 끄면 음성 질문만 사용할 수 있어요.');
  const synth=window.speechSynthesis;
  let voice=localKoreanVoice(synth.getVoices()) as SpeechSynthesisVoice|undefined;
  if(!voice){await new Promise<void>(resolve=>{const done=()=>{clearTimeout(timer);synth.removeEventListener('voiceschanged',done);resolve();};const timer=setTimeout(done,1000);synth.addEventListener('voiceschanged',done);});voice=localKoreanVoice(synth.getVoices()) as SpeechSynthesisVoice|undefined;}
  signal.throwIfAborted();if(!voice)throw Error('설치된 로컬 한국어 음성이 없어요. 기기에 한국어 음성을 설치하거나 답변 읽기를 꺼 주세요. 원격 음성으로 전환하지 않아요.');
  for(const part of speechParts(text)){
    signal.throwIfAborted();await new Promise<void>((resolve,reject)=>{
      const utterance=new SpeechSynthesisUtterance(part);utterance.voice=voice!;utterance.lang='ko-KR';utterance.rate=1;
      let timer:ReturnType<typeof setTimeout>;let settled=false;
      const finish=(error?:Error)=>{if(settled)return;settled=true;clearTimeout(timer);signal.removeEventListener('abort',abort);utterance.onend=null;utterance.onerror=null;utterance.onstart=null;if(error){synth.cancel();reject(error);}else resolve();};
      const abort=()=>finish(Error('답변 읽기를 중단했어요.'));signal.addEventListener('abort',abort,{once:true});
      utterance.onstart=()=>{clearTimeout(timer);timer=setTimeout(()=>finish(Error('답변 읽기 완료를 확인하지 못했어요.')),90000);};
      utterance.onend=()=>finish();utterance.onerror=()=>finish(Error('답변 읽기가 제한되었어요. 읽기를 끄고 다시 시작할 수 있어요.'));
      timer=setTimeout(()=>finish(Error('브라우저가 자동 읽기를 시작하지 않았어요. 읽기를 끄고 다시 시작해 주세요.')),4000);
      synth.speak(utterance);
    });
  }
  // Keep the final sound tail out of the next microphone segment.
  await new Promise<void>(resolve=>setTimeout(resolve,400));signal.throwIfAborted();
}
