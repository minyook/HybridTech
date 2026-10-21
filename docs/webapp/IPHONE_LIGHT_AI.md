# iPhone/iPad 경량 로컬 AI 설계

갱신: 2026-10-21. 실제 iPhone의 Gemma 실행에서 확인한 WebContent highwater 종료에 대응하는 별도 경량 경로입니다. **PWA·기기 내 추론·오프라인을 유지합니다.** PC·Android 브라우저의 Gemma 및 Android 설치형 앱은 기존 경로를 사용합니다. 실제 iPhone 13 Pro / iOS 27.0.1에서 이 변경의 안정성은 아직 검증하지 않았습니다.

## 모델과 고정 파일

| 항목 | 값 |
| --- | --- |
| 모델 | Qwen3 0.6B q4f16_1 MLC |
| 런타임 | `@mlc-ai/web-llm` 0.2.86, lockfile 고정 |
| HF 모델 리비전 | `8c14ce481d4c692769976ad52afea453a102df19` |
| 컴파일 라이브러리 | `Qwen3-0.6B-q4f16_1_cs1k-webgpu.wasm`, SDK 호환 `v0_2_86/base` |
| 라이브러리 커밋 | `00fe7a40e57e5e8eb9fef87526a4bf28ad5846c0` |
| 라이브러리 SHA-256 | `19a1721867a481806adef35de0e0d0f2f4ff00a0c2e80d65b175fa55f21649c9` |
| 다운로드 | 13개, **353,285,213바이트** (약 353.3MB / 336.9MiB) |
| 문맥·출력 | 문맥 2,048 고정, 보충 최대 출력 기본 256 / 선택 128, 한 질문당 0~1회 |
| 입력 처리 | 최대 64토큰씩 GPU prefill, KV prefill 작업 공간도 같은 제한 |
| GPU 조건 | WebGPU·`shader-f16`, HTTPS·Worker·Cache API 저장 가능 환경 |

공식 출처: [MLC 모델 파일](https://huggingface.co/mlc-ai/Qwen3-0.6B-q4f16_1-MLC/tree/8c14ce481d4c692769976ad52afea453a102df19), [고정 실행 라이브러리](https://github.com/mlc-ai/binary-mlc-llm-libs/blob/00fe7a40e57e5e8eb9fef87526a4bf28ad5846c0/web-llm-models/v0_2_86/base/Qwen3-0.6B-q4f16_1_cs1k-webgpu.wasm), [WebLLM 사용 문서](https://webllm.mlc.ai/docs/user/basic_usage.html). 라이브러리 파일명의 `cs1k`는 컴파일된 최대 크기이며 실제 앱 입력 청크는 빌드 연결부에서 64로 줄입니다. 모델 파일 크기·라이브러리의 기본 VRAM 추정치는 전체 WebContent 메모리와 다릅니다.

## 정확한 소스 경로

아래는 저장소 루트 기준이며 이 PC 루트는 `C:\Users\limmi\AndroidStudioProjects\Sllm2`입니다.

| 경로 | 역할 |
| --- | --- |
| `web/src/ai/light-manifest.json` | 모든 파일 URL·크기·SHA-256, 모델/라이브러리 커밋 |
| `web/src/ai/light-model.ts` | Apple 판정, 한 파일씩 스트리밍 검증·저장, 설치 확인·경량 파일만 삭제 |
| `web/src/ai/light.worker.ts` | 캐시 전용 SDK 생성과 네트워크 fetch 차단 |
| `web/src/ai/light-engine.ts` | 준비/생성/측정, 완료 후 SDK·GPU 정리, 오류 응답 |
| `web/src/ai/light-answer.ts`, `light-prompt.ts` | 이전 범위 분할 호환 코드·공통 답변 병합 유틸리티 |
| `web/src/ai/light-output.ts` | 부분/완료 think 템플릿을 표시하지 않고 Markdown 본문 유지 |
| `web/src/ai/runtime.ts` | 기기별 Worker 선택, 단일 준비·잠금·대기·중단, 다음 엔진 자동 준비 |
| `web/src/ai/preferences.ts` | 경량 설정 별도 키·문맥/출력 한도 |
| `web/src/ai/diagnostics.ts` | 로컬 진단 허용 필드·최근 48개·사용자 내보내기 |
| `web/src/features/Settings.tsx` | 기기별 모델명·다운로드·용량·복구·토큰 선택 |
| `web/scripts/light-worker-bridge.mjs` | 고정 SDK의 메모리/캐시/토크나이저/힙 연결부 |
| `web/scripts/build-ai-worker.mjs` | 두 classic Worker 번들, SDK 버전과 연결 계약 검사 |
| `web/public/light-ai-worker.js` → `web/dist/light-ai-worker.js` | 빌드 생성물·PWA 셸 캐시, Git 제외 |
| `web/tests/light-*.test.ts` | 저장 무결성·누락·중단·토큰 예산·SDK 계약·정리 순서·표시 검사 |

## 다운로드와 오프라인

설정의 Qwen3 다운로드 버튼이 공식 고정 파일을 받습니다. 한 파일씩 응답 스트림을 SHA-256에 넣고 Cache API에 기록합니다. 전체 모델을 ArrayBuffer/Blob으로 합치거나 스트림을 tee/clone해서 검증하지 않습니다. 길이/해시 오류와 중단은 완료 파일로 남기지 않으며 완료된 파일은 다음 다운로드에서 재사용합니다. 가장 큰 가중치 shard는 약 77.8MB입니다.

SDK가 사용하는 `webllm/config`, `webllm/model`, `webllm/wasm` Cache API에 같은 immutable URL로 저장해 별도 중복 사본을 만들지 않습니다. 검증 완료 영수증 헤더·길이와 13개 존재 여부를 준비 전에 확인합니다. 매 실행 전체 파일을 다시 해싱하는 방식은 아닙니다. 캐시 삭제/손상은 설정에서 다시 확인하며 원본 고정 해시와 파일이 바뀌면 새 설치로 취급합니다.

추론 Worker의 `fetch`는 실패하도록 고정합니다. 네이티브 Cache.add는 이 재정의와 별개로 네트워크에 접근하므로 SDK의 그 경로도 빌드에서 실패하도록 바꿉니다. 모델 준비/생성 시 누락 파일을 몰래 다운로드하거나 원격 추론으로 전환하지 않습니다. SDK 업그레이드는 버전·연결 문자열 검사에서 막히며 담당자가 계약을 다시 검토해야 합니다. `node_modules`·모델 가중치·공식 WASM 자체는 수정하지 않습니다.

PWA 셸은 경량 Worker를 포함하고, 문서/PDF는 기존 준비 기능으로 저장합니다. 모델 캐시 전용 시험과 실제 기기에서 서버·Wi-Fi·모바일 데이터를 끈 완전 오프라인 재실행 시험은 구분합니다. 후자는 실제 iPhone에서 남아 있습니다.

## 실행 메모리와 답변 품질

현재 구현과 정확한 소스는 [문서 본문 우선 응답 설계](GROUNDED_ANSWER_DESIGN.md)에 정리합니다. `web/src/rag/answer-plan.ts`가 질문·근거·필수 조건·수치/비교 연산자로 공통 계획을 만들고 본문을 먼저 표시합니다. AI는 추가 설명이 필요한 경우 한 번만 생성하며 전체 절차의 순차 재작성은 사용하지 않습니다. 필수 근거가 입력 예산을 넘거나 보충 검증에 실패하면 본문을 유지합니다. 검증 전 토큰은 표시하지 않습니다.

문맥 2,048·prefill 64, 보충 한도 기본 256/선택 128입니다. 설정 키는 `ai-settings-light-v3`이며 이전 설정·대화·Gemma 파일과 Qwen 다운로드는 보존합니다. 일반 절차 질문은 생성 0회이며 본문 글자 수를 모델 토큰으로 추정하지 않습니다. 실제 보충 생성에는 SDK 측정 사용량을 저장합니다.

중단 요청과 정리 완료를 구분합니다. Worker가 실행 루프를 끝내고 SDK/GPU 정리를 확인하면 정상 종료로 처리합니다. 2초 parent watchdog 내에 응답하지 않거나 정리가 실패하면 강제 종료·자동 재적재 잠금을 적용합니다. 명시적 복구는 엔진 잠금을 확보한 뒤 수행합니다. 미완료 기록은 Jetsam 확정이 아니고, localStorage 보호 기록을 쓸 수 없는 Apple 환경은 자동 적재를 막습니다.

변경 전후 150문항의 검색/본문 필수 구절 검사는 132/150 → 150/150입니다. 개발 회귀 결과이며 생성 정확도 점수는 아닙니다. 평가표와 패키지는 출처 확인 상태이고 사람의 전문 검수는 남아 있습니다. 실제 iPhone 13 Pro의 적재·반복 질문·중단·오프라인 재실행·메모리 압박 안정성은 미검증입니다. 가중치 적재 비용은 짧은 생성만으로 없어지지 않습니다.

이전 PC의 순차 생성 관찰(가스 약 177초/CPR 약 67초, WASM 힙 160MiB)은 [날짜별 검증 기록](../../web/docs/validation.md)에 보존하며 새 구조의 속도나 전체 WebContent/GPU 메모리로 사용하지 않습니다. [iPhone 시험 안내](../IPHONE_TEST.md) · [이전 Jetsam 분석](IPHONE_MEMORY_ANALYSIS.md).
