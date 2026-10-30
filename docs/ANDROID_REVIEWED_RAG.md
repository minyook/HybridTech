# Android 문서 우선 검색·의미 재검토·일반 지식 답변

갱신: 2026-10-30. Android 앱만 변경한다. 웹/PWA·PDF 원문·393개 문서 벡터·기존 기록은 보존한다.

## 실제 답변 흐름

1. 텍스트/음성인식 질문에 제한된 이전 문맥을 연결한다. 문맥 없는 ‘그거’와 8개 초과 주제는 임의 해석하지 않는다.
2. 문서 제목이 정확하거나 일반 장비 목록이면 기존 문서 근거로 빠르게 답한다. 현장 승인 상태는 문서/일반 모델로 추측하지 않는다.
3. 나머지 질문은 어휘 후보가 있어도 의미 검색을 실행한다. 후보 간 의미 구별이 충분하면 해당 문단·연결 조건을 선택한다. 의미 후보가 불분명해도 어휘 후보와 상위 의미 후보가 같은 쪽에 있는지 확인한다. 합의하지 못한 후보를 어휘 점수 하나만으로 확정하지 않는다.
4. 복합 질문은 구성 주제별로 검색한다. 근거가 확인된 부분을 합치고, 각 주제의 검색 결과를 별도로 유지한다. 검색 오류/모호한 부분이 있으면 그것을 문서에 없는 주제로 바꾸지 않는다.
5. 선택 문단은 `DocumentAnswerPlan`으로 구성해 Markdown·조건·출처를 먼저 표시한다. 필요한 짧은 보충은 기존 검증 경로를 유지한다.
6. 구성된 인덱스 검색을 완료했지만 직접 근거를 확인하지 못한 주제는 로컬 Gemma로 일반 지식 답변을 만든다. 문서로 확인한 부분이 있으면 같은 답변 안에 이어 붙인다. `일반 지식 안내` 문구와 헤더를 사용하며 검색하지 않은 출처를 붙이지 않는다.

여기서 ‘직접 근거 미확인’은 **원문에 절대로 없다는 증명과 다르다.** 현재 의미 점수/후보 구분 기준은 휴리스틱이다. 모든 PDF 그림·표를 이해하거나 사람처럼 모든 가능성을 검토하는 시스템은 아니다. 검색 누락이 일반 지식 답변으로 이어질 수 있으므로 출처 구분을 유지한다. 모호한 후보는 여전히 확인 질문을 사용할 수 있다.

## 일반 지식 답변

- 로컬 생성 모델이 필요하다. 모델 부재/생성 실패는 사실대로 안내한다. 서버 AI로 전환하지 않는다.
- 한국어 Markdown으로 직접 답하도록 지시한다. SDK의 기존 부분 응답을 최대 약 200ms 간격으로 표시하며 최종 답변을 기록·음성으로 전달한다. TTS는 미완성 토큰이 아닌 최종 본문을 읽는다.
- 일반 지식 응답은 최대 768토큰과 사용자 출력 설정 중 작은 한도를 사용한다. 문서 본문을 이 한도로 자르지 않는다.
- 문서명/쪽수를 꾸미거나 근거 없는 작업 가능·승인 단정, 특정 미확인 장비의 수치 사양, 문서 밖 안전 농도 수치 등을 표시하지 않도록 지시와 보수적 검사로 제한한다.
- 이 검사는 **일반 지식 전체의 사실 검증기와 다르다.** 필터에 잡히지 않는 오설명 가능성도 남는다. 화면은 일반 지식과 문서 근거를 같은 출처로 표시하지 않는다. 과거 기록도 안내 문구를 보존한다.

## 속도 목표와 실제 최적화

목표는 **첫 유효한 본문을 빠르게 표시하는 것**이다. 긴 답변의 완성 시간을 1~2초로 보장하지 않는다. 검색 인코더나 Gemma를 처음 적재하는 시간, 기기 연산 속도, 출력 길이가 실제 시간을 좌우한다.

- 문서 인덱스 JSON을 ViewModel에서 한 번 읽고, Repository는 문서가 같은 동안 준비된 검색기를 재사용한다. 시작 시 문서 준비 뒤 인덱스를 미리 준비한다.
- 토크나이저를 매 질문마다 재분석하지 않는다. 최근 16개 질문 벡터를 메모리에서 재사용한다.
- 검색 인코더 세션/텐서는 질문 후 닫는다. 캐시가 없는 질문의 인코더 실행 전 생성 엔진을 해제하여 두 모델의 동시 실행을 피한다. 캐시된 질문 벡터에는 이 해제를 반복하지 않는다.
- 문서 본문은 전체를 모델이 다시 생성할 때까지 기다리지 않는다. 일반 지식은 일부 본문부터 표시한다.
- `FieldGuardAnswerLatency` 로그에 `searchMs`, `firstBodyStateMs`, `answerMs`를 기록한다. 첫 값은 검색 단계, 두 번째는 전송부터 첫 본문 상태 갱신까지이며 실제 화면 프레임 표시 시간과 다르다. 음성인식 대기 시간도 별도다. 생성 로그는 엔진 초기화/첫 토큰/생성 시간을 별도로 기록한다.

PC JVM 비교는 인덱스를 매번 준비할 때와 재사용할 때의 검색 코드 비용만 측정한다. 휴대전화의 ONNX 추론·Gemma 적재/생성·IME·음성 시간은 포함하지 않는다. 그 수치로 ‘실기기 전체 응답 1~2초 달성’을 주장하지 않는다.

## 파일 경로

| 저장소 루트 기준 파일 | 역할 |
|---|---|
| `app/src/main/java/com/minyook/sllm2/data/QuestionSearchCoordinator.kt` | 실제 질문 경로·복합 주제·모호성/실패/일반 지식 분기 |
| `app/src/main/java/com/minyook/sllm2/data/HybridRetrieval.kt` | 어휘/의미 후보·검토 결과·원문 일치 검사 |
| `app/src/main/java/com/minyook/sllm2/data/KnowledgeRepository.kt` | 원문 변경 감지·준비된 인덱스 재사용 |
| `app/src/main/java/com/minyook/sllm2/data/SemanticEncoder.kt` | 토크나이저·최근 질문 벡터 캐시·CPU 세션 정리 |
| `app/src/main/java/com/minyook/sllm2/data/GeneralKnowledgeAnswer.kt` | 일반 지식 프롬프트·안내·Markdown·표시 제한 |
| `app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt` | 공통 텍스트/음성 조정·부분 본문·로그·최종 저장 |
| `app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt` | 기존 SDK 부분 응답·일반 지식 생성 모드 |
| `app/src/main/java/com/minyook/sllm2/ui/WorkspaceScreens.kt` | 문서/일반 지식/검색 안내 헤더 구분 |
| `app/src/test/java/com/minyook/sllm2/data/QuestionSearchCoordinatorTest.kt` | 후보 재검토·검색 실패·복합 질문·빠른 경로·모호성 |
| `app/src/test/java/com/minyook/sllm2/data/GeneralKnowledgeAnswerTest.kt` | 안내·Markdown 보존·가짜 출처/사양 제한 |
| `app/src/test/java/com/minyook/sllm2/data/ReviewedRetrievalEvaluationTest.kt` | 실제 corpus/고정 벡터 40개 검토 단계 기록·인덱스 비용 비교 |
| `app/build/reports/reviewed-rag/evaluation.json` | JVM 검사 때 생성하는 상세 비교 자료 |
| `docs/validation/android-reviewed-rag-20261030.json` | 검사 수치·APK 해시·실기기 미검증 범위 |

기존 `HybridRetrieval.resolve`의 기본 모드는 이전 개발 비교/회귀용으로 유지하며, **제품의 새 질문은 `KnowledgeRepository.searchQuestion`을 사용한다.** 183개 기존 어휘 회귀와 40개 고정 벡터 회귀의 통과는 새 프로덕션 경로 전체의 정확도 점수가 아니다. 새 조정기 검사에는 의미 벡터를 통제한 단위 사례가 포함된다. 실제 생성 품질·신규 질문의 독립 평가·실기기 첫 본문/전체 답변 지연은 추가 검증이 필요하다.

이번 JVM 검사 76개와 앱/계측 APK 빌드가 통과했다. 새 조정기 검사 7개, 일반 지식 표시/제한 검사 4개, 고정 벡터/인덱스 비교 검사 1개를 포함한다. 연결 기기가 없어 새 생성·음성·UI 동작은 실행하지 않았다. 정확한 PC 측정값·APK 해시·검사 범위는 [실행 기록](validation/android-reviewed-rag-20261030.json)을 참고한다.
