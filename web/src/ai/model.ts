import { sha256 } from '@noble/hashes/sha2.js';
import { commitModel, getModelBlob, modelMetadata, rememberModelVerification, removeModelData } from '../storage/db';
import type { ModelRecord, ModelVerification } from '../types';

export interface InstalledModel extends ModelRecord { storageKey: string }
const asHex = (bytes: Uint8Array) => [...bytes].map(byte => byte.toString(16).padStart(2,'0')).join('');
export async function digestBlob(blob: Blob, signal?: AbortSignal): Promise<string> {
  const hash = sha256.create(); const reader = blob.stream().getReader();
  try {
    while (true) { signal?.throwIfAborted(); const { done, value } = await reader.read(); if (done) break; hash.update(value); }
    return asHex(hash.digest());
  } finally { await reader.cancel().catch(() => {}); }
}
export const MODEL_RECHECK_INTERVAL_MS=7*24*60*60*1000;
const SAMPLE_BYTES=64*1024;
async function sampleDigest(blob:Blob) {
  // At most 512 KiB spread across the file. This is a change-detection check,
  // not a substitute for a full cryptographic integrity check of every byte.
  const offsets=[...new Set(Array.from({length:8},(_,index)=>Math.floor(Math.max(0,blob.size-SAMPLE_BYTES)*index/7)))];
  const hash=sha256.create();hash.update(new TextEncoder().encode(JSON.stringify([blob.size,offsets])));
  for(const offset of offsets)hash.update(new Uint8Array(await blob.slice(offset,offset+SAMPLE_BYTES).arrayBuffer()));
  return asHex(hash.digest());
}
function fileTimestamp(blob:Blob):number|undefined {
  return typeof File!=='undefined'&&blob instanceof File&&Number.isSafeInteger(blob.lastModified)&&blob.lastModified>0?blob.lastModified:undefined;
}
function receiptFor(blob:Blob,expectedSha256:string,sampleSha256:string,verifiedAt:number):ModelVerification|undefined {
  const lastModified=fileTimestamp(blob);
  return lastModified===undefined?undefined:{version:1,sha256:expectedSha256,size:blob.size,lastModified,sampleSha256,verifiedAt};
}
export async function verifyStoredModel(blob:Blob,expectedSha256:string,onProgress:(message:string)=>void=()=>{}):Promise<'full'|'cached'> {
  const expected=expectedSha256.toLowerCase();
  // Storage receipt failures only disable the fast path; never block a full check.
  const metadata=await modelMetadata().catch(()=>undefined);
  const cacheable=metadata?.backend==='opfs'&&typeof metadata.storageKey==='string'&&Boolean(metadata.storageKey)&&metadata.sha256===expected&&metadata.size===blob.size&&fileTimestamp(blob)!==undefined;
  const sample=cacheable?await sampleDigest(blob):undefined;
  const receipt=metadata?.verification;const now=Date.now();
  if(cacheable&&receipt?.version===1&&receipt.sha256===expected&&receipt.size===blob.size&&receipt.lastModified===fileTimestamp(blob)&&receipt.sampleSha256===sample&&Number.isFinite(receipt.verifiedAt)&&now>=receipt.verifiedAt&&now-receipt.verifiedAt<MODEL_RECHECK_INTERVAL_MS) {
    onProgress('저장된 모델 확인 완료 · 이전 검증 결과를 재사용해요');
    return 'cached';
  }
  onProgress('저장된 모델 전체 검사 중 · 완료 후 다음 실행에서 검증 결과를 재사용해요');
  if(await digestBlob(blob)!==expected)throw new Error('저장된 모델 검증에 실패했어요. 모델을 다시 다운로드해 주세요.');
  const verified=cacheable&&sample?receiptFor(blob,expected,sample,now):undefined;
  if(verified)await rememberModelVerification(metadata!,verified).catch(()=>{});
  return 'full';
}
async function rootDirectory() { return navigator.storage.getDirectory(); }
export async function installedModel(): Promise<{ metadata: InstalledModel; blob: Blob } | undefined> {
  const metadata = await modelMetadata() as InstalledModel | undefined;
  if (!metadata) return;
  try {
    const blob = metadata.backend === 'opfs' ? await (await (await rootDirectory()).getFileHandle(metadata.storageKey)).getFile() : await getModelBlob();
    if (!blob || blob.size !== metadata.size) return;
    return { metadata, blob };
  } catch { return; }
}
export async function installModel(source: Blob | ReadableStream<Uint8Array>, options: {
  name: string; size: number; sha256: string; signal: AbortSignal; onProgress: (text: string) => void;
}) {
  if (!/^[a-f0-9]{64}$/i.test(options.sha256) || !Number.isSafeInteger(options.size) || options.size <= 0) throw new Error('올바른 SHA-256과 파일 크기가 필요해요.');
  if (!options.name.endsWith('.litertlm')) throw new Error('웹 호환 .litertlm 모델을 선택해 주세요.');
  const estimate = await navigator.storage.estimate();
  if (estimate.quota && options.size > estimate.quota - (estimate.usage ?? 0)) throw new Error('저장 공간이 부족해요. 이전 모델이나 기기 파일을 정리해 주세요.');
  const previous = await modelMetadata() as InstalledModel | undefined;
  const storageKey = `fieldguard-model-${crypto.randomUUID()}.litertlm`;
  let directory: FileSystemDirectoryHandle | undefined;
  try { directory = await rootDirectory(); } catch { /* IndexedDB fallback below. */ }
  if (!directory && options.size > 512*1024*1024) throw new Error('이 브라우저는 대형 모델 파일 저장을 지원하지 않아요. OPFS 지원 브라우저를 사용해 주세요.');
  const hash = sha256.create(); let bytes = 0;
  const reader = (source instanceof Blob ? source.stream() : source).getReader();
  const parts: BlobPart[] = [];
  let writer: FileSystemWritableFileStream | undefined;
  try {
    if (directory) writer = await (await directory.getFileHandle(storageKey,{create:true})).createWritable();
    while (true) {
      options.signal.throwIfAborted();
      const { done, value } = await reader.read(); if (done) break;
      bytes += value.byteLength;
      if (bytes > options.size) throw new Error('지정한 파일 크기보다 커서 설치를 중단했어요.');
      hash.update(value);
      if (writer) await writer.write(value as Uint8Array<ArrayBuffer>); else parts.push(value.slice().buffer);
      options.onProgress(`모델 저장 중 · ${Math.round(bytes/options.size*100)}%`);
    }
    if (bytes !== options.size || asHex(hash.digest()) !== options.sha256.toLowerCase()) throw new Error('크기 또는 SHA-256이 일치하지 않아요. 불완전한 파일은 설치하지 않았어요.');
    options.signal.throwIfAborted();
    if (writer) { await writer.close(); writer = undefined; }
    // Metadata is the commit point. An interrupted write is never treated as an installed model.
    // The first worker load verifies the saved bytes before creating its receipt.
    await commitModel({ name: options.name, size: bytes, sha256: options.sha256.toLowerCase(), installedAt: new Date().toISOString(), backend: directory ? 'opfs' : 'idb', storageKey } satisfies InstalledModel, directory ? undefined : new Blob(parts));
  } catch (error) {
    await writer?.abort().catch(() => {});
    await directory?.removeEntry(storageKey).catch(() => {});
    throw error;
  } finally { await reader.cancel().catch(() => {}); }
  if (previous?.backend === 'opfs' && previous.storageKey) await directory?.removeEntry(previous.storageKey).catch(() => {});
}
export async function removeModel() {
  const previous = await modelMetadata() as InstalledModel | undefined;
  await removeModelData();
  if (previous?.backend === 'opfs') await (await rootDirectory()).removeEntry(previous.storageKey).catch(() => {});
}
