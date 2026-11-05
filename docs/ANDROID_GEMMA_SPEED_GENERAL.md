# S24 Gemma 생성 속도와 문서 밖 일반 설명

갱신: 2026-11-05. Android 앱만 변경한다. 웹/PWA와 원본 문서·임베딩·기존 채점표는 보존한다.

## 사진에서 재현한 문제와 수정

- ‘필요한 도구는 먼데?’가 장비 목록으로 해석되지 않아 구조용 삼각대 문단 하나만 나왔다. 구어체 요청을 정규화하고 실제 용도별 장비 목록 경로로 연결했다.
- ‘얼마나 필요해?’와 ‘아니 갯수를 물어보잖아 삼각대 갯수’가 이전 대상의 수량 질문으로 이어지지 않았다. 수량 표현·정정 발화를 인식하고 앞선 실제 답변의 대상/목록에서 생략된 범위를 복구한다. 원래 사용자 문장은 바꾸지 않는다.
- 수량 기준을 못 찾으면 문서 확인 결과만 보여주던 제한이 있었다. 이제 **관련 문서에서 확인한 내용은 먼저 표시하고, 확인되지 않은 요청 속성에는 Gemma의 일반 설명을 이어서 제공**한다. 법정/필수 수량을 지어내거나 작업 가능을 승인하는 것은 허용하지 않는다.
- 생성 결과 일부가 검증에 걸리면 답변 전체를 ‘구체적인 수치나 작업 가능 여부를 확정할 수 없다’는 고정 문구로 덮었다. 이제 독립적인 완결 문단 단위로 미확인 수치·출처/승인 단정을 제외하고 나머지 유용한 일반 설명은 유지한다. 목록 번호를 제품 사양 숫자로 오인하던 조건도 좁혔다.
- 일반 설명에 문서 제목·작업 환경을 함께 전달한다. 밀폐공간 질문이 일반 재난 이야기로 넓어지거나 본문을 반복하는 것을 프롬프트에서 제한한다. 긴 이전 답변을 예산 때문에 생략할 때는 실제 Markdown 제목뿐 아니라 실제 굵은 목록 제목도 이미 다룬 항목으로 전달한다. 이는 사실/조건을 요약해 새로 만드는 기능은 아니다.

일반 답변은 **문서 출처로 확인된 사실과 구분되는 AI 설명**으로 표시한다. 모든 생성 사실을 검증하는 시스템은 아니며 문서에 없다는 이유만으로 답변 전체를 금지하지 않는다. 직접 근거가 있는 수량/조건은 계속 문서에서 인용한다. 질문이 실제로 모호한 경우와 검색 장애는 문서 부재로 바꾸지 않는다.

## 속도를 바꾼 부분

S24 Ultra에 연결해 CPU/GPU, 문맥 상한 8,192/4,096, 엔진 첫 준비/재사용, 짧은 답/긴 답을 비교했다. 모든 시험은 실제 `LocalModelRuntime`과 저장된 Gemma 4 E2B를 사용했다. 문서 답변을 생성 속도로 대체하지 않았다.

- 사용자의 저장 설정은 CPU·문맥 8,192·출력 4,096·자동 길이였다. 비교 시험의 설정은 메모리에서 주입하며 시험 중 저장 설정·대화 기록을 바꾸지 않았다.
- 측정된 GPU 경로가 유리해 최종 단말 설정은 **GPU 우선**으로 적용한다. 문맥·출력 길이 설정은 유지한다. 다른 기기의 동일 속도를 보장하지 않으며 GPU 실패 시 기존 CPU 폴백을 유지한다.
- 새 질문의 의미 검색마다 Gemma 엔진을 무조건 해제하던 경로를 바꿨다. 이미 준비된 GPU 엔진이 있고 총 RAM 8GiB 이상, 사용 가능 RAM 2GiB 이상, Android 저메모리 상태가 아닐 때만 엔진을 유지한다. 검색과 생성 연산은 여전히 순차 실행하며, 생성 전 검색 세션은 닫는다. CPU·작은 RAM·메모리 부족에서는 해제한다.
- 실제 S24에서 GPU 엔진을 유지한 채 ONNX 의미 검색을 수행한 후 다음 Gemma 생성이 재초기화 없이 진행되는지 검사한다. 첫 의미 검색 모델 준비 비용도 별도로 기록한다.
- 앱이 배경으로 가면 유휴 엔진/검색 세션을 정리한다. 답변 중에는 진행 중인 네이티브 연산을 닫지 않고 완료 후 배경 상태에서 정리한다. 복귀하면 유지 조건을 다시 평가한다.
- 추론 토큰 비활성화, 불필요한 프롬프트 축소, 완결 문장 단위 중간 표시를 유지한다. 간단한 수량 질문에는 판단 기준과 필요한 확인 질문을 간결하게, 자세한 요청에는 충분히 설명하도록 지시한다. 사용자의 출력 길이 설정을 작은 값으로 몰래 변경하지 않는다.

## 측정 해석

최종 수치와 실제 본문은 [검사 JSON](validation/android-gemma-speed-general-20261105.json), [실제 생성 예시](validation/android-gemma-general-examples-20261105.md)를 참고한다.

- 짧은 시험은 최대 128토큰·세 문장 요청, 긴 시험은 최대 384토큰·상세 설명 요청이다. 이 상한은 **시험 조건**이며 앱의 저장 출력 설정이 아니다. 긴 시험 결과를 모든 항목이 완성된 긴 답변이라고 해석하지 않는다. 일부 출력은 길이 상한에서 끝났다.
- CPU/GPU에 같은 질문·입력/출력 설정을 적용했지만 실제 생성 문장/길이는 달라질 수 있다. 전체 시간의 비율을 그대로 초당 토큰 처리량 비율로 주장하지 않는다. 장비 발열·OS·GPU 디스크 캐시는 완전히 통제하지 않은 개발 시험이다.
- `cold-short`는 해당 프로세스의 엔진을 처음 준비한 질문이다. 파일/드라이버 캐시를 지운 최초 설치 시험은 아니다. `warm-short`는 엔진을 재사용한다.
- 첫 네이티브 텍스트, 표시 가능한 완결 문장, 생성 완료 시간을 구분한다. 실제 Compose 프레임·키보드·음성 입력/낭독 시간은 제외한다.
- SDK 0.17.1의 `getBenchmarkInfo()`는 이 실행에서 `Benchmark is not enabled ... BenchmarkParams ... EngineSettings` 오류를 반환했다. **정확한 prefill/decode 토큰 수와 초당 토큰 수는 미측정**이며 해당 값은 비워 둔다. 길이 한도 도달 플래그도 이 통계에 의존하므로 false를 ‘완결 보장’으로 해석하지 않는다.
- 준비된 GPU에서 첫 문장이 빨리 나오는 것과 긴 답변 전체가 1~2초 안에 끝나는 것은 다르다. 생성 길이와 최초 모델/검색 준비에 따라 전체 대기는 더 길다.

## 파일 경로

루트: `C:\Users\limmi\AndroidStudioProjects\Sllm2`.

| 경로 | 역할 |
|---|---|
| `app/src/main/java/com/minyook/sllm2/data/QuestionFocus.kt`, `app/src/main/java/com/minyook/sllm2/data/ConversationContext.kt` | 정정/수량 표현·답변 대상 복구·최근 설명 항목 |
| `app/src/main/java/com/minyook/sllm2/data/QuestionUnderstanding.kt`, `app/src/main/java/com/minyook/sllm2/data/EquipmentOverview.kt` | 구어체 도구 목록 |
| `app/src/main/java/com/minyook/sllm2/data/EquipmentDetailAnswer.kt`, `app/src/main/java/com/minyook/sllm2/data/QuestionSearchCoordinator.kt` | 문서의 미확인 속성을 일반 설명으로 연결 |
| `app/src/main/java/com/minyook/sllm2/data/DocumentAnswerPlan.kt` | 문서 제목·환경을 포함한 근거 문맥 |
| `app/src/main/java/com/minyook/sllm2/data/GeneralKnowledgeAnswer.kt` | 일반 설명 지시·문단별 검사·기존 대화 안내 호환 |
| `app/src/main/java/com/minyook/sllm2/model/EngineReusePolicy.kt` | GPU 엔진 유지의 메모리 조건 |
| `app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt` | 메모리 내 시험 설정 주입·엔진 유지·네이티브 시간 |
| `app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt`, `app/src/main/java/com/minyook/sllm2/MainActivity.kt` | 공통 텍스트/음성 답변·검색/생성 교대·전경/배경 정리 |
| `app/src/androidTest/java/com/minyook/sllm2/GemmaGenerationBenchmarkTest.kt` | 실제 Gemma CPU/GPU·문맥·재사용 시험 |
| `app/src/androidTest/java/com/minyook/sllm2/GemmaDialogueIntegrationTest.kt` | 사진의 질문·수량 정정·일반 생성 실기기 통합 검사·측정 후 GPU 적용 |
| `app/src/test/java/com/minyook/sllm2/data/EquipmentDialogueTest.kt`, `app/src/test/java/com/minyook/sllm2/data/GeneralKnowledgeAnswerTest.kt` | 구어체·수량 일반 설명·문단 보존 회귀 |
| `app/src/test/java/com/minyook/sllm2/model/EngineReusePolicyTest.kt` | CPU/작은 RAM/부족 상태에서 엔진 유지 금지 |
| `tmp/gemma-s24-20261105/` | 원시 시간·본문·계측 로그와 최종 APK 보관 |

## 재실행

기존 앱 위에 APK를 설치한다. 모델·문서 재다운로드나 데이터 삭제는 필요하지 않다. 계측에는 테스트 APK도 설치해야 한다.

```powershell
adb shell am instrument -w -r -e class com.minyook.sllm2.GemmaGenerationBenchmarkTest -e runGemmaBenchmark true -e backend GPU -e contextTokens 8192 -e includeSearch true -e runLabel gpu-reuse com.minyook.sllm2.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class com.minyook.sllm2.GemmaDialogueIntegrationTest -e runGemmaDialogue true com.minyook.sllm2.test/androidx.test.runner.AndroidJUnitRunner
```

기본 테스트 실행에서는 긴 생성 시험을 건너뛴다. 결과는 단말 `getExternalFilesDir(null)/generation-checks/`에 저장한다. 첫 명령의 설정은 메모리에만 적용하며 기존 설정은 유지한다. `applyMeasuredGpu=true`는 별도 명시 호출로 측정한 GPU 설정만 저장하는 시험이다.

## 최종 측정 요약

최종 코드에서 준비된 GPU의 짧은 답변 완료는 2.534~2.667초, 첫 완결 문장은 1.12~1.19초였다. 이 짧은 출력은 세 문장이 완성됐다. 첫 엔진 준비를 포함한 짧은 시험은 7.906초였다. 긴 시험은 최대 384토큰에서 11.724초이며 상세 항목 전체의 완성을 보장하는 수치가 아니다.

GPU를 유지한 첫 의미 검색은 3.962초(검색 모델 첫 준비 포함)였고, 이후 생성의 엔진 재준비는 4ms였다. 사용자 설정은 GPU 우선·문맥 8,192·출력 4,096·자동 길이로 확인했다.

JVM 149개 통과 / AP 선택 스트레스 7개 제외 / 실패 0개. 기존 150/150·추가 24/24 통과. APK/계측 APK 빌드와 Lint 오류 0개 / 경고 111개. 실기기 최종 대화 5단계(실제 일반 생성 3회), GPU 재사용 생성 4회, GPU 설정 적용 검사를 통과했다. Compose·음성·실제 경보/AP 무선 시험은 이번 범위가 아니다.
