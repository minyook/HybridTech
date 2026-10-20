import { MLCEngine } from '@mlc-ai/web-llm';
import { LIGHT_MODEL } from './light-model';
import { LightWorkerEngine } from './light-engine';
import type { AIReply, AIRequest } from './protocol';

// Inference never fetches network resources. Native Cache.add is also disabled
// by the pinned build bridge, so eviction fails safely instead of redownloading.
globalThis.fetch=async()=>{throw Error('AI 실행 파일이 누락됐어요. 설정에서 경량 모델 다운로드를 확인해 주세요.');};
const engine=new LightWorkerEngine(()=>new MLCEngine({logLevel:'SILENT',appConfig:{cacheBackend:'cache',model_list:[{
  model_id:LIGHT_MODEL.id,model:LIGHT_MODEL.base,model_lib:LIGHT_MODEL.files.find(file=>file.name.endsWith('.wasm'))!.url,
  required_features:['shader-f16'],overrides:{context_window_size:2048},
}]}}));
const scope=self as unknown as {onmessage:((event:MessageEvent<AIRequest>)=>void)|null;postMessage:(message:AIReply)=>void};
scope.onmessage=event=>{void engine.handle(event.data,message=>scope.postMessage(message));};
