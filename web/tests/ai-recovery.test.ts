import { afterEach, beforeEach, expect, it, vi } from 'vitest';
let values:Map<string,string>;
beforeEach(()=>{vi.resetModules();values=new Map();vi.stubGlobal('localStorage',{getItem:(key:string)=>values.get(key)??null,setItem:(key:string,value:string)=>values.set(key,value),removeItem:(key:string)=>values.delete(key)});});
afterEach(()=>vi.unstubAllGlobals());
it('준비 중 페이지가 종료된 뒤 새 런타임에서 미완료 단계가 복원된다',async()=>{
  let guard=await import('../src/ai/recovery');guard.beginAIOperation('verification',4096);guard.updateAIStage('engine');
  expect(guard.unfinishedAIOperation()).toBeUndefined();vi.resetModules();guard=await import('../src/ai/recovery');
  expect(guard.unfinishedAIOperation()).toMatchObject({stage:'engine',contextTokens:4096});
  guard.resetAIRecovery();expect(guard.unfinishedAIOperation()).toBeUndefined();
});
it('정상 완료는 자기 기록만 지우고 다른 화면의 진행 기록은 지우지 않는다',async()=>{
  const guard=await import('../src/ai/recovery');guard.beginAIOperation('generation',4096);guard.finishAIOperation();expect(values.size).toBe(0);
  guard.beginAIOperation('engine',4096);values.set(guard.AI_OPERATION_KEY,JSON.stringify({version:1,id:'other-tab',stage:'engine',contextTokens:4096,startedAt:Date.now()}));
  guard.finishAIOperation();expect(guard.unfinishedAIOperation()?.id).toBe('other-tab');
});
it('저장소 접근 오류는 예외를 누적하거나 앱을 종료시키지 않는다',async()=>{
  vi.stubGlobal('localStorage',{getItem:()=>{throw Error('denied');},setItem:()=>{throw Error('denied');},removeItem:()=>{throw Error('denied');}});
  const guard=await import('../src/ai/recovery');expect(()=>{guard.beginAIOperation('engine',4096);guard.updateAIStage('generation');guard.finishAIOperation();guard.resetAIRecovery();}).not.toThrow();
});
