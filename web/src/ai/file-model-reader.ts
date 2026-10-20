import { MODEL_READ_CHUNK_BYTES,type ModelReaderStats } from './model-stream';

interface ReadResult {error?:Error}

function offsetNumber(value:number|bigint){
  const number=Number(value);
  if(!Number.isSafeInteger(number)||number<0)throw new Error('Invalid model file range');
  return number;
}

// LiteRT-LM 0.18.0's C++ ReadableStreamDataStream protocol. Preserve means the
// bytes must be available for future reads, which the immutable Blob supplies.
// No sequential seek cache or model-sized JS buffer is needed for local files.
export class FileModelReader {
  private reading=false;
  readonly stats:ModelReaderStats={readCount:0,bytesRead:0,maxReadBytes:0};
  constructor(private readonly file:Blob,private readonly getHeap:()=>Uint8Array){}

  private range(offset:number|bigint,count:number|bigint){
    const start=offsetNumber(offset),length=offsetNumber(count);
    if(start>this.file.size||length>this.file.size-start)throw new Error('Model file range exceeds saved file');
    return {start,length};
  }
  private async read(destination:number,offset:number|bigint,count:number|bigint):Promise<ReadResult>{
    if(this.reading)return {error:new Error('Concurrent model file reads are not supported')};
    this.reading=true;
    try{
      const {start,length}=this.range(offset,count);
      // Emscripten may provide a signed i32 for a wasm32 pointer.
      if(!Number.isInteger(destination)||destination<-(2**31)||destination>0xffffffff)throw new Error('Invalid WASM destination');
      const address=destination>>>0;
      if(address>this.getHeap().byteLength||length>this.getHeap().byteLength-address)throw new Error('Model read exceeds WASM memory');
      for(let done=0;done<length;){
        const size=Math.min(MODEL_READ_CHUNK_BYTES,length-done);
        const bytes=new Uint8Array(await this.file.slice(start+done,start+done+size).arrayBuffer());
        if(bytes.byteLength!==size)throw new Error('Saved model file returned incomplete bytes');
        // Read the heap after every await: memory growth detaches old views.
        this.getHeap().set(bytes,address+done);
        done+=size;this.stats.readCount++;this.stats.bytesRead+=size;
        this.stats.maxReadBytes=Math.max(this.stats.maxReadBytes,size);
      }
      return {};
    }catch(error){return {error:error instanceof Error?error:new Error(String(error))};}
    finally{this.reading=false;}
  }
  readAndDiscard(destination:number,offset:number|bigint,count:number|bigint){return this.read(destination,offset,count);}
  readAndPreserve(destination:number,offset:number|bigint,count:number|bigint){return this.read(destination,offset,count);}
  discard(offset:number|bigint,count:number|bigint):ReadResult{
    try{this.range(offset,count);return {};}catch(error){return {error:error instanceof Error?error:new Error(String(error))};}
  }
}
