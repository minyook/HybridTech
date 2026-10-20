import { expect,it,vi } from 'vitest';
import { Buffer } from 'node:buffer';
import { FileModelReader } from '../src/ai/file-model-reader';
import { boundedModelStream,localModelStream,MODEL_READ_CHUNK_BYTES as CHUNK } from '../src/ai/model-stream';
import { ReadableStreamDataStreamWrapper } from '../src/ai/sdk-stream-reader';

it('3GB 파일의 끝부분을 읽어도 앞부분을 순차 읽기하거나 캐시하지 않는다',async()=>{
  const slice=vi.fn((start:number,end:number)=>new Blob([Uint8Array.from({length:end-start},(_,index)=>(start+index)%251)]));
  const file={size:3_000_000_000,slice} as unknown as Blob;
  const heap=new Uint8Array(64),reader=new FileModelReader(file,()=>heap);
  expect(await reader.readAndPreserve(4,2_900_000_000n,17n)).toEqual({});
  expect(slice.mock.calls).toEqual([[2_900_000_000,2_900_000_017]]);
  expect([...heap.slice(4,21)]).toEqual(Array.from({length:17},(_,index)=>(2_900_000_000+index)%251));
  expect(reader.stats.bytesRead).toBe(17);
});

it('보존·되감기·폐기 후 읽기는 같은 로컬 바이트를 다시 읽고 앞선 데이터를 보관하지 않는다',async()=>{
  const file=new Blob([Uint8Array.from({length:100},(_,i)=>i)]),slice=vi.spyOn(file,'slice');
  const heap=new Uint8Array(50),reader=new FileModelReader(file,()=>heap);
  await reader.readAndPreserve(0,70,10);await reader.readAndDiscard(10,5,10);
  expect(reader.discard(70,10)).toEqual({});await reader.readAndPreserve(20,70,10);
  expect([...heap.slice(0,10)]).toEqual([...heap.slice(20,30)]);
  expect([...heap.slice(10,20)]).toEqual(Array.from({length:10},(_,i)=>i+5));
  expect(slice.mock.calls).toEqual([[70,80],[5,15],[70,80]]);
});

it('큰 C++ 읽기는 1MiB 이하로 나누어 WASM에 복사한다',async()=>{
  const data=Uint8Array.from({length:2*CHUNK+13},(_,i)=>i%251),file=new Blob([data]);
  const slice=vi.spyOn(file,'slice'),heap=new Uint8Array(data.length+8),reader=new FileModelReader(file,()=>heap);
  expect(await reader.readAndDiscard(8,0,data.length)).toEqual({});
  expect(Buffer.compare(heap.subarray(8),data)).toBe(0);expect(slice.mock.calls.every(([a=0,b=file.size])=>b-a<=CHUNK)).toBe(true);
  expect(reader.stats).toEqual({readCount:3,bytesRead:data.length,maxReadBytes:CHUNK});
});

it('파일 읽기를 기다리는 중 WASM 메모리가 커지면 새 힙에 기록한다',async()=>{
  const original=new Uint8Array(8);let heap=original;
  const file={size:3,slice:()=>({arrayBuffer:async()=>{heap=new Uint8Array(16);return new Uint8Array([1,2,3]).buffer;}})} as unknown as Blob;
  expect(await new FileModelReader(file,()=>heap).readAndDiscard(2,0,3)).toEqual({});
  expect([...heap.slice(2,5)]).toEqual([1,2,3]);expect([...original]).toEqual(Array(8).fill(0));
});

it('잘못된 파일 범위와 힙 범위는 파일을 읽지 않고 C++에 오류로 반환한다',async()=>{
  const file=new Blob(['model']),slice=vi.spyOn(file,'slice'),reader=new FileModelReader(file,()=>new Uint8Array(8));
  for(const [destination,offset,count] of [[0,-1,1],[0,4,2],[0,Number.MAX_SAFE_INTEGER+1,1],[7,0,2],[2**32,0,1]]){
    expect((await reader.readAndDiscard(destination,offset,count)).error).toBeInstanceOf(Error);
  }
  expect(reader.discard(0,6).error).toBeInstanceOf(Error);expect(slice).not.toHaveBeenCalled();
});

it('중첩 읽기와 파일 읽기 실패 후에도 다음 읽기가 정상 처리된다',async()=>{
  let finish!:(buffer:ArrayBuffer)=>void;
  const arrayBuffer=vi.fn().mockImplementationOnce(()=>new Promise<ArrayBuffer>(resolve=>{finish=resolve;})).mockRejectedValueOnce(Error('file unavailable')).mockResolvedValue(new Uint8Array([9]).buffer);
  const file={size:1,slice:()=>({arrayBuffer})} as unknown as Blob,heap=new Uint8Array(1),reader=new FileModelReader(file,()=>heap);
  const first=reader.readAndDiscard(0,0,1);
  expect((await reader.readAndDiscard(0,0,1)).error?.message).toContain('Concurrent');finish(new Uint8Array([7]).buffer);await first;
  expect((await reader.readAndDiscard(0,0,1)).error?.message).toBe('file unavailable');
  expect(await reader.readAndDiscard(0,0,1)).toEqual({});expect(heap[0]).toBe(9);
});

it('등록된 로컬 스트림만 파일 읽기를 사용하고 일반 스트림은 기존 SDK를 유지한다',async()=>{
  const file=new Blob(['0123456789']),slice=vi.spyOn(file,'slice'),heap=new Uint8Array(5);
  expect(await new ReadableStreamDataStreamWrapper(localModelStream(file),()=>heap).readAndPreserve(0,8,2)).toEqual({});
  expect([...heap.slice(0,2)]).toEqual([56,57]);expect(slice.mock.calls).toEqual([[8,10]]);
  slice.mockClear();const standard=new ReadableStreamDataStreamWrapper(boundedModelStream(file),()=>heap,10);
  expect(await standard.readAndDiscard(0,0,5)).toEqual({});expect([...heap]).toEqual([48,49,50,51,52]);
});
