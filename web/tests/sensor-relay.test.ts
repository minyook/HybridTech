import {beforeEach,afterEach,expect,it} from 'vitest';
import {createWorkStore,type WorkStore} from '../server/store';
import {validateSample} from '../src/sensor/sample';
import {freshSample} from '../src/sensor/ble';
let store:WorkStore,time:number;
beforeEach(()=>{time=Date.parse('2026-10-23T01:00:00Z');store=createWorkStore(':memory:',()=>time);});afterEach(()=>store.close());
const sample=()=>({o2:20.9,h2s:0,co:0,co2:null,lel:2,device:'테스트 BLE',receivedAt:new Date(time).toISOString()});
it('송신 기기만 갱신·중지하고 읽기 키 소지자는 읽기만 가능하다',()=>{
  const sender=store.connect(),viewer=store.connect(),stream=store.createSensorStream(sender.token);
  expect(()=>store.readSensorStream(viewer.token,stream.id,'A'.repeat(43))).toThrow();
  expect(()=>store.updateSensorStream(viewer.token,stream.id,sample())).toThrow();expect(()=>store.stopSensorStream(viewer.token,stream.id)).toThrow();
  store.updateSensorStream(sender.token,stream.id,sample());expect(store.readSensorStream(viewer.token,stream.id,stream.readKey).sample).toEqual(sample());expect(store.connect(viewer.token).session.supervisor).toBeUndefined();
  expect(()=>store.redeemInvite(viewer.token,stream.readKey,'viewer')).toThrow();
});
it('공유 중지·새 공유 생성 시 이전 수신값을 현재 값으로 남기지 않는다',()=>{
  const sender=store.connect(),viewer=store.connect(),old=store.createSensorStream(sender.token);store.updateSensorStream(sender.token,old.id,sample());
  const current=store.createSensorStream(sender.token);expect(store.readSensorStream(viewer.token,old.id,old.readKey)).toMatchObject({active:false,sample:null});
  store.updateSensorStream(sender.token,current.id,sample());store.updateSensorStream(sender.token,current.id,null);expect(store.readSensorStream(viewer.token,current.id,current.readKey).sample).toBeNull();
  store.stopSensorStream(sender.token,current.id);expect(()=>store.updateSensorStream(sender.token,current.id,sample())).toThrow();
});
it('15초 넘은 데이터·미래 시각·수치 변조·빈 채널을 거부한다',()=>{
  const initial=sample();time+=16_000;expect(()=>validateSample(initial,time)).toThrow();
  for(const value of [{...sample(),receivedAt:new Date(time+6000).toISOString()},{...sample(),o2:Infinity},{...sample(),lel:101},{...sample(),h2s:'0'}])expect(()=>validateSample(value,time)).toThrow();
  expect(validateSample(sample(),time)).toMatchObject({lel:2,co2:null});
});
it('연결이 사라져도 10초 후 현재 값으로 표시하지 않으며 공유는 30분 후 만료된다',()=>{
  const sender=store.connect(),viewer=store.connect(),stream=store.createSensorStream(sender.token);store.updateSensorStream(sender.token,stream.id,sample());
  time+=10_000;const reading=store.readSensorStream(viewer.token,stream.id,stream.readKey).sample;expect(freshSample(reading,time)).toBe(false);
  time+=30*60*1000;expect(()=>store.readSensorStream(viewer.token,stream.id,stream.readKey)).toThrow('만료');
});
