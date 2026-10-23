export type PacketFormat='raw'|'json-v1'|'key-value-v1'|'csv-o2-h2s-co-lel';
export interface GasSample {o2:number|null;h2s:number|null;co:number|null;co2:number|null;lel:number|null;receivedAt:string;device:string}
export interface BleConfig {service:string;characteristic:string;namePrefix:string;format:PacketFormat;framing?:'packet'|'newline'}
export interface BleCharacteristic extends EventTarget {uuid:string;properties:{read?:boolean;notify?:boolean;indicate?:boolean};value?:DataView;readValue():Promise<DataView>;startNotifications():Promise<BleCharacteristic>;stopNotifications():Promise<BleCharacteristic>}
interface BleService {getCharacteristics():Promise<BleCharacteristic[]>;getCharacteristic(id:string):Promise<BleCharacteristic>}
interface BleServer {connected:boolean;connect():Promise<BleServer>;disconnect():void;getPrimaryService(id:string):Promise<BleService>}
export interface BleDevice extends EventTarget {id:string;name?:string;gatt?:BleServer}
export interface BluetoothPort {requestDevice(options:{acceptAllDevices?:boolean;filters?:{namePrefix:string}[];optionalServices:string[]}):Promise<BleDevice>}
export interface BleState {phase:'idle'|'choosing'|'connecting'|'connected'|'disconnected'|'error';message:string;device:string;channels:{uuid:string;read:boolean;notify:boolean}[];channel:string;subscribed:boolean;packets:number;lastAt:string;sample?:GasSample;logs:{at:string;hex:string;text:string}[]}
export const blankBleState=():BleState=>({phase:'idle',message:'측정기를 선택해 연결해 주세요.',device:'',channels:[],channel:'',subscribed:false,packets:0,lastAt:'',logs:[]});
export function bluetoothSupport(secure=globalThis.isSecureContext,port:unknown=(globalThis.navigator as Navigator&{bluetooth?:BluetoothPort})?.bluetooth){return !secure?'HTTPS 또는 localhost에서 접속해 주세요.':!port?'이 브라우저는 직접 BLE 연결을 지원하지 않아요. Android Chrome·PC Chrome/Edge를 사용하거나 측정값 공유 링크를 열어 주세요.':'';}
export function uuid(value:string){
  const text=value.trim().toLowerCase().replace(/^0x/,'');
  if(/^[0-9a-f]{4}$/.test(text))return `0000${text}-0000-1000-8000-00805f9b34fb`;
  if(/^[0-9a-f]{8}$/.test(text))return `${text}-0000-1000-8000-00805f9b34fb`;
  if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(text))throw Error('서비스·채널 UUID를 확인해 주세요. 16/32비트 또는 전체 UUID를 입력해 주세요.');
  return text;
}
export function parseGasPacket(text:string,format:PacketFormat,device:string,time=Date.now()):GasSample|undefined{
  if(format==='raw'||text.length>8192)return;
  let values:Record<string,unknown>={};
  try{
    if(format==='json-v1'){const parsed:unknown=JSON.parse(text);if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))return;values=parsed as Record<string,unknown>;}
    else if(format==='key-value-v1'){for(const match of text.matchAll(/\b(o2|oxygen|h2s|co2|co|lel|lfl)\s*[:=]\s*(-?\d+(?:\.\d+)?)(?=\s|[,;]|$)/gi))values[match[1].toLowerCase()]=Number(match[2]);}
    else {const cells=text.trim().split(/[,;]/);if(cells.length!==4||cells.some(value=>!value.trim()||!Number.isFinite(Number(value))))return;values=Object.fromEntries(['o2','h2s','co','lel'].map((key,i)=>[key,Number(cells[i])]));}
  }catch{return;}
  const aliases={o2:['o2','oxygen','oxygenPercent'],h2s:['h2s'],co:['co'],co2:['co2'],lel:['lel','lfl']};
  const sample:GasSample={o2:null,h2s:null,co:null,co2:null,lel:null,receivedAt:new Date(time).toISOString(),device};
  for(const key of Object.keys(aliases) as (keyof typeof aliases)[]){
    const matches=aliases[key].filter(alias=>Object.hasOwn(values,alias));
    if(matches.length>1&&matches.some(alias=>values[alias]!==values[matches[0]]))return;
    if(!matches.length)continue;const value=values[matches[0]];
    if(typeof value!=='number'||!Number.isFinite(value)||value<0||value>({o2:100,h2s:1000,co:10000,co2:100,lel:100}[key]))return;
    sample[key]=value;
  }
  return [sample.o2,sample.h2s,sample.co,sample.co2,sample.lel].some(value=>value!==null)?sample:undefined;
}
export function freshSample(sample:GasSample|undefined,time=Date.now()){return !!sample&&time>=Date.parse(sample.receivedAt)&&time-Date.parse(sample.receivedAt)<10_000;}
export class BleClient {
  private state=blankBleState();private listeners=new Set<()=>void>();private device?:BleDevice;private channel?:BleCharacteristic;private epoch=0;private format:PacketFormat='raw';
  private framing:'packet'|'newline'='packet';private buffer='';private decoder=new TextDecoder('utf-8',{fatal:true});
  constructor(private port:()=>BluetoothPort|undefined=()=>((navigator as Navigator&{bluetooth?:BluetoothPort}).bluetooth),private now=()=>Date.now()){}
  snapshot=()=>this.state;
  subscribe=(listener:()=>void)=>{this.listeners.add(listener);return()=>{this.listeners.delete(listener);};};
  private publish(patch:Partial<BleState>){this.state={...this.state,...patch};this.listeners.forEach(listener=>listener());}
  private changed=()=>{if(this.channel?.value)this.receive(this.channel.value);};
  private lost=()=>{this.detach();this.channels=[];++this.epoch;this.publish({phase:'disconnected',message:'측정기 연결이 끊겼어요. 다시 연결해 주세요.',subscribed:false,sample:undefined,channels:[],channel:''});};
  private detach(){const old=this.channel;old?.removeEventListener('characteristicvaluechanged',this.changed);if(old&&this.state.subscribed)void old.stopNotifications().catch(()=>{});this.channel=undefined;}
  disconnect(){++this.epoch;this.detach();this.buffer='';this.decoder=new TextDecoder('utf-8',{fatal:true});this.channels=[];this.device?.removeEventListener('gattserverdisconnected',this.lost);this.device?.gatt?.disconnect();this.device=undefined;this.publish({...blankBleState(),phase:'disconnected',message:'연결을 해제했어요. 이전 값은 현재 측정값으로 표시하지 않아요.'});}
  async connect(config:BleConfig){
    if(['choosing','connecting'].includes(this.state.phase))return;
    const port=this.port();if(!port)throw Error('이 브라우저는 직접 BLE 연결을 지원하지 않아요.');
    const service=config.service.trim()?uuid(config.service):'';const channel=config.characteristic.trim()?uuid(config.characteristic):'';
    if(channel&&!service)throw Error('데이터 채널을 지정하려면 서비스 UUID도 입력해 주세요.');
    this.disconnect();const epoch=this.epoch;this.format=config.format;this.framing=config.framing??'packet';this.publish({phase:'choosing',message:'브라우저의 기기 선택 창에서 측정기를 선택해 주세요.'});
    let device:BleDevice|undefined;
    try{
      // Must run synchronously in the click handler, before any await.
      device=await port.requestDevice({...(config.namePrefix.trim()?{filters:[{namePrefix:config.namePrefix.trim()}]}:{acceptAllDevices:true}),optionalServices:service?[service]:[]});
      if(epoch!==this.epoch)return;this.device=device;if(!device.gatt)throw Error('이 기기는 BLE GATT 연결을 제공하지 않아요.');
      device.addEventListener('gattserverdisconnected',this.lost);this.publish({phase:'connecting',device:device.name??'이름 없는 BLE 기기',message:'기기에 연결하고 있어요…'});
      const server=await device.gatt.connect();if(epoch!==this.epoch){server.disconnect();return;}
      this.publish({phase:'connected',message:service?'수신 가능한 채널을 확인하고 있어요.':'연결만 확인했어요. 데이터 수신에는 제조사의 서비스 UUID가 필요해요.'});
      if(service){const svc=await server.getPrimaryService(service);if(epoch!==this.epoch)return;
        const channels=channel?[await svc.getCharacteristic(channel)]:await svc.getCharacteristics();if(epoch!==this.epoch)return;
        this.channels=channels;this.publish({channels:channels.map(item=>({uuid:item.uuid,read:!!item.properties.read,notify:!!(item.properties.notify||item.properties.indicate)})),message:'연결됐어요. 채널을 선택한 뒤 읽기 또는 실시간 수신을 눌러 주세요.'});
      }
    }catch(error){if(epoch!==this.epoch)return;this.detach();device?.removeEventListener('gattserverdisconnected',this.lost);device?.gatt?.disconnect();this.device=undefined;this.publish({phase:'error',subscribed:false,sample:undefined,message:error instanceof DOMException&&error.name==='NotFoundError'?'기기 선택을 취소했거나 일치하는 기기가 없어요.':error instanceof Error?error.message:'기기 연결에 실패했어요.'});}
  }
  private channels:BleCharacteristic[]=[];
  stopReceiving(){this.detach();this.buffer='';this.decoder=new TextDecoder('utf-8',{fatal:true});this.publish({subscribed:false,channel:'',sample:undefined,message:'실시간 수신을 중지했어요. 기기 연결은 유지해요.'});}
  async listen(id:string){
    if(this.state.phase!=='connected')throw Error('먼저 측정기에 연결해 주세요.');
    const selected=this.channels.find(item=>item.uuid===id);if(!selected?.properties.notify&&!selected?.properties.indicate)throw Error('알림 수신을 지원하지 않는 채널이에요.');
    const epoch=this.epoch;this.stopReceiving();this.channel=selected;this.publish({subscribed:false,sample:undefined,channel:id});selected.addEventListener('characteristicvaluechanged',this.changed);
    try{await selected.startNotifications();if(epoch!==this.epoch||this.channel!==selected){selected.removeEventListener('characteristicvaluechanged',this.changed);void selected.stopNotifications().catch(()=>{});return;}this.publish({subscribed:true,message:'실시간 알림 수신 중 · 화면을 켜고 연결 상태를 확인해 주세요.'});}
    catch(error){selected.removeEventListener('characteristicvaluechanged',this.changed);if(epoch===this.epoch&&this.channel===selected)this.publish({subscribed:false,message:'수신 구독을 시작하지 못했어요. UUID와 측정기 상태를 확인해 주세요.'});throw error;}
  }
  async read(id:string){const selected=this.channels.find(item=>item.uuid===id);if(this.state.phase!=='connected'||!selected?.properties.read)throw Error('읽을 수 있는 연결된 채널을 선택해 주세요.');const epoch=this.epoch;const value=await selected.readValue();if(epoch===this.epoch)this.receive(value);}
  private receive(view:DataView){
    if(view.byteLength>65_536){this.buffer='';this.decoder=new TextDecoder('utf-8',{fatal:true});this.publish({sample:undefined,message:'너무 큰 패킷을 거부했어요.'});return;}
    const bytes=new Uint8Array(view.buffer,view.byteOffset,view.byteLength);let text='';let sample:GasSample|undefined;
    try{
      text=(this.framing==='newline'?this.decoder.decode(bytes,{stream:true}):new TextDecoder('utf-8',{fatal:true}).decode(bytes)).replace(/\0+$/,'');
      if(this.framing==='newline'){
        this.buffer+=text;if(this.buffer.length>8192)throw Error('frame too large');
        const frames=this.buffer.split(/\r?\n/);this.buffer=frames.pop()??'';sample=this.state.sample;
        for(const frame of frames.filter(value=>value.trim()))sample=parseGasPacket(frame.trim(),this.format,this.state.device,this.now());
      }else sample=parseGasPacket(text.trim(),this.format,this.state.device,this.now());
    }catch{this.buffer='';this.decoder=new TextDecoder('utf-8',{fatal:true});sample=undefined;}
    const at=new Date(this.now()).toISOString();
    this.publish({packets:this.state.packets+1,lastAt:at,sample,logs:[{at,hex:[...bytes.slice(0,128)].map(value=>value.toString(16).padStart(2,'0')).join(' '),text:text.slice(0,2048)},...this.state.logs].slice(0,20),message:sample?'텍스트 형식의 값을 해석했어요. 제조사 규격·단위 검증은 별도로 필요해요.':'원시 데이터를 수신했어요. 바이너리·지원하지 않는 형식은 가스값으로 추측하지 않아요.'});
  }
}
export const bleClient=new BleClient();
