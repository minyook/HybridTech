import type { AISettings } from './preferences';
import type { AIStage } from './recovery';
import type { AIExecutionProfile } from './execution';
import type { ModelReaderStats } from './model-stream';
import type { AIDiagnostic } from './diagnostics';
import type { Source } from '../types';
import type { AnswerPlan } from '../rag/answer-plan';
export type AIModelSource=Blob|{backend:'opfs';storageKey:string;size:number};
export interface TokenUsage { inputTokens?:number;outputTokens?:number;totalTokens?:number;maxOutputTokens:number;contextTokens:number;passes?:number;perPassMaxOutputTokens?:number }
export interface GenerationResult { answer:string;usage:TokenUsage;truncated?:boolean;sourceSupplemented?:boolean;generated?:boolean;supplementSkipped?:'not-needed'|'budget'|'validation'|'recovery' }
export interface InitializationTiming { verification:'full'|'cached';verificationMs:number;runtimeMs:number;engineMs:number;totalMs:number;modelRead? :ModelReaderStats&{mode:'file-range'} }
export type AIRequest =
  | {id:number;type:'initialize';model:AIModelSource;sha256:string;runtimeUrl:string;settings:AISettings;profile?:AIExecutionProfile;provider?:'light'}
  | {id:number;type:'generate';prompt:string;systemInstruction?:string;settings:AISettings;question?:string;sources?:Source[];plan?:AnswerPlan}
  | {id:number;type:'stop'};
export type AIReply =
  | {id:number;type:'diagnostic';sample:AIDiagnostic}
  | {id:number;type:'progress';message:string;stage?:AIStage}
  | {id:number;type:'text';answer:string}
  | {id:number;type:'done';result?:GenerationResult;initialization?:InitializationTiming;cleanupComplete?:boolean}
  | {id:number;type:'stopped';cleanupComplete:boolean}
  | {id:number;type:'error';message:string;cleanupComplete?:boolean};
