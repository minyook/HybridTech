import {sha256} from '@noble/hashes/sha2.js';
import {bytesToHex} from '@noble/hashes/utils.js';
import {VOICE_CACHE,VOICE_FILES,VOICE_REVISION,VOICE_MODEL_BYTES,voiceKey} from './catalog';
export interface VoiceFile {path:string;size:number;sha256:string}
interface VoiceManifest {version:string;files:VoiceFile[]}
const runtimeManifest=()=>new URL('voice-runtime/manifest.json',document.baseURI).href;
async function manifest():Promise<VoiceManifest>{const r=await fetch(runtimeManifest());if(!r.ok)throw Error('음성 실행 파일이 없어요. 웹앱을 다시 빌드해 주세요.');return r.json();}
export async function voiceInstalled(){
  if(!globalThis.caches)return false;
  const cache=await caches.open(VOICE_CACHE),marker=await cache.match(voiceKey('installed.json'));
  if(!marker)return false;const installed=await marker.json() as VoiceManifest;
  for(const [name] of VOICE_FILES)if(!await cache.match(voiceKey(name)))return false;
  for(const file of installed.files)if(!await cache.match(new URL(`voice-runtime/${file.path}`,document.baseURI).href))return false;
  return true;
}
export async function downloadVoice(signal:AbortSignal,onProgress:(message:string)=>void){
  signal.throwIfAborted();
  const runtime=await manifest(),cache=await caches.open(VOICE_CACHE);
  const files=[...VOICE_FILES.map(([path,size,sha256])=>({path,size,sha256,key:voiceKey(path),url:`https://huggingface.co/Xenova/whisper-tiny/resolve/${VOICE_REVISION}/${path}`})),...runtime.files.map(file=>({...file,key:new URL(`voice-runtime/${file.path}`,document.baseURI).href,url:new URL(`voice-runtime/${file.path}`,document.baseURI).href}))];
  const estimate=await navigator.storage?.estimate();
  const total=VOICE_MODEL_BYTES+runtime.files.reduce((sum,file)=>sum+file.size,0);
  if(estimate?.quota&&estimate.quota-(estimate.usage??0)<total)throw Error('음성 파일을 저장할 공간이 부족해요.');
  await cache.delete(voiceKey('installed.json'));let done=0;
  for(const file of files){
    signal.throwIfAborted();const existing=await cache.match(file.key);
    if(existing&&Number(existing.headers.get('X-Fieldguard-Size'))===file.size&&existing.headers.get('X-Fieldguard-SHA256')===file.sha256){done+=file.size;continue;}
    onProgress(`음성 파일 다운로드 ${Math.floor(done/total*100)}% · ${file.path}`);
    const response=await fetch(file.url,{signal,credentials:'omit'});if(!response.ok||!response.body)throw Error('음성 파일 다운로드에 실패했어요. 연결을 확인해 주세요.');
    const reader=response.body.getReader(),hash=sha256.create(),parts:Uint8Array<ArrayBuffer>[]= [];let size=0;
    try{while(true){signal.throwIfAborted();const item=await reader.read();if(item.done)break;size+=item.value.length;if(size>file.size)throw Error('음성 파일 크기가 달라요.');hash.update(item.value);parts.push(new Uint8Array(item.value));onProgress(`음성 파일 다운로드 ${Math.floor((done+size)/total*100)}%`);}}
    finally{await reader.cancel().catch(()=>{});reader.releaseLock();}
    if(size!==file.size||bytesToHex(hash.digest())!==file.sha256)throw Error('음성 파일 검증에 실패했어요. 다시 다운로드해 주세요.');
    const type=file.path.endsWith('.wasm')?'application/wasm':file.path.endsWith('.mjs')?'text/javascript':'application/octet-stream';
    await cache.put(file.key,new Response(new Blob(parts,{type}),{headers:{'Content-Type':type,'X-Fieldguard-Size':String(size),'X-Fieldguard-SHA256':file.sha256}}));done+=size;
  }
  signal.throwIfAborted();await cache.put(voiceKey('installed.json'),new Response(JSON.stringify(runtime)));onProgress('로컬 한국어 음성 인식 파일을 저장했어요.');
}
