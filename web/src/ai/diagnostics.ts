import { AI_OPERATION_KEY } from './recovery';
export const AI_DIAGNOSTIC_KEY='fieldguard-ai-diagnostics';
export const AI_RUNTIME_REVISION='apple-gpu-lifecycle-20261020.3';
export interface AIDiagnostic {
  stage:string;kind?:'memory'|'gpu-error'|'gpu-lost'|'gpu-destroyed';wasmHeapBytes?:number;
  gpuError?:'out-of-memory'|'validation'|'internal'|'unknown';
  contextTokens?:number;maxOutputTokens?:number;
}
interface Entry extends AIDiagnostic {at:string;revision:string}
// Whitelist fields; never store prompts, responses, URLs, raw errors or device IDs.
function clean(value:unknown):Entry|undefined{
  if(!value||typeof value!=='object')return;
  const input=value as Record<string,unknown>;
  if(typeof input.stage!=='string'||!/^[a-z-]{1,32}$/.test(input.stage)||typeof input.at!=='string'||!Number.isFinite(Date.parse(input.at)))return;
  const result:Entry={stage:input.stage,at:new Date(input.at).toISOString(),revision:typeof input.revision==='string'&&/^[a-z0-9.-]{1,64}$/.test(input.revision)?input.revision:'unknown'};
  if(['memory','gpu-error','gpu-lost','gpu-destroyed'].includes(String(input.kind)))result.kind=input.kind as Entry['kind'];
  if(['out-of-memory','validation','internal','unknown'].includes(String(input.gpuError)))result.gpuError=input.gpuError as Entry['gpuError'];
  for(const key of ['wasmHeapBytes','contextTokens','maxOutputTokens'] as const){const n=input[key];if(typeof n==='number'&&Number.isSafeInteger(n)&&n>=0)result[key]=n;}
  return result;
}
function entries():Entry[]{try{const data=JSON.parse(localStorage.getItem(AI_DIAGNOSTIC_KEY)??'[]');return Array.isArray(data)?data.slice(-48).flatMap(value=>clean(value)??[]):[];}catch{return [];}}
export function recordAIDiagnostic(sample:AIDiagnostic){
  try{const next=clean({...sample,at:new Date().toISOString(),revision:AI_RUNTIME_REVISION});if(next)localStorage.setItem(AI_DIAGNOSTIC_KEY,JSON.stringify([...entries(),next].slice(-48)));}catch{/* Optional local diagnostics must not stop AI. */}
}
export function aiDiagnosticReport(){
  let interrupted:{stage:string;contextTokens?:number;startedAt?:number}|undefined;
  try{const saved=JSON.parse(localStorage.getItem(AI_OPERATION_KEY)??'null');if(saved&&typeof saved.stage==='string'&&/^[a-z-]{1,32}$/.test(saved.stage))interrupted={stage:saved.stage,...(Number.isSafeInteger(saved.contextTokens)?{contextTokens:saved.contextTokens}:{}),...(Number.isFinite(saved.startedAt)?{startedAt:saved.startedAt}:{})};}catch{/* Optional guard. */}
  return {version:1,revision:AI_RUNTIME_REVISION,exportedAt:new Date().toISOString(),memoryScope:'WASM heap capacity only; excludes GPU and total WebContent memory',interrupted,events:entries()};
}
