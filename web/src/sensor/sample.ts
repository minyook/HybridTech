import type {GasSample} from './ble.ts';
export function validateSample(value:unknown,time=Date.now()):GasSample{
  if(!value||typeof value!=='object')throw Error('측정 데이터 형식을 확인해 주세요.');
  const input=value as Record<string,unknown>,sample:Record<string,unknown>={};
  for(const key of ['o2','h2s','co','co2','lel'] as const){const v=input[key];if(v!==null&&(typeof v!=='number'||!Number.isFinite(v)||v<0||v>({o2:100,h2s:1000,co:10000,co2:100,lel:100}[key])))throw Error('측정값 범위를 확인해 주세요.');sample[key]=v;}
  const at=typeof input.receivedAt==='string'?Date.parse(input.receivedAt):NaN;
  if(!Number.isFinite(at)||time-at>15_000||at-time>5_000)throw Error('오래되었거나 미래 시각인 측정값은 공유할 수 없어요.');
  if(typeof input.device!=='string'||input.device.length>200)throw Error('측정기 이름을 확인해 주세요.');
  if(['o2','h2s','co','co2','lel'].every(key=>sample[key]===null))throw Error('수신된 측정값이 없어요.');
  return {...sample,device:input.device,receivedAt:new Date(at).toISOString()} as unknown as GasSample;
}
export interface SensorLease {id:string;readKey:string;expiresAt:string}
export interface SensorView {sample:GasSample|null;updatedAt:string|null;expiresAt:string;active:boolean}
export function sensorURL(origin:string,lease:Pick<SensorLease,'id'|'readKey'>){return `${new URL(origin).origin}/#/sensor?stream=${encodeURIComponent(lease.id)}&key=${encodeURIComponent(lease.readKey)}`;}
export function parseSensorURL(value:string,origin:string):Pick<SensorLease,'id'|'readKey'>{
  let url:URL;try{url=new URL(value.trim());}catch{throw Error('같은 사이트의 측정값 공유 링크를 입력해 주세요.');}
  if(url.origin!==new URL(origin).origin||url.pathname!=='/'||url.search||!url.hash.startsWith('#/sensor?'))throw Error('이 사이트에서 발급한 측정값 공유 QR만 사용할 수 있어요.');
  const params=new URLSearchParams(url.hash.split('?')[1]),id=params.get('stream')??'',readKey=params.get('key')??'';
  if(!/^[a-f0-9-]{36}$/.test(id)||!/[A-Za-z0-9_-]{43}/.test(readKey)||readKey.length!==43)throw Error('공유 링크가 올바르지 않아요.');
  return {id,readKey};
}
