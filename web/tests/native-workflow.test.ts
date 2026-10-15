import { afterEach, expect, it, vi } from 'vitest';
import { isAndroidEmbedded, nativeAIReady, nativeDestination } from '../src/workflow/native';
afterEach(()=>vi.unstubAllGlobals());
it('일반 브라우저에서는 Android 전환 주소를 만들지 않는다',()=>{
  vi.stubGlobal('location',{search:''});expect(isAndroidEmbedded()).toBe(false);expect(nativeDestination('/ask')).toBeUndefined();expect(nativeAIReady()).toBe(false);
});
it('앱 점검 화면의 질문·모델 준비만 네이티브 화면으로 연결한다',()=>{
  vi.stubGlobal('location',{search:'?embedded=android&nativeAiReady=true'});
  expect(nativeAIReady()).toBe(true);expect(nativeDestination('/ask')).toBe('fieldguard://ask');expect(nativeDestination('/settings')).toBe('fieldguard://settings');
  expect(nativeDestination('/supervisor')).toBeUndefined();expect(nativeDestination('/sources/guide/24')).toBeUndefined();expect(nativeDestination('https://other.test')).toBeUndefined();
});
