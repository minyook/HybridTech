import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {createServer} from 'vite';
import {fileURLToPath} from 'node:url';
const server=await createServer({server:{middlewareMode:true},appType:'custom'});
try{
  const {createAnswerPlan}=await server.ssrLoadModule('/src/rag/answer-plan.ts');
  const corpus=await readFile(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8');
  const chunks=JSON.parse(corpus).chunks.map(chunk=>({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}`}));
  const records=chunks.map(chunk=>({sourceId:chunk.id,documentId:chunk.documentId,page:chunk.pageNumber,sourceSha256:createHash('sha256').update(chunk.body).digest('hex'),reviewStatus:'pending-human-domain-review',units:createAnswerPlan('',[{chunk,score:1}]).evidence}));
  const output=new URL('../.local/knowledge-package-v1.json',import.meta.url);
  await mkdir(new URL('../.local/',import.meta.url),{recursive:true});
  await writeFile(output,JSON.stringify({schemaVersion:1,corpusSha256:createHash('sha256').update(corpus).digest('hex'),reviewStatus:'draft-not-approved-for-publication',records},null,2)+'\n');
  console.log(`Draft package: ${fileURLToPath(output)}; ${records.length} source pages. Human domain review required; not automatically deployed.`);
}finally{await server.close();}
