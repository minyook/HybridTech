/* 소스 안내
 * Android 질문 평가 결과를 집계해 사람이 읽을 요약 자료로 만든다. 기록된 실행 조건을 새 측정으로 바꾸지 않는다.
 */
import {readFile,writeFile,copyFile,mkdir} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const directory=resolve(root,process.argv[2]||'tmp/evaluation-150-20261103');
const read=async name=>JSON.parse(await readFile(resolve(directory,name),'utf8'));
const [pc,additional,before,documents,full,gpu]=await Promise.all(['results.json','additional/results.json','device-before.json','device-final-documents.json','device-after-complete.json','device-gpu.json'].map(read));
if(pc.phase!=='complete'||additional.phase!=='complete'||documents.phase!=='complete'||full.phase!=='complete')throw Error('All evaluation runs must finish first');
const rubric=JSON.parse(await readFile(resolve(root,'app/src/androidTest/assets/rag-evaluation/evaluation-150.json'),'utf8'));
const normalize=s=>s.normalize('NFKC').toLowerCase().replace(/[\s\\*_`#]/g,'');
const grade=(row,index)=>{
 const item=rubric.cases[index],pages=row.sources?row.sources.filter(s=>s.documentId===rubric.documentId).map(s=>s.page):row.pages;
 const pagePass=item.pages.length?item.pages.every(p=>(Array.isArray(p)?p:[p]).some(n=>pages.includes(n))):!pages.length;
 const missing=(item.required||[]).filter(t=>!normalize(row.answerMarkdown).includes(normalize(t)));
 const forbidden=(item.forbidden||[]).filter(t=>normalize(row.answerMarkdown).includes(normalize(t)));
 return {number:row.number,pagePass,missing,forbidden,pass:pagePass&&!missing.length&&!forbidden.length};
};
const combined=[...documents.results,...full.results.filter(r=>r.number>140)].sort((a,b)=>a.number-b.number);
if(combined.length!==150||new Set(combined.map(r=>r.number)).size!==150)throw Error('Missing or duplicate device questions');
const quality=combined.map(r=>grade(r,r.number-1));
const quantile=(values,p)=>{const v=[...values].sort((a,b)=>a-b);return v.length?v[Math.min(v.length-1,Math.ceil(v.length*p)-1)]:null;};
const stats=values=>({count:values.length,medianMs:quantile(values,.5),p95Ms:quantile(values,.95),minMs:Math.min(...values),maxMs:Math.max(...values),within2s:values.filter(x=>x<=2000).length});
const docRows=documents.results;
const general=full.results.filter(r=>r.number>=141&&r.number<=149);
const beforeDocs=before.results.filter(r=>r.number<=140);
const matched=docRows.filter(r=>beforeDocs.some(b=>b.number===r.number));
const summary={date:'2026-11-03',platform:documents.platform,scope:'Android app search/answer-state measurements, not rendered UI/audio latency. Final document 140 and earlier general 9 runs are separate. General engine/prompt/settings unchanged, but the earlier general run predates the last search preprocessing optimization. Not one continuous final-code 150 run.',
 quality:{pc150:{passed:pc.passed,total:pc.total},additional24:{passed:additional.passed,total:additional.total,developmentRegression:true},deviceDocument140:{passed:quality.filter(r=>r.number<=140&&r.pass).length,total:140,fullyAnswered:docRows.filter(r=>r.status==='answered').length},generalClassification:{passed:quality.filter(r=>r.number>140&&r.pass).length,total:10,factualAccuracyNotEvaluated:true},failures:quality.filter(r=>!r.pass)},
 timing:{documentFirstBodyState:stats(docRows.map(r=>r.firstBodyStateMs)),documentCompletion:stats(docRows.map(r=>r.totalMs)),generalFirstBodyState:stats(general.filter(r=>r.firstBodyStateMs>=0).map(r=>r.firstBodyStateMs)),generalCompletion:stats(general.map(r=>r.totalMs)),generalGenerationFirstBodyAfterSearch:stats(general.map(r=>r.firstBodyStateMs-r.searchMs)),generalGenerationCompletion:stats(general.map(r=>r.generationMs)),generalErrors:general.filter(r=>r.generation==='error').map(r=>({number:r.number,error:r.error})),matchedSearchBefore:stats(beforeDocs.map(r=>r.searchMs)),matchedSearchAfter:stats(matched.map(r=>r.searchMs)),repeatedQuestions:docRows.filter(r=>r.immediateRepeatSearchAndCompositionMs!=null).map(r=>({number:r.number,millis:r.immediateRepeatSearchAndCompositionMs}))},
 gpuComparison:{requestedBackend:gpu.requestedBackend,observedBackend:'GPU; verified FieldGuardInference logs',note:'Two sequential development questions, cold initialization included; not a controlled CPU/GPU benchmark. Original CPU setting restored after test.',results:gpu.results.map(r=>({number:r.number,firstBodyStateMs:r.firstBodyStateMs,totalMs:r.totalMs,generationMs:r.generationMs,generation:r.generation,cpuEarlierTotalMs:general.find(x=>x.number===r.number)?.totalMs}))},
 inferenceSettings:{originalBackend:'CPU',contextTokens:8192,responseTokens:4096,generalOutputTokenCap:768,automaticAnswerLength:true},
 runs:{beforeCount:before.results.length,documentCount:docRows.length,generalCount:general.length,finalIndexPrepareMs:documents.indexPrepareMs,sourceAnswersWrittenToChatHistory:false},
 limits:['Fixed development regression sets; not an independent unseen-question accuracy score.','First-body state is computed at the answer state boundary, not an actual rendered frame.','One S24, fixed sequential question order; no controlled thermal/JIT/storage conditions or cross-device guarantee.','General-knowledge completion and visible output do not establish factual correctness.','Cold tokenizer/session startup is included in the first document question. Corpus/index startup preparation is recorded separately.']};
const native={platform:documents.platform,total:150,completed:150,phase:'complete',combinedRuns:true,scope:summary.scope,results:combined,quality};
await mkdir(resolve(root,'docs/validation'),{recursive:true});
await Promise.all([
 writeFile(resolve(root,'docs/validation/android-accuracy-latency-20261103.json'),JSON.stringify(summary,null,2)+'\n'),
 writeFile(resolve(root,'docs/validation/android-s24-latency-20261103.json'),JSON.stringify({summary,before,documents,generalResults:general,gpu,quality},null,2)+'\n'),
 writeFile(resolve(directory,'native-latency.json'),JSON.stringify(native,null,2)),
 copyFile(resolve(directory,'results.json'),resolve(root,'docs/validation/android-150-improved-20261103.json')),
 copyFile(resolve(directory,'results.md'),resolve(root,'docs/validation/android-150-improved-20261103.md')),
 copyFile(resolve(directory,'additional/results.json'),resolve(root,'docs/validation/android-additional-20261103.json')),
 copyFile(resolve(directory,'additional/results.md'),resolve(root,'docs/validation/android-additional-20261103.md')),
]);
const md=['# S24 실제 질문별 답변 시간 · 2026-11-03','',summary.scope,'','| 번호 | 질문 | 첫 본문(초) | 완료(초) | 문서 기준 |','|---|---|---:|---:|---|',...combined.map(r=>`| ${r.number} | ${r.question.replaceAll('|','\\|')} | ${(r.firstBodyStateMs/1000).toFixed(3)} | ${(r.totalMs/1000).toFixed(3)} | ${quality.find(q=>q.number===r.number).pass?'통과':'실패'} |`),'','문서 밖 141~150번의 통과는 잘못된 문서 연결/승인 단정을 막았는지 검사하며, 일반 지식의 사실 정확도 통과를 뜻하지 않습니다.','',...combined.flatMap(r=>[`## ${r.number}. ${r.question}`,'',`- 상태: ${r.status} / 생성: ${r.generation}`,`- 첫 본문: ${(r.firstBodyStateMs/1000).toFixed(3)}초 / 완료: ${(r.totalMs/1000).toFixed(3)}초`,'',r.answerMarkdown,''])].join('\n').trimEnd()+'\n';
await writeFile(resolve(root,'docs/validation/android-s24-answers-20261103.md'),md.replace(/[ \t]+$/gm,'').trimEnd()+'\n');
const seconds=ms=>(ms/1000).toFixed(3);
const metrics=`<!-- evaluation-metrics -->\n| 측정 | 실제 결과 |\n|---|---|\n| S24 최종 문서 질문 기준 통과 | ${summary.quality.deviceDocument140.passed}/140 |\n| 문서 첫 본문 중앙값 / 95백분위 | ${seconds(summary.timing.documentFirstBodyState.medianMs)}초 / ${seconds(summary.timing.documentFirstBodyState.p95Ms)}초 |\n| 문서 완료 중앙값 / 95백분위 | ${seconds(summary.timing.documentCompletion.medianMs)}초 / ${seconds(summary.timing.documentCompletion.p95Ms)}초 |\n| 문서 완료 2초 이내 / 최대 | ${summary.timing.documentCompletion.within2s}/140 · ${seconds(summary.timing.documentCompletion.maxMs)}초 |\n| 같은 26개 검색 시간 중앙값 전 → 후 | ${seconds(summary.timing.matchedSearchBefore.medianMs)}초 → ${seconds(summary.timing.matchedSearchAfter.medianMs)}초 |\n| CPU 일반 지식 9개 첫 본문 중앙값 | ${seconds(summary.timing.generalFirstBodyState.medianMs)}초 |\n| CPU 일반 지식 9개 완료 중앙값 / 최대 | ${seconds(summary.timing.generalCompletion.medianMs)}초 / ${seconds(summary.timing.generalCompletion.maxMs)}초 |\n\nCPU 일반 지식은 최종 검색 전처리 최적화 전 별도 실행이다. 생성 엔진/프롬프트/설정은 동일하다. GPU 우선 비교 2개는 실제 GPU 로그를 확인했으며 완료 시간은 ${gpu.results.map(r=>seconds(r.totalMs)+'초').join(', ')}였다. 이 비교로 GPU가 항상 더 빠르다고 판단할 수 없으며 원래 CPU 설정을 복원했다.\n<!-- /evaluation-metrics -->`;
const designFile=resolve(root,'docs/ANDROID_RAG_ACCURACY_LATENCY.md');
const design=await readFile(designFile,'utf8');
await writeFile(designFile,design.replace(/<!-- evaluation-metrics -->[\s\S]*?<!-- \/evaluation-metrics -->/,metrics));
console.log(JSON.stringify(summary,null,2));
