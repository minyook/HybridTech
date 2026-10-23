import {useEffect,useState,useSyncExternalStore} from 'react';
import {bleClient,freshSample} from './ble';
import {go} from '../ui';
export default function DirectSensorSummary(){
  const ble=useSyncExternalStore(bleClient.subscribe,bleClient.snapshot),[clock,setClock]=useState(Date.now());
  useEffect(()=>{const timer=setInterval(()=>setClock(Date.now()),1000);return()=>clearInterval(timer);},[]);
  const fresh=ble.phase==='connected'&&freshSample(ble.sample,clock);
  return <><span className="badge">{fresh?'BLE 수신값 · 규격 미검증':'현재 확인할 수 있는 BLE 값 없음'}</span><div className="metric-grid">{([['o2','O₂ %'],['h2s','H₂S ppm'],['co','CO ppm'],['co2','CO₂ %'],['lel','LEL %']] as const).map(([key,label])=><div className="metric" key={key}><strong>{fresh?ble.sample?.[key]??'—':'—'}</strong><span>{label}</span></div>)}</div><button className="btn secondary" onClick={()=>go('/sensor')}>BLE 연결·아이폰 공유 확인</button><p className="caption">공유 링크의 원격 값은 측정기 연결 화면에서 확인해 주세요. 현재 값과 승인 당시 수동 기록은 별개이며, 경보는 실제 측정기에서 확인해 주세요.</p></>;
}
