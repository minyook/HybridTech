import type { AISettings } from './preferences';
import type { AIStage } from './recovery';
import type { AIExecutionProfile } from './execution';
import type { ModelReaderStats } from './model-stream';
import type { AIDiagnostic } from './diagnostics';
export type AIModelSource=Blob|{backend:'opfs';storageKey:string;size:number};
export interface TokenUsage { inputTokens?:number;outputTokens?:number;totalTokens?:number;maxOutputTokens:number;contextTokens:number }
export interface GenerationResult { answer:string;usage:TokenUsage }
export interface InitializationTiming { verification:'full'|'cached';verificationMs:number;runtimeMs:number;engineMs:number;totalMs:number;modelRead? :ModelReaderStats&{mode:'file-range'} }
export type AIRequest =
  | {id:number;type:'initialize';model:AIModelSource;sha256:string;runtimeUrl:string;settings:AISettings;profile?:AIExecutionProfile}
  | {id:number;type:'generate';prompt:string;systemInstruction?:string;settings:AISettings};
export type AIReply =
  | {id:number;type:'diagnostic';sample:AIDiagnostic}
  | {id:number;type:'progress';message:string;stage?:AIStage}
  | {id:number;type:'text';answer:string}
  | {id:number;type:'done';result?:GenerationResult;initialization?:InitializationTiming}
  | {id:number;type:'error';message:string};
