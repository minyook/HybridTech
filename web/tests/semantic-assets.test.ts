import {afterEach,beforeEach,expect,it,vi} from 'vitest';
vi.mock('../../tools/semantic-model-files.json',()=>({default:{'config.json':{size:3,sha256:'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'}}}));
import {prepareSemantic,semanticInstalled,semanticKey} from '../src/rag/semantic-assets';
let files:Map<string,Response>,fetcher:ReturnType<typeof vi.fn>;
beforeEach(()=>{
 files=new Map();vi.stubGlobal('location',{href:'https://qa.test/',origin:'https://qa.test'});vi.stubGlobal('navigator',{storage:{estimate:async()=>({quota:1e9,usage:0})}});vi.stubGlobal('localStorage',{removeItem:vi.fn()});
 vi.stubGlobal('caches',{open:async()=>({match:async(k:string)=>files.get(k)?.clone(),put:async(k:string,r:Response)=>{const body=await r.arrayBuffer();files.set(k,new Response(body,{headers:r.headers}));},delete:async(k:string)=>files.delete(k)})});
 fetcher=vi.fn(async(url:string)=>url.endsWith('semantic-model/manifest.json')?new Response(JSON.stringify({revision:'5090578d9565bb06545b4552f76e6bc2c93e4a66',dimensions:768,files:[{name:'config.json',size:3,sha256:'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'}]})):url.endsWith('manifest.json')?new Response(JSON.stringify({files:[]})):new Response('abc'));vi.stubGlobal('fetch',fetcher);
});
afterEach(()=>vi.unstubAllGlobals());
it('installs only after the entire streamed body has passed its hash and size checks',async()=>{await prepareSemantic(new AbortController().signal,()=>{});expect(await semanticInstalled()).toBe(true);});
it('does not mark corrupt bytes as ready and deletes the partial cache entry',async()=>{const original=fetcher.getMockImplementation()! as (url:string)=>Promise<Response>;fetcher.mockImplementation(async(url:string)=>url.endsWith('/config.json')?new Response('abd'):original(url));await expect(prepareSemantic(new AbortController().signal,()=>{})).rejects.toThrow('검증');expect(await semanticInstalled()).toBe(false);expect(files.has(semanticKey('config.json'))).toBe(false);});
it('checks actual cached files instead of trusting only an installed marker',async()=>{files.set(semanticKey('installed.json'),new Response(JSON.stringify({files:[semanticKey('missing')]})));expect(await semanticInstalled()).toBe(false);});
