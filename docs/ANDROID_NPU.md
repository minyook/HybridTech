# Android NPU 우선 실행

작성일 2026-11-09.

**2026-11-10 실기기 추가 확인:** S24 Ultra/SM8650에서 설치된 1B NPU 파일로 짧은 생성을 요청했으나 `Unsupported dispatch runtime version`·`No usable Dispatch runtime found` 및 텐서 할당 실패가 발생했다. NPU 전용 프로세스 종료 후 GPU로 전환해 두 요청을 처리했다. 현재 SDK·dispatch 조합의 버전 호환성을 먼저 해결해야 하며 **NPU 생성 성공이나 속도는 검증되지 않았다.** [실기기 비교와 해당 로그](ANDROID_MODEL_BENCHMARK_20261110.md).

## 앱에서 설정하기

1. 설정에서 Gemma 3 270M 또는 Gemma 3 1B를 선택하고 범용 모델을 다운로드한다.
2. 앱이 표시한 칩셋과 NPU 추가 파일을 확인해 다운로드한다. 기존 Hugging Face 읽기 토큰을 재사용하거나 같은 입력란에 새 토큰을 입력할 수 있다.
3. 실행 방식을 자동 또는 NPU 우선으로 선택하고 저장한다. 짧은 생성 질문을 시험할 때는 최대 답변을 256토큰으로 설정할 수 있다.
4. 답변 후 설정의 최근 AI 실제 실행에서 NPU·GPU·CPU와 전환 이유를 확인한다. 문서 본문만 구성한 질문에는 새 모델 실행 기록이 생기지 않는다.
5. 같은 질문의 모델 비교에서는 요청한 장치와 실제 사용한 장치를 함께 확인한다. 결과 JSON에는 실제 파일과 유효 문맥 한도도 기록한다.

범용 파일은 GPU·CPU 전환용으로 보관한다. NPU 추가 파일은 별도 파일에 저장하며 다운로드 작업과 설치 상태도 구분한다. CPU와 GPU를 직접 선택하면 해당 실행 정책을 따른다.

## 연결한 모델과 칩셋

| 칩셋 | Gemma 3 270M Q8 NPU 바이트 | Gemma 3 1B INT4 NPU 바이트 | Hexagon 런타임 |
| --- | --- | --- | --- |
| SM8550 | 461,750,272 | 690,143,232 | v73 |
| SM8650 | 461,766,656 | 690,094,080 | v75 |
| SM8750 | 461,340,672 | 689,291,264 | v79 |
| SM8850 | 463,011,840 | 693,747,712 | v81 |

Android가 제공하는 SOC_MODEL에서 지원 칩셋 식별자를 확인한다. 지원 목록에 없는 칩셋은 범용 경로를 사용한다. NPU 하드웨어가 있어도 이 실행 런타임과 모델 패키지 조합이 있어야 선택한다.

270M 파일명은 `gemma3-270m-it-q8.qualcomm.sm8650.litertlm`, 1B는 `Gemma3-1B-IT_q4_ekv1280_sm8650.litertlm` 형태다. 모델별 고정 Hugging Face 리비전은 기존 범용 모델과 동일하다. 1B 파일 이름에 명시된 KV 길이와 초기 보수적 실행 조건을 반영해 NPU 시도 시 앱의 문맥 상한은 1,280토큰이다. 필수 근거나 조건을 잘라 NPU에 맞추지 않는다. 요청한 출력 예산이 이 범위를 넘거나 실제 입력을 처리하지 못하면 범용 경로로 전환한다. 따라서 NPU와 범용 경로의 실제 문맥 한도가 다를 수 있다.

Gemma 4 E2B 배포 저장소에서 이번에 확인한 Qualcomm 파일은 QCS8275용이었다. 위 휴대폰 칩셋에 다른 제품용 파일을 적용하지 않으며 현재 E2B는 범용 GPU·CPU 경로를 사용한다. MediaTek·Google Tensor·기타 NPU는 별도 런타임과 검증한 파일 연결이 필요하다.

배포 목록에서 파일명·크기를 확인했다. [Gemma 3 270M](https://huggingface.co/litert-community/gemma-3-270m-it/tree/main) · [Gemma 3 1B](https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main) · [Gemma 4 E2B](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/tree/main).

## 실행과 장애 처리

NPU는 비공개 bound service인 NpuInferenceService에서 실행한다. 프로세스 이름은 `:npu`이며 사용자 채팅·문서 데이터베이스를 그 프로세스에서 초기화하지 않는다. 메인 프로세스는 질문을 전달하고 출력과 계측을 받는다. 같은 엔진은 질문 사이에서 재사용할 수 있다.

SDK 0.17.1의 Backend.NPU API를 사용하고 Qualcomm QNN Runtime 2.44.0을 Maven 의존성으로 포함했다. 필요한 HTP·System·칩셋별 Stub/Skel과 LiteRT dispatch 파일이 추출된 네이티브 디렉터리에 있는지 확인한 뒤 NPU를 시도한다. ADSP_LIBRARY_PATH는 NPU 프로세스에서 설정한다. 관련 라이선스와 출처는 APK의 assets/licenses/npu에 포함했다.

NPU용 dispatch는 Google 공식 예제 커밋 `32a331c33a7115a3b86ef554fb58a9c11d03b123`의 라이브러리다. SHA-256은 `9c039e24d7b80c5a213455cb29f9c2766125dca5f135fa9111c3ef224792e95f`다. SDK·dispatch·QNN·펌웨어 조합의 실기기 호환성은 별도 검증 대상이다.

서비스 연결은 최대 15초, 첫 출력까지는 최대 60초, 출력 중 다음 메시지까지는 최대 30초를 기다린다. 시간 초과나 서비스 종료·모델 오류가 발생하면 해당 NPU 시도를 중단하고 프로세스 종료 확인 후 범용 모델을 사용한다. 정리 완료를 확인하지 못하면 추가 모델 적재를 진행하지 않고 재실행을 안내한다. 같은 런타임에서 드라이버·프로세스 오류로 실패한 NPU 패키지는 다음 질문마다 반복해서 적재하지 않는다. 문맥 초과로 전환한 경우에는 다음의 짧은 질문에서 NPU를 다시 시도할 수 있다. 앱 재실행이나 사용자가 새로 시작한 비교 작업에서도 다시 시험할 수 있다.

NPU 전용 파일을 GPU에 적용하지 않는다. 전환할 때는 기존 범용 파일로 돌아간다. 일반 엔진 해제와 실행 실패로 인한 격리는 다른 상태로 다룬다.

[LiteRT-LM Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md) · [Google Qualcomm NPU 예제](https://github.com/google-ai-edge/litert-samples/tree/main/samples/litert/qualcomm/llm_chatbot_npu).

## 프로젝트 경로

프로젝트 루트는 `C:\Users\limmi\AndroidStudioProjects\Sllm2`다.

| 루트 기준 파일 | 역할 |
| --- | --- |
| app/src/main/java/com/minyook/sllm2/model/NpuModel.kt | 칩셋별 모델·크기·런타임 목록 |
| app/src/main/java/com/minyook/sllm2/model/NpuInferenceClient.kt | 서비스 연결·스트리밍·종료 확인 |
| app/src/main/java/com/minyook/sllm2/model/NpuInferenceService.kt | 별도 프로세스에서 NPU 추론 |
| app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt | NPU 우선과 범용 전환, 실제 실행 계측 |
| app/src/main/java/com/minyook/sllm2/model/ModelDownloadWorker.kt | NPU 파일별 다운로드·이어받기 |
| app/src/main/java/com/minyook/sllm2/ui/FieldWorkspaceUi.kt | NPU 설치 상태·다운로드·실제 실행 안내 |
| app/src/main/jniLibs/arm64-v8a/libLiteRtDispatch_Qualcomm.so | 네이티브 dispatch |
| app/src/main/assets/licenses/npu | 배포 라이선스와 출처 |

APK·코드 검사는 휴대폰의 실제 NPU 실행을 증명하지 않는다. 실제 NPU 성공과 속도 수치는 모델을 다운로드한 지원 기기에서 별도로 측정해야 한다.

## 2026-11-09 APK·코드 검증 기록

설치용 APK와 계측 APK 빌드, Android 서명 검증을 완료했다. 자동 검사 182개 중 172개 통과, 선택 실행 10개 제외, 실패와 오류 0개다. Android Lint는 오류 0개와 경고 112개다.

APK에서 dispatch·QNN HTP·System·v73/v75/v79/v81 Stub/Skel의 포함과 네이티브 라이브러리 추출 설정, `:npu` 서비스 선언을 확인했다. 칩셋 식별·전용 파일명·고정 다운로드 주소·잘못된 칩셋 제외·문맥 오류 구분을 회귀 검사했다.

사용자 요청 범위에 따라 APK와 코드 검사로 마쳤다. 휴대폰에서의 NPU 초기화·생성 성공, 네이티브 장애 복구, 실제 성능은 아직 검증하지 않았다. [검증 기록](validation/android-npu-20261109.json).

설치용 APK는 `C:\Users\limmi\AndroidStudioProjects\Sllm2\tmp\npu-20261109\fieldguard-npu-priority-debug.apk`다.
