# Android 검색 정확도 개선과 S24 응답 시간 · 2026-11-03

기존 150개 질문의 자동 기준을 유지하면서 검색·조건 선택을 수정했다. **PC 실제 검색 모델 평가에서 136/150 → 150/150**, 추가 확인 없이 기준을 통과한 문서 질문은 **118/140 → 140/140**이다. 추가 표현 24개도 통과했다. 이 24개는 개선에 사용한 개발 회귀 집합이며 독립된 외부 평가가 아니다. 처음 보는 모든 질문의 정확도가 100%라는 의미는 아니다.

<!-- evaluation-metrics -->
| 측정 | 실제 결과 |
|---|---|
| S24 최종 문서 질문 기준 통과 | 140/140 |
| 문서 첫 본문 중앙값 / 95백분위 | 0.081초 / 0.146초 |
| 문서 완료 중앙값 / 95백분위 | 0.081초 / 0.146초 |
| 문서 완료 2초 이내 / 최대 | 139/140 · 3.055초 |
| 같은 26개 검색 시간 중앙값 전 → 후 | 1.140초 → 0.081초 |
| CPU 일반 지식 9개 첫 본문 중앙값 | 5.949초 |
| CPU 일반 지식 9개 완료 중앙값 / 최대 | 42.716초 / 93.507초 |

CPU 일반 지식은 최종 검색 전처리 최적화 전 별도 실행이다. 생성 엔진/프롬프트/설정은 동일하다. GPU 우선 비교 2개는 실제 GPU 로그를 확인했으며 완료 시간은 88.661초, 96.585초였다. 이 비교로 GPU가 항상 더 빠르다고 판단할 수 없으며 원래 CPU 설정을 복원했다.
<!-- /evaluation-metrics -->

## 바뀐 검색·답변 흐름

1. **명사구를 유지한다.** ‘정확도와 신뢰성’, ‘위치와 개소’, ‘산소 및 유해가스’를 따로 잘라 의미가 불완전한 질문으로 만들지 않는다. 명시적인 ‘그리고’ 등으로 독립 질문을 구분한다.
2. **구어체는 검색에만 정규화한다.** ‘가스를 재다’, ‘위아래/가로’, ‘전기가 나가다’, ‘점심 먹고’, ‘펌프를 뜯다’ 같은 표현을 해당 도메인의 측정·방향·정전·작업 재개·분해 표현으로 바꾼다. 원래 질문과 기록은 보존한다. 질문 문장을 특정 페이지/답변에 매핑하는 표는 사용하지 않는다.
3. **의미 점수 하나로 절차를 덮지 않는다.** 동일 문장이 반복된 서론이나 사례가 실제 절차보다 높은 점수를 얻는 경우를 완화했다. 원문 제목·질문 항목·고유 대상 용어의 문서 내 빈도와 동시 출현을 의미 점수와 함께 확인한다. 근거가 부족한 후보는 여전히 미확인/되묻기로 처리한다.
4. **필요한 조건을 함께 가져온다.** 같은 절차 표가 다음 페이지로 이어지면 연결한다. 호흡용 보호구의 송기라인·정전 주의사항과 같이 관련 조건을 보존한다. 질문에 명시된 조건이 같은 페이지의 다른 문단에 있으면 그 조건의 실제 행동 문단을 함께 포함한다.
5. **문서 답변을 바로 완료한다.** 단순한 이유·차이 질문마다 같은 본문을 다시 생성하던 경로를 줄였다. 사용자가 ‘쉽게/풀어 설명’처럼 명시적으로 요청한 경우에만 검증된 짧은 모델 보충을 추가한다. 일반 지식 생성과 Markdown 표시는 유지한다.

검색 모델은 기존 EmbeddingGemma 300M q4, 생성 모델은 기존 Gemma 4 E2B다. PDF 2종, 87개 원문 조각과 393개 문서 벡터, 150개 기존 정답 기준은 변경하지 않았다. 검색 오류를 문서 부재로 바꾸지 않으며, 실제 현장 승인 상태를 모델이 만들어내지 않는다.

## 메모리와 속도

문서 질문 사이에서 토크나이저·인덱스·질문 벡터 캐시뿐 아니라 검색 CPU 세션도 재사용한다. 텐서는 질문마다 닫는다. Gemma 생성 전에는 검색 세션을 닫고, 새 질문의 검색 모델을 실행하기 전에는 생성 엔진을 해제한다. 앱이 배경으로 갈 때와 ViewModel 종료 시에도 검색 세션을 정리한다.

‘첫 본문’, ‘문서 답변 완료’, ‘새 일반 지식 생성 완료’는 다른 측정이다. 문서 답변은 전체 모델 재생성 없이 끝나지만 일반 지식은 토큰을 실제 생성해야 한다. **긴 일반 지식 답변의 완료 1~2초를 보장하지 않는다.** 사용자 기기에 저장돼 있던 CPU 설정과 출력 설정을 그대로 사용한 결과, 임시 GPU 우선 비교를 구분한다. GPU 비교 뒤 원래 설정을 복원한다.

## 측정 범위와 한계

- 기기: Galaxy S24 Ultra `SM-S928N`, Android 16. 앱/계측 APK를 기존 데이터 삭제 없이 업데이트했다. 모델 재다운로드나 대화 저장은 하지 않았다.
- 수정 전 대표 27개, 개선 후 문서 140개, 일반 지식 9개 및 현장 승인 구분 1개를 측정했다. 최종 문서 실행과 일반 지식 실행은 분리돼 있으며, 최종 코드의 연속 150개 단일 실행으로 포장하지 않는다. 일반 지식 생성 엔진·프롬프트·설정은 동일하지만 CPU 일반 지식 실행은 최종 검색 전처리 최적화 전이다. 검색을 제외한 생성 단계 시간도 별도로 기록한다.
- 실제 Android Repository, ONNX 질문 모델, 본문 조립, LiteRT-LM 생성 경로를 사용했다. ViewModel 상태에 본문을 전달할 수 있는 시점을 측정하며 실제 Compose 프레임 표시·키보드·음성인식/낭독 시간은 포함하지 않는다.
- 최초 검색 모델/토크나이저 준비 시간은 첫 질문 시간에 포함한다. 앱의 원문/인덱스 사전 준비 시간은 별도로 기록한다. 반복 질문 캐시 시간도 별도 항목이다.
- 같은 기기에서 순서대로 한 번씩 실행한 개발 측정이다. 발열·JIT·OS 상태를 통제한 정식 성능 벤치마크가 아니며 다른 기기에서 같은 시간을 보장하지 않는다.
- 문서 밖 답변의 출력 완료는 사실 정확도 검증과 다르다. 모델의 일반 지식에 대한 독립 채점은 이번 범위에 포함하지 않았다.
- 원문 표/도식/절 제목의 추출 품질과 관련 문단의 과도한 포함은 계속 검수해야 한다. 단어/쪽 검사만으로 실제 답변 품질 전체가 보증되지는 않는다.
- 마지막 JVM 실행은 80개 검사(174개 실모델 평가를 실행하는 2개 작업 포함)가 통과했고 앱/계측 APK 빌드도 통과했다. Compose·음성·BLE 전체 검사를 실행한 결과는 아니다. 최종 APK SHA-256은 `4c83c7e032c873f88881bd75e51bd953acbe9d8ff81c6b0570d1ed6f50da1bb9`다.

## 실제 파일과 결과

아래 경로는 저장소 루트 `C:\Users\limmi\AndroidStudioProjects\Sllm2` 기준이다.

| 경로 | 역할 |
|---|---|
| `app/src/main/java/com/minyook/sllm2/data/QuestionUnderstanding.kt` | 검색용 구어체 정규화 |
| `app/src/main/java/com/minyook/sllm2/data/QuestionSearchCoordinator.kt` | 명사구/복합 질문 처리와 오류·일반 지식 분기 |
| `app/src/main/java/com/minyook/sllm2/data/HybridRetrieval.kt` | 의미 재검토·제목/고유 대상 근거·절차/조건 연결 |
| `app/src/main/java/com/minyook/sllm2/data/SemanticEncoder.kt` | 검색 세션 재사용과 해제 |
| `app/src/main/java/com/minyook/sllm2/data/DocumentAnswerPlan.kt` | 문서 답변 즉시 완료·명시적 요청에 대한 보충 |
| `app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt`, `MainActivity.kt` | 생성 전·배경 전환 시 검색 메모리 정리 |
| `app/src/test/java/com/minyook/sllm2/data/LiveQuestionEvaluationTest.kt` | 고정 150개 + 추가 24개 PC 실모델 평가 |
| `app/src/test/resources/additional-questions-20261103.json` | 추가 개발 질문과 고정 기준 |
| `app/src/androidTest/java/com/minyook/sllm2/AndroidQuestionLatencyEvaluationTest.kt` | 실기기 검색·첫 본문·완료·반복·임시 GPU 비교 |
| `docs/validation/android-accuracy-latency-20261103.json` | 최종 품질·시간 요약과 측정 경계 |
| `docs/validation/android-150-improved-20261103.json`, `android-150-improved-20261103.md` | 개선 후 150개 질문의 실제 본문/검사 결과 |
| `docs/validation/android-additional-20261103.json`, `android-additional-20261103.md` | 추가 24개 검사 결과 |
| `docs/validation/android-s24-latency-20261103.json` | S24 수정 전/후 원시 시간·본문·별도 일반 지식 생성 |
| `docs/validation/android-s24-answers-20261103.md` | S24 질문별 시간과 최종 Markdown 답변 |
| `tmp/evaluation-150-20261103/` | 실행 중 원시 파일·벡터·진행 화면. Git 제외 |
| `tmp/accuracy-speed-20261103/fieldguard-accuracy-speed-20261103.apk` | 설치한 최종 앱 APK의 로컬 보관 사본 |

진행/결과 화면은 `http://127.0.0.1:8875/`이며 `tools/evaluation-150.html`이 질문별 결과와 실기기 시간을 표시한다. 제품 웹/PWA를 개발하거나 수정한 것은 아니다.

PC 재실행은 [150개 평가 절차](ANDROID_150_EVALUATION_20261103.md)를 사용한다. `FIELDGUARD_EVALUATE_150=1`이면 추가 24개도 실행한다. Android 실기기는 빌드·계측 APK 설치 후 아래 예시로 측정한다.

```powershell
adb shell am instrument -w -r -e class com.minyook.sllm2.AndroidQuestionLatencyEvaluationTest -e runLabel after -e includeGeneration true com.minyook.sllm2.test/androidx.test.runner.AndroidJUnitRunner
```

`numbers` 인수에 쉼표로 질문 번호를 주면 부분 집합을 측정한다. `backendOverride=GPU`는 해당 테스트 안에서만 GPU 우선 설정을 적용하고 `finally`에서 기존 설정을 복원한다. 원시 결과는 단말의 `getExternalFilesDir(null)/rag-checks/device-<runLabel>.json`에 저장한다. `tools/watch-device-evaluation.mjs`로 진행을 읽고, `tools/summarize-android-evaluation.mjs`로 문서/일반 지식 실행을 분명히 구분한 최종 결과를 만든다.
