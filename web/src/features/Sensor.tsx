import {useEffect,useRef,useState,useSyncExternalStore} from 'react';
import QRCode from 'qrcode';
import {bleClient,bluetoothSupport,freshSample,type BleConfig,type GasSample,type PacketFormat} from '../sensor/ble';
import {parseSensorURL,sensorURL,type SensorLease,type SensorView} from '../sensor/sample';
import {getSetting,setSetting} from '../storage/db';
import {useWorkSession,workRequest} from '../workflow/access';
import {formatDate,Icon,Notice,PageHead} from '../ui';
import QrScanner from './QrScanner';
const fields=[['o2','O₂','%'],['h2s','H₂S','ppm'],['co','CO','ppm'],['co2','CO₂','%'],['lel','LEL','% LEL']] as const;
function Readings({sample,clock}:{sample?:GasSample|null;clock:number}){
  const fresh=freshSample(sample??undefined,clock);
  return <div className="sensor-values"><span className={`badge ${fresh?'blue':''}`}>{fresh?'수신값 · 규격 미검증':sample?'마지막 수신값 · 10초 이상 갱신 안 됨':'수신된 측정값 없음'}</span><div className="gas-form">{fields.map(([key,label,unit])=><div className="sensor-value" key={key}><span>{label}</span><strong>{fresh&&sample?.[key]!==null&&sample?.[key]!==undefined?sample[key]:'—'}<small>{unit}</small></strong></div>)}</div>{sample&&<p className="caption">{sample.device} · 마지막 수신 {formatDate(sample.receivedAt)}{!fresh?' · 현재 상태로 사용할 수 없어요.':''}</p>}</div>;
}
export default function Sensor(){
  const access=useWorkSession(),ble=useSyncExternalStore(bleClient.subscribe,bleClient.snapshot),support=bluetoothSupport();
  const [config,setConfig]=useState<BleConfig>({service:'',characteristic:'',namePrefix:'',format:'raw'}),[loaded,setLoaded]=useState(false),[selected,setSelected]=useState('');
  const [error,setError]=useState(''),[busy,setBusy]=useState(false),[clock,setClock]=useState(Date.now()),[hidden,setHidden]=useState(document.hidden);
  const [lease,setLease]=useState<SensorLease>(),[qr,setQr]=useState(''),[viewer,setViewer]=useState<Pick<SensorLease,'id'|'readKey'>>(),[remote,setRemote]=useState<SensorView>(),[remoteError,setRemoteError]=useState(''),[link,setLink]=useState('');
  const latest=useRef(ble);latest.current=ble;
  useEffect(()=>{let active=true;void getSetting<BleConfig>('ble-config-v1').then(value=>{if(active&&value)setConfig(value);}).catch(()=>{}).finally(()=>{if(active)setLoaded(true);});return()=>{active=false;};},[]);
  useEffect(()=>{if(!loaded)return;const timer=setTimeout(()=>{void setSetting('ble-config-v1',config).catch(()=>setError('연결 설정을 저장하지 못했어요.'));},300);return()=>clearTimeout(timer);},[config,loaded]);
  useEffect(()=>{const timer=setInterval(()=>setClock(Date.now()),1000),change=()=>setHidden(document.hidden);document.addEventListener('visibilitychange',change);return()=>{clearInterval(timer);document.removeEventListener('visibilitychange',change);};},[]);
  function join(value:string){try{setViewer(parseSensorURL(value,location.origin));setRemote(undefined);setRemoteError('');setLink('');}catch(error){setRemoteError((error as Error).message);}}
  useEffect(()=>{if(!location.hash.includes('?stream='))return;join(`${location.origin}${location.pathname}${location.hash}`);history.replaceState(null,'',`${location.pathname}${location.search}#/sensor`);},[]);
  useEffect(()=>{if(!ble.channels.some(channel=>channel.uuid===selected))setSelected(ble.channels[0]?.uuid??'');},[ble.channels,selected]);
  async function execute(action:()=>Promise<unknown>){if(busy)return;setBusy(true);setError('');try{await action();}catch(error){setError(error instanceof Error?error.message:'처리하지 못했어요.');}finally{setBusy(false);}}
  useEffect(()=>{
    if(!lease)return;let active=true,running=false;
    const push=async()=>{if(!active||running)return;running=true;try{const value=latest.current;await workRequest(`sensor-streams/${lease.id}/update`,{sample:value.phase==='connected'&&freshSample(value.sample)&&!document.hidden?value.sample:null});}catch(error){if(active)setError((error as Error).message);}finally{running=false;}};
    void push();const timer=setInterval(()=>{void push();},2000);
    return()=>{active=false;clearInterval(timer);void workRequest(`sensor-streams/${lease.id}/stop`,{}).catch(()=>{});};
  },[lease]);
  useEffect(()=>{
    if(!viewer||!access.session)return;let active=true,running=false;
    const read=async()=>{if(!active||running)return;running=true;try{const result=await workRequest<SensorView>(`sensor-streams/${viewer.id}`,undefined,{'X-Fieldguard-Sensor':viewer.readKey});if(active){setRemote(result);setRemoteError('');}}catch(error){if(active){setRemote(undefined);setRemoteError((error as Error).message);}}finally{running=false;}};
    void read();const timer=setInterval(()=>{if(!document.hidden)void read();},2000);return()=>{active=false;clearInterval(timer);};
  },[viewer,access.session?.workerId]);
  const connected=ble.phase==='connected',connecting=['connecting','choosing'].includes(ble.phase),channel=ble.channels.find(item=>item.uuid===selected);
  return <><PageHead eyebrow="BLUETOOTH LE" title="가스 측정기 연결" description="지원 브라우저에서 BLE 연결과 데이터 수신을 시험해요. 아이폰은 공유 링크로 수신값을 확인할 수 있어요."/>
    <Notice kind="warning">연결·수신 시험 기능이에요. 제조사별 패킷·단위·검교정은 아직 검증하지 않았어요. 수신값으로 작업을 자동 승인하거나 비상 경보를 대신하지 않아요.</Notice>
    <div className="sensor-layout"><section className="card"><h2><Icon name="bluetooth"/> 내 기기에 직접 연결</h2>{support?<Notice>{support}</Notice>:<>
      <p>측정기 전원을 켜고 다른 앱의 연결을 해제한 뒤 선택해 주세요. 별도의 측정 시작 명령이 필요한 기기는 제조사 규격 확인이 필요해요.</p>
      <label className="field">기기 이름 접두어 (선택)<input value={config.namePrefix} maxLength={80} disabled={connected||connecting} onChange={event=>setConfig({...config,namePrefix:event.target.value})} placeholder="비워두면 주변 BLE 기기 선택"/></label>
      <label className="field">측정 서비스 UUID<input value={config.service} maxLength={36} disabled={connected||connecting} onChange={event=>setConfig({...config,service:event.target.value})} placeholder="제조사 UUID · 비우면 연결만 확인"/></label>
      <label className="field">수신 채널 UUID (선택)<input value={config.characteristic} maxLength={36} disabled={connected||connecting} onChange={event=>setConfig({...config,characteristic:event.target.value})} placeholder="비워두면 해당 서비스의 채널 목록 확인"/></label>
      <label className="field">데이터 해석 형식<select value={config.format} disabled={connected||connecting} onChange={event=>setConfig({...config,format:event.target.value as PacketFormat})}><option value="raw">원시 데이터만 보기 (기본)</option><option value="json-v1">시험용 JSON · o2/h2s/co/co2/lel 숫자</option><option value="key-value-v1">시험용 key=value 텍스트</option><option value="csv-o2-h2s-co-lel">시험용 CSV · O₂,H₂S,CO,LEL 순서</option></select></label>
      <p className="caption">CO₂는 %, LEL은 % LEL로 서로 다른 채널이에요. CSV의 마지막 값을 CO₂로 사용하지 않아요. 형식 변경은 연결 해제 후 가능해요.</p>
      <label className="field">텍스트 프레임 구분<select value={config.framing??'packet'} disabled={connected||connecting} onChange={event=>setConfig({...config,framing:event.target.value as 'packet'|'newline'})}><option value="packet">알림 1개 = 데이터 1개</option><option value="newline">줄바꿈까지 합치기 (분할 전송·UART)</option></select></label>
      <div className="button-row"><button className="btn primary" disabled={connecting||connected||busy||!loaded} onClick={()=>execute(()=>bleClient.connect(config))}>기기 선택·연결</button><button className="btn secondary" disabled={!connecting&&!connected} onClick={()=>bleClient.disconnect()}>{connecting?'연결 취소':'연결 해제'}</button></div>
    </>}
    <p role="status" className="notice">{ble.device&&`${ble.device} · `}{ble.message}</p>
    {!!ble.channels.length&&<><label className="field">데이터 채널<select value={selected} onChange={event=>{bleClient.stopReceiving();setSelected(event.target.value);}} disabled={busy}>{ble.channels.map(item=><option key={item.uuid} value={item.uuid}>{item.uuid} · {item.notify?'알림 ':''}{item.read?'읽기':''}</option>)}</select></label><div className="button-row"><button className="btn primary" disabled={!channel?.notify||busy||ble.subscribed&&ble.channel===selected} onClick={()=>execute(()=>bleClient.listen(selected))}>실시간 수신 시작</button><button className="btn secondary" disabled={!ble.subscribed||busy} onClick={()=>bleClient.stopReceiving()}>수신 중지</button><button className="btn secondary" disabled={!channel?.read||busy} onClick={()=>execute(()=>bleClient.read(selected))}>한 번 읽기</button></div></>}
    {hidden&&<Notice kind="warning">화면이 숨겨졌어요. 백그라운드의 계속 수신을 보장하지 않으며 공유는 신선한 값만 전송해요.</Notice>}
    <Readings sample={connected?ble.sample:undefined} clock={clock}/><p className="caption">수신 패킷 {ble.packets}개{ble.lastAt?` · ${formatDate(ble.lastAt)}`:''}</p>
    {!!ble.logs.length&&<details><summary>원시 패킷 확인 · 최근 20개</summary><div className="sensor-log">{ble.logs.map((item,i)=><div key={`${item.at}:${i}`}><small>{formatDate(item.at)}</small><pre>{item.text||'(UTF-8 텍스트 아님)'}</pre><code>{item.hex}</code></div>)}</div><button className="btn secondary" onClick={()=>{const url=URL.createObjectURL(new Blob([JSON.stringify({device:ble.device,format:config.format,logs:ble.logs},null,2)],{type:'application/json'}));const anchor=document.createElement('a');anchor.href=url;anchor.download='fieldguard-ble-diagnostics.json';anchor.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}}>수신 진단 저장</button></details>}
    {error&&<p className="notice warning" role="alert">{error}</p>}</section>
    <section className="card"><h2>아이폰·다른 기기로 공유</h2><p>직접 연결한 S24·태블릿·PC가 이 화면을 열어둔 동안, 같은 서버를 통해 해석된 최신 값만 공유해요. 원시 패킷은 서버에 보내지 않아요. 공유 링크는 감독관 권한을 부여하지 않아요.</p>
      <button className="btn primary" disabled={!connected||config.format==='raw'||!access.session||busy||!!lease} onClick={()=>execute(async()=>{const value=await workRequest<SensorLease>('sensor-streams',{});setLease(value);setQr(await QRCode.toDataURL(sensorURL(location.origin,value),{width:300,margin:4}));})}>측정값 공유 QR 만들기</button>
      {lease&&<div className="supervisor-qr">{clock<Date.parse(lease.expiresAt)&&qr?<><img src={qr} alt="측정값 읽기 전용 공유 QR" width={260} height={260}/><p>공유 만료 {formatDate(lease.expiresAt)}</p><button className="btn secondary" onClick={()=>execute(()=>navigator.clipboard.writeText(sensorURL(location.origin,lease)))}>공유 링크 복사</button></>:<p>공유 기한이 지났어요.</p>}<button className="btn secondary" onClick={()=>{setLease(undefined);setQr('');}}>공유 중지</button></div>}
      <hr/><h3>전달받은 측정값 보기</h3><QrScanner onCode={join}/><label className="field">측정값 공유 링크<input type="password" autoComplete="off" spellCheck={false} maxLength={2048} value={link} onChange={event=>setLink(event.target.value)}/></label><button className="btn secondary" disabled={!link.trim()} onClick={()=>join(link)}>공유 연결</button>
      {viewer&&<><p className="badge">서버를 통한 읽기 전용 수신 · 직접 BLE 연결 아님</p><Readings sample={remote?.active?remote.sample:null} clock={clock}/>{remote&&!remote.active&&<Notice>보내는 기기에서 공유를 중지했어요.</Notice>}<button className="text-button" onClick={()=>{setViewer(undefined);setRemote(undefined);setRemoteError('');}}>공유 보기 종료</button></>}
      {(remoteError||access.error)&&<p className="notice warning" role="alert">{remoteError||access.error}</p>}
      <p className="caption">다른 기기에도 같은 HTTPS 주소로 접속해 주세요. 서버·송신 기기 연결이 끊기거나 10초간 값이 갱신되지 않으면 현재 값으로 표시하지 않아요.</p>
    </section></div></>;
}
