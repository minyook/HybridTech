// PC-only evaluation helper. Android's own SemanticTokenizer exports the exact input IDs.
// Reuses an installed ONNX Node runtime; never downloads models or modifies web/.
import {readFile, writeFile, rename} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {createHash} from 'node:crypto';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const output=resolve(process.argv[2]);
const require=createRequire(import.meta.url);
const ort=require(require.resolve('onnxruntime-node',{paths:[root,resolve(root,'web')]}));
const assets=resolve(root,'app/src/main/assets/semantic-model');
const manifest=JSON.parse(await readFile(resolve(assets,'manifest.json'),'utf8'));
for(const entry of manifest.files.filter(f=>f.name.startsWith('onnx/')||f.name==='tokenizer.json')) {
  const bytes=await readFile(resolve(assets,entry.name));
  if(bytes.length!==entry.size||createHash('sha256').update(bytes).digest('hex')!==entry.sha256)throw Error('Model asset verification failed: '+entry.name);
}
const inputs=JSON.parse(await readFile(resolve(output,'tokenized-queries.json'),'utf8'));
const started=performance.now();
const model=await ort.InferenceSession.create(resolve(assets,'onnx/model_q4.onnx'),{intraOpNumThreads:2,interOpNumThreads:1,enableMemPattern:false,executionProviders:['cpu']});
const loadMs=performance.now()-started;
const queries=[];
try {
  for(const item of inputs) {
    const ids=BigInt64Array.from(item.ids,BigInt), mask=BigInt64Array.from(item.ids,()=>1n);
    const t=performance.now();
    const result=await model.run({input_ids:new ort.Tensor('int64',ids,[1,ids.length]),attention_mask:new ort.Tensor('int64',mask,[1,ids.length])});
    const raw=Array.from(result.sentence_embedding.data),norm=Math.sqrt(raw.reduce((sum,v)=>sum+v*v,0));
    if(raw.length!==768||!Number.isFinite(norm)||norm<=0)throw Error('Invalid embedding: '+item.question);
    queries.push({question:item.question,vector:raw.map(v=>Math.fround(v/norm)),inferenceMs:performance.now()-t,tokens:ids.length});
    const progress=JSON.parse(await readFile(resolve(output,'progress.json'),'utf8'));
    progress.encoded=queries.length;progress.embeddingQuestion=item.question;
    await writeFile(resolve(output,'progress.json.part'),JSON.stringify(progress));
    for(let attempt=0;;attempt++)try {
      await rename(resolve(output,'progress.json.part'),resolve(output,'progress.json'));break;
    }catch(error){if(attempt===49)throw error;await new Promise(done=>setTimeout(done,20));}
    console.log(`[embedding ${queries.length}/${inputs.length}] ${item.question}`);
  }
} finally {await model.release();}
await writeFile(resolve(output,'query-vectors.json'),JSON.stringify({metadata:{platform:'PC ONNX CPU',onnxRuntime:ort.env.versions,modelRevision:manifest.revision,exactAndroidTokenizerIds:true,sessionReuse:true,loadMs,queryCount:queries.length,warning:'Android uses ONNX Runtime 1.22; PC runtime version recorded above. Inference vectors are computed before Kotlin evaluation; timing is not device wall-clock latency.'},queries}));
