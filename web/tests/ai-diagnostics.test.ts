import {afterEach,beforeEach,expect,it,vi} from 'vitest';
import {aiDiagnosticReport,AI_DIAGNOSTIC_KEY,recordAIDiagnostic} from '../src/ai/diagnostics';
import {AI_OPERATION_KEY} from '../src/ai/recovery';
let values:Map<string,string>;
beforeEach(()=>{values=new Map();vi.stubGlobal('localStorage',{getItem:(k:string)=>values.get(k)??null,setItem:(k:string,v:string)=>values.set(k,v)});});
afterEach(()=>vi.unstubAllGlobals());
it('중단 표시와 실제 힙 크기를 보존하고 질문·답변·원시 오류를 보고서에서 제외한다',()=>{
  recordAIDiagnostic({stage:'decode',kind:'memory',wasmHeapBytes:123456,contextTokens:2048,maxOutputTokens:256,...{prompt:'private question',answer:'private answer',url:'private URL'}});
  values.set(AI_OPERATION_KEY,JSON.stringify({stage:'decode',contextTokens:2048,startedAt:100,id:'private-id',prompt:'private question'}));
  const report=aiDiagnosticReport();expect(report.events[0]).toMatchObject({stage:'decode',wasmHeapBytes:123456,contextTokens:2048});expect(report.interrupted).toEqual({stage:'decode',contextTokens:2048,startedAt:100});
  expect(JSON.stringify(report)).not.toContain('private');expect(report.memoryScope).toContain('excludes GPU');
});
it('최근 48개만 남기고 변조된 저장 내용과 잘못된 메모리 수치를 제외한다',()=>{
  for(let i=0;i<60;i++)recordAIDiagnostic({stage:'decode',wasmHeapBytes:i});
  expect(aiDiagnosticReport().events).toHaveLength(48);expect(aiDiagnosticReport().events[0].wasmHeapBytes).toBe(12);
  values.set(AI_DIAGNOSTIC_KEY,JSON.stringify([{stage:'decode',at:'invalid',wasmHeapBytes:10},{stage:'decode',at:new Date().toISOString(),wasmHeapBytes:-1,prompt:'secret'}]));
  expect(aiDiagnosticReport().events).toHaveLength(1);expect(aiDiagnosticReport().events[0].wasmHeapBytes).toBeUndefined();expect(JSON.stringify(aiDiagnosticReport())).not.toContain('secret');
});
it('저장소가 없거나 손상돼도 진단 때문에 AI 실행을 중단하지 않는다',()=>{
  values.set(AI_DIAGNOSTIC_KEY,'bad JSON');expect(aiDiagnosticReport().events).toEqual([]);
  vi.stubGlobal('localStorage',{getItem:()=>{throw Error('denied');},setItem:()=>{throw Error('denied');}});
  expect(()=>recordAIDiagnostic({stage:'prefill'})).not.toThrow();expect(aiDiagnosticReport().events).toEqual([]);
});
