// Bound input buffers rather than relying on the browser's Blob.stream chunk size.
export const MODEL_READ_CHUNK_BYTES=1024*1024;
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
