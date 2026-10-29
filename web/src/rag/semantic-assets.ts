import {assetUrl} from '../offline/assets';
import {sha256} from '@noble/hashes/sha2.js';
import {bytesToHex} from '@noble/hashes/utils.js';
import pinnedFiles from '../../../tools/semantic-model-files.json';
export const SEMANTIC_CACHE='fieldguard-semantic-embeddinggemma-5090578d-v1';
export const SEMANTIC_MARKER='fieldguard-semantic-incomplete-v1';
export function semanticRecovery(){try{return localStorage.getItem(SEMANTIC_MARKER)!==null;}catch{return true;}}
interface Catalog {revision:string;dimensions:number;files:{name:string;size:number;sha256:string}[]}
export const semanticKey=(name:string)=>new URL(`semantic-model/${name}`,assetUrl('')).href;
export async function semanticInstalled(){
 if(!globalThis.caches)return false;
 const cache=await caches.open(SEMANTIC_CACHE),marker=await cache.match(semanticKey('installed.json'));if(!marker)return false;
 const installed=await marker.json() as {files?:string[]};if(!installed.files?.length)return false;
 for(const url of installed.files)if(!await cache.match(url))return false;return true;
}
export async function prepareSemantic(signal:AbortSignal,progress:(text:string)=>void){
 signal.throwIfAborted();
 const response=await fetch(assetUrl('semantic-model/manifest.json'),{signal});if(!response.ok)throw Error('의미 검색 파일 정보를 읽지 못했어요.');
 const catalog=await response.json() as Catalog;
 if(catalog.revision!=='5090578d9565bb06545b4552f76e6bc2c93e4a66'||catalog.dimensions!==768)throw Error('의미 검색 모델 버전이 맞지 않아요.');
 if(catalog.files.length!==Object.keys(pinnedFiles).length||Object.entries(pinnedFiles).some(([name,expected])=>!catalog.files.some(f=>f.name===name&&f.size===expected.size&&f.sha256===expected.sha256)))throw Error('고정된 의미 검색 파일 목록과 다릅니다.');
 const runtime=await fetch(assetUrl('voice-runtime/manifest.json'),{signal}).then(r=>r.json()) as {files:{path:string;size:number;sha256:string}[]};
 const entries=[...catalog.files.map(f=>({...f,url:semanticKey(f.name)})),...runtime.files.map(f=>({...f,name:f.path,url:assetUrl('voice-runtime/'+f.path)}))];
 const cache=await caches.open(SEMANTIC_CACHE);await cache.delete(semanticKey('installed.json'));
 let done=0;const total=entries.reduce((s,f)=>s+f.size,0);
 const estimate=await navigator.storage?.estimate();if(estimate?.quota&&estimate.quota-(estimate.usage??0)<total)throw Error('문장 의미 검색 파일을 저장할 공간이 부족해요.');
 for(const f of entries){
  signal.throwIfAborted();progress(`문장 의미 검색 준비 · ${Math.round(done/total*100)}%`);
  let result=await cache.match(f.url);
  if(!result||result.headers.get('X-SHA256')!==f.sha256||result.headers.get('X-Size')!==String(f.size)){
   result=await fetch(f.url,{signal});if(!result.ok||!result.body)throw Error('의미 검색 파일 다운로드를 완료하지 못했어요.');
   let size=0;const hash=sha256.create();
   const stream=result.body.pipeThrough(new TransformStream<Uint8Array,Uint8Array>({
    transform(chunk,controller){signal.throwIfAborted();size+=chunk.length;if(size>f.size)throw Error('의미 검색 파일 크기가 달라요.');hash.update(chunk);controller.enqueue(chunk);progress(`문장 의미 검색 준비 · ${Math.round((done+size)/total*100)}%`);},
    flush(){signal.throwIfAborted();if(size!==f.size||bytesToHex(hash.digest())!==f.sha256)throw Error('의미 검색 파일 검증에 실패했어요.');}
   }));
   try{await cache.put(f.url,new Response(stream,{headers:{'X-SHA256':f.sha256,'X-Size':String(f.size),'Content-Type':f.name.endsWith('.json')?'application/json':f.name.endsWith('.mjs')?'text/javascript':f.name.endsWith('.wasm')?'application/wasm':'application/octet-stream'}}));}
   catch(error){await cache.delete(f.url);throw error;}
  }done+=f.size;
 }
 signal.throwIfAborted();await cache.put(semanticKey('installed.json'),new Response(JSON.stringify({revision:catalog.revision,runtime:runtime.files,files:entries.map(f=>f.url)})));
 localStorage.removeItem(SEMANTIC_MARKER);progress('문장 의미 검색 준비 완료');
}
