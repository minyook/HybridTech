/* 소스 안내
 * 로컬 모델의 범위 읽기/ReadableStream을 구성하고 읽기 지표를 모은다. 파일 검증이나 질문 생성 정책과 별도 계층이다.
 */
// Bound input buffers rather than relying on the browser's Blob.stream chunk size.
export const MODEL_READ_CHUNK_BYTES=1024*1024;
export interface ModelReaderStats {readCount:number;bytesRead:number;maxReadBytes:number}
// A verified local file can be read at arbitrary offsets without retaining all
// preceding stream bytes. The worker bundle's pinned SDK adapter uses this map.
const localSources=new WeakMap<ReadableStream<Uint8Array>,Blob>();
const readerStats=new WeakMap<ReadableStream<Uint8Array>,ModelReaderStats>();
export const localModelSource=(stream:ReadableStream<Uint8Array>)=>localSources.get(stream);
export const localModelReadStats=(stream:ReadableStream<Uint8Array>)=>readerStats.get(stream);
export function recordLocalModelReader(stream:ReadableStream<Uint8Array>,stats:ModelReaderStats){readerStats.set(stream,stats);}
export function localModelStream(blob:Blob):ReadableStream<Uint8Array>{
  const stream=boundedModelStream(blob);localSources.set(stream,blob);return stream;
}
export function boundedModelStream(blob:Blob):ReadableStream<Uint8Array>{
  let offset=0;
  return new ReadableStream({
    type:'bytes',autoAllocateChunkSize:MODEL_READ_CHUNK_BYTES,
    async pull(controller:ReadableByteStreamController){
      if(offset>=blob.size){controller.close();controller.byobRequest?.respond(0);return;}
      const request=controller.byobRequest;
      const length=Math.min(MODEL_READ_CHUNK_BYTES,blob.size-offset,request?.view?.byteLength??MODEL_READ_CHUNK_BYTES);
      const bytes=new Uint8Array(await blob.slice(offset,offset+length).arrayBuffer());offset+=bytes.byteLength;
      if(request?.view){new Uint8Array(request.view.buffer,request.view.byteOffset,bytes.byteLength).set(bytes);request.respond(bytes.byteLength);}
      else controller.enqueue(bytes);
      if(offset>=blob.size)controller.close();
    },
  },{highWaterMark:0});
}
