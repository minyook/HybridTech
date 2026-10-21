import 'fake-indexeddb/auto';
import { expect, it, vi } from 'vitest';
import { DEFAULT_AI_SETTINGS, loadAISettings, normalizeAISettings, saveAISettings } from '../src/ai/preferences';
import { getSetting, setSetting } from '../src/storage/db';

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
it('복구용 2,048 문맥도 명시적으로 저장하고 답변 한도를 검증한다',async()=>{
  expect(await saveAISettings({contextTokens:2048,maxOutputTokens:256})).toEqual({contextTokens:2048,maxOutputTokens:256});
  expect(await loadAISettings()).toEqual({contextTokens:2048,maxOutputTokens:256});
  expect(normalizeAISettings({contextTokens:2048,maxOutputTokens:4096}).maxOutputTokens).toBe(1024);
});
it('아이폰은 경량 전용 한도를 사용하고 기존 Gemma 설정을 덮어쓰지 않는다',async()=>{
  const previous={contextTokens:8192,maxOutputTokens:1536};await setSetting('ai-settings',previous);await setSetting('ai-settings-light-v1',{contextTokens:2048,maxOutputTokens:256});await setSetting('ai-settings-light-v2',undefined);
  vi.stubGlobal('navigator',{userAgent:'iPhone',maxTouchPoints:5});
  try{
    expect(await loadAISettings()).toEqual({contextTokens:2048,maxOutputTokens:512});
    expect(normalizeAISettings(previous)).toEqual({contextTokens:2048,maxOutputTokens:512});
    expect(await saveAISettings({contextTokens:16384,maxOutputTokens:512})).toEqual({contextTokens:2048,maxOutputTokens:512});
    expect(await loadAISettings()).toEqual({contextTokens:2048,maxOutputTokens:512});expect(await getSetting('ai-settings')).toEqual(previous);
    expect(await getSetting('ai-settings-light-v1')).toEqual({contextTokens:2048,maxOutputTokens:256});
    await saveAISettings({contextTokens:2048,maxOutputTokens:256});expect((await loadAISettings()).maxOutputTokens).toBe(256);
  }
  finally{vi.unstubAllGlobals();}
});
