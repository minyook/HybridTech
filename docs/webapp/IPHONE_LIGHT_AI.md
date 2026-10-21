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
| 문맥·출력 | 문맥 2,048 고정, 최대 출력 기본 512 / 선택 256 |
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
| `web/src/ai/light-prompt.ts` | 동일 토크나이저로 문서 근거 예산 계산·발췌 |
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

10/21부터 실제 답변은 AI 정리와 문서 상세 발췌를 구분해 함께 표시합니다. 수치·조건·절차는 원문에서 읽기 때문에 상세 내용은 GPU 문맥·출력 한도를 추가로 사용하지 않습니다. SDK 사용량 표시도 문서 발췌를 제외한 AI 정리만 가리킵니다. `web/src/rag/text.ts`, `search.ts`, `evidence.ts`와 `web/src/App.tsx`가 구현 경로입니다. 문서 원문을 생성 답변으로 위장하지 않으며 정확도 전체를 보장하지 않습니다.

Apple에서는 기존 Gemma가 설치돼 있어도 로드하지 않습니다. 경량 파일 다운로드 완료 후 자동 준비하며, 이전 Gemma 중단 표시는 이 시점에 해제합니다. 기존 OPFS Gemma 파일·`ai-settings`·대화·작업 데이터는 보존합니다. 경량 선택은 별도 `ai-settings-light-v2` 키에 저장하며 큰 문맥을 선택할 수 없습니다. 이전 v1 키도 삭제하지 않습니다. 출력 기본값은 512이며 사용자가 256을 선택할 수 있습니다. 모델 가중치/라이브러리는 바뀌지 않아 기존 다운로드를 재사용합니다.

시스템 지침의 실제 토큰, 최대 출력 전체, 템플릿 여유 96토큰을 먼저 빼고 질문/원문을 넣습니다. 10/21부터 `rag/evidence.ts`의 문장/표 항목과 섹션을 이용해 페이지/섹션별로 균등하게 채웁니다. 반복 머리말은 제외하며 긴 첫 문장이 넘친다고 이후 절차를 전부 포기하지 않습니다. 문서의 완결된 행/문장을 선택하며 질문 자체가 넘치거나 근거를 넣지 못하면 GPU 생성 전에 원문 전환 오류를 반환합니다. 이전 대화는 보내지 않습니다. 한 번의 답변 후 SDK `unload()`와 장치 정리를 기다린 다음 Worker를 종료하고 다음 엔진 하나를 자동 준비합니다. 준비 중 질문은 같은 준비 Promise를 기다리며 중복 엔진을 생성하지 않습니다.

Windows Chromium의 최종 경량 경로에서 3회 생성과 캐시 재준비를 확인했고, 관찰한 **모델 런타임 WASM 힙 용량은 160MiB**였습니다. 기존 Gemma의 1,347.1MiB 관찰은 다른 런타임의 힙이며, 이 수치를 GPU·별도 토크나이저·JS·WebContent 전체 사용량이나 실제 iPhone의 감소율로 해석하지 않습니다. [상세 검증](../../web/docs/validation.md).

작은 모델에서 심폐소생술 답변의 압박 절차 누락을 관찰했습니다. 기존 근거 검사를 런타임에도 적용해 해당 경량 스트림을 보류하고 최종 답변은 원문으로 전환합니다. 이 검사는 모든 수치·의학/안전 정확도를 보장하지 않습니다. 산소 측정 답변에도 소제목 분류 부정확성이 관찰됐으며, 생성 완료를 품질 합격으로 표시하지 않습니다. **실제 iPhone에서 안정성과 한국어 근거 답변 품질 검증이 더 필요합니다.**

사용 순서는 [iPhone 시험 안내](../IPHONE_TEST.md), 이전 종료 원인/시각은 [Jetsam 분석](IPHONE_MEMORY_ANALYSIS.md)을 참고하세요. 질문·답변은 외부로 전송하지 않습니다.
