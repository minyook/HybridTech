import {holdAIForAudio} from '../ai/runtime';
import {acquireEngineLease} from '../ai/engine-lease';
import {SEMANTIC_MARKER,semanticInstalled,semanticRecovery} from './semantic-assets';
/** No persistent encoder: do not overlap search, ASR and Gemma allocations. */
export async function queryVector(question:string,signal:AbortSignal):Promise<number[]|undefined>{
 if(!await semanticInstalled())return;
 if(semanticRecovery())throw Error('이전 문장 의미 검색의 완료를 확인하지 못했어요. 기본 검색을 사용하며 설정의 ‘의미 검색 파일 확인·복구’에서 다시 시도할 수 있어요.');
 const release=await holdAIForAudio();let unlock:(()=>void)|undefined,worker:Worker|undefined;
 try{
  signal.throwIfAborted();unlock=await acquireEngineLease();if(!unlock)return;
  localStorage.setItem(SEMANTIC_MARKER,JSON.stringify({at:Date.now(),stage:'query-embedding'}));
  worker=new Worker(new URL('./semantic.worker.ts',import.meta.url),{type:'module'});
  return await new Promise<number[]>((resolve,reject)=>{
   let settled=false;
   const finish=(error?:Error,vector?:number[],clean=false)=>{if(settled)return;settled=true;clearTimeout(timer);signal.removeEventListener('abort',abort);if(clean)localStorage.removeItem(SEMANTIC_MARKER);error?reject(error):resolve(vector!);};
   const abort=()=>finish(new DOMException('검색을 중단했어요.','AbortError'));
   const timer=setTimeout(()=>finish(Error('문장 의미 검색 시간이 초과됐어요. 설정에서 다시 준비할 수 있어요.')),60000);
   signal.addEventListener('abort',abort,{once:true});
   if(signal.aborted){abort();return;}
   worker!.onmessage=event=>{const r=event.data;if(r.error)finish(Error(r.error),undefined,r.clean);else if(Array.isArray(r.vector)&&r.vector.length===768&&r.vector.every(Number.isFinite))finish(undefined,r.vector,r.clean);else finish(Error('의미 검색 결과 형식이 올바르지 않아요.'));};
   worker!.onerror=event=>{event.preventDefault();finish(Error('의미 검색 실행이 중단됐어요.'));};worker!.postMessage({question});
  });
 }finally{worker?.terminate();unlock?.();release();}
}
