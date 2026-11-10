/* 소스 안내
 * 웹 회귀 검사: 실제 고정 SDK에서 네이티브 Cache.add 네트워크 경로를 차단하고 입력 배치를 제한한다 / SDK 계약이 바뀌면 메모리 제한 없이 빌드하는 대신 명시적으로 실패한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다.
 * 검사 예: 실제 고정 SDK에서 네이티브 Cache.add 네트워크 경로를 차단하고 입력 배치를 제한한다, SDK 계약이 바뀌면 메모리 제한 없이 빌드하는 대신 명시적으로 실패한다
 * 실행: web/에서 npm test. 실제 네트워크/모델 검증과 mock·순수 검사의 범위는 각 fixture를 확인한다.
 */
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { expect, it } from 'vitest';
const source=readFileSync(new URL('../scripts/light-worker-bridge.mjs',import.meta.url),'utf8').replace('export function','function');
const bridge=runInNewContext(`${source}\nbridgeLightSDK`) as (source:string)=>string;
const sdk=readFileSync(new URL('../node_modules/@mlc-ai/web-llm/lib/index.js',import.meta.url),'utf8');
it('실제 고정 SDK에서 네이티브 Cache.add 네트워크 경로를 차단하고 입력 배치를 제한한다',()=>{
  const built=bridge(sdk);expect(built).not.toContain('yield this.cache.add(request);');expect(built).toContain('Math.min(metadata.prefill_chunk_size, 64)');
  expect(built).toContain('__fieldguardCountTokens = text => tokenizer.encode(text).length');
  // The embedding pipeline remains unchanged; this worker only uses an LLM.
  expect(built.match(/this.prefillChunkSize = metadata.prefill_chunk_size;/g)).toHaveLength(1);
});
it('SDK 계약이 바뀌면 메모리 제한 없이 빌드하는 대신 명시적으로 실패한다',()=>{
  expect(()=>bridge(sdk.replace('yield this.cache.add(request);','changedCacheAPI();'))).toThrow('contract changed');
});
