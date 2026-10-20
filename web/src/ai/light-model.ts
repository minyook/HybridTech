import { sha256 } from '@noble/hashes/sha2.js';
import manifest from './light-manifest.json';
import { browserAIFeatures } from './capabilities';

export const usesLightAI=()=>browserAIFeatures().appleMobile;
export const LIGHT_MODEL={id:'Qwen3-0.6B-q4f16_1-MLC',label:'Qwen3 0.6B',...manifest,
  size:manifest.files.reduce((sum,file)=>sum+file.size,0),
  source:`https://huggingface.co/mlc-ai/Qwen3-0.6B-q4f16_1-MLC/tree/${manifest.revision}`};
export type LightArtifact=typeof manifest.files[number];
export function artifactScope(file:LightArtifact){return file.name.endsWith('.wasm')?'webllm/wasm':file.name==='mlc-chat-config.json'?'webllm/config':'webllm/model';}
const hex=(bytes:Uint8Array)=>Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');
export function verifiedArtifact(response:Response|undefined,file:LightArtifact){
  return Boolean(response?.ok&&response.headers.get('x-fieldguard-sha256')===file.sha256&&response.headers.get('content-length')===String(file.size));
}
export async function lightModelInstalled(storage:CacheStorage=caches,files=LIGHT_MODEL.files){
  for(const file of files){const response=await (await storage.open(artifactScope(file))).match(file.url);const valid=verifiedArtifact(response,file);void response?.body?.cancel().catch(()=>{});if(!valid)return false;}
  return true;
}
/** Hash and store one response at a time. No complete model ArrayBuffer, Blob,
 * response.clone/tee, or concurrent shard downloads in the page's JS heap. */
export async function downloadLightModel(signal:AbortSignal,onProgress:(message:string)=>void,
  dependencies={storage:caches,fetchFile:(input:RequestInfo|URL,init?:RequestInit)=>fetch(input,init),files:LIGHT_MODEL.files,estimate:()=>navigator.storage.estimate()}){
  signal.throwIfAborted();
  const {quota,usage}=await dependencies.estimate();
  const size=dependencies.files.reduce((sum,file)=>sum+file.size,0);
  let missing=0;
  for(const file of dependencies.files){const response=await (await dependencies.storage.open(artifactScope(file))).match(file.url);if(!verifiedArtifact(response,file))missing+=file.size;void response?.body?.cancel().catch(()=>{});}
  if(quota&&quota-(usage??0)<missing*1.15)throw Error('경량 모델을 저장할 공간이 부족해요. 기기 저장 공간을 확인해 주세요.');
  let completed=0;
  for(const file of dependencies.files){
    signal.throwIfAborted();const cache=await dependencies.storage.open(artifactScope(file));
    const cached=await cache.match(file.url);const valid=verifiedArtifact(cached,file);void cached?.body?.cancel().catch(()=>{});
    if(valid){completed+=file.size;continue;}
    const response=await dependencies.fetchFile(file.url,{signal,credentials:'omit'});
    if(!response.ok||!response.body)throw Error(`경량 모델 다운로드에 실패했어요 (${response.status}).`);
    const length=response.headers.get('content-length');
    if(length&&Number(length)!==file.size){await response.body.cancel();throw Error('모델 파일의 크기가 달라 저장하지 않았어요.');}
    const reader=response.body.getReader(),hash=sha256.create();let received=0,lastPercent=-1;
    const stream=new ReadableStream<Uint8Array>({
      async pull(controller){
        try{
          signal.throwIfAborted();const {done,value}=await reader.read();signal.throwIfAborted();
          if(done){if(received!==file.size||hex(hash.digest())!==file.sha256)throw Error('경량 모델의 무결성 검증에 실패했어요. 다시 다운로드해 주세요.');controller.close();return;}
          received+=value.byteLength;if(received>file.size)throw Error('모델 파일의 크기가 달라 저장하지 않았어요.');
          hash.update(value);const percent=Math.floor((completed+received)/size*100);
          if(percent!==lastPercent){lastPercent=percent;onProgress(`경량 모델 다운로드 ${percent}% · ${file.name}`);}
          controller.enqueue(value);
        }catch(error){await reader.cancel().catch(()=>{});controller.error(error);}
      },cancel:reason=>reader.cancel(reason),
    });
    try{await cache.put(file.url,new Response(stream,{headers:{'content-length':String(file.size),'content-type':file.name.endsWith('.wasm')?'application/wasm':'application/octet-stream','x-fieldguard-sha256':file.sha256}}));}
    catch(error){await reader.cancel().catch(()=>{});await cache.delete(file.url);throw error;}
    completed+=file.size;
  }
  signal.throwIfAborted();onProgress('경량 모델 다운로드 100% · SHA-256 검증 완료');
}
export async function removeLightModel(){for(const file of LIGHT_MODEL.files)await (await caches.open(artifactScope(file))).delete(file.url);}
