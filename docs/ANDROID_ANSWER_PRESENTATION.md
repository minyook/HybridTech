# Android 답변 표시·완료 처리 개선

갱신: 2026-11-04. Android 설치형 앱 변경이며 기존 모델 파일·PDF·검색 인덱스는 그대로 사용한다.

2026-11-10 후속: 검증기에 공개하는 단위는 완결 문장·문단으로 유지하고, 통과한 텍스트의 화면 표시를 작은 글자 묶음으로 바꿨다. 과거 대화·문서 본문은 재생하지 않는다. “각 항목 한 문장”도 전체 문장 수 제한과 구별한다. 최신 실제 기기 검사는 [부드러운 답변 표시](ANDROID_SMOOTH_ANSWERS.md)에 정리하며 아래 기기 미실행 문구는 2026-11-04 검사 당시 범위다.

## 확인한 원인과 수정

| 확인한 문제 | 반영한 처리 |
|---|---|
| 일반 지식 생성은 자동 길이에서도 저장된 수동 토큰 값과 최대 768 제한을 사용 | 자동은 기존 문맥 예산 정책의 출력 한도(문맥 절반, 최대 8192), 수동은 사용자가 지정한 값 사용. 필수 문서 본문은 토큰 생성 없이 즉시 표시 |
| 200ms마다 부분 단어·열린 강조/코드 구문을 그대로 표시 | 완성된 문장·문단·목록 경계만 중간 공개. 소수점/단독 목록 번호는 문장 완료로 취급하지 않음 |
| 표의 헤더보다 긴 행에서 뒤쪽 셀 삭제 | 가장 긴 행을 기준으로 열을 맞추고 모든 셀 보존. 이스케이프/인라인 코드 안의 파이프는 셀 구분자로 쓰지 않음 |
| `- ①` 중복 기호와 붙어 나온 원문 번호 | 분명한 원문 번호를 별도 번호 목록으로 표시. `①~③` 범위·조건·수치 보존 |
| 중첩 목록·기울임·구분선·4~6단계 제목이 원시 Markdown 기호로 표시 | 들여쓰기·목록·강조·기울임·구분선·제목을 표시. 긴 목록 번호의 폭 확대, 본문/목록 글자와 줄 간격 통일 |
| PDF 표 머리말·`하십 시오` 같은 추출 어미 | 명확히 식별한 열 머리말과 끊어진 어미만 정리. 조건·행동을 축약하거나 모델로 다시 쓰지 않음 |
| 생성 도중 오류가 나면 이미 표시한 답변을 오류 문장으로 교체 | 검사를 통과해 표시한 부분을 유지하고 생성 중단을 알림 |
| 길이 제한에 닿아도 완료된 답변처럼 표시 | SDK decode 토큰 집계가 한도 도달을 보고하면 길이 한도 안내를 추가. 열린 Markdown은 닫되 빠진 사실/문장을 지어내지 않음 |

화면·복사·새로 저장하는 대화는 같은 정리된 최종 본문을 사용한다. 기존 대화에 이미 잘려 저장된 문장은 서식 변경만으로 복원되지 않는다. 같은 질문을 다시 보내 새 출력으로 확인한다.

## 출력 예산

`AnswerOutputPolicy`가 자동/수동을 구분한다. 자동 일반 답변은 문맥 2048일 때 최대 1024, 4096일 때 최대 2048, 8192일 때 최대 4096 토큰을 사용한다. 큰 문맥의 출력 상한은 기존 정책대로 최대 8192다. 수동 값은 문맥별 허용 범위 안에서 적용한다. 선택적 문서 보충만 최대 192 토큰이며 기존 검증기를 유지한다.

긴 일반 답변은 실제로 더 많은 토큰을 생성할 수 있으므로 완료 시간이 늘 수 있다. 문서 답변을 먼저 표시하는 경로와 모델/검색 메모리 교대 정책은 유지한다. 응답 길이를 늘린 것이 기기별 메모리·발열·생성 안정성을 검증했다는 뜻은 아니다.

`LiteRT-LM 0.17.1`의 `getBenchmarkInfo().lastDecodeTokenCount`를 완료 시 읽어 한도 도달을 확인한다. 고정 SDK의 실험 API에 해당하므로 해당 호출에 opt-in을 적용하고 조회 실패는 별도로 허용한다. 이 API는 종료 사유 enum이 아니며, OS 강제 종료·네이티브 크래시·모델이 스스로 일찍 끝낸 답변을 모두 검출하는 수단이 아니다. [공식 Conversation API 소스](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt).

## 변경 파일

모두 저장소 루트 기준이다.

| 파일 | 역할 |
|---|---|
| `app/src/main/java/com/minyook/sllm2/data/AnswerMarkdown.kt` | 표시용 정리·문장/문단 단위 중간 공개·번호/수치 보존·구문 닫기 |
| `app/src/main/java/com/minyook/sllm2/data/FocusedEvidence.kt` | 기존 선택 근거의 제목/목록 표시 연결 |
| `app/src/main/java/com/minyook/sllm2/data/GeneralKnowledgeAnswer.kt` | 일반 지식 안내·검증을 유지한 서식 정리·길이 한도 안내·간결한 프롬프트 |
| `app/src/main/java/com/minyook/sllm2/model/AnswerOutputPolicy.kt` | 자동/수동/선택적 보충의 출력 예산 |
| `app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt` | 예산 적용·완료 시 decode 집계 확인 |
| `app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt` | 검증된 중간 본문 유지·완료/오류/한도 상태 조립 |
| `app/src/main/java/com/minyook/sllm2/ui/MarkdownBlocks.kt`, `MarkdownText.kt` | 모든 표 셀 보존·중첩 목록·인라인 서식·제목/구분선·줄 간격 |
| `app/src/main/java/com/minyook/sllm2/ui/ChatTimeline.kt` | 구분선의 lazy 항목 타입. 기존 스크롤/키보드 로직 유지 |
| `app/src/test/java/com/minyook/sllm2/data/AnswerMarkdownTest.kt` | 미완성 스트림·열린 코드/강조·PDF 머리말·필수 조건/범위 보존 |
| `app/src/test/java/com/minyook/sllm2/model/AnswerOutputPolicyTest.kt` | 자동/수동 및 보충 예산 |
| `app/src/test/java/com/minyook/sllm2/ui/MarkdownBlocksTest.kt` | 표 추가 셀·코드 내부 파이프·들여쓰기·번호/소수점·인라인 표시 |

`web/`와 AP 운영 코드는 변경하지 않았다. 모델 다운로드/문서 재색인도 필요하지 않다.

## 검증 범위

고정 150개 질문과 추가 개발 질문 24개를 실제 PC 검색 모델/프로덕션 답변 계획으로 다시 평가했다. 자동 기준 통과는 질문별 필수 문구·조건·출처의 회귀 결과이며 처음 보는 모든 질문·모델 일반 지식의 사실 정확도를 보장하지 않는다. 질문/기준·원본 PDF·인덱스는 수정하지 않았다.

새 단위 검사는 표 뒷부분과 조건이 없어지지 않는지, 소수점을 문장 끝으로 착각하지 않는지, 코드의 원시 구문을 보존하는지 검사한다. APK·계측 APK 컴파일과 Lint도 실행했다. 최종 수치와 APK SHA-256은 [검증 기록](validation/android-answer-presentation-20261104.json)에 기록한다.

현재 ADB 기기가 없어 **실제 Gemma 생성·연속 질문·터치/키보드·시각적 렌더링/발열은 이번에 실행하지 않았다.** 중간 공개 기준이 문장/문단이므로 첫 글자보다 조금 늦게 보일 수 있다. PDF 표 자체의 붙어 나온 문장·추출 오탈자 전체를 자동 교정한 것은 아니며 의미를 확정하기 어려운 원문은 유지한다.

재실행 예시:

```powershell
$env:FIELDGUARD_EVALUATE_150='1'
$env:FIELDGUARD_EVALUATION_DIR='tmp/answer-format-20261104/evaluation'
$env:FIELDGUARD_WIFI_AP_STRESS='0'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain
```

최종 결과: JVM 108개 통과, Wi-Fi 별도 검사 7개 제외, 실패 0개. 기존 150개·추가 24개 질문 통과, Lint 오류 0개/경고 118개. [실제 본문 예시](validation/android-answer-presentation-samples-20261104.md).
