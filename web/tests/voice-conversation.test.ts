import {expect,it,vi} from 'vitest';
import {VoiceConversation,type ConversationPorts,type VoiceState} from '../src/voice/conversation';
import {SpeechSegmenter,resample16k} from '../src/voice/segmenter';
import {localKoreanVoice,speechParts} from '../src/voice/browser-audio';
const silence=()=>new Float32Array(1600),speech=()=>new Float32Array(1600).fill(0.08);
function defer<T>(){let resolve!:(value:T)=>void;const promise=new Promise<T>(done=>{resolve=done;});return {promise,resolve};}
function fixture(overrides:Partial<ConversationPorts>={}){
  let frames!:(audio:Float32Array,rate:number)=>void;const states:VoiceState[]=[],capture={pause:vi.fn(),close:vi.fn()};
  const ports:ConversationPorts={open:vi.fn(async receive=>{frames=receive;return capture;}),prepare:vi.fn(async()=>{}),transcribe:vi.fn(async()=>'전등은 어떤 걸 써야 해?'),release:vi.fn(),answer:vi.fn(async()=>'가연성가스 조건에서 방폭구조 전등을 사용합니다.'),speak:vi.fn(async()=>{}),update:value=>states.push(value),...overrides};
  const session=new VoiceConversation(ports);
  const say=()=>{for(let i=0;i<8;i++)frames(silence(),16000);for(let i=0;i<8;i++)frames(speech(),16000);for(let i=0;i<14;i++)frames(silence(),16000);};
  return {session,ports,states,capture,say,frame:(audio:Float32Array)=>frames(audio,16000)};
}
it('짧은 충격음·침묵은 질문으로 보내지 않고 말이 끝나면 자동으로 세그먼트를 반환한다',()=>{
  const detector=new SpeechSegmenter();for(let i=0;i<8;i++)expect(detector.push(silence(),16000)).toBeUndefined();
  detector.push(speech(),16000);for(let i=0;i<14;i++)expect(detector.push(silence(),16000)).toBeUndefined();
  for(let i=0;i<8;i++)detector.push(speech(),16000);let result;for(let i=0;i<13;i++){const event=detector.push(silence(),16000);if(event?.type==='complete')result=event;}
  expect(result?.audio?.length).toBeGreaterThan(16000);expect(result?.rate).toBe(16000);
});
it('긴 발화는 중간을 잘라 자동 전송하지 않는다',()=>{const detector=new SpeechSegmenter();for(let i=0;i<8;i++)detector.push(silence(),16000);let tooLong=false;for(let i=0;i<215;i++)if(detector.push(speech(),16000)?.type==='too-long')tooLong=true;expect(tooLong).toBe(true);});
it.each([44100,48000])('%s Hz 실제 마이크 PCM을 16kHz로 변환하고 범위를 제한한다',rate=>{const out=resample16k(new Float32Array(rate).fill(0.5),rate);expect(out).toHaveLength(16000);expect(out.every(n=>n===0.5)).toBe(true);expect(()=>resample16k(new Float32Array(rate*22),rate)).toThrow();});
it('한 번 켠 뒤 발화→자동 전송→읽기→다시 듣기를 수행하고 읽는 소리를 재입력하지 않는다',async()=>{
  const reading=defer<void>(),f=fixture({speak:vi.fn(()=>reading.promise)});await f.session.start();f.say();
  await vi.waitFor(()=>expect(f.states.at(-1)?.phase).toBe('reading'));expect(f.ports.answer).toHaveBeenCalledTimes(1);expect(f.capture.pause).toHaveBeenLastCalledWith(true);
  f.say();expect(f.ports.transcribe).toHaveBeenCalledTimes(1);expect(f.ports.prepare).toHaveBeenCalledTimes(1);
  reading.resolve();await vi.waitFor(()=>expect(f.states.at(-1)?.phase).toBe('listening'));expect(f.ports.prepare).toHaveBeenCalledTimes(2);
  f.session.stop();expect(f.capture.close).toHaveBeenCalled();
});
it('종료 후 도착한 음성 인식 결과를 질문으로 보내지 않는다',async()=>{
  const decode=defer<string>(),f=fixture({transcribe:()=>decode.promise});await f.session.start();f.say();expect(f.states.at(-1)?.phase).toBe('transcribing');f.session.stop();decode.resolve('뒤늦은 질문');await Promise.resolve();expect(f.ports.answer).not.toHaveBeenCalled();expect(f.states.at(-1)?.phase).toBe('off');
});
it('마이크 권한 대기 중 종료해도 뒤늦게 얻은 마이크를 닫는다',async()=>{
  const opened=defer<{pause:()=>void;close:()=>void}>(),close=vi.fn(),f=fixture({open:()=>opened.promise});const started=f.session.start();f.session.stop();opened.resolve({pause:()=>{},close});await started;expect(close).toHaveBeenCalledOnce();expect(f.ports.prepare).not.toHaveBeenCalled();
});
it('준비 오류가 자동 재시도 루프를 만들지 않는다',async()=>{const f=fixture({prepare:vi.fn(async()=>{throw Error('메모리 정리 미확인');})});await f.session.start();expect(f.ports.prepare).toHaveBeenCalledTimes(1);expect(f.states.at(-1)).toMatchObject({phase:'error',message:'메모리 정리 미확인'});expect(f.capture.close).toHaveBeenCalled();});
it('음성으로 대화 종료를 말하면 문서 질문 없이 종료한다',async()=>{const f=fixture({transcribe:async()=>'음성 대화 종료.'});await f.session.start();f.say();await vi.waitFor(()=>expect(f.states.at(-1)?.phase).toBe('off'));expect(f.ports.answer).not.toHaveBeenCalled();});
it('한국어 원격 음성을 선택하지 않으며 수치·조건을 읽기에서 보존한다',()=>{
  const remote={lang:'ko-KR',localService:false},local={lang:'ko-KR',localService:true};expect(localKoreanVoice([remote])).toBeUndefined();expect(localKoreanVoice([remote,local])).toBe(local);
  const text=speechParts('## 산소\n**18% 이상 23.5% 미만**입니다. [원문](https://field.test)\n| 기준 | 값 |\n|---|---|\n| CO | 30 ppm |').join(' ');expect(text).toContain('18% 이상 23.5% 미만');expect(text).toContain('30 ppm');expect(text).not.toContain('https://');expect(text).not.toContain('---');
});
