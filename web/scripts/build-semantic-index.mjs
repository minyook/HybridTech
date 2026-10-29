import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import ts from 'typescript';
import {env,AutoTokenizer} from '@huggingface/transformers';
import ort from 'onnxruntime-node';
const root=fileURLToPath(new URL('../../',import.meta.url));
const raw=await readFile(root+'app/src/main/assets/knowledge/seed_chunks_v1.json');
const corpusSha256=createHash('sha256').update(raw).digest('hex');
const modelRevision='5090578d9565bb06545b4552f76e6bc2c93e4a66';
const target=root+'app/src/main/assets/knowledge/semantic-index-v1.json';
const existing=await readFile(target,'utf8').then(JSON.parse).catch(()=>null);
if(existing?.passageVersion===3&&existing?.corpusSha256===corpusSha256&&existing?.modelRevision===modelRevision&&!process.argv.includes('--force')){console.log('Semantic index unchanged.');process.exit(0);}
await mkdir(root+'tmp/semantic',{recursive:true});
for(const name of ['text','evidence','semantic-passages']){
 const source=await readFile(root+`web/src/rag/${name}.ts`,'utf8');
 const output=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText.replace(/from '(\.\/[^']+)'/g,"from '$1.mjs'");
 await writeFile(root+`tmp/semantic/${name}.mjs`,output);
}
const {knowledgePassages}=await import(new URL('../../tmp/semantic/semantic-passages.mjs',import.meta.url));
const chunks=JSON.parse(raw).chunks.map(c=>({...c,id:`${c.documentId}:${c.pageNumber}:${c.chunkIndex}`}));
const passages=knowledgePassages(chunks);
env.allowRemoteModels=false;env.allowLocalModels=true;env.useFSCache=false;env.localModelPath=root+'tmp/';
const tokenizer=await AutoTokenizer.from_pretrained('embedgemma',{local_files_only:true});
const model=await ort.InferenceSession.create(root+'tmp/embedgemma/onnx/model_q4.onnx',{intraOpNumThreads:2});
const started=Date.now();
for(let i=0;i<passages.length;i++){
 const inputs=await tokenizer('title: '+passages[i].section+' | text: '+passages[i].text,{truncation:true,max_length:384});
 const output=await model.run(Object.fromEntries(Object.entries(inputs).map(([k,v])=>[k,new ort.Tensor('int64',v.data,v.dims)])));
 passages[i].vector=Array.from(output.sentence_embedding.data,x=>Number(x.toFixed(7)));
 if(i%100===0)console.log(`${i}/${passages.length}`);
}
await model.release();
await writeFile(target,JSON.stringify({version:1,passageVersion:3,corpusSha256,modelRevision,dimensions:768,passages}));
console.log(`Indexed ${passages.length} atomic passages locally in ${Date.now()-started}ms.`);
