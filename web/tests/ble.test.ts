import {expect,it,vi} from 'vitest';
import {BleClient,bluetoothSupport,freshSample,parseGasPacket,uuid,type BleCharacteristic,type BleDevice,type BleConfig} from '../src/sensor/ble';
const service='0000181a-0000-1000-8000-00805f9b34fb',channel='00002a6e-0000-1000-8000-00805f9b34fb';
const config:BleConfig={service:'181a',characteristic:'',namePrefix:'',format:'json-v1'};
function fake(){
  const characteristic=Object.assign(new EventTarget(),{uuid:channel,properties:{read:true,notify:true},value:undefined as DataView|undefined,readValue:vi.fn(async()=>new DataView(new TextEncoder().encode('{"o2":20.9}').buffer)),startNotifications:vi.fn(async()=>characteristic),stopNotifications:vi.fn(async()=>characteristic)}) as BleCharacteristic;
  const server={connected:false,connect:vi.fn(async()=>{server.connected=true;return server;}),disconnect:vi.fn(()=>{server.connected=false;}),getPrimaryService:vi.fn(async()=>({getCharacteristics:async()=>[characteristic],getCharacteristic:async()=>characteristic}))};
  const device=Object.assign(new EventTarget(),{id:'test',name:'Test meter',gatt:server}) as BleDevice;
  const requestDevice=vi.fn(async()=>device),client=new BleClient(()=>({requestDevice}),()=>100_000);
  const send=(text:string)=>{const bytes=new TextEncoder().encode(text);characteristic.value=new DataView(bytes.buffer);characteristic.dispatchEvent(new Event('characteristicvaluechanged'));};
  return {client,device,server,characteristic,requestDevice,send};
}
it('HTTPS·브라우저 지원 조건과 UUID를 확인한다',()=>{
  expect(bluetoothSupport(false,{})).toContain('HTTPS');expect(bluetoothSupport(true,undefined)).toContain('지원하지');expect(bluetoothSupport(true,{})).toBe('');
  expect(uuid('0x181A')).toBe(service);expect(uuid('12345678')).toBe('12345678-0000-1000-8000-00805f9b34fb');expect(()=>uuid('battery')).toThrow();
});
it('클릭 직후 기기 권한 선택을 요청하고 명시한 서비스만 접근한다',async()=>{
  const f=fake(),promise=f.client.connect(config);expect(f.requestDevice).toHaveBeenCalledWith({acceptAllDevices:true,optionalServices:[service]});
  await promise;expect(f.server.getPrimaryService).toHaveBeenCalledWith(service);expect(f.client.snapshot().phase).toBe('connected');expect(f.client.snapshot().channels[0].notify).toBe(true);
});
it('서비스를 모르면 연결만 확인하고 측정값·채널을 꾸며내지 않는다',async()=>{
  const f=fake();await f.client.connect({...config,service:''});expect(f.server.getPrimaryService).not.toHaveBeenCalled();expect(f.client.snapshot().sample).toBeUndefined();expect(f.client.snapshot().message).toContain('연결만');
});
it('잘못된 UUID는 하드웨어 선택 요청 전에 거부한다',async()=>{const f=fake();await expect(f.client.connect({...config,service:'invalid'})).rejects.toThrow();expect(f.requestDevice).not.toHaveBeenCalled();});
it('notify 구독·값 수신·구독 중지·끊김 후 이전 값 제거를 처리한다',async()=>{
  const f=fake();await f.client.connect(config);await f.client.listen(channel);f.send('{"o2":20.9,"co":0,"lel":2}');
  expect(f.client.snapshot().sample).toMatchObject({o2:20.9,co:0,lel:2,co2:null});expect(f.client.snapshot().packets).toBe(1);
  f.client.stopReceiving();f.send('{"o2":21}');expect(f.client.snapshot().packets).toBe(1);expect(f.client.snapshot().sample).toBeUndefined();
  await f.client.listen(channel);f.send('{"o2":20}');f.device.dispatchEvent(new Event('gattserverdisconnected'));
  expect(f.client.snapshot()).toMatchObject({phase:'disconnected',subscribed:false,channels:[]});expect(f.client.snapshot().sample).toBeUndefined();
});
it('read 단일 수신과 바이너리·무효 데이터에서 오래된 값 제거를 처리한다',async()=>{
  const f=fake();await f.client.connect(config);await f.client.read(channel);expect(f.client.snapshot().sample?.o2).toBe(20.9);
  await f.client.listen(channel);f.send('not-json');expect(f.client.snapshot().sample).toBeUndefined();expect(f.client.snapshot().logs[0].text).toBe('not-json');
});
it('기기 선택 중 취소 뒤 늦게 도착한 결과로 연결하지 않는다',async()=>{
  const f=fake();let finish!:(device:BleDevice)=>void;const client=new BleClient(()=>({requestDevice:()=>new Promise(resolve=>{finish=resolve;})}));
  const pending=client.connect(config);client.disconnect();finish(f.device);await pending;expect(f.server.connect).not.toHaveBeenCalled();expect(client.snapshot().phase).toBe('disconnected');
});
it('연결 중 취소하면 늦은 연결도 해제하고 이전 채널을 재사용하지 않는다',async()=>{
  const f=fake();let finish!:(server:typeof f.server)=>void;f.server.connect.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
  const pending=f.client.connect(config);await vi.waitFor(()=>expect(f.server.connect).toHaveBeenCalled());f.client.disconnect();finish(f.server);await pending;expect(f.server.disconnect).toHaveBeenCalled();expect(f.client.snapshot().sample).toBeUndefined();
});
it('진단은 최근 20개·패킷별 제한을 유지하며 DataView의 오프셋을 준수한다',async()=>{
  const f=fake();await f.client.connect(config);await f.client.listen(channel);for(let i=0;i<30;i++)f.send('{"o2":20}');expect(f.client.snapshot().logs).toHaveLength(20);
  const bytes=new TextEncoder().encode('XX{"o2":21}ZZ');f.characteristic.value=new DataView(bytes.buffer,2,bytes.length-4);f.characteristic.dispatchEvent(new Event('characteristicvaluechanged'));expect(f.client.snapshot().sample?.o2).toBe(21);
});
it('수신 실패·기기 선택 취소를 오류 상태로 처리하며 권한은 자동 재요청하지 않는다',async()=>{
  const port={requestDevice:vi.fn(async()=>{throw new DOMException('cancel','NotFoundError');})},client=new BleClient(()=>port);await client.connect(config);expect(client.snapshot().phase).toBe('error');expect(port.requestDevice).toHaveBeenCalledTimes(1);
});
it('줄바꿈 프레임은 분할·복수 전송을 복원하며 구독 중지 시 잔여 조각을 버린다',async()=>{
  const f=fake();await f.client.connect({...config,framing:'newline'});await f.client.listen(channel);f.send('{"o2":');expect(f.client.snapshot().sample).toBeUndefined();
  f.send('20.9}\n{"co":2}\n');expect(f.client.snapshot().sample).toMatchObject({co:2,o2:null});
  f.send('{"o2":');f.client.stopReceiving();await f.client.listen(channel);f.send('19}\n');expect(f.client.snapshot().sample).toBeUndefined();
});
it('과도한 프레임과 패킷을 거부하고 이전 유효값을 남기지 않는다',async()=>{
  const f=fake();await f.client.connect({...config,framing:'newline'});await f.client.listen(channel);f.send('{"o2":20}\n');expect(f.client.snapshot().sample?.o2).toBe(20);f.send('x'.repeat(8193));expect(f.client.snapshot().sample).toBeUndefined();
  f.send('{"o2":20}\n');f.characteristic.value=new DataView(new ArrayBuffer(65537));f.characteristic.dispatchEvent(new Event('characteristicvaluechanged'));expect(f.client.snapshot().sample).toBeUndefined();
});
it('CSV의 LEL을 CO₂로 바꾸지 않고 숫자·범위·형식·별칭 충돌을 검증한다',()=>{
  expect(parseGasPacket('20.9,0,0,2','csv-o2-h2s-co-lel','test',100_000)).toMatchObject({o2:20.9,lel:2,co2:null});
  for(const text of ['20.9,,0,2','20.9,0,0,NaN','120,0,0,0'])expect(parseGasPacket(text,'csv-o2-h2s-co-lel','test')).toBeUndefined();
  for(const text of ['{"o2":"20.9"}','{"o2":20.9,"oxygen":19}','{"o2":-1}'])expect(parseGasPacket(text,'json-v1','test')).toBeUndefined();
  expect(parseGasPacket('{"o2":20.9}','raw','test')).toBeUndefined();
  const sample=parseGasPacket('o2=20.9;co=0;co2=0.04','key-value-v1','test',100_000)!;expect(sample.co2).toBe(.04);expect(freshSample(sample,109_999)).toBe(true);expect(freshSample(sample,110_000)).toBe(false);expect(freshSample(sample,99_999)).toBe(false);
});
