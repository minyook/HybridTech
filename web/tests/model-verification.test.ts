import 'fake-indexeddb/auto';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { digestBlob, installModel, MODEL_RECHECK_INTERVAL_MS, verifyStoredModel } from '../src/ai/model';
import { commitModel, modelMetadata, rememberModelVerification, removeModelData, setSetting } from '../src/storage/db';
import type { ModelRecord } from '../src/types';

const bytes=new Uint8Array(2*1024*1024).map((_,index)=>index%251);
const timestamp=1_790_000_000_000;
let file:File;let metadata:ModelRecord;
beforeEach(async()=>{
  file=new File([bytes],'model.litertlm',{lastModified:timestamp});
  metadata={name:'model.litertlm',size:file.size,sha256:await digestBlob(file),backend:'opfs',storageKey:'unique-installation',installedAt:new Date(timestamp).toISOString()};
  await removeModelData();await commitModel(metadata);
});
afterEach(()=>vi.restoreAllMocks());

it('기존 설치는 한 번 전체 검사하고 새 조회에서도 결과를 복원해 전체 파일 읽기를 생략한다',async()=>{
  const read=vi.spyOn(file,'stream');const fullRead=vi.spyOn(file,'slice');expect(await verifyStoredModel(file,metadata.sha256)).toBe('full');expect(read).not.toHaveBeenCalled();expect(fullRead.mock.calls.some(([start=0,end=file.size])=>end-start===1024*1024)).toBe(true);
  expect((await modelMetadata())?.verification?.sampleSha256).toMatch(/^[0-9a-f]{64}$/);
  const reopened=new File([bytes],'model.litertlm',{lastModified:timestamp});const secondRead=vi.spyOn(reopened,'stream');const samples=vi.spyOn(reopened,'slice');
  expect(await verifyStoredModel(reopened,metadata.sha256)).toBe('cached');expect(secondRead).not.toHaveBeenCalled();
  expect(samples.mock.calls.reduce((total,[start=0,end=reopened.size])=>total+Math.min(end-start,reopened.size-start),0)).toBeLessThanOrEqual(512*1024);
});
it('같은 크기의 수정 파일은 수정시각이 바뀌면 전체 검사하고 손상을 거부한다',async()=>{
  await verifyStoredModel(file,metadata.sha256);const changed=bytes.slice();changed[100_000]^=1;
  const modified=new File([changed],'model.litertlm',{lastModified:timestamp+1});const read=vi.spyOn(modified,'stream');
  await expect(verifyStoredModel(modified,metadata.sha256)).rejects.toThrow('검증에 실패');expect(read).not.toHaveBeenCalled();
});
it('크기·수정시각이 같아도 표본 구간이 다르면 전체 검사한다',async()=>{
  await verifyStoredModel(file,metadata.sha256);const changed=bytes.slice();changed[0]^=1;
  const modified=new File([changed],'model.litertlm',{lastModified:timestamp});
  await expect(verifyStoredModel(modified,metadata.sha256)).rejects.toThrow('검증에 실패');
});
it('표본 밖 손상도 검증 유효기간이 지나면 전체 검사로 발견한다',async()=>{
  await verifyStoredModel(file,metadata.sha256);const receipt=(await modelMetadata())!.verification!;
  const changed=bytes.slice();changed[100_000]^=1;const modified=new File([changed],'model.litertlm',{lastModified:timestamp});
  vi.spyOn(Date,'now').mockReturnValue(receipt.verifiedAt+MODEL_RECHECK_INTERVAL_MS);
  await expect(verifyStoredModel(modified,metadata.sha256)).rejects.toThrow('검증에 실패');
});
it('미래 시각·알 수 없는 버전의 검증 결과는 신뢰하지 않는다',async()=>{
  await verifyStoredModel(file,metadata.sha256);const current=(await modelMetadata())!;
  await setSetting('model',{...current,verification:{...current.verification,verifiedAt:Date.now()+60_000}});
  expect(await verifyStoredModel(file,metadata.sha256)).toBe('full');
  const renewed=(await modelMetadata())!;await setSetting('model',{...renewed,verification:{...renewed.verification,version:2}});
  expect(await verifyStoredModel(file,metadata.sha256)).toBe('full');
});
it('파일 수정시각을 확인할 수 없는 Blob·IndexedDB 저장은 전체 검사를 유지한다',async()=>{
  await verifyStoredModel(file,metadata.sha256);expect(await verifyStoredModel(new Blob([bytes]),metadata.sha256)).toBe('full');
  await commitModel({...metadata,backend:'idb'});expect(await verifyStoredModel(file,metadata.sha256)).toBe('full');
});
it('옛 검증 작업은 삭제·교체된 설치를 되살리거나 새 모델에 결과를 붙이지 않는다',async()=>{
  await verifyStoredModel(file,metadata.sha256);const receipt=(await modelMetadata())!.verification!;
  await removeModelData();await rememberModelVerification(metadata,receipt);expect(await modelMetadata()).toBeUndefined();
  await commitModel({...metadata,storageKey:'replacement'});await rememberModelVerification(metadata,receipt);
  expect((await modelMetadata())?.verification).toBeUndefined();
});
it('새 OPFS 설치는 첫 실행에서 저장된 바이트를 전체 검사한 뒤부터 결과를 재사용한다',async()=>{
  const entries=new Map<string,File>();
  const directory={
    getFileHandle:vi.fn(async(name:string)=>({
      createWritable:async()=>{const parts:BlobPart[]=[];return{write:async(part:Uint8Array)=>{parts.push(part.slice().buffer);},close:async()=>{entries.set(name,new File(parts,name,{lastModified:timestamp}));},abort:async()=>{}};},
      getFile:async()=>{const stored=entries.get(name);if(!stored)throw new Error('missing');return stored;},
    })),
    removeEntry:async(name:string)=>{entries.delete(name);},
  };
  vi.stubGlobal('navigator',{storage:{estimate:async()=>({quota:1e9,usage:0}),getDirectory:async()=>directory}});
  try {
    await removeModelData();await installModel(new Blob([bytes]),{name:'new.litertlm',size:bytes.length,sha256:metadata.sha256,signal:new AbortController().signal,onProgress:()=>{}});
    const installed=(await modelMetadata())!;expect(installed.verification).toBeUndefined();
    expect(await verifyStoredModel(entries.get(installed.storageKey)!,metadata.sha256)).toBe('full');
    expect((await modelMetadata())?.verification?.version).toBe(1);
    expect(await verifyStoredModel(entries.get(installed.storageKey)!,metadata.sha256)).toBe('cached');
  } finally {vi.unstubAllGlobals();}
});
