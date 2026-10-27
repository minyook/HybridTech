import {env,pipeline,type AutomaticSpeechRecognitionPipeline} from '@huggingface/transformers';
import {VOICE_CACHE,VOICE_MODEL,voiceKey} from './catalog';
let recognizer:AutomaticSpeechRecognitionPipeline|undefined;
let processing=false;
self.onmessage=async(event:MessageEvent<{type:'prepare'|'transcribe';audio?:Float32Array}>)=>{
  if(processing)return;processing=true;
  try{
    if(event.data.type==='prepare'){
      const fetchBlob=self.fetch.bind(self);
      self.fetch=(input,init)=>{
        const url=String(input instanceof Request?input.url:input);
        if(!url.startsWith('blob:'))return Promise.reject(Error('음성 인식 중 네트워크 요청을 차단했어요. 파일을 다시 준비해 주세요.'));
        return fetchBlob(input,init);
      };
      const cache=await caches.open(VOICE_CACHE);
      // Required AND optional files are cache-only; no audio or model request is sent to a remote service.
      env.allowRemoteModels=false;env.allowLocalModels=true;env.useBrowserCache=false;env.useFSCache=false;env.useCustomCache=true;
      env.localModelPath='/voice-models/';
      env.customCache={match:async (key:string)=>cache.match(new URL(String(key),self.location.origin).href),put:async()=>{}};
      env.backends.onnx.wasm!.numThreads=1;env.backends.onnx.wasm!.proxy=false;
      const marker=await cache.match(voiceKey('installed.json',self.location.origin));if(!marker)throw Error('설정에서 음성 파일을 준비해 주세요.');
      const manifest=await marker.json() as {files:{path:string}[]};
      // Runtime files use local blob URLs so a missing service worker never sends data externally.
      const urls:Record<string,string>={};for(const file of manifest.files){const response=await cache.match(new URL(`/voice-runtime/${file.path}`,self.location.origin).href);if(!response)throw Error('음성 실행 파일이 없어요.');urls[file.path]=URL.createObjectURL(await response.blob());}
      env.backends.onnx.wasm!.wasmPaths={mjs:urls['ort-wasm-simd-threaded.jsep.mjs'],wasm:urls['ort-wasm-simd-threaded.jsep.wasm']};
      const create=pipeline as unknown as (task:'automatic-speech-recognition',model:string,options:{device:'wasm';dtype:'q8';local_files_only:true})=>Promise<AutomaticSpeechRecognitionPipeline>;
      recognizer=await create('automatic-speech-recognition',VOICE_MODEL,{device:'wasm',dtype:'q8',local_files_only:true});
      self.postMessage({type:'ready'});
    }else{
      const audio=event.data.audio;if(!recognizer||!audio||audio.length>320000||audio.length<3200)throw Error('인식할 음성이 없거나 너무 길어요.');
      const result=await recognizer(audio,{language:'korean',task:'transcribe',return_timestamps:false,max_new_tokens:128,do_sample:false});
      self.postMessage({type:'text',text:Array.isArray(result)?result[0]?.text:result.text});
    }
  }catch(error){self.postMessage({type:'error',message:error instanceof Error?error.message:'로컬 음성 인식에 실패했어요.'});}
  finally{processing=false;}
};
