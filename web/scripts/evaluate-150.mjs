import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { createServer } from 'vite';
const baseline=process.argv.includes('--baseline');
const server=await createServer({server:{middlewareMode:true},appType:'custom'});
try{
  const {retrieve}=await server.ssrLoadModule('/src/rag/search.ts');
  const {evidenceAnswer}=await server.ssrLoadModule('/src/rag/evidence.ts');
  const planner=baseline?undefined:await server.ssrLoadModule('/src/rag/answer-plan.ts');
  const corpus=await readFile(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8');
  const rubric=await readFile(new URL('../tests/evaluation-150.json',import.meta.url),'utf8');
  const dataset=JSON.parse(rubric),raw=JSON.parse(corpus);
  const chunks=raw.chunks.map(chunk=>({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}`}));
  const norm=text=>text.normalize('NFKC').replace(/[\s\\*_`#]/g,'').toLowerCase();
  const sourceText=page=>chunks.filter(chunk=>chunk.documentId===dataset.documentId&&chunk.pageNumber===page).map(chunk=>chunk.body).join('\n');
  // Validate expectations against original pages, never against search results.
  const invalid=dataset.cases.flatMap(item=>item.required.filter(clause=>!item.pages.flat().some(page=>norm(sourceText(page)).includes(norm(clause)))).map(clause=>({id:item.id,clause})));
  if(invalid.length)throw Error(`Rubric clauses absent from their source pages: ${JSON.stringify(invalid)}`);
  const results=dataset.cases.map(item=>{
    const start=performance.now(),sources=retrieve(item.question,chunks),retrievalMs=performance.now()-start;
    const begin=performance.now(),answer=planner?planner.createAnswerPlan(item.question,sources).body:evidenceAnswer(sources),compositionMs=performance.now()-begin;
    const pages=sources.filter(source=>source.chunk.documentId===dataset.documentId).map(source=>source.chunk.pageNumber);
    const retrievalPassed=item.pages.length?item.pages.every(group=>group.some(page=>pages.includes(page))):sources.length===0;
    const missing=item.required.filter(clause=>!norm(answer).includes(norm(clause)));
    const forbidden=item.forbidden.filter(clause=>norm(answer).includes(norm(clause)));
    return {id:item.id,question:item.question,category:item.category,expectedPages:item.pages,retrieved:sources.map(source=>({documentId:source.chunk.documentId,page:source.chunk.pageNumber})),retrievalPassed,bodyPassed:retrievalPassed&&!missing.length&&!forbidden.length,missing,forbidden,retrievalMs,compositionMs,generatedAnswer:'not-run',humanSemanticReview:'pending'};
  });
  const report={version:1,createdAt:new Date().toISOString(),variant:baseline?'before-sequential-generation':'grounded-answer-plan',corpusSha256:createHash('sha256').update(corpus).digest('hex'),rubricSha256:createHash('sha256').update(rubric).digest('hex'),rubricStatus:dataset.rubricStatus,measurement:'PC retrieval and deterministic source-body composition only. No GPU generation, iPhone execution, or clinical/legal certification.',total:results.length,retrievalPassed:results.filter(x=>x.retrievalPassed).length,bodyPassed:results.filter(x=>x.bodyPassed).length,results};
  await mkdir(new URL('../docs/',import.meta.url),{recursive:true});
  await writeFile(new URL(`../docs/evaluation-150-${baseline?'baseline':'after'}.json`,import.meta.url),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({variant:report.variant,total:report.total,retrievalPassed:report.retrievalPassed,bodyPassed:report.bodyPassed,rubricStatus:report.rubricStatus}));
}finally{await server.close();}
