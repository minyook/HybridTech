import 'fake-indexeddb/auto';
import { expect, it, vi } from 'vitest';
import { DEFAULT_AI_SETTINGS, loadAISettings, normalizeAISettings, saveAISettings } from '../src/ai/preferences';
import { setSetting } from '../src/storage/db';

it('저장하지 않은 경우와 손상된 값에는 유효한 기본 토큰 설정을 사용한다',async()=>{
  await setSetting('ai-settings',undefined);
  expect(await loadAISettings()).toEqual(DEFAULT_AI_SETTINGS);
  for(const invalid of [null,'8192',{contextTokens:NaN,maxOutputTokens:-1},{contextTokens:1e9,maxOutputTokens:1e9}])expect(normalizeAISettings(invalid)).toEqual(DEFAULT_AI_SETTINGS);
});
it('답변 한도와 문맥 크기를 저장하고 새 조회에서 복원한다',async()=>{
  const value={contextTokens:16384,maxOutputTokens:2048};
  await saveAISettings(value);expect(await loadAISettings()).toEqual(value);
});
it('문맥을 줄이면 답변 한도를 문맥의 절반 이하로 제한한다',()=>{
  expect(normalizeAISettings({contextTokens:4096,maxOutputTokens:4096})).toEqual({contextTokens:4096,maxOutputTokens:2048});
});
it('iPhone의 새 기본값은 작게 잡되 사용자가 저장한 설정은 유지한다',()=>{
  vi.stubGlobal('navigator',{userAgent:'iPhone',maxTouchPoints:5});
  try{expect(normalizeAISettings(undefined)).toEqual({contextTokens:4096,maxOutputTokens:512});expect(normalizeAISettings({contextTokens:8192,maxOutputTokens:1536})).toEqual({contextTokens:8192,maxOutputTokens:1536});}
  finally{vi.unstubAllGlobals();}
});
