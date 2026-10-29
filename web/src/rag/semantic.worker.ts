import {env,AutoModel,AutoTokenizer,type PreTrainedModel} from '@huggingface/transformers';
import {SEMANTIC_CACHE,semanticKey} from './semantic-assets';
self.onmessage=async(event:MessageEvent<{question:string}>)=>{
 let encoder:PreTrainedModel|undefined;const urls:string[]=[];
 try{
  const cache=await caches.open(SEMANTIC_CACHE),marker=await cache.match(semanticKey('installed.json'));if(!marker)throw Error('설정에서 문장 의미 검색을 준비해 주세요.');
  const data=await marker.json() as {runtime:{path:string}[]};
  env.allowRemoteModels=false;env.allowLocalModels=true;env.useFSCache=false;env.useBrowserCache=false;env.useCustomCache=true;env.localModelPath=new URL('../',semanticKey('config.json')).href;
  env.customCache={match:async(key:string)=>cache.match(new URL(String(key),self.location.origin).href),put:async()=>{}};
  env.backends.onnx.wasm!.numThreads=1;env.backends.onnx.wasm!.proxy=false;
  const runtime:Record<string,string>={};for(const f of data.runtime){const r=await cache.match(new URL(`../voice-runtime/${f.path}`,semanticKey('')).href);if(!r)throw Error('의미 검색 실행 파일이 없어요.');runtime[f.path]=URL.createObjectURL(await r.blob());urls.push(runtime[f.path]);}
  env.backends.onnx.wasm!.wasmPaths={mjs:runtime['ort-wasm-simd-threaded.jsep.mjs'],wasm:runtime['ort-wasm-simd-threaded.jsep.wasm']};
  const localFetch=self.fetch.bind(self);self.fetch=(input,init)=>{const url=String(input instanceof Request?input.url:input);return url.startsWith('blob:')?localFetch(input,init):Promise.reject(Error('의미 검색 중 네트워크 요청을 차단했어요.'));};
  const tokenizer=await AutoTokenizer.from_pretrained('semantic-model',{local_files_only:true});
  const inputs=await tokenizer('task: search result | query: '+event.data.question);if(Number(inputs.input_ids.dims.at(-1))>256)throw Error('질문이 길어 의미 검색을 생략했어요. 조건별로 나누어 주세요.');
  encoder=await AutoModel.from_pretrained('semantic-model',{device:'wasm',dtype:'q4',local_files_only:true});
  const output=await encoder(inputs);
  const vector=Array.from(output.sentence_embedding.data as Float32Array);await encoder.dispose();encoder=undefined;
  self.postMessage({vector,clean:true});
 }catch(error){try{await encoder?.dispose();self.postMessage({error:error instanceof Error?error.message:'의미 검색을 완료하지 못했어요.',clean:true});}catch{self.postMessage({error:'의미 검색 정리를 확인하지 못했어요.',clean:false});}}
 finally{urls.forEach(url=>URL.revokeObjectURL(url));}
};
