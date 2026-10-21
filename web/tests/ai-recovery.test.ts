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
it.each(['conversation','prefill','decode'] as const)('생성 중 종료 위치 %s를 재접속 후 복원한다',async stage=>{
  let guard=await import('../src/ai/recovery');guard.beginAIOperation('conversation',2048);guard.updateAIStage(stage);
  vi.resetModules();guard=await import('../src/ai/recovery');expect(guard.unfinishedAIOperation()).toMatchObject({stage,contextTokens:2048});
  expect(guard.recoveryMessage(stage)).toContain(stage==='conversation'?'대화 작업 공간':stage==='prefill'?'질문·문서 입력':'답변 토큰');
});
it('저장소 접근 오류는 예외를 누적하거나 앱을 종료시키지 않는다',async()=>{
  vi.stubGlobal('localStorage',{getItem:()=>{throw Error('denied');},setItem:()=>{throw Error('denied');},removeItem:()=>{throw Error('denied');}});
  const guard=await import('../src/ai/recovery');expect(()=>{guard.beginAIOperation('engine',4096);guard.updateAIStage('generation');guard.finishAIOperation();guard.resetAIRecovery();}).not.toThrow();
});
it('엔진 정리 도중 종료된 기록도 다시 실행할 때 자동 적재를 멈춘다',async()=>{
  let guard=await import('../src/ai/recovery');guard.beginAIOperation('decode',2048);guard.updateAIStage('cleanup');
  vi.resetModules();guard=await import('../src/ai/recovery');expect(guard.unfinishedAIOperation()).toMatchObject({stage:'cleanup',contextTokens:2048});
  expect(guard.recoveryMessage('cleanup')).toContain('AI 메모리 정리');
});
it('강제 종료 상태는 finally의 완료 처리와 재접속을 지나도 유지된다',async()=>{
  let guard=await import('../src/ai/recovery');guard.beginAIOperation('decode',2048,{revision:'test-runtime',model:'test-model'});guard.markAIUncertain('stop-timeout');guard.finishAIOperation();
  expect(guard.unfinishedAIOperation()).toMatchObject({state:'uncertain',reason:'stop-timeout',revision:'test-runtime',model:'test-model'});
  vi.resetModules();guard=await import('../src/ai/recovery');expect(guard.unfinishedAIOperation()?.state).toBe('uncertain');
  guard.resetAIRecovery();expect(guard.unfinishedAIOperation()).toBeUndefined();
});
