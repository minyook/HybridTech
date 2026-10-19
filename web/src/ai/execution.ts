import type { GpuArtisanConfig, LlmExecutorSettings } from '@litert-lm/core';

export type AIExecutionProfile='standard'|'apple-mobile';

export function executorSettings(contextTokens:number,profile:AIExecutionProfile='standard'):LlmExecutorSettings {
  if(profile==='standard')return {maxNumTokens:contextTokens};
  // LiteRT-LM 0.18.0: batch 1 reuses the decode graph for prompt encoding.
  // Autosized ringbuffers trade instant rewind for lower local-attention memory.
  // These reduce temporary allocations; they cannot cap total WebKit/GPU memory.
  const backendConfig:GpuArtisanConfig={
    num_output_candidates:1,wait_for_weight_uploads:true,num_decode_steps_per_sync:1,
    sequence_batch_size:1,supported_lora_ranks:[],max_top_k:1,
    enable_decode_logits:false,enable_external_embeddings:false,use_submodel:false,
    use_autosized_ringbuffers:true,
  };
  return {maxNumTokens:contextTokens,backendConfig};
}
