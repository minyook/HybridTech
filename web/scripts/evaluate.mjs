import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createServer } from 'vite';
const server=await createServer({server:{middlewareMode:true},appType:'custom'});
try {
const { retrieve }=await server.ssrLoadModule('/src/rag/search.ts');
const evaluation=JSON.parse(await readFile(new URL('../tests/evaluation.json',import.meta.url),'utf8'));
const data=JSON.parse(await readFile(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8'));
const chunks=data.chunks.map(chunk=>({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}`}));
const results=evaluation.map((item,index)=>{
  const sources=retrieve(item.question,chunks).slice(0,3);
  const pages=sources.filter(source=>source.chunk.documentId==='confined_space_safety_guide_2022').map(source=>source.chunk.pageNumber);
  return {...item,category:index<18?'single':index<26?'compound':'out-of-scope',retrieved:sources.map(source=>({documentId:source.chunk.documentId,page:source.chunk.pageNumber})),passed:item.pages.length?item.pages.every(group=>group.some(page=>pages.includes(page))):sources.length===0};
});
const report={date:new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Seoul',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()),kind:'local retrieval regression; not generated-answer or real-device validation',total:results.length,passed:results.filter(item=>item.passed).length,results};
await mkdir(new URL('../docs/',import.meta.url),{recursive:true});
await writeFile(new URL('../docs/evaluation-results.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
console.log(`Retrieval regression: ${report.passed}/${report.total}`);
for(const result of results.filter(item=>!item.passed)) console.log(result.question,result.retrieved);
if(report.passed!==report.total) process.exitCode=1;
} finally {
  await server.close();
}
