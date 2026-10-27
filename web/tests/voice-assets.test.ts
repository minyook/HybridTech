import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {downloadVoice,voiceInstalled} from '../src/voice/assets';
import {VOICE_FILES,voiceKey} from '../src/voice/catalog';
let files:Map<string,Response>,fetcher:ReturnType<typeof vi.fn>;
beforeEach(()=>{
  files=new Map();vi.stubGlobal('location',{origin:'https://field.test'});vi.stubGlobal('document',{baseURI:'https://field.test/'});vi.stubGlobal('navigator',{storage:{estimate:async()=>({quota:1e9,usage:0})}});
  vi.stubGlobal('caches',{open:async()=>({match:async(key:string)=>files.get(key)?.clone(),put:async(key:string,response:Response)=>{files.set(key,response);},delete:async(key:string)=>files.delete(key)})});
  fetcher=vi.fn(async(url:string)=>url.endsWith('manifest.json')?new Response(JSON.stringify({version:'test',files:[]})):new Response('bad model bytes'));vi.stubGlobal('fetch',fetcher);
});
afterEach(()=>vi.unstubAllGlobals());
it('손상되거나 잘못된 모델 파일을 설치 완료로 표시하지 않는다',async()=>{await expect(downloadVoice(new AbortController().signal,()=>{})).rejects.toThrow('검증');expect(await voiceInstalled()).toBe(false);expect(files.has(voiceKey('installed.json'))).toBe(false);});
it('설치 완료 표식만 있고 필수 파일이 없으면 미준비로 판단한다',async()=>{files.set(voiceKey('installed.json'),new Response(JSON.stringify({files:[]})));expect(await voiceInstalled()).toBe(false);});
it('모델이 있어도 실행 파일이 누락되면 미준비로 판단한다',async()=>{for(const [name] of VOICE_FILES)files.set(voiceKey(name),new Response('cached'));files.set(voiceKey('installed.json'),new Response(JSON.stringify({files:[{path:'missing.wasm'}]})));expect(await voiceInstalled()).toBe(false);});
it('사용자가 취소한 다운로드는 모델을 요청하지 않는다',async()=>{const abort=new AbortController();abort.abort();await expect(downloadVoice(abort.signal,()=>{})).rejects.toThrow();expect(fetcher).not.toHaveBeenCalled();expect(files.has(voiceKey('installed.json'))).toBe(false);});
