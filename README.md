# HybridTech · O₂ Field Guard

밀폐공간 안전 문서를 근거로 질문에 답하고, 작업 요청부터 감독관 점검·승인, 현장 투입과 퇴장까지 기록하는 프로젝트입니다. **Android는 설치형 앱으로 유지하고, PC·갤럭시 탭·iPhone은 반응형 웹/PWA도 함께 사용합니다.** 질문과 문서 검색은 기기 안에서 처리하며, 작업 요청·승인만 공통 작업 서버를 사용합니다.

최종 갱신: **2026-10-15**. 이 README는 저장소 첫 화면에서 프로젝트 구조, 실행 주소, 실제 소스 경로, 감독관 코드, 테스트 방법을 확인할 수 있도록 정리한 안내입니다.

## 먼저 알아둘 구성

| 구성 | 구현과 역할 | 실행·진입 경로 |
|---|---|---|
| Android 설치형 앱 | Kotlin·Compose 화면, 기기 내 RAG/Gemma, 원본 PDF, 음성, BLE 측정·경보·위젯 | `app/` → Android Studio에서 실행 |
| 반응형 웹/PWA | React·TypeScript 화면, 기기 내 Gemma, 원문 PDF, 대화 기록, 작업 요청·점검 | `web/` → 개발 5174 / 미리보기 4173 |
| 공통 작업 서버 | 기기별 세션, 당일 6자리 감독관 코드, 요청 소유권·승인 권한 검사, 작업 기록 | `web/server/` → Vite 개발·미리보기와 함께 실행 |
| 공통 작업 화면 | 작업자 요청·감독관 점검·승인·투입·대피·종료 | `web/src/features/WorkChecklist.tsx`; 앱은 이 화면을 제한된 WebView로 사용 |

**웹 디자인을 공통 기준으로 사용합니다.** 홈 문구·파랑/회색 색상·흰색 카드·문서 표지·메뉴 명칭을 앱에도 적용했습니다. 작은 화면은 `홈 / 질문 / 문서 / 설정`, 넓은 앱 화면은 웹처럼 사이드바를 사용합니다. 앱의 가스 측정·음성·위젯은 Android 기능으로 이어지며, 웹에서 측정값을 만들어 표시하지 않습니다.

## 앱 디자인 화면

아래 파일은 `WorkspaceDesignTest`가 실제 Compose 화면을 렌더링해 저장하는 검증 자료입니다. 테스트 문서와 가상 상태를 사용하며 모델 다운로드·BLE·실제 작업을 시작하지 않습니다. 촬영 기기와 최종 결과는 [검증 기록](web/docs/validation.md)에 기록합니다.

| 홈 | 설정 |
|---|---|
| ![웹 기준 Android 홈](docs/screenshots/android-workspace-home.png) | ![웹 기준 Android 설정](docs/screenshots/android-workspace-settings.png) |

[문서 보관함 화면](docs/screenshots/android-workspace-library.png) · [앱 내 공통 감독관 화면](docs/screenshots/android-workspace-supervisor.png)

## 빠른 실행: 웹

필요 환경은 **Node.js 24.15 이상**입니다. PowerShell에서는 `npm.cmd`를 사용합니다. 아래 명령은 저장소 루트 기준입니다.

```powershell
cd web
npm.cmd ci
npm.cmd run dev
```

- **개발 주소: `http://localhost:5174/`** — 수정 내용이 바로 반영됩니다.
- **PWA·오프라인 시험: `http://localhost:4173/`** — 다음 빌드·미리보기 명령을 사용합니다.
- 5173은 이 프로젝트의 주소가 아닙니다. 5174·4173이 이미 사용 중이면 다른 포트로 자동 변경하지 않습니다.
- `docs/webapp-ui/`는 예전 정적 시안입니다. 현재 앱은 반드시 `web/`에서 실행합니다.

```powershell
npm.cmd test
npm.cmd run build
npm.cmd run preview
```

개발 모드는 서비스 워커를 등록하지 않습니다. 이전 PWA가 열려 있다면 같은 주소의 탭·홈 화면 앱을 모두 닫았다가 다시 열어 새 버전을 적용합니다. `localhost`와 `127.0.0.1`, 그리고 5174와 4173의 모델·대화 저장소는 서로 다릅니다.

## 갤럭시 탭에서 웹/PWA 테스트

1. 탭에서 Chrome을 업데이트하고 개발자 옵션의 USB 디버깅을 켭니다.
2. 데이터 USB 케이블로 PC에 연결하고 디버깅 허용을 승인합니다.
3. PC에서 위의 `npm.cmd run preview`를 켜 둡니다.
4. 새 PowerShell에서 아래 명령을 실행하고 탭 Chrome의 `http://localhost:4173/`을 엽니다.

```powershell
$tabletAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $tabletAdb devices
& $tabletAdb reverse tcp:4173 tcp:4173
```

개발 서버를 테스트하려면 두 포트를 모두 5174로 바꿉니다. 테스트 중 PC 서버와 USB 연결을 유지합니다. 여러 단말이 연결된 경우 ADB 명령에 `-s <기기-ID>`를 추가합니다.

설정의 **전체 오프라인 파일 준비 → Gemma 4 E2B 다운로드 받기**를 실행하면 모델 검증·AI 준비가 자동으로 이어집니다. 탭에는 PC와 별도로 약 2.01GB 모델을 저장해야 합니다. 문서·PDF의 오프라인 시험은 준비 완료 후 PC 서버도 중지하고 탭 인터넷을 끊어 진행합니다. USB 경로가 살아 있는 상태를 완전 오프라인으로 판단하지 않습니다.

## 감독관: 오늘의 6자리 암호코드

기본 사용자는 작업자입니다. 작업 전 점검의 **감독관 화면으로 전환** 또는 설정에서 6자리 코드 입력 화면을 엽니다. 작업자가 전환 버튼을 누르거나 주소를 직접 입력해도 **코드 확인 전에는 감독관 기록과 승인 기능을 이용할 수 없습니다.** 권한은 서버가 목록 조회와 상태 변경마다 다시 검사합니다.

서버 관리자만 `web/`에서 실행합니다.

```powershell
npm.cmd run supervisor -- today
npm.cmd run supervisor -- rotate
npm.cmd run supervisor -- revoke
```

| 명령·규칙 | 동작 |
|---|---|
| `today` | 한국 날짜 기준 오늘의 6자리 숫자 코드 확인. 같은 날 재조회하면 같은 코드 |
| `rotate` | 오늘 코드를 즉시 교체하고 기존 감독관 인증도 무효화 |
| `revoke` | 오늘 코드와 해당 코드로 인증한 모든 기기를 사용 중지 |
| 유효기간 | 한국 시간 다음 날 00:00까지. 자정에 코드와 감독관 권한 만료 |
| 사용 인원 | 감독관 여러 명이 당일 같은 코드를 사용. 잠근 뒤에도 당일 코드로 재인증 가능 |
| 잘못된 입력 | 기기별 5회 실패 후 15분 제한. 새 기기를 반복 생성하는 시도도 접속 단위 제한 |
| 기록 | 공통 코드는 개인 신원을 증명하지 않음. 서버가 기기별 감독관 ID를 구분해 사건에 기록 |

코드는 서버 관리자 CLI에서만 확인하고 감독관에게만 전달합니다. 실제 코드를 README·소스·공개 환경변수에 쓰지 않습니다. 코드 원문을 DB에 저장하지 않고 별도 서버 키로 계산합니다. 세션 토큰은 해시로 보관하며 HttpOnly·SameSite 쿠키와 CSRF 검사를 사용합니다. 이전 일회용 코드·8시간 인증 방식은 당일 6자리 방식으로 대체했습니다.

개인 로그인, 감독관의 현장별 배정, 계정 복구, 푸시 알림은 아직 없습니다. 인증된 감독관은 현재 서버 프로젝트의 작업을 점검할 수 있습니다.

## 작업 전 점검 흐름

1. **작업자**: 이름·현장·위치·작업 내용을 입력하고 점검 요청.
2. **감독관**: 당일 코드 인증 후 요청을 선택. 가스·보호구·통신·환기·작업자 상태 5항목과 수동 측정값·장비·시각·감시인·연락·투입 유효기한 입력.
3. **감독관**: `점검 내용 저장` 후 `점검 완료 · 투입 승인 기록`. 누락·문서 기준 이탈로 승인할 수 없으며 보완 요청·승인 취소 가능.
4. **작업자**: 승인 당시 내용을 확인하고 `작업자 현장 투입 확인`.
5. **작업 중**: 기기 내 문서 지원 사용. 위험 상황은 직접 확인한 내용을 입력해 수동 신고.
6. **작업자**: 정상 퇴장 또는 긴급 탈출 후 실제 집결 위치 기록.
7. **감독관**: 퇴장 기록을 확인하고 작업 종료.

작업자는 본인 요청만 확인합니다. 같은 서버에 연결한 PC·탭·앱은 요청과 승인 결과를 공유하고 화면 진입·복귀 및 15초 간격으로 갱신합니다. Chrome과 Android WebView는 별도 기기로 식별되므로 이름이 같아도 소유권을 합치지 않습니다. 작업 서버의 상태 변경은 SQLite 트랜잭션과 버전으로 중복 갱신을 막고, 승인 당시 점검 내용도 보존합니다.

**새 작업·점검·승인·투입/퇴장 기록에는 서버 연결이 필요합니다.** 문서 검색·PDF·기기 내 AI와 기존 로컬 이력은 준비 후 오프라인으로 사용할 수 있습니다. 가스값은 수동 입력 기록이며 웹 센서 실시간 수신·가스 예측·자동 대피 경로는 구현하지 않았습니다. 실제 작업허가를 자동 발급하는 시스템이 아닙니다.

## Android 설치형 앱 실행과 디자인 확인

저장소 루트를 Android Studio로 열어 `app`을 실행합니다. 현재 구성은 `compileSdk/targetSdk 37`, `minSdk 26`, Kotlin·Compose·ObjectBox·LiteRT-LM입니다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

이 PC에서 사용하는 JDK는 `C:\Program Files\Android\Android Studio1\jbr`, SDK는 `C:\Users\limmi\AppData\Local\Android\Sdk`입니다. 다른 PC에서는 해당 설치 경로와 `JAVA_HOME`/Android Studio Gradle JDK를 맞춰 주세요. `local.properties`와 로컬 Gradle 캐시는 Git에서 제외합니다.

| 결과물 | 정확한 경로 |
|---|---|
| Android 디버그 APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 계측 테스트 APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| 이 PC에서 보관한 디자인 검증용 APK | `app/build/outputs/apk/designpreview/app-designpreview.apk` (생성물, Git 제외) |
| Android 단위 테스트 결과 | `app/build/test-results/testDebugUnitTest/` |
| 웹 프로덕션 파일 | `web/dist/` |

기존 앱과 빌드 서명이 다르면 업데이트 설치가 거부될 수 있습니다. 기존 앱·모델을 삭제하지 않고 디자인을 확인하려면 별도 패키지를 빌드합니다.

```powershell
.\gradlew.bat -PdesignPreview=true :app:assembleDebug :app:assembleDebugAndroidTest
```

디자인 빌드는 `com.minyook.sllm2.designpreview`, 앱 이름은 **Field Guard 디자인**입니다. 일반 빌드와 출력 APK 위치는 같으므로 파일의 빌드 종류를 구분해 설치합니다. 기존 설치 앱과 데이터는 보존됩니다. 앱의 **설정 → 현장 작업 서버**에 HTTPS 서버 기본 주소를 입력하면 웹과 같은 점검 화면을 사용합니다. 개발 빌드의 USB 시험에서는 `http://localhost:4173`도 허용하며 위의 ADB 포트 연결이 필요합니다.

앱 전체를 웹으로 바꾸지 않았습니다. 홈·문서·질문·설정은 Compose, 작업 요청·감독관 점검은 공통 React 화면을 사용하는 제한된 WebView입니다. WebView에서 질문·모델 준비를 누르면 기존 Android 화면으로 돌아갑니다. 임의 외부 출처·파일 접근·JavaScript 네이티브 인터페이스는 열지 않습니다.

## 문서·모델·Markdown·스크롤

- 공식 문서 2종, 총 87쪽의 검색 데이터와 실제 PDF를 함께 사용합니다. 답변 근거는 문서 ID와 실제 쪽수를 유지합니다.
- 웹 모델은 `web/src/ai/catalog.ts`에 고정한 **Gemma 4 E2B 웹용 `.litertlm`**, 2,008,432,640바이트입니다. 다운로드 전체 크기·SHA-256 확인 후 OPFS에 저장합니다. Android용 모델 파일과 구분합니다.
- 다운로드 완료·웹 재접속 후 AI를 자동 준비합니다. 실행·메모리 해제 버튼은 없습니다. 파일 검증·WASM/GPU 로드·생성은 백그라운드 Worker에서 처리합니다.
- 웹은 최초 저장 파일 전체 검사 후 검증 기록을 보관합니다. 후속 실행은 크기·수정 시각·분산 표본과 7일 유효기간을 검사하고 필요 시 전체 SHA-256을 다시 확인합니다. 표본 검사는 전체 검사와 보장 범위가 다르며 엔진 적재 시간은 여전히 필요합니다.
- `모델 파일 저장됨`과 `AI 사용 가능`은 구분합니다. 재접속의 불러오기는 모델 재다운로드를 뜻하지 않습니다.
- 답변/문맥 토큰 설정은 실제 SDK에 전달하고 SDK 측정 사용량만 표시합니다. 웹 기본값은 답변 1,536·문맥 8,192이며 Android는 기기 메모리에 맞춘 기존 설정을 사용합니다.
- 웹 답변은 한국어 Markdown의 제목·굵게·목록·표·코드를 표시합니다. 생성 HTML·원격 이미지 자동 로드는 허용하지 않습니다. 비정상 문자권 혼입·깨진 문자·심한 반복은 원문 표시로 전환하며 사실 정확도 전체를 보장하는 검사는 아닙니다.
- 답변 중에도 자유롭게 스크롤합니다. 다시 생성 중인 마지막 줄이 보이는 위치로 내려오면 자동 따라가기를 재개하고, 답변 완료 후 위치를 강제로 바꾸지 않습니다.

## 파일과 디렉터리 지도

아래 경로는 모두 **저장소 루트 기준**입니다. 현재 작업 폴더는 `C:\Users\limmi\AndroidStudioProjects\Sllm2`입니다.

```text
Sllm2/
├─ README.md                         프로젝트 전체 안내: 이 문서
├─ app/                              기존 Android 설치형 앱
│  ├─ build.gradle                   SDK·의존성·디자인 검증용 패키지 설정
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml
│     │  ├─ assets/knowledge/seed_chunks_v1.json
│     │  ├─ assets/source-pdfs/       공식 PDF 원본 2종
│     │  ├─ java/com/minyook/sllm2/   아래 Android 소스 표 참고
│     │  └─ res/xml/work_network_security.xml
│     ├─ test/                       JVM 단위 테스트
│     └─ androidTest/                실제 기기 Compose·PDF 테스트
├─ web/
│  ├─ src/                           React 화면·로컬 AI·저장소
│  ├─ server/                        작업 API·SQLite·감독관 코드 CLI
│  ├─ scripts/                       원본 준비·Worker·PWA 빌드
│  ├─ tests/                         Vitest 회귀 테스트
│  ├─ docs/                          웹 검증·작업 흐름·화면
│  ├─ public/                        아이콘·PWA 설정·생성된 배포 자산
│  ├─ .local/                        작업 DB·서버 키: Git 제외
│  └─ dist/                          생성된 배포 결과: Git 제외
└─ docs/
   ├─ ANDROID_APP_SPEC.md             Android 현행 설계서
   ├─ OFFLINE_RAG_ARCHITECTURE.md     Android 검색·근거 설계
   ├─ webapp/TARGET_ARCHITECTURE.md   웹·공통 작업 서버 현행 설계서
   ├─ webapp/BUILD_PROMPT.md          유지보수 기준
   ├─ webapp-ui/                     과거 정적 시안: 현재 앱 아님
   └─ screenshots/                   Android 디자인 검증 화면
```

### Android 주요 소스

기준 디렉터리: `app/src/main/java/com/minyook/sllm2/`.

| 파일·폴더 | 역할 |
|---|---|
| `MainActivity.kt` | Compose 진입, ViewModel·음성·권한·생명주기 연결 |
| `SllmApplication.kt` | ObjectBox·알림 초기화 |
| `ui/TossFieldGuardApp.kt` | 공통 화면 틀·작은 화면 메뉴·채팅·문서·센서·상세 화면 |
| `ui/FieldWorkspaceUi.kt` | 웹 기준 디자인 토큰·홈·사이드바·문서 표지·설정 카드 |
| `ui/FieldGuardViewModel.kt` | 앱 상태·기존 RAG/모델/BLE 호출·작업 서버 주소 저장 |
| `ui/NativeWorkPage.kt` | 공통 작업 화면 WebView·서버 설정·네이티브 질문 전환 |
| `ui/WorkServerAddress.kt` | HTTPS/개발 localhost·동일 출처 검증 |
| `ui/MarkdownText.kt`, `SourcePdfPage.kt` | Markdown 답변·원본 PDF 표시 |
| `ui/VoiceConversationController.kt` | 기기 음성 인식·응답 음성 경로 |
| `data/KnowledgeRepository.kt` | ObjectBox 검색·근거 선택 |
| `data/QuestionEvidencePlanner.kt`, `QuestionAnswerVerifier.kt` | 복합 주제·근거 누락/충돌 검사 |
| `data/SourcePdfCatalog.kt`, `SourcePdfRepository.kt` | 번들 PDF 판본·해시·실제 쪽 렌더링 |
| `model/LocalModelRuntime.kt`, `ModelDownloadWorker.kt` | Android LiteRT-LM·모델 다운로드 |
| `gas/` | BLE 수신·측정 서비스·데모 구분·위젯·알림 |

현재 진입 UI는 `TossFieldGuardApp`입니다. `ui/HybridTechApp.kt`와 `HybridTechTheme.kt`는 이전 화면 구현과 공통 액션 선언을 포함하므로 현재 앱의 기준 화면과 혼동하지 않습니다.

### 웹·작업 서버 주요 소스

| 정확한 경로 | 역할 |
|---|---|
| `web/src/App.tsx` | 홈·질문·보관함·기록·탐색·권한별 메뉴·앱 내 표시 모드 |
| `web/src/features/WorkChecklist.tsx` | 작업자·감독관 공통 작업 화면 |
| `web/src/features/SupervisorAccess.tsx` | 6자리 숫자 입력·인증·잠금 |
| `web/src/features/Settings.tsx`, `PdfPage.tsx` | 모델·토큰·파일 준비·원본 PDF |
| `web/src/workflow/work.ts` | 상태 전환·승인 조건·사건 스냅샷 |
| `web/src/workflow/access.ts`, `native.ts` | 서버 세션·API·Android 전환 |
| `web/server/store.ts` | SQLite·요청 소유권·감독관 권한·한국 날짜/당일 코드 |
| `web/server/api.ts`, `plugin.ts`, `manage.ts` | HTTP API·Vite 연결·코드 관리 CLI |
| `web/src/ai/catalog.ts`, `model.ts` | 공식 모델 정보·다운로드·저장·검증 재사용 |
| `web/src/ai/runtime.ts`, `worker-engine.ts`, `engine.worker.ts` | 자동 준비·Worker 통신·실제 SDK 추론 |
| `web/src/ai/preferences.ts`, `output-quality.ts` | 토큰 설정·비정상 생성 검사 |
| `web/src/chat/scroll.ts`, `useChatScroll.ts` | 수동 스크롤 보존·생성 위치 자동 재개 |
| `web/src/rag/search.ts`, `storage/db.ts` | 로컬 검색·IndexedDB 기록/설정 |
| `web/scripts/prepare-assets.mjs`, `build-ai-worker.mjs` | Android 자산 복사·SDK Worker 생성 |
| `web/scripts/build-pwa.mjs`, `sw-template.js` | 버전별 셸 캐시·API 캐시 제외 |
| `web/vite.config.ts`, `package.json` | 포트·COOP/COEP·작업 API·명령 |

## 저장 위치와 배포 경계

| 위치 | 저장하는 것 |
|---|---|
| Android `context.noBackupFilesDir/models/gemma-4-e2b-it.litertlm` | 설치된 Android 모델 (`.part`는 다운로드 중 파일) |
| Android `context.noBackupFilesDir/source-pdfs/` | 첫 사용 시 복사·검증한 번들 PDF |
| Android `SharedPreferences` | `field_guard_ui`: 서버 주소·온보딩·이전 체크, `inference_settings`: 추론 설정, `local_model`: 모델 상태. `local_chat_history`: 대화, `gas_reading`: 가스 기록 |
| 브라우저 IndexedDB `fieldguard-web` v2 | 대화·토큰 설정·모델 메타데이터/검증 기록·이전 로컬 작업 |
| 브라우저 OPFS | 검증 후 저장한 웹 모델 |
| Cache API·PWA 셸 | 준비된 검색 데이터·PDF·런타임·앱 화면 |
| `web/.local/workflow.sqlite` | 새 작업·사건·기기 세션 해시·당일 코드 상태 |
| `web/.local/workflow.sqlite.daily-key` | 당일 코드 계산용 서버 비밀 키 |

서버 DB와 키는 서버를 중지한 뒤 함께 백업합니다. `.local/`, 모델·쿠키·키·실제 코드, `node_modules`, `dist`, 생성 자산, APK, Gradle/테스트 임시 파일은 Git에 올리지 않습니다. 이전 로컬 작업을 서버 승인으로 가져오지 않으며 읽기 전용 이력으로 보존합니다. 기기 기록 삭제는 서버 작업·승인을 삭제하지 않습니다. JSON 내보내기는 기기 이력과 본인 서버 기록을 구분합니다.

위 Android 경로는 PC 저장소의 `app/` 아래가 아니라 **단말의 앱 전용 저장소**입니다. `noBackupFilesDir`의 실제 루트는 일반적인 기본 사용자에서 `/data/user/0/<패키지명>/no_backup/`이며 운영체제·프로필에 따라 달라집니다. 디자인 검증용 패키지는 별도 루트를 사용합니다. 브라우저 모델도 PC 프로젝트 폴더가 아닌 접속 origin별 OPFS에 저장되므로 고정된 Windows 파일 경로로 접근하지 않습니다.

웹 정적 출력은 `web/dist/`입니다. 문서·AI에는 HTTPS·COOP/COEP·WASM MIME 설정이 필요합니다. **감독관 인증·새 점검은 서버 API가 필요하므로 정적 파일만 게시해서는 동작하지 않습니다.** 현재 작업 API는 Vite 개발·미리보기용이며 외부 운영 배포·운영 DB 백업 자동화·개인 계정 연동은 수행하지 않았습니다. iPhone은 HTTPS 주소를 Safari에서 열고 공유 → 홈 화면에 추가로 설치합니다. 실기기 iPhone 메모리·발열·재부팅 후 저장 유지 시험은 남아 있습니다.

## 검증 방법과 상세 문서

```powershell
cd web
npm.cmd test -- --maxWorkers=1
npm.cmd run build
```

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Android UI 계측 테스트: `TossNavigationTest`, `WorkspaceDesignTest`. 원본 PDF 검증은 `data/SourcePdfRepositoryTest`입니다. 웹 검증에는 검색·Markdown·스크롤·모델 무결성·검증 재사용·토큰·날짜 경계·당일 코드 공동 사용·권한 우회 거부·동시 갱신을 포함합니다. 검색 회귀 정답 쪽 포함 결과는 29/30이며 AI 생성 답변 전체의 정확도 점수와 구분합니다. 최신 실행 수치·실기기 결과·남은 항목은 아래 검증 기록을 기준으로 합니다.

2026-10-15 최종 결과: **웹 126개·Android JVM 15개·갤럭시 탭 UI 계측 10개 통과**, 웹 프로덕션 및 Android APK 빌드 성공. 앱 화면은 Galaxy Tab S9 Ultra(SM-X910, Android 16)의 실제 렌더링 캡처이며 UI 테스트는 가상 상태를 사용합니다. 새 UI·공통 WebView 검증과 실제 모델 추론·BLE 장비 검증을 구분합니다.

| 문서 | 내용 |
|---|---|
| [Android 현행 설계서](docs/ANDROID_APP_SPEC.md) | 네이티브 화면·RAG·BLE·공통 작업 화면 연결 |
| [웹·서버 현행 설계서](docs/webapp/TARGET_ARCHITECTURE.md) | React·Worker·저장소·인증·API 구조 |
| [작업 흐름 설계](web/docs/workflow.md) | 당일 6자리 코드·역할·작업 상태·승인 조건 |
| [웹 실행 안내](web/README.md) | 모델·토큰·PWA·서버 명령·배포 설정 |
| [검증 기록](web/docs/validation.md) | 자동 검사·실제 화면·기기·측정 조건 |
| [Android 오프라인 RAG](docs/OFFLINE_RAG_ARCHITECTURE.md) | 기존 기기 내 근거 검색·검증 |
| [유지보수 기준](docs/webapp/BUILD_PROMPT.md) | 이후 변경에서 보존할 동작 |

현재 BLE 수신 기반과 데모 구분은 구현되어 있지만 특정 제조사 프로토콜·현장 장비 정확도는 검증 전입니다. 실제 작업허가·현장 안전 판단·대피 절차는 담당자의 현장 확인을 따릅니다.
