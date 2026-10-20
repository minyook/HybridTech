import { expect, it, vi } from 'vitest';
import { sha256 } from '@noble/hashes/sha2.js';
import { artifactScope, downloadLightModel, LIGHT_MODEL, lightModelInstalled, type LightArtifact } from '../src/ai/light-model';
const payload=new Uint8Array([1,2,3,4]);
const file:LightArtifact={name:'params_shard_0.bin',url:'https://model.invalid/rev/params.bin',size:payload.length,sha256:Array.from(sha256(payload),x=>x.toString(16).padStart(2,'0')).join('')};
function storage(){
  const values=new Map<string,Response>();
  const cache={match:async(key:string)=>values.get(key)?.clone(),put:async(key:string,response:Response)=>{const bytes=await response.arrayBuffer();values.set(key,new Response(bytes,{headers:response.headers}));},delete:async(key:string)=>values.delete(key)};
  return {values,storage:{open:async()=>cache} as unknown as CacheStorage};
}
const deps=(store:CacheStorage,fetchFile=vi.fn(async()=>new Response(payload)))=>({storage:store,fetchFile,files:[file],estimate:async()=>({quota:1000,usage:0})});
it('경량 모델은 공식 고정 리비전의 13개 파일과 해시를 포함한다',()=>{
  expect(LIGHT_MODEL.files).toHaveLength(13);expect(LIGHT_MODEL.size).toBe(353285213);
  for(const file of LIGHT_MODEL.files){expect(file.sha256).toMatch(/^[a-f0-9]{64}$/);expect(file.url).not.toContain('/main/');}
  expect(artifactScope(LIGHT_MODEL.files.at(-1)!)).toBe('webllm/wasm');
});
it('스트리밍 검증 완료 후에만 설치로 인식하고 완료 파일은 재다운로드하지 않는다',async()=>{
  const store=storage(),dependencies=deps(store.storage),signal=new AbortController().signal;
  expect(await lightModelInstalled(store.storage,[file])).toBe(false);
  await downloadLightModel(signal,vi.fn(),dependencies);expect(await lightModelInstalled(store.storage,[file])).toBe(true);
  await downloadLightModel(signal,vi.fn(),dependencies);expect(dependencies.fetchFile).toHaveBeenCalledTimes(1);
});
it('해시가 틀린 파일과 잘린 응답은 설치로 남기지 않는다',async()=>{
  for(const body of [new Uint8Array([4,3,2,1]),new Uint8Array([1,2])]){
    const store=storage();await expect(downloadLightModel(new AbortController().signal,vi.fn(),deps(store.storage,vi.fn(async()=>new Response(body))))).rejects.toThrow('무결성');
    expect(await lightModelInstalled(store.storage,[file])).toBe(false);
  }
});
it('중단된 다운로드를 저장하지 않고 기존 Gemma 캐시에는 접근하지 않는다',async()=>{
  const store=storage(),controller=new AbortController();let second=false;
  const body=new ReadableStream({pull(stream){if(!second){second=true;stream.enqueue(payload.slice(0,2));}else{controller.abort();stream.enqueue(payload.slice(2));}}});
  await expect(downloadLightModel(controller.signal,vi.fn(),deps(store.storage,vi.fn(async()=>new Response(body))))).rejects.toThrow();
  expect(store.values.size).toBe(0);
});
it('다운로드 도중 파일이 사라지면 전체 설치로 표시하지 않는다',async()=>{
  const store=storage();await downloadLightModel(new AbortController().signal,vi.fn(),deps(store.storage));store.values.delete(file.url);
  expect(await lightModelInstalled(store.storage,[file])).toBe(false);
});
