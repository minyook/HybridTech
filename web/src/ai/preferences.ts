import { getSetting, setSetting } from '../storage/db';
import { browserAIFeatures } from './capabilities';

export const OUTPUT_TOKEN_OPTIONS = [256,512,1024,1536,2048,4096] as const;
export const CONTEXT_TOKEN_OPTIONS = [2048,4096,8192,16384] as const;
export interface AISettings { maxOutputTokens:number;contextTokens:number }
export const DEFAULT_AI_SETTINGS:AISettings = {maxOutputTokens:1536,contextTokens:8192};
export const REDUCED_AI_SETTINGS:AISettings = {maxOutputTokens:512,contextTokens:4096};
export const RECOVERY_AI_SETTINGS:AISettings = {maxOutputTokens:256,contextTokens:2048};
export const LIGHT_AI_SETTINGS:AISettings = {maxOutputTokens:512,contextTokens:2048};
export function normalizeAISettings(value:unknown):AISettings {
  const candidate = value && typeof value==='object' ? value as Partial<AISettings> : {};
  if(browserAIFeatures().appleMobile)return {...LIGHT_AI_SETTINGS,maxOutputTokens:candidate.maxOutputTokens===256?256:512};
  const defaults=DEFAULT_AI_SETTINGS;
  const contextTokens = CONTEXT_TOKEN_OPTIONS.find(option=>option===candidate.contextTokens)??defaults.contextTokens;
  const output = OUTPUT_TOKEN_OPTIONS.find(option=>option===candidate.maxOutputTokens)??defaults.maxOutputTokens;
  return {contextTokens,maxOutputTokens:Math.min(output,contextTokens/2)};
}
// Preserve v1/Gemma preferences. The revised evidence prompt has its own default
// output budget; a deliberate 256-token choice can still be saved in v2.
const settingsKey=()=>browserAIFeatures().appleMobile?'ai-settings-light-v2':'ai-settings';
export async function loadAISettings() { return normalizeAISettings(await getSetting(settingsKey())); }
export async function saveAISettings(value:AISettings) { const settings=normalizeAISettings(value);await setSetting(settingsKey(),settings);return settings; }
