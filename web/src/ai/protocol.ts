import type { AISettings } from './preferences';
export interface TokenUsage { inputTokens?:number;outputTokens?:number;totalTokens?:number;maxOutputTokens:number;contextTokens:number }
export interface GenerationResult { answer:string;usage:TokenUsage }
export interface InitializationTiming { verification:'full'|'cached';verificationMs:number;runtimeMs:number;engineMs:number;totalMs:number }
export type AIRequest =
  | {id:number;type:'initialize';model:Blob;sha256:string;runtimeUrl:string;settings:AISettings}
  | {id:number;type:'generate';prompt:string;systemInstruction?:string;settings:AISettings};
export type AIReply =
  | {id:number;type:'progress';message:string}
  | {id:number;type:'text';answer:string}
  | {id:number;type:'done';result?:GenerationResult;initialization?:InitializationTiming}
  | {id:number;type:'error';message:string};
