/* 소스 안내
 * 웹 로컬 STT에 필요한 고정 모델/Worker 자산을 준비한다. Android의 OS 로컬 음성 데이터는 이 파일로 설치하지 않는다.
 */
import {cp,mkdir,readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const directory=new URL('../public/voice-runtime/',import.meta.url);await mkdir(directory,{recursive:true});
const files=[];
for(const name of ['ort-wasm-simd-threaded.jsep.mjs','ort-wasm-simd-threaded.jsep.wasm']){
  const source=new URL(`../node_modules/onnxruntime-web/dist/${name}`,import.meta.url),target=new URL(name,directory);await cp(source,target);const data=await readFile(source);files.push({path:name,size:data.length,sha256:createHash('sha256').update(data).digest('hex')});
}
await writeFile(new URL('manifest.json',directory),JSON.stringify({version:'transformers-3.8.1-ort-1.22.0-dev.20250409-89f8206ba4',files},null,2));
console.log('Prepared local CPU speech runtime.');
