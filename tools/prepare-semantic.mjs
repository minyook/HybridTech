// Pinned Apache-2.0 assets; all question inference is local.
import {mkdir,readFile,writeFile,copyFile,unlink} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const destinations=process.argv.includes('--android-only')?['app/src/main/assets/semantic-model']:['app/src/main/assets/semantic-model','web/public/semantic-model'];
const revision='5090578d9565bb06545b4552f76e6bc2c93e4a66';
const source='onnx-community/embeddinggemma-300m-ONNX';
const expected=JSON.parse(await readFile(new URL('./semantic-model-files.json',import.meta.url),'utf8'));
const sha=b=>createHash('sha256').update(b).digest('hex');
for(const [name,{size,sha256}] of Object.entries(expected)){
 const target=path.join(root,'tmp/embedgemma',name);await mkdir(path.dirname(target),{recursive:true});
 let data=await readFile(target).catch(()=>null);
 if(!data||data.length!==size||sha(data)!==sha256){const r=await fetch('https://huggingface.co/'+source+'/resolve/'+revision+'/'+name);if(!r.ok)throw Error(name+': '+r.status);data=Buffer.from(await r.arrayBuffer());if(data.length!==size||sha(data)!==sha256)throw Error('Embedding integrity: '+name);await writeFile(target,data);}
 for(const directory of destinations){const dest=path.join(root,directory,name);await mkdir(path.dirname(dest),{recursive:true});await copyFile(target,dest);}
}
const catalog={version:2,model:'embeddinggemma-300m-q4',revision,source,dimensions:768,pooling:'sentence_embedding',maxQueryTokens:256,files:Object.entries(expected).map(([name,meta])=>({name,...meta}))};
for(const directory of destinations){
 for(const obsolete of ['sentencepiece.bpe.model','tokenizer.onnx','onnx/model_quantized.onnx'])await unlink(path.join(root,directory,obsolete)).catch(()=>{});
 await writeFile(path.join(root,directory,'manifest.json'),JSON.stringify(catalog,null,2));
}
console.log('Prepared pinned EmbeddingGemma 300M q4; SHA-256 verified.'+(process.argv.includes('--android-only')?' Android only.':''));
