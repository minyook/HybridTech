import { expect, it } from 'vitest';
import { outputIssue } from '../src/ai/output-quality';

const reference='산소 및 유해가스 측정방법. 산소(O₂), 일산화탄소(CO), 황화수소(H₂S) 농도를 측정한다. 측정자는 공기호흡기를 착용한다.';

it('태블릿 사진처럼 한국어·영어·아랍어·데바나가리어가 뒤섞인 출력을 거부한다',()=>{
  const answer='## 산소 및 유 stages 관련 안내\n\n1. 측정(측 Supplement)\n\n측 Layer\n\n1. 측 Layer\n   - 측 Supplement\n     - 측شون\n       - 측شون (측g version)\n\n1. 측정 조건\n   - 측 Layerनुसार\n     - 측 Initially gamer (측شون)';
  expect(outputIssue(answer,reference)).toContain('언어가 섞여');
});
it.each(['\ufffd산소 측정 안내','산소\u0000 측정 안내'])('깨진 문자·제어 문자를 거부한다',answer=>{
  expect(outputIssue(answer,reference)).toContain('깨진 문자');
});
it('영문으로 이탈하거나 같은 문장만 반복한 출력을 거부한다',()=>{
  expect(outputIssue('Measurement layer supplement initially gamer. Measurement conditions supplement layer.',reference)).toContain('한국어');
  expect(outputIssue('## 측정 안내\n1. 측정 조건을 확인하세요.\n2. 측정 조건을 확인하세요.\n3. 측정 조건을 확인하세요.',reference)).toContain('반복');
});
it.each([
  '산소(O₂)와 유해가스(CO, H₂S) 농도를 측정하세요. 측정자는 공기호흡기를 착용하세요.',
  'O₂ 18.0% 이상 23.5% 미만, CO 30 ppm 미만, H₂S 10 ppm 미만입니다.',
  '센서 수치가 20.9%를 초과하면 교정(calibration) 또는 센서 교체가 필요할 수 있습니다.',
  '## 측정 준비\n1. 측정기 상태를 확인하세요.\n2. 작업 전 농도를 측정하세요.\n3. 작업 중 농도를 측정하세요.',
  '문서에서 해당 내용을 확인할 수 없습니다.',
])('정상 한국어와 기술 용어·단위는 허용한다: %s',answer=>{
  expect(outputIssue(answer,reference)).toBeUndefined();
});
it('근거에 실제로 있는 외국어 인용과 그리스 문자 단위를 허용한다',()=>{
  expect(outputIssue('문서의 원문 표기는 "شون"입니다. 기준 단위 μg/m³를 확인하세요.',reference+' شون μg/m³')).toBeUndefined();
});
