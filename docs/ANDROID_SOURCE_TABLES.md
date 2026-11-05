# 원문 표 구조를 보존하는 Android 답변

갱신: 2026-11-05. 현재 개발 대상은 Android이며 웹/PWA 파일은 수정하지 않았다.

후속: 산소 단독 질문의 범위를 좁히고 11쪽 영향 도식을 추가해 현재 구조 자료는 다섯 개다. 변경 후 APK/PC 검사와 기기 미실행 범위는 [가스 질문 설계](ANDROID_GAS_QUESTION_SCOPE.md)를 기준으로 한다. 아래 S24 검증 기록은 이전 네 표 수정 시점의 결과로 보존한다.

## 문제와 수정 결과

원문 PDF의 표가 쪽별 검색 텍스트로 바뀔 때 셀의 연결이 사라졌다. 예를 들어 ‘유해가스 / 주된 위험 / 외관 및 냄새’가 하나의 제목으로 붙고, 다음 가스 이름이 앞 가스의 냄새 뒤에 붙었다. 이를 목록으로 표시하면 어떤 설명이 어느 가스에 해당하는지 알 수 없었다. Markdown 표를 그리는 기능만 추가하거나 Gemma에게 다시 정리하도록 시키는 것으로는 잘못된 연결을 해결할 수 없다.

이번 변경은 **원본 PDF의 셀 좌표에서 확인한 행·열을 답변의 표로 복원**한다. 가스명·위험·냄새, 농도·건강영향·노출시간을 같은 행에 보존한다. Gemma가 표의 수치나 관계를 재생성하지 않는다.

| 원본 | 복원한 표 | 데이터 행 × 열 |
| --- | --- | --- |
| `safety-guide-2022.pdf` 11쪽 | 산소농도별 인체 영향 도식 | 6 × 2 |
| `safety-guide-2022.pdf` 12쪽 | 황화수소 농도별 인체 영향 | 5 × 3 |
| 같은 PDF 13쪽 | 일산화탄소 농도별 인체 영향 | 7 × 3 |
| 같은 PDF 13쪽 | 유해가스별 주된 위험·외관 및 냄새 | 8 × 3 |
| 같은 PDF 14쪽 | 2012–2021년 질식재해 발생추이 | 4 × 12 |

총 30개 데이터 행, 머리글을 포함한 143개 셀이다. 원문의 `30~1시간` 등 불명확한 표기는 임의로 단위를 보충하지 않는다. 이 표들은 제공된 2022 문서의 내용을 표시하며 최신 기준을 새로 확인한 자료가 아니다.

## 실제 동작 흐름

1. 관리자/개발 PC의 `tools/extract-source-tables.py`가 PDF 경계선과 셀 좌표를 읽는다. 화학식의 작은 숫자도 같은 셀에서 추출한다. 산소 영향 도식은 나란한 농도 라벨과 그 아래 설명의 x 좌표로 열을 구분한다.
2. 머리글·마지막 행과 모든 셀 문자열을 쪽별 원문 텍스트와 대조한다. PDF SHA-256, 원문 쪽 본문 SHA-256, 좌표, 행·열을 감사 JSON과 생성 Kotlin에 기록한다. 빈 셀·병합 셀·그림 그리드는 추측으로 채우지 않는다.
3. 기존 검색기는 기존 원문·의미 인덱스를 사용한다. `FocusedEvidence.selected()`는 선택된 문장 조각이 검증된 표의 일부이면 완전한 표 한 개로 묶는다. 같은 검색 근거의 표 밖 문장과 경고는 유지하고 중복 복원을 막는다.
4. 문서 ID·쪽·청크 번호와 두 해시가 맞는 자료에만 복원을 적용한다. 내용이 바뀌면 기존 검색 근거를 그대로 사용하며 오래된 셀을 덧씌우지 않는다.
5. `DocumentAnswerPlan`의 화면 본문과 AI 보충용 문맥에 동일한 표를 전달한다. 출처는 원래 PDF 쪽으로 연결한다.
6. Compose가 Markdown 표를 셀 단위로 표시한다. 세 열 표는 휴대폰 폭에 맞추고, 최소 셀 폭이 부족하거나 열이 많은 표는 첫 열을 고정한 채 나머지 열을 함께 가로 스크롤한다. 긴 셀은 줄바꿈하며 행 높이를 고정하지 않는다. 셀 내부 `<br>`는 화면의 줄바꿈, TTS의 구분 쉼표로 처리한다.

표 복원은 검색 이후의 본문 구성 단계이다. 검색이 잘못된 쪽을 고르는 모든 문제까지 해결했다는 뜻은 아니다. 기본 원문 87개·의미 인덱스 393개와 ObjectBox 저장 형식은 변경하지 않았다. 모델 재다운로드나 기존 대화 삭제도 필요 없다. **이미 저장된 답변은 다시 작성하지 않으므로 같은 질문을 새로 보내서 확인한다.**

## 파일 경로

저장소 루트: `C:\Users\limmi\AndroidStudioProjects\Sllm2`.

| 경로 | 책임 |
| --- | --- |
| `app/src/main/assets/source-pdfs/safety-guide-2022.pdf` | 셀을 확인한 원본 PDF |
| `app/src/main/assets/knowledge/seed_chunks_v1.json` | 기존 원문 쪽 텍스트, 변경 없음 |
| `app/src/main/assets/knowledge/semantic-index-v1.json` | 기존 의미 검색 인덱스, 변경 없음 |
| `tools/extract-source-tables.py` | PC용 셀 추출·원문 대조·Kotlin/감사 JSON 생성 |
| `app/src/main/java/com/minyook/sllm2/data/SourceTableData.kt` | 검증된 셀·원문 범위·해시의 생성 결과. 셀 수동 편집 금지 |
| `app/src/main/java/com/minyook/sllm2/data/SourceTables.kt` | 해시 확인·선택된 표 조각 복원·주변 문장 보존 |
| `app/src/main/java/com/minyook/sllm2/data/FocusedEvidence.kt` | 검색 근거와 표 복원 연결 |
| `app/src/main/java/com/minyook/sllm2/data/AnswerMarkdown.kt` | 복원한 표를 목록으로 바꾸지 않고 보존 |
| `app/src/main/java/com/minyook/sllm2/ui/MarkdownBlocks.kt` | 표 셀·셀 안 줄바꿈 파싱, 셀 폭 계산 |
| `app/src/main/java/com/minyook/sllm2/ui/MarkdownText.kt` | 표 렌더링·첫 열 고정·가로 스크롤 |
| `app/src/main/java/com/minyook/sllm2/ui/VoiceConversationLoop.kt` | TTS에서 셀 줄바꿈 문법 제거 |
| `app/src/main/java/com/minyook/sllm2/ui/WorkspaceScreens.kt` | 대화 끝 앵커에 실제 너비를 부여해 하단 위치 확인 가능하게 보정 |
| `app/src/test/java/com/minyook/sllm2/data/SourceTablesTest.kt` | 실제 원문 행 관계·경고·중복·해시 불일치 검사 |
| `app/src/androidTest/java/com/minyook/sllm2/SourceTableRenderingTest.kt` | S24 제품 렌더러의 표 정렬·가로 스크롤·캡처 |
| `app/src/androidTest/java/com/minyook/sllm2/SourceTableSearchTest.kt` | 실제 ONNX 인코더·제품 검색 경로의 표 질문 검사 |

재생성에는 `pdfplumber`가 설치된 Python을 사용한다. 저장소 루트에서 실행한다.

```powershell
python tools/extract-source-tables.py --output-dir tmp/source-tables
```

`tmp/source-tables/source-tables.json`에서 수락/거절한 후보와 좌표를 확인하고, 원본 PDF를 렌더링해 셀을 눈으로 대조한다. 생성 코드 diff도 검토한다. 좌표 추출은 사람이 확인한 의미 관계를 대체하지 않는다. 문서를 교체하면 원본 카탈로그·검색 인덱스 갱신과 별도로 표 데이터도 다시 검증해야 한다.

## 검증과 남은 범위

최종 수치·APK 해시·질문별 본문은 [검증 기록](validation/android-source-tables-20261105.json)을 기준으로 한다. [S24 표 화면](validation/android-source-table-s24-20261105.png)은 실제 원문과 제품 Markdown 렌더러를 사용한 계측 화면이며, 저장된 채팅을 조작한 화면이 아니다.

- JVM 165개 중 158개 통과, 선택 실행 7개 제외. 실패 0개. 기존 150문항 및 추가 24문항 회귀 통과.
- S24의 실제 검색·본문 구성에서 표 질문 5개 통과. 표 렌더링 2개 검사에서 열 정렬·마지막 행·가로 스크롤·첫 열 고정을 확인했다.
- 추가 채팅 회귀는 묶음 실행에서 10/11개 통과했다. 다른 화면이 전경에 있어 Android가 클립보드 읽기를 차단했던 복사 1개는 사용자가 화면을 유지한 후 분리 재검사하여 **1/1개 통과(1.219초)**했다. 출처 연결과 원문 전체 답변 복사를 확인했으며, 채팅 11개 항목은 두 실행에 걸쳐 모두 통과했다.
- APK 빌드·설치 완료. Lint 오류 0개, 경고 111개. 모델 설정·대화 기록은 유지했다.

복원 대상은 위 다섯 표/도식이다. 다른 문서의 임의 표, 이미지로만 있는 표, 복잡한 병합 표를 모두 자동 이해하는 기능은 아직 아니다. 추출 후보 중 그림/빈 양식 등 세 개는 빈 셀을 추정하지 않고 제외했다. 단일 가스 질문의 필요한 행 선택은 후속 범위 변경에 반영했으며, 임의 비교 질문과 다른 도식의 검수는 별도로 확대해야 한다.
