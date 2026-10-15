export function isAndroidEmbedded() { return typeof location !== 'undefined' && new URLSearchParams(location.search).get('embedded') === 'android'; }
export function nativeAIReady() { return isAndroidEmbedded() && new URLSearchParams(location.search).get('nativeAiReady') === 'true'; }
export function nativeDestination(path: string): string | undefined {
  if (!isAndroidEmbedded()) return undefined;
  if (path === '/ask') return 'fieldguard://ask';
  if (path === '/settings') return 'fieldguard://settings';
  return undefined;
}
