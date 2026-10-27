import {useEffect,useRef,useState} from 'react';
import {downloadVoice,voiceInstalled} from '../voice/assets';
import {VOICE_MODEL_BYTES} from '../voice/catalog';
import {VoiceConversation,type VoiceState} from '../voice/conversation';
import {LocalRecognizer,voiceRecovery} from '../voice/recognizer';
import {openMicrophone,speakLocal} from '../voice/browser-audio';
export default function VoicePanel({answer,busy,onActive}:{onActive:(active:boolean)=>void;answer:(text:string,signal:AbortSignal)=>Promise<string>;busy:boolean}){
  const [state,setState]=useState<VoiceState>({phase:'off',message:'대화를 켜두면 말이 끝날 때 자동으로 질문해요.'});
  const [installed,setInstalled]=useState(false),[downloading,setDownloading]=useState(false),[progress,setProgress]=useState(''),[read,setRead]=useState(true),[expanded,setExpanded]=useState(false),[recovery,setRecovery]=useState(false);
  const latest=useRef({answer,read,onActive});latest.current={answer,read,onActive};
  const conversation=useRef<VoiceConversation|undefined>(undefined),recognizer=useRef<LocalRecognizer|undefined>(undefined),download=useRef<AbortController|undefined>(undefined);
  useEffect(()=>{
    void voiceInstalled().then(setInstalled).catch(()=>{});setRecovery(voiceRecovery());
    const engine=new LocalRecognizer();recognizer.current=engine;
    const session=new VoiceConversation({open:openMicrophone,prepare:()=>engine.prepare(),transcribe:audio=>engine.transcribe(audio),release:clean=>engine.stop(clean),answer:(text,signal)=>latest.current.answer(text,signal),speak:(text,signal)=>latest.current.read?speakLocal(text,signal):Promise.resolve(),update:value=>{setState(value);latest.current.onActive(!['off','error'].includes(value.phase));}});conversation.current=session;
    const hidden=()=>{if(document.hidden)session.stop();},leave=()=>session.stop();document.addEventListener('visibilitychange',hidden);window.addEventListener('pagehide',leave);
    return()=>{session.stop();download.current?.abort();document.removeEventListener('visibilitychange',hidden);window.removeEventListener('pagehide',leave);};
  },[]);
  const active=!['off','error'].includes(state.phase);
  async function prepare(){setDownloading(true);setProgress('음성 파일 크기와 저장 공간을 확인해요.');download.current=new AbortController();try{await downloadVoice(download.current.signal,setProgress);setInstalled(true);}catch(error){setProgress(download.current.signal.aborted?'다운로드를 중단했어요. 검증된 파일은 다음에 재사용해요.':error instanceof Error?error.message:'다운로드 실패');}finally{setDownloading(false);}}
  return <section className={`voice-panel ${active?'active':''}`} aria-label="로컬 음성 대화">
    <div className="voice-toolbar"><button type="button" className="btn secondary small" aria-expanded={expanded} onClick={()=>setExpanded(!expanded)}>음성 대화 {active?'켜짐':'설정'}</button>{installed&&<button type="button" className={`btn small ${active?'danger':'primary'}`} disabled={!active&&busy} onClick={()=>{if(active)conversation.current?.stop();else{recognizer.current?.resetRecovery();setRecovery(false);void conversation.current?.start();}}}>{active?'대화 종료':recovery?'음성 대화 다시 시작':'음성 대화 시작'}</button>}<span role="status">{state.message}</span></div>
    {(expanded||active||state.phase==='error')&&<div className="voice-details"><p>처음 한 번 켜면 자동으로 듣고 답해요. 인식한 질문은 대화에 표시돼요. 읽는 동안에는 인식을 멈추고, 다른 화면으로 가면 마이크를 닫아요.</p>
      <label><input type="checkbox" checked={read} onChange={event=>setRead(event.target.checked)}/> 설치된 한국어 음성으로 답변 읽기</label>
      {!installed&&<div className="voice-download"><p>한국어 Whisper tiny q8 · 모델 {(VOICE_MODEL_BYTES/1_000_000).toFixed(1)}MB + 실행 파일. 최초 다운로드 후 기기에서 인식해요. 음성은 서버로 보내지 않아요.</p><button type="button" className="btn primary small" disabled={downloading||busy} onClick={()=>{void prepare();}}>로컬 음성 파일 다운로드</button>{downloading&&<button type="button" className="text-button" onClick={()=>download.current?.abort()}>다운로드 중단</button>}</div>}
      {progress&&<p role="status">{progress}</p>}{recovery&&<p className="voice-warning">이전 음성 작업 완료를 확인하지 못했어요. 자동 실행하지 않으며 ‘다시 시작’으로 직접 재시도할 수 있어요.</p>}
      <p className="caption">화면을 켜둔 상태에서 사용해 주세요. 주변 사람의 말·소음도 인식할 수 있고, 전문 용어·수치는 화면과 원문에서 확인해야 해요. 한 질문은 20초 이내예요.</p>
    </div>}
  </section>;
}
