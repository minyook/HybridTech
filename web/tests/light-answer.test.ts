import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import type { Chunk, Source } from '../src/types';
import { retrieve } from '../src/rag/search';
import { evidenceUnits } from '../src/rag/evidence';
import { explainLightPart, joinLightAnswer, lightAnswerParts, missingLightFacts } from '../src/ai/light-answer';

const chunks:Chunk[]=JSON.parse(readFileSync(new URL('../../app/src/main/assets/knowledge/seed_chunks_v1.json',import.meta.url),'utf8')).chunks.map((chunk:Chunk)=>({...chunk,id:`${chunk.documentId}:${chunk.pageNumber}`}));
for(const question of ['산소 및 유해가스 측정방법','심폐소생술'])it(`${question}: 모든 원문 단위를 한 번씩 순서대로 배분하며 각 입력 예산을 지킨다`,()=>{
  const sources=retrieve(question,chunks),parts=lightAnswerParts(question,sources,text=>text.length,1100,512);
  expect(parts.length).toBeGreaterThan(1);expect(parts.flatMap(part=>part.units)).toEqual(sources.flatMap(evidenceUnits));
  for(const part of parts.filter(part=>part.generate))expect(part.prompt.length).toBeLessThanOrEqual(1100);
  const answer=joinLightAnswer(parts.map(part=>part.fallback));expect(answer).not.toMatch(/AI 정리|문서에서 확인한 상세 내용/);
  expect(answer).toContain(question==='심폐소생술'?'회복자세':'측정시 주의사항');
});
it('문맥에 들어가지 않는 큰 단위는 누락하거나 GPU에 제출하지 않고 같은 답변의 원문 표현으로 남긴다',()=>{
  const source={score:1,chunk:{id:'a',documentId:'g',documentTitle:'문서',pageNumber:1,sourceKind:'guide',chunkIndex:0,heading:'',body:'• '+'긴 설명 '.repeat(300)+'\n• 1m 눈금을 확인합니다.'}} as Source;
  const parts=lightAnswerParts('측정',[source],text=>text.length,500,256);
  expect(parts[0].generate).toBe(false);expect(parts.at(-1)?.generate).toBe(true);expect(parts.flatMap(part=>part.units)).toHaveLength(2);
});
it('문단에 있는 수치·단위와 119가 생성 설명에서 빠졌는지 확인한다',()=>{
  const units=[{text:'30회 압박 후 2회 호흡하고 119에 신고합니다.',section:'구조',order:0}];
  expect(missingLightFacts('30회 압박 후 2회 호흡하고 119에 신고합니다.',units)).toBe(false);
  expect(missingLightFacts('30회 압박 후 119에 신고합니다.',units)).toBe(true);
});
it('겹치는 제목과 같은 문단은 합치고 서로 다른 절차는 같은 단어가 있어도 유지한다',()=>{
  const answer=joinLightAnswer(['### 준비\n\n1. 장비를 점검합니다.','### 준비\n\n1. 장비를 점검합니다.\n\n2. 장비를 사용해 측정합니다.','### 주의사항\n\n1. 외부 감시인과 연락합니다.']);
  expect(answer.match(/### 준비/g)).toHaveLength(1);expect(answer.match(/장비를 점검합니다/g)).toHaveLength(1);
  expect(answer).toContain('장비를 사용해 측정합니다.');expect(answer).toContain('외부 감시인');
});
it('질문 자체가 예산을 넘으면 근거 없이 생성하지 않는다',()=>{
  expect(()=>lightAnswerParts('질문'.repeat(100),[],text=>text.length,100,256)).toThrow('질문이');
});
it('범위의 앞 숫자를 생략하거나 원문에 없는 수치를 추가하면 원문 보완이 필요하다',()=>{
  const units=[{text:'분당 100~120회 압박합니다.',section:'압박',order:0}];
  expect(missingLightFacts('분당 100~120회 압박합니다.',units)).toBe(false);
  expect(missingLightFacts('분당 120회 압박합니다.',units)).toBe(true);
  expect(missingLightFacts('분당 100~120회 압박하고 5회 쉽니다.',units)).toBe(true);
});
it('수치가 없어도 서로 다른 주요 절차를 일반적인 한 문장으로 줄이면 원문으로 보완한다',()=>{
  const units=[{text:'측정자의 머리가 개구면 안쪽으로 들어가지 않도록 합니다.',section:'주의사항',order:0},{text:'밀폐공간 외부에는 감시인을 배치합니다.',section:'주의사항',order:1}];
  expect(missingLightFacts('안전하게 작업하세요.',units)).toBe(true);
  expect(missingLightFacts(units.map(unit=>unit.text).join(' '),units)).toBe(false);
});
it('각 AI 설명은 자신의 문서 항목에만 대응하고 누락·다른 항목의 수치는 같은 위치에서 보완한다',()=>{
  const units=[{text:'황화수소는 10ppm 미만이어야 합니다.',section:'적정공기',order:0},{text:'작업을 다시 시작하기 전에 측정합니다.',section:'측정 시기',order:1}];
  const result=explainLightPart('[1] 황화수소는 작업을 다시 시작하기 전에 측정합니다.\n[2] 작업을 다시 시작하기 전에 측정합니다.',units);
  expect(result.supplemented).toBe(true);expect(result.answer).toContain('10ppm');
  expect(result.answer.match(/작업을 다시 시작하기 전에 측정합니다/g)).toHaveLength(1);
});
it('다른 섹션 뒤에 같은 주제가 다시 나오면 그 주제 아래로 합쳐 조건의 소속을 유지한다',()=>{
  const answer=joinLightAnswer(['### 측정 시기\n\n- 작업 전에 측정합니다.\n\n### 적정공기\n\n- 산소 기준을 확인합니다.','### 측정시기\n\n- 작업 재개 전에 측정합니다.']);
  expect(answer.match(/### 측정/g)).toHaveLength(1);
  expect(answer.indexOf('작업 재개')).toBeLessThan(answer.indexOf('### 적정공기'));
});
it('중복 번호와 잘린 마지막 항목은 원문을 유지하며 작성 중에는 내부 번호·지시문이 보이지 않는다',()=>{
  const units=[{text:'장비 상태를 점검합니다.',section:'준비',order:0},{text:'감시인과 연락을 유지합니다.',section:'주의',order:1}];
  expect(explainLightPart('머리말\n[1] 장비 상태를 점검합니다.\n[2] 감시인과',units,false).answer).not.toMatch(/머리말|\[1\]|\[2\]|감시인과/);
  const result=explainLightPart('[1] 장비 상태를 점검합니다.\n[1] 다른 설명\n[2] 감시인과',units,true,true);
  expect(result.answer).toContain('장비 상태를 점검합니다.');expect(result.answer).toContain('감시인과 연락을 유지합니다.');expect(result.answer).not.toContain('다른 설명');
});
it('모델이 방폭 조건을 감시인 조건으로 바꾸거나 금지·농도 경계를 뒤집으면 해당 원문을 유지한다',()=>{
  const unit={text:'어두운 밀폐공간 내부에서 측정을 위해 전등을 사용하는 경우, 가연성가스가 차 있을 수 있으므로 방폭구조의 전등을 사용하십시오.',section:'주의사항',order:0};
  expect(missingLightFacts('어두운 밀폐공간 내부에서 측정을 위해 전등을 사용하는 경우, 반드시 감시인을 배치하여 위험을 대비.',[unit])).toBe(true);
  expect(missingLightFacts('측정자의 머리가 개구면 안쪽으로 들어가도 됩니다.',[{...unit,text:'측정자의 머리가 개구면 안쪽으로 들어가지 않도록 합니다.'}])).toBe(true);
  expect(missingLightFacts('산소는 18% 미만이어야 합니다.',[{...unit,text:'산소는 18% 이상이어야 합니다.'}])).toBe(true);
});
it('압박 위치를 손 자세로 바꾸거나 조건·종료 시점을 생략한 실제 모델 출력은 원문으로 보완한다',()=>{
  const unit=(text:string)=>({text,section:'심폐소생술',order:0});
  expect(missingLightFacts('흉부압박 위치 확인: 양 젖꼭지를 이전에 한손을 겹치고 깍지를 껴서 손가락을 잡아 당김.',[unit('흉부압박 위치 확인 : 양 젖꼭지를 이은 중앙의 흉부부위')])).toBe(true);
  expect(missingLightFacts('인공호흡을 제외하고 지속적으로 가슴압박만을 시행합니다.',[unit('인공호흡 방법을 모르거나 꺼리는 경우에는 인공호흡을 제외하고 지속적으로 가슴압박만을 시행합니다.')])).toBe(true);
  expect(missingLightFacts('30회 가슴압박, 2회 인공호흡, 119 구급대 도착 시 반복실시',[unit('30회 가슴압박, 2회 인공호흡을 119구급대가 도착할 때까지 반복실시')])).toBe(true);
});
