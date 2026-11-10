/* 소스 안내
 * Worker의 모델/SDK 자원 정리 상태를 관리한다. 중복 정리·실패 후 자원 수명은 생성 루프와 분리한다.
 * 연결 모듈: ./diagnostics.
 */
import type { AIDiagnostic } from './diagnostics';
interface Device {
  destroy():void;lost:Promise<{reason:string}>;
  addEventListener(type:string,listener:EventListener):void;
  removeEventListener(type:string,listener:EventListener):void;
}
interface Runtime {liteRtLmWasm:{HEAPU8:Uint8Array;preinitializedWebGPUDevice?:Device}}
// The exported SDK runtime exposes its actual WASM heap and GPU device. These
// samples are capacities, not resident/process/GPU memory measurements.
export class WorkerResources {
  private device?:Device;
  private stage='runtime';
  constructor(private readonly runtime:()=>Runtime,private readonly report:(sample:AIDiagnostic)=>void){}
  sample(stage:string){
    this.stage=stage;
    try{this.report({stage,kind:'memory',wasmHeapBytes:this.runtime().liteRtLmWasm.HEAPU8.byteLength});}catch{/* Diagnostics are optional. */}
  }
  private readonly gpuError:EventListener=event=>{
    const name=(event as Event&{error?:{constructor?:{name?:string}}}).error?.constructor?.name;
    this.report({stage:this.stage,kind:'gpu-error',gpuError:name==='GPUOutOfMemoryError'?'out-of-memory':name==='GPUValidationError'?'validation':name==='GPUInternalError'?'internal':'unknown'});
  };
  watch(){
    try{
      const device=this.runtime().liteRtLmWasm.preinitializedWebGPUDevice;if(!device||this.device===device)return;
      this.device=device;device.addEventListener('uncapturederror',this.gpuError);
      void device.lost.then(()=>{if(this.device===device)this.report({stage:this.stage,kind:'gpu-lost'});}).catch(()=>{});
    }catch{/* Keep SDK execution independent of optional diagnostics. */}
  }
  destroyGPU(){
    const device=this.device;if(!device)return;this.device=undefined;
    device.removeEventListener('uncapturederror',this.gpuError);device.destroy();
    this.report({stage:'cleanup',kind:'gpu-destroyed'});
  }
}
