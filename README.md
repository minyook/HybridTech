# Field Guard · Sllm2

밀폐공간 안전 문서를 기기에서 검색하고 원문 PDF를 열 수 있는 Android 앱입니다. 한국어 음성 질문·답변 읽기, Wi-Fi 가스 측정기 연결, 작업자·감독관의 기기 내 작업 시연을 제공합니다. 앱 ID는 `com.minyook.sllm2`, 버전은 `1.1`(코드 2)입니다. 같은 서명 키로 만든 APK를 덮어 설치하면 기존 앱 데이터를 계속 사용합니다.

## 빌드 및 실행

Android Studio에서 프로젝트를 열거나 Windows PowerShell에서 다음을 실행합니다. SDK 37, NDK 28.2.13676358, CMake 3.22.1과 Gradle 데몬용 JDK 25가 필요합니다. Node.js는 검색 자산 준비, 작업 API, PC 검색 평가에 사용합니다.

```powershell
node tools/prepare-semantic.mjs
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

첫 명령은 번들 검색 모델의 고정 리비전·파일 크기·SHA-256을 확인합니다. 문서 벡터 인덱스는 저장소의 버전을 사용합니다. 첫 네이티브 빌드에는 고정된 llama.cpp 소스와 Snapdragon 아카이브가 필요하며 Gradle/CMake가 다운로드해 검증합니다. APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 앱의 **설정 → 기기 내 EXAONE**에서 생성 모델을 내려받습니다.

같은 모델 카드의 **실행 방식 및 토큰**에서 실행 백엔드와 문맥 한도를 설정합니다. 상단의 인터넷 표시는 Android가 확인한 기본 네트워크의 인터넷 상태를 반영합니다. 가스 측정과 텍스트 질문은 하단 탭에서 열 수 있고, 음성 대화는 홈이나 텍스트 질문의 마이크 버튼에서 엽니다.

## 질문이 답변으로 처리되는 과정

1. **질문과 대화 문맥을 해석합니다.** [ConversationContext.kt](app/src/main/java/com/minyook/sllm2/data/ConversationContext.kt)가 후속 질문에서 생략된 대상을 찾습니다. [QuestionUnderstanding.kt](app/src/main/java/com/minyook/sllm2/data/QuestionUnderstanding.kt)가 검색 표현을 정규화합니다. 검색어를 정규화하면서도 화면과 기록에는 사용자가 입력한 원문을 보존합니다.
2. **문서 구절을 검색합니다.** [SemanticEncoder.kt](app/src/main/java/com/minyook/sllm2/data/SemanticEncoder.kt)가 EmbeddingGemma 300M Q4 ONNX로 질문의 768차원 벡터를 계산합니다. 문서 벡터는 미리 계산해 앱에 넣으므로 기기에서는 질문만 인코딩합니다. [HybridRetrieval.kt](app/src/main/java/com/minyook/sllm2/data/HybridRetrieval.kt)는 의미와 어휘 순위를 결합합니다. 의미 검색은 표현 차이를 흡수하고, 어휘 검색은 가스명·장비명과 같은 대상 식별에 기여합니다. 검색 인덱스의 구절을 ObjectBox 원문과 대조하며 정확한 제목이나 특정 장비·가스 질문에는 원문을 선택하는 경로도 있습니다.
3. **질문과 검색 근거를 EXAONE에 전달합니다.** [DocumentAnswerPlan.kt](app/src/main/java/com/minyook/sllm2/data/DocumentAnswerPlan.kt)가 선택한 구절, 문서 기반 대체 답변, PDF 페이지 ID를 묶습니다. [FieldGuardViewModel.kt](app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt)가 검색이 근거를 찾았고 모델이 준비된 경우 EXAONE을 호출합니다. 재개 질문의 고정 다섯 문장 답변은 제거했습니다. 질문에 형식 요구가 있으면 생성에 반영합니다.
4. **생성 답변을 검사한 뒤 공개합니다.** [DocumentGeneration.kt](app/src/main/java/com/minyook/sllm2/data/DocumentGeneration.kt)는 출처와 다른 수치·단위·비교 조건, 근거에 없는 일부 작업 시점·허가 단정, 불완전한 문장 조각 등을 검사합니다. 출력 한도에 닿거나 검증에 실패하면 선택된 문서 구절로 구성한 답변을 보여줍니다. 이 검사는 규칙 기반이며 모든 문장의 사실을 보증하지는 않습니다. PDF 링크는 검색이 반환한 문서 ID와 쪽수로 연결합니다.
5. **근거가 없는 질문과 검색 오류를 구분합니다.** 검색을 마친 뒤 문서 근거가 확인되지 않은 일반 질문에는 EXAONE 일반 지식 경로를 사용하고 화면에 이를 표시합니다. 검색 실패나 질문 대상이 모호한 경우에는 각각의 상태를 안내합니다. 일반 답변은 검사를 거친 내용을 스트리밍하고, 문서 기반 생성은 검증을 마친 본문을 표시합니다. 화면·복사·TTS에는 같은 최종 본문을 사용합니다.

## 로컬 모델과 토큰 정책

| 부분 | 구현과 이유 |
| --- | --- |
| 생성 모델 | LG AI Research 공식 EXAONE 4.0 1.2B Q4_K_M GGUF와 llama.cpp `b12248` JNI를 사용합니다. 약 812 MB 모델은 APK 밖에 저장해 APK에 가중치를 넣지 않고, 다운로드 이후 기기에서 생성할 수 있게 했습니다. Gemma/LiteRT-LM 생성 및 모델 비교 화면은 제거했습니다. |
| 모델 설치 | [ModelDownloadWorker.kt](app/src/main/java/com/minyook/sllm2/model/ModelDownloadWorker.kt)가 WorkManager의 고유 작업으로 다운로드를 관리합니다. HTTP Range로 임시 파일을 이어받고, [LocalModel.kt](app/src/main/java/com/minyook/sllm2/model/LocalModel.kt)에 고정한 크기와 SHA-256이 일치한 파일만 설치합니다. |
| 실행 순서 | 자동 모드는 Hexagon NPU → Adreno OpenCL GPU → CPU 순서입니다. GPU 우선과 CPU도 선택할 수 있습니다. 가속은 Android 14 이상 arm64 Snapdragon SM8650·SM8750·SM8850을 대상으로 시도합니다. Galaxy S24 Ultra `SM-S928N / S928NKSS6DZH2`는 OpenCL에서 앱 종료가 반복되어 해당 조합에서만 GPU를 건너뜁니다. 실제 백엔드와 전환 이유는 모델 화면에 표시합니다. |
| JNI와 취소 | [llama_bridge.cpp](app/src/main/cpp/llama_bridge.cpp)가 토큰화, 디코딩, UTF-8 스트리밍을 담당합니다. Kotlin 런타임은 추론 요청을 직렬화하고 설정이 같은 엔진을 재사용합니다. JNI 콜백은 취소를 확인합니다. 채팅·음성 화면이 대기 중일 때 ONNX를 준비하고, 메모리에 여유가 있으면 생성 중에도 검색 세션을 유지합니다. 메모리 여유가 부족하면 생성 전에 세션을 해제합니다. |
| 출력 토큰 | 앱은 별도의 답변 상한 없이 설정된 문맥에서 실제 질문·근거·대화 토큰을 뺀 나머지 전부를 출력에 사용할 수 있게 합니다. 모델은 답변을 마치면 종료할 수 있습니다. 실제 문맥 한도에 도달하면 안내를 표시합니다. 총 문맥 크기는 기기 메모리 한도 안에서 설정합니다. |

## Android 기능과 작업 서버

- **화면과 기록** — [MainActivity.kt](app/src/main/java/com/minyook/sllm2/MainActivity.kt)가 [TossFieldGuardApp.kt](app/src/main/java/com/minyook/sllm2/ui/TossFieldGuardApp.kt)에 상태와 이벤트를 연결합니다. [ChatHistoryStore.kt](app/src/main/java/com/minyook/sllm2/data/ChatHistoryStore.kt)가 대화와 PDF 출처 ID를 기기에 저장합니다.
- **PDF** — [SourcePdfRepository.kt](app/src/main/java/com/minyook/sllm2/data/SourcePdfRepository.kt)가 번들 원문을 열고 지정된 쪽을 Android PdfRenderer로 렌더링합니다. 같은 화면에서 검색용 추출 텍스트도 확인할 수 있습니다.
- **음성** — [VoiceConversationController.kt](app/src/main/java/com/minyook/sllm2/ui/VoiceConversationController.kt)가 기기에 설치된 한국어 인식·읽기 서비스를 사용합니다. 인식한 문장은 텍스트 채팅과 같은 검색 경로를 거칩니다.
- **가스 수신** — `gas/`의 로컬 Wi-Fi AP와 TCP 서버가 계측기 데이터를 받습니다. 프로토콜과 수신 테스트는 [Wi-Fi AP 문서](docs/ANDROID_WIFI_AP.md)를 참고하세요.
- **작업 흐름** — 작업자 앱에서 시연 요청을 등록하고 별도 감독관 앱에서 점검·승인·종료를 기록합니다. 같은 기기의 두 앱은 Android 서명 권한으로 보호된 작업 기록을 공유합니다. 작업자 앱은 문서 지원과 Wi‑Fi AP 가스 측정을 제공합니다. [작업 시연 절차와 두 APK 빌드 방법](docs/WORKFLOW.md)

## 파일 구조

```text
app/
  build.gradle                      앱 ID, SDK, ONNX 및 네이티브 패키징
  objectbox-models/                 기존 문서 DB 스키마와 ID
  src/main/
    AndroidManifest.xml             앱, 권한, 서비스, 위젯 등록
    java/com/minyook/sllm2/
      MainActivity.kt               Android 화면 진입점
      ui/                           Compose 화면, 상태, Markdown, PDF, 음성
      data/                         문서 DB, 하이브리드 검색, 검증, 대화 기록
      model/                        EXAONE 설치, JNI 호출, 실행 설정
      gas/                          Wi-Fi AP, TCP 수신, 계측 상태, 위젯
    cpp/                            CMake, 고정 llama.cpp, JNI 브리지
    assets/                         문서, PDF, 검색 인덱스와 준비된 ONNX 자산
    res/                            Android 아이콘, 테마, XML
  src/test/                         JVM 단위 및 검색 회귀 테스트
  src/androidTest/                  앱 화면, 실기기 검색·생성 및 PDF 테스트
server/                             기존 작업 API, SQLite, 관리자 CLI 보관
tools/                              모델 자산 준비, PC 검색 평가, 가스 테스트 도구
docs/                               기능 설명과 이전 실험 기록
datasets/                           질문 평가와 분석용 자료
gradle/                             Wrapper, JDK 기준, 의존성 버전
```

<a id="data-storage"></a>

## 데이터 저장 위치

| 데이터 | 위치 |
| --- | --- |
| 대화·출처·토큰 설정 | Android 앱의 SharedPreferences |
| 검색할 문서 원문 | 번들 자산에서 초기 적재한 앱 내부 ObjectBox |
| EXAONE GGUF 및 다운로드 임시 파일 | 앱의 `noBackupFilesDir/models/` |
| PDF 렌더링용 원문 사본 | 앱의 `noBackupFilesDir/source-pdfs/` |
| 서버 작업·승인·세션 | 작업 서버의 SQLite와 별도 `.daily-key` 파일 |

## 검증 방법과 범위

- `.\gradlew.bat :app:testDebugUnitTest`로 검색·근거 규칙·답변 검증·가스 프로토콜을 검사합니다. Android 테스트 자산의 검색 평가 문항은 웹앱 없이 실행됩니다.
- PC에서 실제 ONNX 검색을 검증하려면 루트에서 `npm ci`를 실행하고 `FIELDGUARD_DIRECT_QUESTION=1`을 설정한 뒤 `LiveQuestionEvaluationTest`의 대표 질문 테스트를 실행합니다.
- 모델을 설치한 기기에서는 `RestartChecklistGenerationTest`의 `runDocumentGeneration=true`, `RestartChecklistScreenTest`의 `runProductionChat=true`로 ONNX 검색, EXAONE 답변과 PDF 화면을 확인할 수 있습니다. `FieldGuardSearchLatency`는 ONNX 준비·토큰화·임베딩 시간, `FieldGuardAnswerLatency`는 벡터 계산을 제외한 검색과 본문 표시 시간, `FieldGuardGenerationLatency`는 실제 백엔드·출력 예산·사용 토큰·한도 도달 여부를 기록합니다.
- Galaxy S24 Ultra에서는 실제 ONNX 검색, Hexagon NPU 생성, CPU 실행과 GPU 우선 시 CPU 전환, 답변 화면 및 PDF 연결을 확인했습니다. 다른 Snapdragon 펌웨어의 OpenCL 실행과 전체 질문의 생성 정확도는 전수 검증하지 않았습니다.
