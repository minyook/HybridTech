# Android 문단 중심 검색·본문 우선 답변

2026-10-29 추가: Android의 넓은 장비 목록 질문은 모호성 질문으로 돌리지 않고 용도별 근거를 모읍니다. 세부 사양·방법·조건 질문은 기존 검색으로 유지합니다. [장비 검색 설계와 별도 평가](ANDROID_EQUIPMENT_SEARCH.md). 아래 2026-10-27 수치는 당시의 고정 회귀 집합 결과입니다.

2026-10-29 현행 경로: [문장 의미 검색·확인 대화 설계](SEMANTIC_RAG_DESIGN.md). 검색 모델 준비, 소스 경로, 실제 평가와 기기 한계를 확인하세요.

갱신: 2026-10-27. Android 설치형 APK의 텍스트/음성 질문에 공통 적용한다.

## 확인한 원인과 최종 동작

이전 일반 질문 경로는 어휘 관련도가 낮아도 해시 벡터 점수와 함께 상위 2쪽을 반환했다. 전체 PDF 페이지가 모델 입력과 폴백 본문으로 들어가 머리말·다른 절차까지 섞였다. 모델이 답변 전체를 다시 생성했고 확인되지 않은 부분 답변도 화면에 표시했다. 웹에서 보완했던 문단 선택은 Android에 없었다.

이제 어휘의 문서 내 빈도(IDF), 질문 단어의 포함률, 같은 문단 안의 동시 출현으로 후보를 고른다. 해시 벡터 충돌만으로 출처를 만들지 않는다. 한국어 요청 표현·조사/활용, 밀폐공간/가스 이름·약어·조명 같은 표기를 정규화하지만 ‘질문→정답 페이지’ 표나 과거 질의 이력은 사용하지 않는다. 사전에 없는 명사도 실제 corpus와 비교하며 없는 사양을 다른 문서 주제로 대신 답하지 않게 한다.

관련도가 충분한 쪽에서 직접 답하는 문단을 앞에 놓는다. 같은 bullet의 조건/행동은 분리하지 않으며, 의미가 연결된 안전 절과 앞 문단을 가리키는 예외를 함께 유지한다. 드문 단어만 남기는 실험에서는 예외·보호 조건이 빠졌으므로 최종 구현은 해당 안전 절을 유지한다. 다른 주제의 페이지는 질문 속 방법/시기/자격 같은 속성과도 맞는지 검사한다. 복합 질문은 주제별로 근거를 확보하고, 절차표의 연속 페이지는 원래 순서를 유지한다.

질문에 직접 답하는 **선택된 문서 본문**을 먼저 표시한다. 일반 절차 질문은 전체 답변을 모델로 다시 생성하지 않는다. ‘왜/뜻/차이/쉽게’ 같은 경우에만 준비된 로컬 모델의 짧은 보충을 시도한다. 보충은 본문과 같은 답변 끝에 붙이며 본문을 교체하지 않는다. 원문 쪽/출처 버튼·음성 읽기도 같은 답변 계획을 사용한다. 기존 대화 기록을 소급 변경하지 않으므로 **새 질문부터** 적용된다.

## 모델 입력과 보충 검증

`DocumentAnswerPlan`은 선택 문단의 식별자·원문·절 제목과 화면 본문을 함께 보관한다. 모델은 이 문단만 받고 JSON의 `evidenceIds`/`text`로 1~2문장의 보충만 반환한다. 생성 상한은 설정값과 192토큰 중 작은 값이다. 보수적인 UTF-8 바이트 기반 입력 예산이 문맥 한도를 넘으면 근거를 자르지 않고 보충을 생략한다.

존재하지 않는 근거 ID, 새로운 수치·단위/비교 조건, 작업 승인·안전 허용 표현, 조건 없는 단정, 본문 복사와 근거 연관성이 낮은 보충은 표시하지 않는다. 미검증 스트리밍 토큰은 화면에 뿌리지 않는다. 실패·모델 미설치·검사 탈락 시에도 선택 본문은 유지하며 음성은 최종 같은 본문을 읽는다. 이는 보수적인 휴리스틱 검사로 의미 오류 전체를 검출하거나 현장 작업을 판정하는 기능은 아니다.

## 화면 정리

- 반복되는 PDF 장/쪽 머리말·꼬리말과 그림 태그는 표시 단계에서 제거한다. 원본 PDF와 ObjectBox 본문은 수정하지 않는다.
- 절 제목·목록·문단 간격을 정돈하고 수치/비교 조건을 강조한다. 목록의 줄바꿈 들여쓰기를 별도 열로 맞춘다.
- 절차표의 다음 단계 이름이 앞 문장 끝에 붙지 않게 분리한다. 도식 기호를 포함하면 원문 그림 확인을 안내한다.
- 출처 버튼은 ‘근거 1’ 대신 실제 문서 종류와 쪽수를 보여준다. 모델과 문서의 같은 내용을 두 번 나열하지 않는다.

## 고정 평가와 한계

웹에서 사용한 **150문항과 세부 질문 33문항을 그대로** Android 검사 자산으로 복사했다. PC 검사는 원본 웹 평가 파일과 SHA-256이 같은지 확인하며, 원문 corpus도 변경하지 않았다. 검색 쪽·필수 구절·금지 표현·첫 400자 내 직접 답변을 검사한다.

| 개발 평가 | 변경 전 | 변경 후 |
|---|---:|---:|
| 150문항 · 쪽과 본문 함께 통과 | 105/150 | 150/150 |
| 세부 33문항 · 쪽과 본문 함께 통과 | 6/33 | 33/33 |

변경 전은 연결된 S24 Ultra의 기존 APK, 변경 후는 PC JVM에서 **실제 제품 Kotlin 검색 코드**를 실행했다. corpus/두 평가표의 SHA-256은 같다. 계산 시간은 기기가 달라 비교하지 않는다. 변경 후 실기기 측정은 USB 연결 해제와 사용자의 ‘APK·코드 검사까지만’ 요청으로 수행하지 않았다. [변경 전 기록](validation/native-rag-baseline-s24.json), [변경 후 기록](validation/native-rag-after-jvm.json).

예를 들어 전등 질문은 27·58쪽/2,079자에서 27쪽/331자로, 산소측정법은 27·15쪽/1,765자에서 27쪽/955자로 정리됐다. CPR 절차는 45·46쪽을 모두 유지하면서 머리말·배치를 정리했다. 문자 수는 Markdown 표시 문자열 기준이며 정답 품질 지표 전체는 아니다.

이 집합은 개발에 사용한 회귀 집합이다. **처음 보는 모든 질문의 정확도, 한국어 음성 인식 정확도, 실제 모델 보충의 의미 정확도, 실기기 화면/오프라인 왕복 합격 점수가 아니다.** PDF 추출 공백·도식·미지원 표현의 한계와 전문가 원문 검수는 남아 있다. JVM 검사와 APK 빌드 성공은 기기에서 답변을 실제로 확인했다는 뜻과 구분한다.

## 실제 소스·실행 경로

| 파일 | 역할 |
|---|---|
| `app/src/main/java/com/minyook/sllm2/data/EvidenceLanguage.kt` | 표기·약어·조사·요청어 처리; 웹 text.ts와 대응 |
| `app/src/main/java/com/minyook/sllm2/data/FocusedEvidence.kt` | corpus 점수·쪽 선택·원자 문단/절 선택·Markdown 본문 |
| `app/src/main/java/com/minyook/sllm2/data/DocumentAnswerPlan.kt` | 화면/모델 공통 계획·구조화 보충 검증 |
| `app/src/main/java/com/minyook/sllm2/data/KnowledgeRepository.kt` | ObjectBox 원본 읽기·내용 변경 시 검색 인덱스 갱신 |
| `app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt` | 본문 먼저 표시·검증 보충·음성/기록 연결 |
| `app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt` | 로컬 보충 프롬프트·입력/출력 예산 |
| `app/src/main/java/com/minyook/sllm2/ui/MarkdownText.kt` | 제목/목록 줄바꿈·간격·강조 렌더링 |
| `app/src/main/java/com/minyook/sllm2/ui/WorkspaceScreens.kt` | 실제 문서·쪽 출처 표시 |
| `app/src/test/java/com/minyook/sllm2/data/NativeAnswerCorpusTest.kt` | PC의 동일 183문항 검사·JSON 보고서 |
| `app/src/androidTest/java/com/minyook/sllm2/NativeAnswerEvaluationTest.kt` | Android 기기용 동일 평가 |

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

최종 JVM 검사 46개와 APK/테스트 APK 빌드가 통과했다. 배포용 사본은 `tmp/native-rag/fieldguard-answer-20261027.apk`이며 SHA-256은 `2fa5875e3b3c58f7251c6600af4cc73a4ddc356955faf0ceaa5e21a5362c7f89`다. 실기기 설치는 수행하지 않았다.

첫 테스트 실행에는 Maven Central의 테스트 전용 `org.json:json:20250517`이 필요하다. 이 라이브러리는 제품 APK에 포함하지 않는다. 기본 APK는 `app/build/outputs/apk/debug/app-debug.apk`, PC 보고서는 `app/build/reports/native-rag/after-jvm.json`이다. 새 모델·임베딩·서버 AI·DB 마이그레이션·대화 삭제는 추가하지 않았다. 기존 앱 위에 업데이트 설치하고 새 질문으로 확인한다.
