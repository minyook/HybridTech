import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createServer} from 'vite';
const before=process.argv.includes('--before');
const baselineCommit='7ed8418';
// Reconstruct the previous implementation with the SAME rubric and scoring
// rules. Never call the current implementation a historical baseline.
if(before){
  const directory=new URL('../.qa/focused-baseline/rag/',import.meta.url);
  await mkdir(directory,{recursive:true});
  for(const name of ['search','text','evidence','answer-plan'])await writeFile(new URL(`${name}.ts`,directory),execFileSync('git',['show',`${baselineCommit}:web/src/rag/${name}.ts`]));
}
const server=await createServer({server:{middlewareMode:true},appType:'custom'});
try {
  const path=before?'/.qa/focused-baseline/rag':'/src/rag';
  const {retrieve}=await server.ssrLoadModule(`${path}/search.ts`);
  const {createAnswerPlan}=await server.ssrLoadModule(`${path}/answer-plan.ts`);
  const corpus=await readFile(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8');
  const rubric=await readFile(new URL('../tests/focused-retrieval.json',import.meta.url),'utf8');
  const dataset=JSON.parse(rubric),chunks=JSON.parse(corpus).chunks.map(chunk=>({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}`}));
  const norm=text=>text.normalize('NFKC').replace(/[\s\\*_`#]/g,'').toLowerCase();
  const results=dataset.cases.map(item=>{
    const start=performance.now(),sources=retrieve(item.question,chunks),plan=createAnswerPlan(item.question,sources);
    const pages=sources.filter(source=>source.chunk.documentId===dataset.documentId).map(source=>source.chunk.pageNumber);
    const pagePassed=item.pages.length?item.pages.every(page=>pages.includes(page)):!sources.length;
    const firstPassed=!item.first||norm(plan.evidence.map(unit=>unit.text).join(' ').slice(0,400)).includes(norm(item.first));
    const missing=item.required.filter(clause=>!norm(plan.body).includes(norm(clause)));
    const forbidden=item.forbidden.filter(clause=>clause.startsWith('###')?plan.body.includes(clause):norm(plan.body).includes(norm(clause)));
    return {question:item.question,pages,expectedPages:item.pages,pagePassed,firstPassed,missing,forbidden,passed:pagePassed&&firstPassed&&!missing.length&&!forbidden.length,ms:performance.now()-start};
  });
  const report={createdAt:new Date().toISOString(),...(before?{baselineCommit}:{}),status:dataset.status,corpusSha256:createHash('sha256').update(corpus).digest('hex'),rubricSha256:createHash('sha256').update(rubric).digest('hex'),measurement:'PC source retrieval/composition only; first = within 400 source characters. No model generation or actual iPhone test.',total:results.length,pagePassed:results.filter(x=>x.pagePassed).length,passed:results.filter(x=>x.passed).length,results};
  await writeFile(new URL(`../docs/focused-retrieval-${before?'before':'after'}.json`,import.meta.url),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({total:report.total,pagePassed:report.pagePassed,passed:report.passed,failures:results.filter(x=>!x.passed)},null,2));
} finally {await server.close();}
