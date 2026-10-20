// Deliberately scoped to the bundled light worker, never node_modules. Review
// against the pinned SDK on upgrades; missing/duplicate markers fail the build.
export function bridgeLightSDK(source){
  const replace=(from,to)=>{if(source.split(from).length!==2)throw Error(`WebLLM 0.2.86 bridge contract changed: ${from.slice(0,70)}`);source=source.replace(from,to);};
  // Cache.add bypasses global fetch interception. Downloads belong exclusively
  // to Settings, which streams and hashes them; the inference worker fails closed.
  replace('yield this.cache.add(request);','throw new Error("경량 모델 파일이 없어요. 설정에서 다운로드를 다시 확인해 주세요.");');
  // The compiled model accepts shorter prefill batches. Bound both input chunks
  // and the KV prefill workspace, rather than the default 1,024-token batch.
  replace('this.prefillChunkSize = metadata.prefill_chunk_size;\n        log.info("Using prefillChunkSize:', 'this.prefillChunkSize = Math.min(metadata.prefill_chunk_size, 64);\n        log.info("Using prefillChunkSize:');
  const tokenizer='const tokenizer = yield asyncLoadTokenizer(modelUrl, curModelConfig, this.appConfig, this.logger, modelRecord.integrity);';
  replace(tokenizer,tokenizer+'\n globalThis.__fieldguardCountTokens = text => tokenizer.encode(text).length;\n globalThis.__fieldguardWasmBytes = () => tvm.memory.memory.buffer.byteLength;');
  replace('\n            let gpuLabel = "WebGPU";','\n            globalThis.__fieldguardDevice = gpuDetectOutput.device;\n            let gpuLabel = "WebGPU";');
  return source;
}
