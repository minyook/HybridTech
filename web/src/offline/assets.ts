import type { AssetManifest, Asset } from '../types';

export const assetUrl = (path: string) => new URL(import.meta.env.BASE_URL + path, location.href).href;
let manifestPromise: Promise<AssetManifest> | undefined;
export function manifest(): Promise<AssetManifest> {
  return manifestPromise ??= fetch(assetUrl('asset-manifest.json')).then(async response => {
    if (!response.ok) throw new Error('문서 패키지 정보를 불러오지 못했어요.');
    return response.json();
  }).catch(error => { manifestPromise = undefined; throw error; });
}
export const cacheName = (version: string) => `fieldguard-assets-${version}`;
export function hex(buffer: ArrayBuffer) { return [...new Uint8Array(buffer)].map(value => value.toString(16).padStart(2,'0')).join(''); }
export async function verifyAsset(asset: Asset, buffer: ArrayBuffer) {
  if (buffer.byteLength !== asset.size || hex(await crypto.subtle.digest('SHA-256',buffer)) !== asset.sha256) throw new Error(`${asset.path}: 파일 검증에 실패했어요. 다시 준비해 주세요.`);
}
export async function preparedGroups(): Promise<Record<Asset['group'], boolean>> {
  const catalog = await manifest(); const cache = await caches.open(cacheName(catalog.version));
  const status = { knowledge: true, pdf: true, runtime: true };
  for (const asset of catalog.assets) {
    const response = await cache.match(assetUrl(asset.path));
    if (!response || response.headers.get('X-FieldGuard-SHA256') !== asset.sha256 || response.headers.get('X-FieldGuard-Size') !== String(asset.size)) status[asset.group] = false;
  }
  return status;
}
export async function prepareAssets(groups: Asset['group'][], onProgress: (text: string) => void, signal?: AbortSignal) {
  const catalog = await manifest(); const cache = await caches.open(cacheName(catalog.version));
  const assets = catalog.assets.filter(asset => groups.includes(asset.group));
  let finished = 0; const total = assets.reduce((sum,asset) => sum+asset.size,0);
  for (const asset of assets) {
    signal?.throwIfAborted();
    const existing = await cache.match(assetUrl(asset.path));
    if (existing) {
      try {
        const buffer = await existing.arrayBuffer(); await verifyAsset(asset,buffer);
        await cache.put(assetUrl(asset.path),new Response(buffer,{headers:{'Content-Type':existing.headers.get('Content-Type')??'application/octet-stream','X-FieldGuard-SHA256':asset.sha256,'X-FieldGuard-Size':String(asset.size)}}));
        finished += asset.size; continue;
      } catch { await cache.delete(assetUrl(asset.path)); }
    }
    onProgress(`${asset.group === 'runtime' ? 'AI 실행 파일' : asset.group === 'pdf' ? 'PDF' : '문서'} 준비 중 · ${Math.round(finished/total*100)}%`);
    const response = await fetch(assetUrl(asset.path), { signal, cache: 'no-store' });
    if (!response.ok) throw new Error('준비 파일을 내려받지 못했어요. 연결 후 다시 시도해 주세요.');
    const buffer = await response.arrayBuffer();
    await verifyAsset(asset,buffer); signal?.throwIfAborted();
    await cache.put(assetUrl(asset.path), new Response(buffer, { headers: { 'Content-Type': response.headers.get('Content-Type') ?? 'application/octet-stream', 'X-FieldGuard-SHA256':asset.sha256,'X-FieldGuard-Size':String(asset.size) } }));
    finished += asset.size;
  }
  onProgress('파일 검증 완료');
}
export async function loadKnowledge() {
  // Verify the text package as well; online use does not imply offline readiness.
  const catalog = await manifest(); const asset = catalog.assets.find(asset => asset.group === 'knowledge');
  if (!asset) throw new Error('문서 패키지가 없어요.');
  const cache = await caches.open(cacheName(catalog.version));
  let response = await cache.match(assetUrl(asset.path));
  if (!response) response = await fetch(assetUrl(asset.path));
  if (!response.ok) throw new Error('문서를 불러오지 못했어요. 인터넷 연결 후 다시 열어 주세요.');
  const buffer = await response.arrayBuffer(); await verifyAsset(asset,buffer);
  await cache.put(assetUrl(asset.path),new Response(buffer,{headers:{'Content-Type':'application/json','X-FieldGuard-SHA256':asset.sha256,'X-FieldGuard-Size':String(asset.size)}}));
  const data = JSON.parse(new TextDecoder().decode(buffer));
  return data.chunks.map((chunk: Record<string, unknown>) => ({ ...chunk, id: `${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}` }));
}
export async function loadSemanticIndex():Promise<import('../rag/hybrid').SemanticIndex>{
  const catalog=await manifest(),asset=catalog.assets.find(a=>a.path==='knowledge/semantic-index-v1.json');
  if(!asset)throw Error('의미 검색 문서 인덱스가 없어요.');
  const cache=await caches.open(cacheName(catalog.version));let response=await cache.match(assetUrl(asset.path));
  if(!response)response=await fetch(assetUrl(asset.path));if(!response.ok)throw Error('의미 검색 문서를 읽지 못했어요.');
  const buffer=await response.arrayBuffer();await verifyAsset(asset,buffer);await cache.put(assetUrl(asset.path),new Response(buffer,{headers:{'Content-Type':'application/json','X-FieldGuard-SHA256':asset.sha256,'X-FieldGuard-Size':String(asset.size)}}));
  const data=JSON.parse(new TextDecoder().decode(buffer));
  if(data.modelRevision!=='5090578d9565bb06545b4552f76e6bc2c93e4a66'||data.dimensions!==768)throw Error('의미 검색 문서 버전이 다릅니다.');
  return data;
}
export async function storageInfo() {
  const estimate = await navigator.storage?.estimate();
  return { usage: estimate?.usage ?? 0, quota: estimate?.quota ?? 0, persisted: await navigator.storage?.persisted?.() ?? false };
}
export async function registerOffline() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return;
  const registration = await navigator.serviceWorker.register(assetUrl('sw.js'), { scope: import.meta.env.BASE_URL });
  return registration;
}
