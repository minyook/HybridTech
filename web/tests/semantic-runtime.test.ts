import {afterEach,beforeEach,expect,it,vi} from 'vitest';
const state=vi.hoisted(()=>({installed:true,recovery:false,release:vi.fn(),unlock:vi.fn(),hold:vi.fn(),lease:vi.fn()}));
vi.mock('../src/ai/runtime',()=>({holdAIForAudio:()=>state.hold()}));
vi.mock('../src/ai/engine-lease',()=>({acquireEngineLease:()=>state.lease()}));
vi.mock('../src/rag/semantic-assets',()=>({SEMANTIC_MARKER:'test-search-marker',semanticInstalled:async()=>state.installed,semanticRecovery:()=>state.recovery}));
import {queryVector} from '../src/rag/semantic-runtime';
class FakeWorker {static latest:FakeWorker;onmessage?: (e:{data:unknown})=>void;onerror?:()=>void;terminate=vi.fn();postMessage=vi.fn();constructor(){FakeWorker.latest=this;}}
let markers:Map<string,string>;
beforeEach(()=>{vi.useFakeTimers();state.installed=true;state.recovery=false;state.release.mockClear();state.unlock.mockClear();state.hold.mockClear().mockResolvedValue(state.release);state.lease.mockClear().mockResolvedValue(state.unlock);markers=new Map();vi.stubGlobal('localStorage',{getItem:(k:string)=>markers.get(k)??null,setItem:(k:string,v:string)=>markers.set(k,v),removeItem:(k:string)=>markers.delete(k)});vi.stubGlobal('Worker',FakeWorker);});
afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals();});
const flush=async()=>{for(let i=0;i<8;i++)await Promise.resolve();};
it('closes the encoder before releasing its exclusive lease and clears a completed operation',async()=>{
 const promise=queryVector('문장',new AbortController().signal);await flush();expect(markers.has('test-search-marker')).toBe(true);
 FakeWorker.latest.onmessage?.({data:{vector:Array(768).fill(.01),clean:true}});expect((await promise)?.length).toBe(768);expect(markers.size).toBe(0);expect(FakeWorker.latest.terminate).toHaveBeenCalledOnce();expect(state.unlock).toHaveBeenCalledOnce();expect(state.release).toHaveBeenCalledOnce();
});
it('keeps an incomplete marker after abort so another question cannot silently reload the model',async()=>{
 const abort=new AbortController(),promise=queryVector('문장',abort.signal),rejected=expect(promise).rejects.toThrow();await flush();abort.abort();await rejected;expect(markers.has('test-search-marker')).toBe(true);expect(state.unlock).toHaveBeenCalledOnce();
});
it('does not initialize an encoder after a previous incomplete operation',async()=>{state.recovery=true;await expect(queryVector('문장',new AbortController().signal)).rejects.toThrow('완료');expect(state.hold).not.toHaveBeenCalled();});
it('does not write a marker or terminate someone else when the lease is unavailable',async()=>{state.lease.mockResolvedValue(undefined);expect(await queryVector('문장',new AbortController().signal)).toBeUndefined();expect(markers.size).toBe(0);expect(state.release).toHaveBeenCalledOnce();});
it('falls back without loading if semantic files are not prepared',async()=>{state.installed=false;expect(await queryVector('문장',new AbortController().signal)).toBeUndefined();expect(state.hold).not.toHaveBeenCalled();});
