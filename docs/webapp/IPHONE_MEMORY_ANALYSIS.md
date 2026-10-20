# iPhone 모델 적재 중 종료 분석

작성: 2026-10-20. Gemma 4 E2B·PWA·기기 내 추론을 유지하는 수정입니다.

## 실제 종료 로그에서 확인한 사실

사용자가 제공한 `JetsamEvent-2026-10-20-104352.ips`를 로컬에서 분석했습니다. 기록 시각은 2026-10-20 10:43:52 +0900, OS는 iPhone OS 27.0.1입니다. 이전에 전달된 10/16·iOS 18.6.2 로그는 이번 오류의 근거로 사용하지 않습니다. 원본 로그·고유 기기/사건 식별자·다른 앱 목록은 저장소에 포함하지 않습니다.

| 대상 | 기록의 종료 사유 | `rpages` | `pageSize` | 계산한 사용량 |
| --- | --- | --- | --- | --- |
| WebKit WebContent 기록 A | `highwater` | 216,384 | 16,384바이트 | 3,381MiB, 약 3.30GiB |
| WebKit WebContent 기록 B | `highwater` | 135,735 | 16,384바이트 | 약 2,120.86MiB, 2.07GiB |

Apple 문서는 `highwater`를 시스템 프로세스가 기대 메모리 사용량을 초과한 경우로 설명하고 `rpages × pageSize`로 바이트 사용량을 계산합니다. 근거: [Jetsam 해석](https://developer.apple.com/documentation/xcode/identifying-high-memory-use-with-jetsam-event-reports), [기기에서 로그 가져오기](https://developer.apple.com/documentation/xcode/acquiring-crash-reports-and-diagnostic-logs).

따라서 **이 로그의 WebContent 종료 사유는 높은 메모리 사용량으로 확인**했습니다. 다만 로그에는 Field Guard URL·JavaScript 할당 스택·실행 문맥 값이 없습니다. 사용자 재현 시각/동작과의 연관을 바탕으로 분석하며, 위 두 항목을 동시에 존재한 두 엔진으로 단정하거나 메모리를 합산하지 않습니다. 3.30GiB를 해당 기기의 고정 한도로 해석하지도 않습니다. GPU 프로세스의 기록 사용량이 작다고 해서 GPU 공유 메모리의 영향을 배제할 수는 없습니다.

## 파일 읽기 경로에서 바꾼 부분

이전 1MiB 입력 제한은 파일의 한 번 읽기 크기를 제한합니다. 고정된 `@litert-lm/core` 0.18.0의 `ReadableStreamDataStreamWrapper`는 별도로 건너뛴 구간과 보존할 구간을 JS 슬라이스에 저장하고 C++가 요청한 바이트를 WASM에도 복사합니다. 일부 구간을 나중에 다시 읽기 위해 보관하는 경로이므로 입력을 작게 나누는 것만으로 이 중간 캐시를 제거하지는 못합니다. 해당 캐시가 실제 iPhone 종료의 유일한 할당 원인이라고 확정한 것은 아닙니다.

새 Apple 모바일 경로는 SHA-256 확인을 거친 같은 로컬 파일의 필요한 구간을 직접 읽습니다. `readAndPreserve`는 파일을 그대로 보존하므로 이후 다시 읽을 수 있고, `readAndDiscard`/`discard`는 별도 JS 구간 캐시를 만들지 않습니다. 대형 요청도 1MiB 이하로 나누어 WASM에 복사하고 각 비동기 읽기 뒤 새 힙 뷰를 가져옵니다. 임의 오프셋·되감기·32비트 포인터·범위·중첩 읽기·파일 오류를 처리합니다. 이 방식은 **중간 JS 구간 캐시를 피하지만 WASM/GPU의 모델·작업 공간을 제거하거나 전체 메모리를 1MiB로 제한하지 않습니다.**

SDK의 공개 `Engine`/대화 API, GPU_ARTISAN, 공식 E2B 파일·해시와 문서 근거는 유지합니다. 모델 변환·재다운로드·서버 추론·원격 질문 전송은 추가하지 않습니다. 데스크톱/일반 스트림은 기존 SDK 경로를 사용합니다.

## 소스와 유지보수 경계

| 경로 | 역할 |
| --- | --- |
| `web/src/ai/file-model-reader.ts` | 파일 구간 읽기와 bounded WASM 복사, 읽기 통계 |
| `web/src/ai/model-stream.ts` | 같은 워커 안의 로컬 파일/스트림 WeakMap 연결 |
| `web/src/ai/sdk-stream-reader.ts` | 등록된 로컬 입력만 파일 읽기로 연결하고 일반 입력은 SDK에 위임 |
| `web/scripts/build-ai-worker.mjs` | classic worker 빌드에서 SDK 내부 bridge import만 연결, 0.18.0 버전·메서드·실제 연결 확인 |
| `web/src/ai/worker-engine.ts` | Apple 모바일 입력 선택, 준비 결과에 읽기 방식·횟수·총 바이트·최대 구간 측정 추가 |
| `web/tests/file-model-reader.test.ts` | 큰 파일의 비순차 접근·보존·되감기·복사 경계·힙 변경·오류·SDK 위임 회귀 |

`node_modules`는 수정하지 않습니다. 이 연결은 고정 SDK의 내부 C++ 스트림 프로토콜에 의존합니다. SDK 업데이트 시 빌드의 버전 검사를 우회하지 않고 upstream 프로토콜·보존/폐기 의미·실제 모델 초기화와 생성을 다시 검증해야 합니다. SDK 버전을 바꾸지 않고도 내부 프로토콜이 달라지는 비정상 설치는 일반 패키지 무결성 확인과 실제 모델 검증으로 다룹니다.

## 검증 범위

자동 검증과 Windows Chromium에서의 실제 E2B 결과는 [검증 기록](../../web/docs/validation.md)을 따릅니다. PC의 최대 파일 읽기 1MiB 관찰은 iPhone 전체 메모리 사용량이나 개선율을 뜻하지 않습니다. **수정 후 iPhone에서 모델 준비·첫 답변이 성공했는지는 아직 확인하지 않았습니다.**

재시험 시 서버를 켠 상태로 홈 화면 앱을 한 번 열어 업데이트를 받고 같은 주소의 모든 Field Guard 화면을 완전히 닫았다가 다시 엽니다. 설정에 **‘저장 파일을 필요한 부분씩 읽어 준비해요’** 문구가 보이는지 확인합니다. 기존 모델을 유지한 상태에서 문맥 2,048/답변 256으로 한 번만 시험합니다. 같은 종료가 반복되면 새 시각의 로그로 비교하며 반복 실행을 계속하지 않습니다. [홈 화면 앱 복구 절차](../IPHONE_TEST.md#모델-준비-중-페이지가-반복-종료될-때).
