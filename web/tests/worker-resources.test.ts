import {expect,it,vi} from 'vitest';
import {WorkerResources} from '../src/ai/worker-resources';
it('현재 힙 뷰에서 크기를 읽고 WebGPU 오류를 내용 없이 종류로만 기록한다',()=>{
  const listeners=new Map<string,EventListener>();const device={destroy:vi.fn(),lost:new Promise<{reason:string}>(()=>{}),addEventListener:(key:string,fn:EventListener)=>{listeners.set(key,fn);},removeEventListener:vi.fn()};
  const wasm={HEAPU8:new Uint8Array(16),preinitializedWebGPUDevice:device};const report=vi.fn();const monitor=new WorkerResources(()=>({liteRtLmWasm:wasm}),report);
  monitor.watch();monitor.watch();monitor.sample('prefill');wasm.HEAPU8=new Uint8Array(32);monitor.sample('decode');
  expect(report).toHaveBeenLastCalledWith({stage:'decode',kind:'memory',wasmHeapBytes:32});
  listeners.get('uncapturederror')?.(Object.assign(new Event('uncapturederror'),{error:{constructor:{name:'GPUOutOfMemoryError'},message:'private labels'}}));
  expect(report).toHaveBeenLastCalledWith({stage:'decode',kind:'gpu-error',gpuError:'out-of-memory'});
  monitor.destroyGPU();monitor.destroyGPU();expect(device.destroy).toHaveBeenCalledOnce();expect(device.removeEventListener).toHaveBeenCalledOnce();
});
it('예상하지 못한 장치 손실만 기록하고 명시적 destroy는 오류로 기록하지 않는다',async()=>{
  let lost!:(value:{reason:string})=>void;const report=vi.fn();const device={destroy:vi.fn(),lost:new Promise<{reason:string}>(resolve=>{lost=resolve;}),addEventListener:vi.fn(),removeEventListener:vi.fn()};
  const monitor=new WorkerResources(()=>({liteRtLmWasm:{HEAPU8:new Uint8Array(1),preinitializedWebGPUDevice:device}}),report);
  monitor.watch();monitor.sample('decode');lost({reason:'unknown'});await Promise.resolve();expect(report).toHaveBeenLastCalledWith({stage:'decode',kind:'gpu-lost'});
  report.mockClear();monitor.destroyGPU();await Promise.resolve();expect(report).toHaveBeenCalledWith({stage:'cleanup',kind:'gpu-destroyed'});expect(report).not.toHaveBeenCalledWith(expect.objectContaining({kind:'gpu-lost'}));
});
it('아직 SDK 런타임이 없으면 표본만 생략한다',()=>{
  const report=vi.fn();const monitor=new WorkerResources(()=>{throw Error('not loaded');},report);
  expect(()=>{monitor.watch();monitor.sample('engine');monitor.destroyGPU();}).not.toThrow();expect(report).not.toHaveBeenCalled();
});
