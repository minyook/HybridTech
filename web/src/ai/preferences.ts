import { getSetting, setSetting } from '../storage/db';

export const OUTPUT_TOKEN_OPTIONS = [256,512,1024,1536,2048,4096] as const;
export const CONTEXT_TOKEN_OPTIONS = [4096,8192,16384] as const;
export interface AISettings { maxOutputTokens:number;contextTokens:number }
export const DEFAULT_AI_SETTINGS:AISettings = {maxOutputTokens:1536,contextTokens:8192};
export function normalizeAISettings(value:unknown):AISettings {
  const candidate = value && typeof value==='object' ? value as Partial<AISettings> : {};
  const contextTokens = CONTEXT_TOKEN_OPTIONS.find(option=>option===candidate.contextTokens)??DEFAULT_AI_SETTINGS.contextTokens;
  const output = OUTPUT_TOKEN_OPTIONS.find(option=>option===candidate.maxOutputTokens)??DEFAULT_AI_SETTINGS.maxOutputTokens;
  return {contextTokens,maxOutputTokens:Math.min(output,contextTokens/2)};
}
export async function loadAISettings() { return normalizeAISettings(await getSetting('ai-settings')); }
export async function saveAISettings(value:AISettings) { const settings=normalizeAISettings(value);await setSetting('ai-settings',settings);return settings; }
