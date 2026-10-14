import 'fake-indexeddb/auto';
import { beforeEach, expect, it, vi } from 'vitest';
import { digestBlob, installedModel, installModel, removeModel } from '../src/ai/model';
import { modelMetadata, setSetting } from '../src/storage/db';
const payload=new Blob(['fieldguard-test-model']);
const options=async () => ({name:'test.litertlm',size:payload.size,sha256:await digestBlob(payload),signal:new AbortController().signal,onProgress:vi.fn()});
beforeEach(async () => {
  vi.stubGlobal('navigator',{storage:{estimate:async () => ({quota:1e9,usage:0}),getDirectory:async () => {throw new Error('no OPFS');}}});
  await removeModel();
});
it('검증을 통과한 파일만 설치하고 새로운 조회에서 복원한다',async () => {
  await installModel(payload,await options());
  expect((await installedModel())?.blob.size).toBe(payload.size);
  expect((await modelMetadata())?.sha256).toBe(await digestBlob(payload));
});
it('해시 불일치로 실패한 파일은 기존 모델을 덮어쓰지 않는다',async () => {
  await installModel(payload,await options());
  await expect(installModel(new Blob(['corrupt']),{...await options(),size:7})).rejects.toThrow('SHA-256');
  expect((await installedModel())?.blob.size).toBe(payload.size);
});
it('크기가 다른 부분 파일과 취소된 파일을 설치 완료로 표시하지 않는다',async () => {
  await expect(installModel(payload,{...await options(),size:payload.size+1})).rejects.toThrow();
  const abort=new AbortController(); abort.abort();
  await expect(installModel(payload,{...await options(),signal:abort.signal})).rejects.toThrow();
  expect(await modelMetadata()).toBeUndefined();
});
it('실제 파일이 없거나 잘리면 저장 메타데이터만으로 준비됨을 주장하지 않는다',async () => {
  await setSetting('model',{name:'missing.litertlm',size:100,backend:'opfs',storageKey:'missing'});
  expect(await installedModel()).toBeUndefined();
});
