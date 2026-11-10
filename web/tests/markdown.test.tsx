/* 소스 안내
 * 웹 회귀 검사: 제목, 강조, 목록, GFM 표와 코드를 의미 있는 HTML로 렌더링한다 / 모델이 만든 HTML이나 이미지로 코드를 실행하거나 외부 요청을 만들지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다.
 * 검사 예: 제목, 강조, 목록, GFM 표와 코드를 의미 있는 HTML로 렌더링한다, 모델이 만든 HTML이나 이미지로 코드를 실행하거나 외부 요청을 만들지 않는다, 수치 범위의 물결표를 취소선으로 바꾸지 않는다
 * 실행: web/에서 npm test. 실제 네트워크/모델 검증과 mock·순수 검사의 범위는 각 fixture를 확인한다.
 */
import { renderToStaticMarkup } from 'react-dom/server';
import { expect, it } from 'vitest';
import { TextContent } from '../src/ui';

it('제목, 강조, 목록, GFM 표와 코드를 의미 있는 HTML로 렌더링한다',() => {
  const html=renderToStaticMarkup(<TextContent text={'## 점검 안내\n\n**원문 확인**\n\n1. 첫 단계\n2. 다음 단계\n\n| 항목 | 상태 |\n| --- | --- |\n| 문서 | 준비됨 |\n\n```text\nlocal only\n```'}/>);
  for (const tag of ['<h2>','<strong>','<ol>','<table>','<th>','<pre>','<code']) expect(html).toContain(tag);
});
it('모델이 만든 HTML이나 이미지로 코드를 실행하거나 외부 요청을 만들지 않는다',() => {
  const html=renderToStaticMarkup(<TextContent text={'<script>alert(1)</script>\n\n[위험 링크](javascript:alert%281%29)\n\n![원격 이미지](https://example.com/track.png)'}/>);
  expect(html).not.toContain('<script'); expect(html).not.toContain('javascript:'); expect(html).not.toContain('<img'); expect(html).toContain('원격 이미지');
});
it('수치 범위의 물결표를 취소선으로 바꾸지 않는다',() => {
  const html=renderToStaticMarkup(<TextContent text={'4~5cm · 100~120회'}/>);
  expect(html).not.toContain('<del>'); expect(html).toContain('4~5cm · 100~120회');
});
