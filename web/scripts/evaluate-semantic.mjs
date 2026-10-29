// Actual pinned CPU inference, production retrieval code, original source corpus.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import ts from 'typescript';
import {env,AutoTokenizer} from '@huggingface/transformers';
import ort from 'onnxruntime-node';
const root=fileURLToPath(new URL('../../',import.meta.url));
const scratch=root+'tmp/semantic-evaluation/';await mkdir(scratch,{recursive:true});
for(const name of ['text','evidence','search','semantic-passages','hybrid','answer-plan']){
 const source=await readFile(root+`web/src/rag/${name}.ts`,'utf8');
 await writeFile(scratch+name+'.mjs',ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText.replace(/from '(\.\/[^']+)'/g,"from '$1.mjs'"));
}
const {resolveHybrid}=await import(new URL('../../tmp/semantic-evaluation/hybrid.mjs',import.meta.url));
const {retrieve}=await import(new URL('../../tmp/semantic-evaluation/search.mjs',import.meta.url));
const {createAnswerPlan}=await import(new URL('../../tmp/semantic-evaluation/answer-plan.mjs',import.meta.url));
const raw=await readFile(root+'app/src/main/assets/knowledge/seed_chunks_v1.json');
const chunks=JSON.parse(raw).chunks.map(c=>({...c,id:`${c.documentId}:${c.pageNumber}:${c.chunkIndex}`}));
const index=JSON.parse(await readFile(root+'app/src/main/assets/knowledge/semantic-index-v1.json','utf8'));
if(createHash('sha256').update(raw).digest('hex')!==index.corpusSha256)throw Error('Stale semantic corpus');
env.allowRemoteModels=false;env.allowLocalModels=true;env.useFSCache=false;env.localModelPath=root+'tmp/';
const tokenizer=await AutoTokenizer.from_pretrained('embedgemma',{local_files_only:true});
const model=await ort.InferenceSession.create(root+'tmp/embedgemma/onnx/model_q4.onnx',{intraOpNumThreads:2});
const results=[],vectors=[],groups={};const normalize=t=>t.normalize('NFKC').toLowerCase().replace(/[\s\\*_`#]/g,'');
for(const file of ['evaluation-150.json','focused-retrieval.json','deep-questions.json']){
 const bytes=await readFile(root+'web/tests/'+file),cases=JSON.parse(bytes).cases;let before=0,after=0,bodyPass=0,wrong=0,clarifications=0;
 for(const item of cases){
  const start=performance.now(),input=await tokenizer('task: search result | query: '+item.question);const output=await model.run(Object.fromEntries(Object.entries(input).map(([k,v])=>[k,new ort.Tensor('int64',v.data,v.dims)])));const vector=Array.from(output.sentence_embedding.data);
  const resolution=resolveHybrid(item.question,chunks,index,vector),old=retrieve(item.question,chunks);const body=resolution.sources.length?createAnswerPlan(item.question,resolution.sources).body:resolution.message;
  const matches=sources=>!item.pages.length?!sources.length:item.pages.every(p=>Array.isArray(p)?sources.some(s=>p.includes(s.chunk.pageNumber)):sources.some(s=>s.chunk.pageNumber===p));
  const oldPass=matches(old),pagePass=matches(resolution.sources);
  const failures=[...(item.required??[]).filter(s=>!normalize(body).includes(normalize(s))).map(s=>'missing: '+s),...(item.forbidden??[]).filter(s=>normalize(body).includes(normalize(s))).map(s=>'forbidden: '+s)];
  if(item.first&&!normalize(body.slice(0,400)).includes(normalize(item.first)))failures.push('no direct answer');
  if(oldPass)before++;if(pagePass)after++;if(pagePass&&!failures.length)bodyPass++;if(!pagePass&&resolution.sources.length)wrong++;if(resolution.status==='clarify')clarifications++;
  results.push({set:file,question:item.question,before:old.map(s=>s.chunk.pageNumber),after:resolution.sources.map(s=>s.chunk.pageNumber),status:resolution.status,pagePass,failures,millis:performance.now()-start});
  if(file==='deep-questions.json')vectors.push({question:item.question,vector});
 }
 groups[file]={total:cases.length,beforePagePass:before,afterPagePass:after,pageAndBodyPass:bodyPass,nonmatchingAnswer:wrong,clarifications,rubricSha256:createHash('sha256').update(bytes).digest('hex')};console.log(file,groups[file]);
}
await model.release();
await writeFile(scratch+'deep-vectors.json',JSON.stringify(vectors));
await writeFile(scratch+'report.json',JSON.stringify({platform:'PC ONNX CPU; no device or generation measurement',scope:'development regression/challenge sets, not independent holdout; heuristic clarification is not proof of absence',modelRevision:index.modelRevision,corpusSha256:index.corpusSha256,groups,results},null,2));
