import {afterEach,beforeEach,expect,it,vi} from 'vitest';
const fixture=vi.hoisted(()=>({installed:true,release:vi.fn(),unlock:vi.fn()}));
vi.mock('../src/ai/runtime',()=>({holdAIForAudio:async()=>fixture.release}));
vi.mock('../src/ai/engine-lease',()=>({acquireEngineLease:async()=>fixture.unlock}));
vi.mock('../src/voice/assets',()=>({voiceInstalled:async()=>fixture.installed}));
import {LocalRecognizer,voiceRecovery} from '../src/voice/recognizer';
class TestWorker {static instances:TestWorker[]=[];onmessage?:((event:{data:unknown})=>void);onerror?:((event:{preventDefault:()=>void})=>void);messages:unknown[]=[];terminated=false;constructor(){TestWorker.instances.push(this);}postMessage(value:unknown){this.messages.push(value);}terminate(){this.terminated=true;}reply(data:unknown){this.onmessage?.({data});}}
let values:Map<string,string>;
beforeEach(()=>{fixture.installed=true;fixture.release.mockClear();fixture.unlock.mockClear();TestWorker.instances=[];values=new Map();vi.stubGlobal('Worker',TestWorker);vi.stubGlobal('localStorage',{setItem:(key:string,value:string)=>values.set(key,value),getItem:(key:string)=>values.get(key)??null,removeItem:(key:string)=>values.delete(key)});});
afterEach(()=>vi.unstubAllGlobals());
async function ready(engine:LocalRecognizer){const pending=engine.prepare();await vi.waitFor(()=>expect(TestWorker.instances).toHaveLength(1));TestWorker.instances[0].reply({type:'ready'});await pending;return TestWorker.instances[0];}
it('음성 모델이 없으면 마이크 준비 뒤 임의 다운로드나 Worker 적재를 하지 않는다',async()=>{fixture.installed=false;await expect(new LocalRecognizer().prepare()).rejects.toThrow('다운로드');expect(TestWorker.instances).toHaveLength(0);});
it('화면 진입·종료·재시도 클릭만으로 다른 세션의 완료 미확인 기록을 지우지 않는다',()=>{values.set('fieldguard-voice-incomplete-v1','previous');const engine=new LocalRecognizer();engine.stop();expect(voiceRecovery()).toBe(true);engine.resetRecovery();expect(voiceRecovery()).toBe(true);});
it('저장 모델로 인식한 뒤 Worker·공유 메모리 잠금을 정리하고 미완료 기록을 지운다',async()=>{const engine=new LocalRecognizer(),worker=await ready(engine);expect(voiceRecovery()).toBe(true);const pending=engine.transcribe(new Float32Array(16000));worker.reply({type:'text',text:'산소 농도'});expect(await pending).toBe('산소 농도');engine.stop();expect(worker.terminated).toBe(true);expect(fixture.release).toHaveBeenCalledOnce();expect(fixture.unlock).toHaveBeenCalledOnce();expect(voiceRecovery()).toBe(false);});
it('실패 기록은 일반 종료로 지워지지 않고 재시작 클릭만으로 타 세션 기록을 지우지 않는다',async()=>{const engine=new LocalRecognizer(),worker=await ready(engine);const pending=engine.transcribe(new Float32Array(16000));worker.reply({type:'error',message:'음성 실행 오류'});await expect(pending).rejects.toThrow('음성 실행 오류');engine.stop();expect(voiceRecovery()).toBe(true);engine.resetRecovery();expect(voiceRecovery()).toBe(true);});
it('인식 중 중단한 뒤 늦은 결과를 무시하고 다음 질문을 자동 생성하지 않는다',async()=>{const engine=new LocalRecognizer(),worker=await ready(engine),pending=engine.transcribe(new Float32Array(16000));engine.stop();worker.reply({type:'text',text:'뒤늦은 질문'});await expect(pending).rejects.toThrow('중단');expect(worker.terminated).toBe(true);});
