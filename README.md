# HybridTech O₂ Field Guard

> 밀폐공간 작업을 위한 **온디바이스 문서 RAG·Gemma 4 E2B 채팅·BLE 가스 모니터링** Android 앱입니다.

HybridTech O₂ Field Guard는 작업자가 휴대전화 안에서 안전작업 문서를 검색하고, 다운로드한 로컬 Gemma 모델로 답변을 생성하며, Bluetooth LE 가스 검출기의 수치를 확인할 수 있게 만든 Kotlin 기반 Android 앱입니다.

모델 추론과 문서 검색은 모델 다운로드 이후 기기 안에서 수행됩니다. 네트워크는 **Gemma 모델을 앱에서 내려받는 과정**에만 필요합니다.

> [!WARNING]
> 이 저장소의 BLE 연동은 다양한 검출기의 일반적인 알림 패킷을 읽는 기반 구현입니다. 실제 현장 안전 판정이나 법정 측정기로 사용하려면 제조사별 GATT UUID, 패킷 규격, 교정·정확도·통신 단절 처리 및 현장 검증을 반드시 추가해야 합니다. 데모 시뮬레이션 값은 실제 측정값이 아닙니다.

## 구현 상태

| 영역 | 구현 내용 | 상태 |
| --- | --- | --- |
| 문서 RAG | 제공 PDF 2종 87개 청크를 ObjectBox에 저장하고 오프라인 검색 | 구현됨 |
| 로컬 생성 AI | Gemma 4 E2B LiteRT-LM 모델 다운로드·저장·CPU/GPU 폴백 생성 | 구현됨 |
| 채팅 | Markdown 답변, 대화 이력, 새 대화, 최근 답변 이동 | 구현됨 |
| 음성 입력 | 마이크 버튼, Android 음성 인식, 시스템 음성 입력 폴백, `오투야` 호출 대기 UI | 구현됨 — 기기 음성 서비스에 따라 동작 여부가 달라짐 |
| BLE 가스 연동 | BLE 검색·연결·Notify/Indicate 수신·일반 텍스트 패킷 파싱 | 기반 구현됨 — 제조사 프로토콜 연동 필요 |
| 가스 시뮬레이션 | 안정 범위의 임의 O₂/H₂S/CO/LEL 값, 20초 주기 갱신 | 구현됨 — 데모 전용 |
| 백그라운드 표시 | 포그라운드 서비스 알림 카드와 홈 화면 앱 위젯 | 구현됨 |
| 설정 | 모델 관리, 추론 백엔드, 문맥/응답 토큰 범위, 설정 영속화 | 구현됨 |

## 핵심 화면

- **현장 채팅**: ChatGPT 스타일의 대화 화면입니다. 질문 입력, 빠른 질문, 음성 입력, Markdown 답변, 최근 답변으로 이동 버튼을 제공합니다.
- **대화 기록**: 최근 20개 세션과 세션별 최대 80개 대화를 기기에 보관합니다. 대화 내용은 `SharedPreferences`에만 저장됩니다.
- **센서 연결 상태**: BLE 검출기 검색·연결·연결 해제와 현재 O₂, H₂S, CO, LEL 수치를 보여 줍니다.
- **설정**: Gemma 모델 다운로드와 준비 상태, CPU/GPU/자동 백엔드, 기기 RAM 기반 문맥 한도, 응답 토큰 수를 관리합니다.
- **상단 가스 바**: 마지막 수신 가스값과 출처(BLE/시뮬레이션)를 채팅 상단에서 계속 확인할 수 있습니다.

## 오프라인 RAG 구조

Gemma가 PDF를 직접 여는 방식이 아니라, 앱이 먼저 관련 문서 조각을 검색하고 그 근거를 Gemma 프롬프트에 넣는 구조입니다.

```text
PDF 텍스트 추출본(JSON)
  └─ 2개 문서 / 87개 청크
       └─ ObjectBox 저장
            ├─ 본문·제목·페이지·검색 텍스트
            └─ 384차원 로컬 검색 벡터

사용자 질문(텍스트 또는 음성 인식 텍스트)
  └─ 동일한 384차원 벡터 생성
       └─ ObjectBox HNSW 최근접 벡터 검색
            + 단어 일치 점수 재정렬
                 └─ 상위 PDF 근거 2개
                      └─ [근거 + 질문]을 Gemma 4 E2B에 전달
                           └─ Markdown 답변
```

### 포함 문서

| 문서 | 청크 수 | 용도 |
| --- | ---: | --- |
| `밀폐공간 질식재해예방 안전작업 가이드 (2022)` | 84 | 환기, 측정, 보호구, 작업·구조 안전 기준 등의 상세 근거 |
| `밀폐공간 질식사고 위험작업 자율점검표` | 3 | 현장 점검 항목과 사전 확인 근거 |

문서 원본에서 추출한 데이터는 [`app/src/main/assets/knowledge/seed_chunks_v1.json`](app/src/main/assets/knowledge/seed_chunks_v1.json)에 포함됩니다. 앱 첫 실행 시 `KnowledgeSeeder`가 이 데이터를 ObjectBox에 적재합니다. 시드 버전이 같고 청크 수가 유지되면 재적재하지 않습니다.

### 검색·답변 생성 흐름

1. 채팅 입력과 음성 인식 결과는 모두 `MainActivity.answerQuestion()`으로 들어갑니다.
2. `KnowledgeRepository.retrieve()`가 질문을 384차원 로컬 해시 임베딩으로 바꿉니다.
3. ObjectBox의 HNSW 코사인 최근접 검색 결과와 `searchableText` 단어 일치도를 합칩니다.
   - 벡터 유사도: **72%**
   - 단어 일치도: **28%**
4. 상위 근거 2개를 문맥으로 만들어 Gemma에 전달합니다. 절차나 표가 잘리지 않도록 원문 청크는 그대로 유지합니다.
5. Gemma는 “검색 문서만 근거로, 수치·단위·이상/미만을 정확히” 답하도록 프롬프트를 받습니다.
6. 모델이 준비되지 않았거나 생성이 실패하면, 같은 검색 결과를 문서 근거와 페이지 출처로 바로 표시합니다.

현재 임베딩은 앱 용량과 초기 실행 부담을 낮추기 위한 `OfflineHashEmbedding`입니다. 즉, 생성 모델인 Gemma 4 E2B와 별도의 대형 문장 임베딩 모델은 아직 탑재하지 않았습니다. 표현이 크게 달라지는 질문의 의미 검색 정확도를 더 높여야 한다면, 향후 임베딩 전용 온디바이스 모델로 교체하는 것이 다음 개선 지점입니다.

### RAG 관련 주요 파일

| 파일 | 역할 |
| --- | --- |
| [`KnowledgeRepository.kt`](app/src/main/java/com/minyook/sllm2/data/KnowledgeRepository.kt) | ObjectBox 벡터·키워드 하이브리드 검색, 모델 폴백 근거 생성 |
| [`KnowledgeSeeder.kt`](app/src/main/java/com/minyook/sllm2/data/KnowledgeSeeder.kt) | 번들 PDF 청크를 최초 1회 DB에 적재 |
| [`KnowledgeChunk.kt`](app/src/main/java/com/minyook/sllm2/data/KnowledgeChunk.kt) | ObjectBox 엔터티와 HNSW 벡터 인덱스 정의 |
| [`OfflineHashEmbedding.kt`](app/src/main/java/com/minyook/sllm2/data/OfflineHashEmbedding.kt) | 질문·문서 공통 384차원 로컬 임베딩 |
| [`LocalModelRuntime.kt`](app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt) | 검색 근거를 포함한 Gemma 프롬프트 구성과 로컬 생성 |

## Gemma 4 E2B 모델 관리

앱 APK에 약 2GB 이상 모델을 포함하지 않습니다. 설정 화면의 **앱에서 다운로드**를 누르면 `WorkManager`가 고유 작업으로 다운로드를 시작합니다.

- 대상 모델 파일: `gemma-4-E2B-it.litertlm`
- 저장 위치: 앱의 `noBackupFilesDir/models/`
- 다운로드 중: 포그라운드 알림과 진행률 표시
- 재시작 후: 파일 크기와 저장된 상태를 확인해 준비 상태를 복원
- 추론 엔진: LiteRT-LM
- 백엔드: 자동(GPU 우선), GPU 우선(실패 시 CPU), CPU 안전 모드
- 엔진 생성 실패: 다른 백엔드로 순차 폴백

### 기기 성능 기반 토큰 설정

Gemma의 이론상 문맥 창은 128,000 토큰이지만, 모바일 기기는 모델·Android·GPU 드라이버의 메모리를 함께 사용합니다. 앱은 총 RAM과 저메모리 상태를 보고 안전한 문맥 상한을 정합니다.

| 기기 RAM | 앱 문맥 상한 |
| --- | ---: |
| 6GB 미만 또는 저메모리 기기 | 2,048 토큰 |
| 6GB 이상 ~ 8GB 미만 | 4,096 토큰 |
| 8GB 이상 ~ 12GB 미만 | 8,192 토큰 |
| 12GB 이상 ~ 16GB 미만 | 16,384 토큰 |
| 16GB 이상 ~ 24GB 미만 | 24,576 토큰 |
| 24GB 이상 | 32,768 토큰 |

응답 토큰은 선택한 문맥의 절반 이하, 최대 8,192 토큰으로 제한됩니다. 이는 “무제한”처럼 보이는 설정으로 인한 OOM·ANR 위험을 줄이기 위한 보호 장치입니다.

## BLE 가스 검출기와 백그라운드 모니터링

### BLE 연결 흐름

1. 센서 연결 화면에서 Bluetooth 권한을 요청하고 주변 BLE 기기를 검색합니다.
2. 사용자가 기기를 선택하면 GATT 연결 후 Notify 또는 Indicate 가능한 특성(characteristic)을 구독합니다.
3. 수신 데이터는 `GasPacketParser`가 아래 형식으로 해석합니다.

```text
JSON:      {"o2":20.9,"h2s":0.0,"co":0.0,"lel":0.0}
Key-value: O2=20.9,H2S=0.0,CO=0.0,LEL=0.0
CSV:       20.9,0.0,0.0,0.0
```

4. 파싱된 값은 앱 내부 저장소에 마지막 수신 시각·기기명·출처와 함께 보관됩니다.
5. `GasMonitoringService`가 포그라운드 서비스로 유지되며, 알림 패널과 앱 UI를 갱신합니다.

지원 채널은 O₂(%), H₂S(ppm), CO(ppm), LEL(%)입니다. 현재는 UUID나 제조사 바이너리 프레임을 고정하지 않은 범용 수신기이므로, 실제 장비를 붙일 때는 해당 제조사의 서비스 UUID·특성 UUID·단위·CRC·경보 상태 규격을 `BleGasClient`에 반영해야 합니다.

### 데모 시뮬레이션

모델 준비 이후 실제 BLE 측정이 없는 상황에서는 `GasSimulationService`가 안정 범위의 임의 가스값을 20초마다 발행할 수 있습니다. 화면·알림에는 반드시 **데모 시뮬레이션 / 실제 측정값 아님**으로 구분됩니다. 실제 BLE 수신이 시작되면 BLE 값이 우선합니다.

### 알림·위젯

- **알림 패널**: 포그라운드 서비스의 맞춤 카드에 O₂, H₂S, CO, LEL, 수신 시각을 표시합니다.
- **홈 화면 위젯**: `GasWidgetProvider`가 마지막 저장 가스값을 표시합니다.
- 알림과 위젯 모두 마지막 저장값을 사용하므로 앱 화면이 닫혀 있어도 서비스가 동작하는 동안 최신 값을 표시할 수 있습니다.

> Android의 알림/위젯 표시 방식은 제조사 런처와 OS 버전에 따라 다를 수 있습니다. Android 13 이상에서는 알림 권한이 필요합니다.

## 음성 입력

- 전송 버튼 옆 마이크 버튼으로 Android `SpeechRecognizer`를 시작합니다.
- 음성 인식 결과는 일반 텍스트 질문과 **같은 RAG 경로**로 전달됩니다.
- 시스템 음성 입력 Intent를 폴백으로 사용합니다.
- `오투야` 호출 대기 상태를 UI에서 켤 수 있습니다.

음성 인식 엔진은 기기 제조사, 설치된 Google 앱/음성 서비스, 언어팩, 네트워크·오프라인 음성팩 상태에 영향을 받습니다. 따라서 RAG와 Gemma 추론은 로컬이더라도 음성 인식 자체가 모든 기기에서 완전 오프라인으로 보장되지는 않습니다.

## 권한과 로컬 데이터

| 권한 | 용도 |
| --- | --- |
| `INTERNET` | 최초 Gemma 모델 다운로드 |
| `POST_NOTIFICATIONS` | 모델 다운로드·가스 모니터링 알림 |
| `RECORD_AUDIO` | 음성 질문 입력 |
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` | BLE 검출기 검색·연결 |
| Foreground Service | 장시간 모델 다운로드와 가스 모니터링 |

앱은 문서 청크, RAG 벡터 DB, 채팅 이력, 모델 설정, 마지막 가스값을 앱 전용 저장소에 보관합니다. 현재 구현에는 사용자 질의·채팅·가스 데이터를 별도 서버로 전송하는 API가 없습니다.

## 프로젝트 구조

```text
app/
├─ objectbox-models/                 # ObjectBox 스키마
├─ src/main/assets/knowledge/         # PDF 텍스트 청크 시드
├─ src/main/java/com/minyook/sllm2/
│  ├─ MainActivity.kt                # 화면 전환, 채팅, 음성, BLE UI
│  ├─ data/                          # ObjectBox RAG, 문서 시드, 채팅 이력
│  ├─ model/                         # Gemma 다운로드·상태·LiteRT 추론·설정
│  └─ gas/                           # BLE, 가스 파서, 서비스, 알림, 위젯
├─ src/main/res/                     # XML UI, 색상, 아이콘, 알림·위젯 레이아웃
└─ src/test/                         # JVM 단위 테스트
```

## 빌드 및 실행

### 요구 사항

- Android Studio 최신 안정판
- Android SDK 37
- 최소 Android API 26 (Android 8.0)
- JDK 11 호환 빌드 설정

### Android Studio

1. 이 저장소를 Android Studio에서 엽니다.
2. Gradle 동기화를 완료합니다.
3. 실제 Android 기기 또는 에뮬레이터를 선택합니다.
4. `app`을 실행합니다.
5. 설정 화면에서 Gemma 모델을 다운로드하고 준비 상태가 될 때까지 기다립니다.

### 명령줄

Windows PowerShell에서 다음 명령을 실행합니다.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

생성 APK 경로:

```text
app/build/outputs/apk/debug/app-debug.apk
```

현재 JVM 단위 테스트에는 가스 패킷(JSON, key-value, CSV) 파싱과 기본 앱 테스트가 포함됩니다.

## 상용화 전 우선 과제

1. **실측 BLE 프로토콜 확정**: 제조사별 UUID, 바이너리 프레임, CRC, 단위, 경보 플래그, 통신 재연결 정책을 반영합니다.
2. **안전 검증**: 교정된 검출기와 실제 현장 조건에서 알림 지연, 백그라운드 지속성, 통신 단절, 권한 거부, 저전력 모드를 검증합니다.
3. **모델 배포 보안**: 현재 모델 다운로드 주소를 운영용 서명 CDN/매니페스트·SHA-256 검증·버전 롤백 정책으로 교체합니다.
4. **RAG 평가 체계**: 대표 질문 세트와 정답 페이지를 만들고, 검색 상위 근거·수치 정확도·환각률을 자동 회귀 테스트로 관리합니다.
5. **문장 임베딩 고도화**: 해시 임베딩을 한국어 지원 온디바이스 임베딩 모델로 교체해 동의어·구어체 검색 품질을 개선합니다.
6. **음성 오프라인화**: 지원 기기에서 오프라인 한국어 음성팩을 검증하거나 별도 온디바이스 STT 엔진을 도입합니다.

## 참고

- 현재 UI·RAG·가스 시뮬레이션은 현장 작업 보조를 위한 구현입니다.
- 실제 작업 허가, 입장 여부, 대피 판단은 사업장 안전관리 체계와 법정 기준, 검교정된 측정기를 우선해야 합니다.
