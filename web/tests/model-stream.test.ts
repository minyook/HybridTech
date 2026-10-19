import { createHash } from 'node:crypto';
import { expect, it, vi } from 'vitest';
import { boundedModelStream, MODEL_READ_CHUNK_BYTES as CHUNK } from '../src/ai/model-stream';
import { digestBlob } from '../src/ai/model';
const data=new Uint8Array(2*CHUNK+123).map((_,index)=>index%251);
it('큰 Blob의 기본 stream을 쓰지 않고 1MiB 이하 입력으로 같은 바이트를 읽는다',async()=>{
  const blob=new Blob([data]);const read=vi.spyOn(blob,'slice');vi.spyOn(blob,'stream').mockImplementation(()=>{throw Error('unbounded stream');});
  const reader=boundedModelStream(blob).getReader();const hash=createHash('sha256');let bytes=0;
  while(true){const part=await reader.read();if(part.done)break;expect(part.value.byteLength).toBeLessThanOrEqual(CHUNK);bytes+=part.value.byteLength;hash.update(part.value);}
  expect(bytes).toBe(data.length);expect(hash.digest('hex')).toBe(createHash('sha256').update(data).digest('hex'));
  expect(read.mock.calls.every(([start=0,end=blob.size])=>end-start<=CHUNK)).toBe(true);
});
it('SDK가 큰 BYOB 버퍼를 요청해도 한 번에 읽는 파일 범위를 제한한다',async()=>{
  const reader=boundedModelStream(new Blob([data])).getReader({mode:'byob'});let total=0;
  while(true){const part=await reader.read(new Uint8Array(10*CHUNK));if(part.done)break;expect(part.value.byteLength).toBeLessThanOrEqual(CHUNK);total+=part.value.byteLength;}
  expect(total).toBe(data.length);
});
it('무결성 검사도 1MiB씩 읽으며 중단 요청 후 파일을 읽지 않는다',async()=>{
  const blob=new Blob([data]);const slice=vi.spyOn(blob,'slice');
  expect(await digestBlob(blob)).toBe(createHash('sha256').update(data).digest('hex'));expect(slice.mock.calls.every(([a=0,b=blob.size])=>b-a<=CHUNK)).toBe(true);
  slice.mockClear();const controller=new AbortController();controller.abort();await expect(digestBlob(blob,controller.signal)).rejects.toThrow();expect(slice).not.toHaveBeenCalled();
});
