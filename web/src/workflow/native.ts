/* 소스 안내
 * Android WebView embedded 표시와 네이티브 목적지 링크를 판단한다. 웹 AI 중복 초기화와 네이티브 화면 이동 경계를 구분한다.
 */
export function isAndroidEmbedded() { return typeof location !== 'undefined' && new URLSearchParams(location.search).get('embedded') === 'android'; }
export function nativeAIReady() { return isAndroidEmbedded() && new URLSearchParams(location.search).get('nativeAiReady') === 'true'; }
export function nativeDestination(path: string): string | undefined {
  if (!isAndroidEmbedded()) return undefined;
  if (path === '/ask') return 'fieldguard://ask';
  if (path === '/settings') return 'fieldguard://settings';
  return undefined;
}
