import { WorkerEngine } from './worker-engine';
import type { AIReply, AIRequest } from './protocol';
const engine=new WorkerEngine();
const scope=self as unknown as {onmessage:((event:MessageEvent<AIRequest>)=>void)|null;postMessage:(message:AIReply)=>void};
scope.onmessage=event=>{void engine.handle(event.data,message=>scope.postMessage(message));};
