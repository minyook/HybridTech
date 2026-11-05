# Android 대화 대상·질문 초점과 생성 지연

갱신: 2026-11-05. Android 앱만 변경하며 웹/PWA 파일은 보존한다.

같은 날 실기기 후속: 문서에서 미확인한 수량은 일반 설명으로 연결하고 고정 거절문 대체를 줄였다. GPU 생성과 메모리 조건부 유지도 실제 S24에서 검사한다. 아래 초기 검사 수치는 보존하며 최신 동작·속도는 [후속 설계](ANDROID_GEMMA_SPEED_GENERAL.md)를 따른다.

## 재현된 문제

안전대·구명줄·삼각대를 물었는데 추락 예방 문단만 나왔고, 이어서 ‘갯수는 어느정도로 챙겨?’라고 하면 장비 대상을 다시 물었다. 대상을 다시 명시해도 준비 의무만 보여주고 **수량 기준 유무**에 답하지 않았다. 11월 4일의 대화 기록 전달만으로 해결되지 않았던 문제다.

## 현재 처리

1. **대상과 속성:** 장비 개념과 수량/용도/역할 차이/짧은 사양 질문을 구분한다. 개념의 표기·동의어 사전이며 질문별 정답/쪽수 표가 아니다. 알 수 없는 수식어·부품 이름은 지우지 않고 의미 검색에 남긴다.
2. **후속 질문:** 생략된 대상은 같은 채팅에서 복구한다. 두 장비만 다시 말하면 범위를 좁힌다. 수량 질문 뒤 ‘두 명이면?’은 인원 조건을 유지하고, ‘언제 써?’는 사용 조건으로 초점을 바꾼다. 이전 문장을 계속 이어 붙이지 않는다.
3. **복수 대상:** 단순한 복수 장비 설명은 각 대상의 실제 문서 절에서 용도/행동을 찾아 먼저 보여준다. 같은 원문을 설명하는 장비는 함께 표시하고 원문 문장 중복을 줄인다. 미확인 대상은 구분한다.
4. **수량:** 관련 장비 절을 확인해 수량 조건의 원문이 있으면 조건과 함께 표시한다. 미확인이면 ‘관련 장비 항목에서 고정 준비 수량을 확인하지 못했다’고 답한다. 구비 의무와 수량 규정을 구분하며 모든 법령/제조사 지침에 수량 규정이 없다고 주장하지 않는다. 인원만으로 장비 수량이나 작업 가능을 추정하지 않는다.
5. **완성된 문서 답변:** 문서로 직접 답할 수 있는 항목은 바로 조립한다. 수량 기준이 미확인인 요청은 확인 결과를 먼저 표시하고 일반적인 판단 기준을 Gemma가 이어서 설명한다. 수량을 법정/필수 값으로 추측하거나 본문을 재생성하지 않도록 제한하며 출처는 유지한다.

보수적인 도메인 문맥 처리이며 모든 자연어를 완벽하게 이해하는 별도 LLM은 아니다. 기억 범위·저장 한도·채팅 격리는 [기존 설계](ANDROID_CONVERSATION_CONTEXT.md)를 따른다. 새로운 표현·복합 조건은 추가 평가가 필요하다.

## Gemma의 1~2초 목표

문서 답변 완료, 첫 모델 텍스트, 화면에 표시 가능한 완결 문장, 전체 생성 완료를 구분한다. **새로운 긴 Gemma 답변 전체를 1~2초 안에 완료한다는 보장은 하지 않는다.** PC 문서 답변 시간도 휴대폰 생성 시간으로 제시하지 않는다.

- 설치된 LiteRT-LM **0.17.1** Kotlin API를 확인해 `ThinkingConfig(enableThinking=false, thinkingTokenBudget=0)`과 템플릿의 `enable_thinking=false`를 명시했다. 답변 전 숨은 추론 토큰을 생성하지 않는 설정이다. 이전 실행에서 추론 토큰이 실제 지연 원인이었다고 확인한 것은 아니다.
- 일반 지식 프롬프트의 중복 지시를 줄였다. 한국어 Markdown, 직답 우선, 대화 대상/초점, 미확인 수량·사양·허가 추측 금지, 가짜 출처 금지는 유지했다.
- 부분 업데이트 간격을 200ms에서 80ms로 줄였지만 **완결 문장 검증을 통과한 본문만 표시**한다. 반 문장을 완료된 답처럼 보여주지 않는다.
- 출력 길이 설정은 유지한다. 속도를 위해 상세 답변을 몰래 짧게 자르지 않는다. 모델/양자화/문맥/CPU·GPU도 교체하지 않았다.
- 검색 모델과 Gemma 엔진의 기존 메모리 교대 정책을 유지했다. 두 모델을 무조건 동시에 상주시키지 않는다.
- 네이티브 초기화, 첫 실제 텍스트, 생성 전체, 입력 바이트/토큰, 출력 토큰과 decode 속도, 실제 백엔드를 기록한다. 최초 원시 텍스트는 아직 검증된 화면 문장과 다르다.

SDK 근거: [Google AI Edge LiteRT-LM API · thinking 설정](https://developers.google.com/edge/litert-lm/api_overview), [공식 Kotlin 구성](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt). 새 의존성으로 업그레이드하지 않았고 설치된 0.17.1 JAR에서도 생성자/프로퍼티를 확인했다. 실제 기기에서의 지연·정확도 변화는 별도 시험 대상이다.

## 실제 경로

저장소 루트: `C:\Users\limmi\AndroidStudioProjects\Sllm2`.

| 파일 | 역할 |
|---|---|
| `app/src/main/java/com/minyook/sllm2/data/QuestionFocus.kt` | 장비 개념·수량/용도/비교·생략된 대상·속성 전환 |
| `app/src/main/java/com/minyook/sllm2/data/ConversationContext.kt` | 기존 채팅과 연결, 구형 불완전 후속 주제 건너뛰기 |
| `app/src/main/java/com/minyook/sllm2/data/EquipmentDetailAnswer.kt` | 실제 절·수량 조건 검색, 속성에 직접 답하는 문서 본문 |
| `app/src/main/java/com/minyook/sllm2/data/HybridRetrieval.kt` | 기존 검색에 명확한 장비 상세 경로 통합, `SearchResolution.answerBody` |
| `app/src/main/java/com/minyook/sllm2/data/QuestionSearchCoordinator.kt` | 직접 답변/일반 검색, 중복 ‘더 알려줘’ 분기 |
| `app/src/main/java/com/minyook/sllm2/data/DocumentAnswerPlan.kt` | 직접 본문·근거·출처 통합, 불필요한 보충 생략 |
| `app/src/main/java/com/minyook/sllm2/data/GeneralKnowledgeAnswer.kt` | 압축한 생성 지시·미확인 장비 수량 표시 방지 |
| `app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt` | 추론 토큰 설정·스트리밍·`GenerationMetrics` |
| `app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt` | 텍스트/음성 공통 경로·시간 로그 |
| `app/src/test/java/com/minyook/sllm2/data/EquipmentDialogueTest.kt` | 생략·축소·속성 변경·미확인 부품·수량 조건 회귀 |
| `app/src/test/java/com/minyook/sllm2/data/EquipmentDialogueEvaluationTest.kt` | 실제 문서/인덱스의 10단계 대화·PC 시간 검사 |
| `app/src/androidTest/java/com/minyook/sllm2/AndroidQuestionLatencyEvaluationTest.kt` | 기기 검색/첫 검증 본문/생성 완료·토큰 시간 |
| `docs/validation/android-dialogue-focus-20261105.json` | 최종 검사·측정 범위·APK 정보 |
| `docs/validation/android-dialogue-focus-examples-20261105.md` | PC에서 실제 조립한 대화별 답변 |

## 검증·재현

원본 문서/PDF·393개 임베딩·기존 150개 및 추가 24개 채점 기준은 변경하지 않는다. 신규 대화는 페이지뿐 아니라 대상 전체 포함, 첫 문장의 수량 답변, 인원 조건 유지, 임의 수량 금지, 불필요한 생성 미호출을 검사한다.

APK를 기존 앱 위에 설치하고 다음을 같은 채팅에서 묻는다. 모델·문서 재다운로드나 앱 데이터 삭제는 필요하지 않다.

1. 안전대와 구명줄, 구조용 삼각대에 대해 알려줘
2. 갯수는 어느정도로 챙겨?
3. 구조용 삼각대랑 구명줄 등등 갯수 정해진 게 있나?
4. 그럼 두 명이면?
5. 쉽게 설명해줘
6. 그럼 언제 써?
7. 둘의 차이는?
8. 각각 몇 개 필요해?

이번에는 실제 Android 기기가 연결되지 않았다. **실제 Gemma 생성(추론 설정 전후 포함), Compose 표시, 음성, 발열·반복 생성의 시간은 미측정**이다. 기기 연결 후 같은 질문·출력 길이·CPU/GPU 설정으로 초기화/첫 본문/완료를 비교해야 한다. 확정 검사 수치와 APK 경로/해시는 검사 JSON에 기록한다.

최종 검사: JVM 143개 통과 / 선택 AP 스트레스 7개 제외 / 실패 0개. 실제 검색 모델의 150/150·추가 24/24, 기존 10단계·신규 장비 10단계 대화 통과. 앱/계측 APK 빌드 통과. Lint 오류 0개 / 경고 111개. 신규 장비 대화의 PC 검색·조립 중앙값 4.77ms, 최대 32.68ms이며 모델/화면/음성 시간은 아니다.
