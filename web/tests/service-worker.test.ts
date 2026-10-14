import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { expect, it, vi } from 'vitest';

const template=readFileSync(new URL('../scripts/sw-template.js',import.meta.url),'utf8').replace('__VERSION__','"test"').replace('__PRECACHE__','[]');
type CacheMatcher = (request: unknown, options?: {ignoreVary?:boolean;ignoreSearch?:boolean}) => Promise<Response | undefined>;
function worker(shellMatch: CacheMatcher, assetMatch: CacheMatcher=async () => undefined) {
  const events: Record<string,(event: unknown)=>void> = {};
  const fetch=vi.fn(async () => {throw new Error('network offline');});
  runInNewContext(template,{
    self:{location:{origin:'https://field.test'},registration:{scope:'https://field.test/app/'},addEventListener:(name:string,fn:(event:unknown)=>void)=>{events[name]=fn;}},
    URL,fetch,caches:{open:async (name:string)=>({match:name==='fieldguard-shell-test'?shellMatch:assetMatch})},
  });
  return {fetch,request(url:string,mode='cors') {let response: Promise<Response> | undefined;events.fetch({request:{url,method:'GET',mode},respondWith:(result:Promise<Response>)=>{response=result;}});return response!;}};
}
it('오프라인 새로고침은 같은 배포 버전의 화면을 반환한다',async () => {
  const match=vi.fn(async () => new Response('<html>cached app</html>'));
  const runtime=worker(match);
  expect(await (await runtime.request('https://field.test/app/#/ask','navigate')).text()).toContain('cached app');
  expect(runtime.fetch).not.toHaveBeenCalled();
});
it('Vary: Origin 때문에 캐시된 CORS JavaScript 모듈이 누락되지 않는다',async () => {
  const match=vi.fn(async (_request,options) => options?.ignoreVary?new Response('cached module'):undefined);
  const runtime=worker(match);
  expect(await (await runtime.request('https://field.test/app/assets/app.js')).text()).toBe('cached module');
  expect(runtime.fetch).not.toHaveBeenCalled();
});
it('현재 화면 버전에 해당하는 준비 PDF를 인터넷 없이 반환한다',async () => {
  const match=vi.fn(async request => String(request).endsWith('asset-manifest.json')?Response.json({version:'docs-1'}):undefined);
  const runtime=worker(match,vi.fn(async () => new Response('prepared PDF')));
  expect(await (await runtime.request('https://field.test/app/source-pdfs/guide.pdf')).text()).toBe('prepared PDF');
  expect(runtime.fetch).not.toHaveBeenCalled();
});
