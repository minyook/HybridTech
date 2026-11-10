/* 소스 안내
 * SDK가 기대하는 ReadableStream 데이터 읽기 인터페이스를 어댑트한다. 범위/수명 계약을 모델 스트림 경로와 맞춘다.
 * 연결 모듈: ../../node_modules/@litert-lm/core/dist/readable_stream_data_stream_wrapper.js, ./file-model-reader, ./model-stream.
 */
// This exact SDK-internal boundary is redirected only by build-ai-worker.mjs.
// Keep the upstream implementation for unregistered streams (desktop/default).
import { ReadableStreamDataStreamWrapper as UpstreamReader } from '../../node_modules/@litert-lm/core/dist/readable_stream_data_stream_wrapper.js';
import { FileModelReader } from './file-model-reader';
import { localModelSource,recordLocalModelReader } from './model-stream';

export class ReadableStreamDataStreamWrapper {
  private readonly reader:UpstreamReader|FileModelReader;
  constructor(stream:ReadableStream<Uint8Array>,getHeap:()=>Uint8Array,bytesPerSlice?:number){
    const file=localModelSource(stream);
    this.reader=file?new FileModelReader(file,getHeap):new UpstreamReader(stream,getHeap,bytesPerSlice);
    if(this.reader instanceof FileModelReader)recordLocalModelReader(stream,this.reader.stats);
  }
  readAndDiscard(destination:number,offset:number|bigint,count:number|bigint){return this.reader.readAndDiscard(destination,offset,count);}
  readAndPreserve(destination:number,offset:number|bigint,count:number|bigint){return this.reader.readAndPreserve(destination,offset,count);}
  discard(offset:number|bigint,count:number|bigint){return this.reader.discard(offset,count);}
}
