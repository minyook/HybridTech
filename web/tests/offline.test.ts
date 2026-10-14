import { createHash } from 'node:crypto';
import { afterEach, expect, it, vi } from 'vitest';
import type { Asset } from '../src/types';

afterEach(() => { vi.unstubAllGlobals(); });
async function environment() {
  vi.resetModules();
  const payload=new TextEncoder().encode('complete document');
  const asset: Asset={path:'knowledge/data.json',size:payload.length,sha256:createHash('sha256').update(payload).digest('hex'),group:'knowledge'};
  const stored=new Map<string,Response>();
  const cache={match:async (key:string)=>stored.get(key)?.clone(),put:async (key:string,response:Response)=>{stored.set(key,response.clone());},delete:async (key:string)=>stored.delete(key)};
  vi.stubGlobal('location',{href:'https://field.test/'});
  vi.stubGlobal('caches',{open:async()=>cache});
  const fetch=vi.fn(async (url:string)=>url.endsWith('asset-manifest.json')?Response.json({version:'v1',assets:[asset]}):new Response(payload));
  vi.stubGlobal('fetch',fetch);
  return {payload,asset,stored,fetch,module:await import('../src/offline/assets')};
}
it('불완전한 다운로드는 오프라인 준비 완료로 표시하지 않는다',async () => {
  const env=await environment();
  env.fetch.mockImplementation(async url=>url.endsWith('asset-manifest.json')?Response.json({version:'v1',assets:[env.asset]}):new Response('short'));
  await expect(env.module.prepareAssets(['knowledge'],vi.fn())).rejects.toThrow('파일 검증');
  expect(env.stored.size).toBe(0); expect((await env.module.preparedGroups()).knowledge).toBe(false);
});
it('완료된 파일은 다음 준비에서 다시 검증하고 다운로드를 재사용한다',async () => {
  const env=await environment();
  await env.module.prepareAssets(['knowledge'],vi.fn());
  const before=env.fetch.mock.calls.length;
  await env.module.prepareAssets(['knowledge'],vi.fn());
  expect(env.fetch.mock.calls.length).toBe(before); expect((await env.module.preparedGroups()).knowledge).toBe(true);
});
it('캐시의 손상된 파일은 재다운로드해 복구한다',async () => {
  const env=await environment();
  env.stored.set(env.module.assetUrl(env.asset.path),new Response('broken cache'));
  expect((await env.module.preparedGroups()).knowledge).toBe(false);
  await env.module.prepareAssets(['knowledge'],vi.fn());
  expect((await env.module.preparedGroups()).knowledge).toBe(true);
});
