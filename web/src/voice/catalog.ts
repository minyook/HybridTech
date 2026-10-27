// Immutable multilingual Whisper tiny q8 (not the English-only .en model).
export const VOICE_REVISION='5332fcc35e32a33b86612b9a57a89be7906102b1';
export const VOICE_CACHE='fieldguard-voice-tiny-v1';
export const VOICE_MODEL='whisper-tiny';
export const VOICE_FILES=[
  ['config.json',2248,'2b2e4e519084e0ea028b19b153f95202735a971870d6844aa26e559edd292e94'],
  ['generation_config.json',3716,'68ac791fcb4999461a313472125042934656240ba1cba7d1c2627fcbb19ac24c'],
  ['preprocessor_config.json',339,'a6a76d28c93edb273669eb9e0b0636a2bddbb1272c3261e47b7ca6dfdbac1b8d'],
  ['tokenizer.json',2480466,'27fc476bfe7f17299480be2273fc0608e4d5a99aba2ab5dec5374b4482d1a566'],
  ['tokenizer_config.json',282683,'2a4c4281cf9f51ac6ccc406fdc711a087afe6530f671fa7b80953edc498275ce'],
  ['onnx/encoder_model_quantized.onnx',10124910,'fd9d995b9dcb0520f0dbf6cf68651af639fc385f594d9d876e69ca2802dc438e'],
  ['onnx/decoder_model_merged_quantized.onnx',30727765,'6c0c125986b007d2e3734bec84c18bda0152071b90b87fadac6d7764499927a0'],
] as const;
export const VOICE_MODEL_BYTES=VOICE_FILES.reduce((sum,item)=>sum+item[1],0);
export const voiceKey=(name:string,origin=location.origin)=>new URL(`/voice-models/${VOICE_MODEL}/${name}`,origin).href;
