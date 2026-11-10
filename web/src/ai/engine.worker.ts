/* 소스 안내
 * 일반 웹 생성 Worker 진입점. 메시지를 WorkerEngine에 전달해 메인 UI와 무거운 SDK 실행을 분리한다.
 * 연결 모듈: ./worker-engine, ./protocol.
 */
import { WorkerEngine } from './worker-engine';
import type { AIReply, AIRequest } from './protocol';
const engine=new WorkerEngine();
const scope=self as unknown as {onmessage:((event:MessageEvent<AIRequest>)=>void)|null;postMessage:(message:AIReply)=>void};
scope.onmessage=event=>{void engine.handle(event.data,message=>scope.postMessage(message));};
