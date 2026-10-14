import { installModel } from './model';

// Official web artifact pinned to an immutable Hugging Face revision.
// Metadata verified 2026-10-14 via HF's model API and the artifact's HEAD response.
export const GEMMA4_E2B = Object.freeze({
  label: 'Gemma 4 E2B',
  name: 'gemma-4-E2B-it-web.litertlm',
  revision: 'b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1',
  url: 'https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it-web.litertlm',
  size: 2008432640,
  sha256: '3a08e8d94e23b814ae5414469c370c503813949acb8ceaa17e4ebf8a35af35b5',
  source: 'https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it-web.litertlm',
});

export async function downloadGemma(signal: AbortSignal, onProgress: (message: string) => void, dependencies = {
  fetchFile: (input:RequestInfo|URL,init?:RequestInit) => globalThis.fetch(input,init),
  install: installModel,
  estimate: () => navigator.storage.estimate(),
}) {
  signal.throwIfAborted();
  const storage = await dependencies.estimate();
  if (storage.quota && GEMMA4_E2B.size > storage.quota - (storage.usage ?? 0)) throw new Error('Gemma를 저장할 공간이 부족해요. 약 2.01 GB의 빈 공간이 필요해요.');
  onProgress('Gemma 4 E2B 다운로드 연결 중');
  const response = await dependencies.fetchFile(GEMMA4_E2B.url, { signal, credentials: 'omit' });
  if (!response.ok || !response.body) throw new Error(`모델을 다운로드하지 못했어요 (${response.status}). 연결 후 다시 시도해 주세요.`);
  const length = response.headers.get('Content-Length');
  if (length && Number(length) !== GEMMA4_E2B.size) {
    await response.body.cancel();
    throw new Error('배포 파일의 크기가 고정된 모델 정보와 달라요. 설치를 중단했어요.');
  }
  try {
    await dependencies.install(response.body, { name:GEMMA4_E2B.name,size:GEMMA4_E2B.size,sha256:GEMMA4_E2B.sha256,signal,onProgress });
  } finally { await response.body.cancel().catch(() => {}); }
}
