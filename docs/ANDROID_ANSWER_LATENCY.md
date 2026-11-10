# Android 답변 지연 관리

기준일: 2026-11-10. 대상은 Android 앱이다. 현재 전체 답변 8~9초 목표는 실기기에서 달성 확인되지 않았다.

## 실제 요청 경로

`FieldGuardViewModel.submitQuestion()`은 대화 복원 → 문서 검색 → 근거 본문 조립 → 필요한 일반 생성 → 검사/표시 → 최종 기록 저장 순서로 동작한다. 문서 본문을 바로 표시하는 시간과 Gemma 직접 생성 완료 시간은 다르다.

기존 `ReferenceAnswerStreamingTest`는 준비된 GPU 엔진과 고정 설정·빈 대화를 사용해 `LocalModelRuntime.generate()`를 직접 호출한다. 채팅 표시 코드가 같아도 실제 ViewModel 전체 경로의 재현 시험은 아니다. 이전 16.7~18.5초 기록을 이번 변경 후의 측정값으로 재사용하지 않는다.

## 구현 정책

| 처리 | 진입·구현 | 제한 |
| --- | --- | --- |
| 유휴 사전 적재 | 채팅/음성 진입·다운로드 완료·전경 복귀 → `prepareIdleModel()` → `LocalModelRuntime.prepareForQuestion()` | E2B AUTO/GPU, ARM64, 전체 RAM ≥ 8 GiB, 가용 RAM ≥ 4 GiB, low-memory 아님 |
| 자원 순서 | 의미 벡터가 필요할 때 사전 적재 종료 확인 → 인코더 실행 → 생성 전 인코더 해제 | 제목/직접 문서 응답은 준비를 기다리지 않음. 의미 검색이 필요하면 아직 시작하지 않은 준비는 취소하고 이미 시작된 native 초기화는 종료를 기다림 |
| 엔진 보존 | `EngineReusePolicy.requiresReload()` | 출력 길이/자동 길이만 바뀌면 유지. 모델·장치·문맥 변경은 다음 실행에서 같은 키인지 재확인 |
| 요청 형식 준수 | `GeneralKnowledgeAnswer.numberedItemCount()`와 프롬프트 | 사용자 명시 개수 2~12와 항목별 한 문장 요청이 모두 있을 때만 적용. 상충하는 개수는 적용하지 않음 |
| 생성 규칙 | 요청한 번호 목록으로 시작, 항목별 대상/행동·조건/예외 포함, 도입/중복 결론 생략 | 출력 토큰 한도와 최종 본문은 이 기능으로 자르지 않음. 상세 질문에 단문 강제하지 않음 |
| 표시 | `StreamingTextReveal`의 검증된 본문 버퍼 | native decode 처리량 개선과 구분 |

사전 적재는 가짜 질문이나 숨은 출력 토큰을 만들지 않는다. 같은 설정에서 실패하거나 메모리가 부족하면 자동 반복하지 않으며 실제 질문은 기존 요청 시 적재 경로로 처리한다. 백그라운드에서는 준비 작업의 종료를 확인한 후 유휴 자원을 해제한다. 백그라운드 정리 대기 중 앱이 복귀하면 뒤늦은 해제를 건너뛴다. NPU/CPU 선택을 사전 적재 때문에 다른 실행 장치로 바꾸지 않는다.

이는 첫 질문에 포함될 수 있는 엔진 초기화 비용과 불필요한 반복 생성을 줄이기 위한 변경이다. 커널 프리필까지 사전 실행하는 기능이 아니며, 실제 준비 효과·발열·메모리 동작·문장 형식 준수와 8~9초 달성은 별도 실기기 검증 대상이다.

## 검증 방법

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

정책 검사는 메모리·장치·모델 조건, 출력 길이 변경 시 엔진 재사용, 요청한 5개 항목과 상세/상충 질문의 구분을 확인한다. 앱 APK는 `app/build/outputs/apk/debug/app-debug.apk`, JVM 결과는 `app/build/test-results/testDebugUnitTest/`, 자체 평가 출력은 `app/build/reports/fieldguard/`에 둔다. 프로젝트 루트의 `tmp`는 생성하지 않는다.

실기기 재측정은 동일 모델·실제 실행 장치·문맥/출력 설정·원문 질문을 기록하고 새 대화/기존 대화, 첫 실행/반복 실행을 구분한다. 보내기부터 첫 본문 공개·전체 완료를 측정하며 문서 검색·엔진 초기화·첫 native 응답·생성 시간을 함께 기록한다. 실제 UI 프레임 표시는 상태 갱신 시각과 별도 측정해야 한다. 속도 때문에 필수 조건이나 예외를 삭제하지 않는다.
