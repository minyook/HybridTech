# 전체 코드 지도

기준: 2026-11-10. README의 현재 구조 설명과 함께 읽는 파일별 탐색 목록입니다.

직접 관리하는 소스·스크립트·리소스·설정·테스트 **314개 파일**의 역할을 정리했습니다. 각 링크는 저장소 루트의 실제 파일로 연결됩니다. 정적 데이터(JSON/PDF/모델)의 위치와 형식은 [README의 저장 경계](../README.md#data-storage)를 참고합니다.

## 읽는 순서

1. Android는 `SllmApplication → MainActivity → FieldGuardActions → FieldGuardViewModel`에서 시작합니다.
2. 질문은 `ConversationContext → KnowledgeRepository → QuestionSearchCoordinator → HybridRetrieval → DocumentAnswerPlan`으로 이어집니다.
3. 생성이 필요한 분기만 `LocalModelRuntime`으로 가고 일반/문서 보충 검사를 거쳐 상태·기록·음성에 전달합니다.
4. AP는 `GasMonitoringService → WifiMeterServer/Registry → WifiApHub/GasReadingStore`, 업무는 `NativeWorkPage → API → store → work`로 별도 처리합니다.

## 주석을 읽는 방법

- 파일의 **소스 안내**는 역할과 직접 연결 모듈을 설명합니다. import 목록은 의존관계이며 호출 순서 전체와 같지는 않습니다.
- KDoc/함수 주석은 주요 처리 단계·반환 계약·예외/자원 수명을 설명합니다. 단순 표시 컴포넌트는 상태와 콜백의 전달 관계로 읽습니다.
- 테스트 주석은 대표 검사 이름과 실행 진입점을 보여줍니다. mock·fixture 검사와 실제 모델/기기/무선 검사를 구분합니다.
- 표의 보존 웹 코드는 현재 Android 실행 경로와 별도입니다. 작업 API와 업무 WebView의 의존성은 유지됩니다.

## 파일 목록

| 분류 | 파일 수 |
| --- | ---: |
| Android 실행 소스 | 68 |
| Android 디버그 진입점 | 1 |
| Android 등록·리소스·빌드 | 37 |
| Android JVM 검사 | 38 |
| Android 기기·Compose 검사 | 18 |
| 작업 서버 | 6 |
| 보존 웹 클라이언트 | 71 |
| 보존 웹 빌드·평가 | 16 |
| 웹·작업 API 검사 | 45 |
| 도구·디자인 시안·루트 빌드 | 14 |

### Android 실행 소스

| 파일 | 역할 |
| --- | --- |
| [app/src/main/java/com/minyook/sllm2/MainActivity.kt](../app/src/main/java/com/minyook/sllm2/MainActivity.kt) | Android Activity 진입점. Compose 상태 구독·UI 이벤트·권한·음성 컨트롤러와 앱 생명주기를 연결한다. |
| [app/src/main/java/com/minyook/sllm2/SllmApplication.kt](../app/src/main/java/com/minyook/sllm2/SllmApplication.kt) | 기본 프로세스의 ObjectBox와 다운로드 알림 채널을 초기화한다. :npu 프로세스의 불필요한 DB 초기화는 건너뛴다. |
| [app/src/main/java/com/minyook/sllm2/data/AnswerMarkdown.kt](../app/src/main/java/com/minyook/sllm2/data/AnswerMarkdown.kt) | 모델/문서 Markdown의 표시 정리와 완결 경계를 관리한다. ViewModel의 부분 공개와 UI 표시가 사용하며 원문 수치나 조건을 요약하지 않는다. |
| [app/src/main/java/com/minyook/sllm2/data/ChatHistoryStore.kt](../app/src/main/java/com/minyook/sllm2/data/ChatHistoryStore.kt) | 최종 대화·출처·주제·선택지를 로컬 JSON으로 저장/복원한다. ViewModel의 기록 목록과 다음 질문 문맥에 연결된다. |
| [app/src/main/java/com/minyook/sllm2/data/ConversationContext.kt](../app/src/main/java/com/minyook/sllm2/data/ConversationContext.kt) | 완료된 대화에서 후속 질문의 대상·초점을 복원하고 모델용 역할 대화를 예산 안에 구성한다. |
| [app/src/main/java/com/minyook/sllm2/data/DocumentAnswerPlan.kt](../app/src/main/java/com/minyook/sllm2/data/DocumentAnswerPlan.kt) | 검색 결과를 본문·근거 ID·출처로 묶는다. 화면에 먼저 표시한 뒤 선택적 보충을 근거 계약으로 검사한다. |
| [app/src/main/java/com/minyook/sllm2/data/EquipmentDetailAnswer.kt](../app/src/main/java/com/minyook/sllm2/data/EquipmentDetailAnswer.kt) | 명시한 장비와 수량/설명 속성에 맞는 원문 절을 선택한다. HybridRetrieval이 호출하며 미확인 수량을 모델 추측으로 채우지 않는다. |
| [app/src/main/java/com/minyook/sllm2/data/EquipmentOverview.kt](../app/src/main/java/com/minyook/sllm2/data/EquipmentOverview.kt) | 준비물/장비 목록 질문에서 여러 용도의 실제 문서 절을 모아 직접 응답한다. 세부 속성 질문은 상세/일반 검색으로 넘긴다. |
| [app/src/main/java/com/minyook/sllm2/data/EvidenceLanguage.kt](../app/src/main/java/com/minyook/sllm2/data/EvidenceLanguage.kt) | 질문과 문서가 공유하는 도메인 어휘·별칭·어절 정규화 규칙. FocusedRetriever/HybridRetrieval이 후보 점수와 범위를 계산할 때 사용한다. |
| [app/src/main/java/com/minyook/sllm2/data/FocusedEvidence.kt](../app/src/main/java/com/minyook/sllm2/data/FocusedEvidence.kt) | 원문을 절/문단으로 나누고 선택 근거를 표시한다. FocusedRetriever의 어휘 순위와 SourceTables 복원을 같은 파일에서 연결한다. |
| [app/src/main/java/com/minyook/sllm2/data/GasDescriptionAnswer.kt](../app/src/main/java/com/minyook/sllm2/data/GasDescriptionAnswer.kt) | 단일 가스의 대상과 요청 속성을 구분해 원문 절/검수 표 행으로 직접 답한다. 이전 전체 가스 주제를 임의로 상속하지 않는다. |
| [app/src/main/java/com/minyook/sllm2/data/GeneralKnowledgeAnswer.kt](../app/src/main/java/com/minyook/sllm2/data/GeneralKnowledgeAnswer.kt) | 일반 생성의 프롬프트·제한된 규칙 검사·완결 문장 공개·최종 안내를 관리한다. 문서 보충 검증과 별도 경로다. |
| [app/src/main/java/com/minyook/sllm2/data/HybridRetrieval.kt](../app/src/main/java/com/minyook/sllm2/data/HybridRetrieval.kt) | 직접 응답 선택기와 어휘/768차원 의미 순위를 결합한다. 출처·검색 상태를 반환하고 확정하지 못한 구체적인 질문은 주제 선택 없이 일반 설명에 전달한다. |
| [app/src/main/java/com/minyook/sllm2/data/KnowledgeChunk.kt](../app/src/main/java/com/minyook/sllm2/data/KnowledgeChunk.kt) | ObjectBox에 저장하는 페이지 단위 원문 엔티티. 문서 ID/쪽/본문과 기존 해시 임베딩 필드를 보유한다. |
| [app/src/main/java/com/minyook/sllm2/data/KnowledgeRepository.kt](../app/src/main/java/com/minyook/sllm2/data/KnowledgeRepository.kt) | ObjectBox 조회와 재사용 검색 인덱스를 조정한다. ViewModel의 질문을 QuestionSearchCoordinator로 전달한다. |
| [app/src/main/java/com/minyook/sllm2/data/KnowledgeSeeder.kt](../app/src/main/java/com/minyook/sllm2/data/KnowledgeSeeder.kt) | 번들 JSON 버전이 바뀔 때 문서 엔티티를 트랜잭션으로 적재한다. 채팅 저장소와 별개이며 앱 초기 준비에서 호출된다. |
| [app/src/main/java/com/minyook/sllm2/data/ObjectBoxStore.kt](../app/src/main/java/com/minyook/sllm2/data/ObjectBoxStore.kt) | Application에서 초기화하는 기본 프로세스 DB 소유자. Seeder와 Repository가 같은 store를 사용한다. |
| [app/src/main/java/com/minyook/sllm2/data/OfflineHashEmbedding.kt](../app/src/main/java/com/minyook/sllm2/data/OfflineHashEmbedding.kt) | 기존 엔티티 적재용 결정적 384차원 해시 벡터. 현재 질문의 신경망 의미 검색은 SemanticEncoder의 별도 768차원 경로다. |
| [app/src/main/java/com/minyook/sllm2/data/QuestionAnswerVerifier.kt](../app/src/main/java/com/minyook/sllm2/data/QuestionAnswerVerifier.kt) | 기존 답변 경로의 제한된 모순 검사와 회귀 대상. 현재 ViewModel의 일반/보충 검증기는 다른 객체이므로 호출 범위를 구분한다. |
| [app/src/main/java/com/minyook/sllm2/data/QuestionEvidencePlanner.kt](../app/src/main/java/com/minyook/sllm2/data/QuestionEvidencePlanner.kt) | FocusedRetriever의 주제 분리·페이지 순위를 노출하는 기존 어댑터. 현재 전체 검색 진입점은 QuestionSearchCoordinator다. |
| [app/src/main/java/com/minyook/sllm2/data/QuestionFocus.kt](../app/src/main/java/com/minyook/sllm2/data/QuestionFocus.kt) | 장비 대상과 수량·용도·차이 등 요청 속성을 분리한다. 후속 질문 복원·장비 검색·일반 생성이 공유한다. |
| [app/src/main/java/com/minyook/sllm2/data/QuestionSearchCoordinator.kt](../app/src/main/java/com/minyook/sllm2/data/QuestionSearchCoordinator.kt) | 대화 의도·복합 질문·빠른 경로·질문 벡터 생성을 조정한다. 오류/모호성/근거 미확인을 구분해 ViewModel 분기를 결정한다. |
| [app/src/main/java/com/minyook/sllm2/data/QuestionUnderstanding.kt](../app/src/main/java/com/minyook/sllm2/data/QuestionUnderstanding.kt) | 구어체를 검색용 질의로 정규화한다. 사용자의 화면/기록 원문과 검색 표현을 분리한다. |
| [app/src/main/java/com/minyook/sllm2/data/SemanticEncoder.kt](../app/src/main/java/com/minyook/sllm2/data/SemanticEncoder.kt) | 질문만 ONNX CPU로 인코딩해 768차원 정규화 벡터를 반환한다. 세션/최근 질의를 재사용하고 생성 전 호출자가 해제한다. |
| [app/src/main/java/com/minyook/sllm2/data/SemanticTokenizer.kt](../app/src/main/java/com/minyook/sllm2/data/SemanticTokenizer.kt) | 고정 EmbeddingGemma tokenizer.json의 BPE 병합·특수 토큰·UTF-8 fallback을 재현한다. SemanticEncoder의 ONNX 입력 토큰을 만든다. |
| [app/src/main/java/com/minyook/sllm2/data/SourcePdfCatalog.kt](../app/src/main/java/com/minyook/sllm2/data/SourcePdfCatalog.kt) | 문서 ID를 번들 PDF 파일·쪽수·판본 해시에 연결한다. PDF 렌더와 표 복원에서 같은 판본을 확인한다. |
| [app/src/main/java/com/minyook/sllm2/data/SourcePdfRepository.kt](../app/src/main/java/com/minyook/sllm2/data/SourcePdfRepository.kt) | 검증한 번들 PDF의 요청 쪽만 IO 스레드에서 렌더링한다. 결과 Bitmap은 SourcePdfPage에 전달한다. |
| [app/src/main/java/com/minyook/sllm2/data/SourceTableData.kt](../app/src/main/java/com/minyook/sllm2/data/SourceTableData.kt) | 원본 PDF/본문 해시와 함께 관리하는 검수 표/도식 데이터. SourceTables가 선택 근거에 적용하며 셀 변경 시 원본 대조가 필요하다. |
| [app/src/main/java/com/minyook/sllm2/data/SourceTables.kt](../app/src/main/java/com/minyook/sllm2/data/SourceTables.kt) | 판본/청크 해시가 맞는 검수 표를 선택 문단에 복원한다. 표시 본문과 모델 문맥이 같은 구조를 사용한다. |
| [app/src/main/java/com/minyook/sllm2/gas/GasMonitoringService.kt](../app/src/main/java/com/minyook/sllm2/gas/GasMonitoringService.kt) | 사용자가 시작하는 Local-only AP foreground service. AP/TCP 수명·주기적 상태 전달·장비 경보 알림·정리를 소유한다. |
| [app/src/main/java/com/minyook/sllm2/gas/GasReading.kt](../app/src/main/java/com/minyook/sllm2/gas/GasReading.kt) | 선택 장비의 채널 값과 신선도·세션 상태를 표현/저장한다. 앱·서비스 알림·위젯 사이의 스냅샷 경계다. |
| [app/src/main/java/com/minyook/sllm2/gas/GasWidgetProvider.kt](../app/src/main/java/com/minyook/sllm2/gas/GasWidgetProvider.kt) | 저장된 최신 장비 스냅샷을 홈 화면 RemoteViews로 표시한다. 직접 연결/측정을 시작하지 않는다. |
| [app/src/main/java/com/minyook/sllm2/gas/WifiApState.kt](../app/src/main/java/com/minyook/sllm2/gas/WifiApState.kt) | AP 세션과 장비 목록을 서비스에서 ViewModel로 전달하는 StateFlow. 연결 비밀은 현재 세션 메모리에만 둔다. |
| [app/src/main/java/com/minyook/sllm2/gas/WifiMeterProtocol.kt](../app/src/main/java/com/minyook/sllm2/gas/WifiMeterProtocol.kt) | 시험 TCP v1의 LF 프레임·hello/reading 파싱과 장비별 registry를 정의한다. 순번·신선도·소유자·RTT를 관리한다. |
| [app/src/main/java/com/minyook/sllm2/gas/WifiMeterServer.kt](../app/src/main/java/com/minyook/sllm2/gas/WifiMeterServer.kt) | AP 주소에 바인딩한 TCP 수신기. 연결별 인증/검증/ACK와 heartbeat를 수행하고 결과를 registry에 반영한다. |
| [app/src/main/java/com/minyook/sllm2/model/AnswerOutputPolicy.kt](../app/src/main/java/com/minyook/sllm2/model/AnswerOutputPolicy.kt) | 일반/보충·자동/수동 길이 정책을 실제 출력 토큰 한도로 바꾼다. 표시 애니메이션이나 계정 질문 횟수 제한과 별개다. |
| [app/src/main/java/com/minyook/sllm2/model/EngineReusePolicy.kt](../app/src/main/java/com/minyook/sllm2/model/EngineReusePolicy.kt) | 유휴 E2B 사전 적재의 장치/메모리 조건, 의미 검색 중 엔진 유지, 설정 변경 시 재적재 필요성을 결정하는 순수 정책이다. |
| [app/src/main/java/com/minyook/sllm2/model/FieldComparisonSuite.kt](../app/src/main/java/com/minyook/sllm2/model/FieldComparisonSuite.kt) | 모델 간 공통 현장 복합 문항·검색 질의·검토 항목·출력 예산을 정의한다. Runner와 기기 계측이 같은 조건을 사용한다. |
| [app/src/main/java/com/minyook/sllm2/model/InferenceSettings.kt](../app/src/main/java/com/minyook/sllm2/model/InferenceSettings.kt) | 모델/실행 장치/문맥/출력 설정과 기기별 상한·저장을 정의한다. 설정 UI와 Runtime이 같은 값을 사용한다. |
| [app/src/main/java/com/minyook/sllm2/model/LocalModel.kt](../app/src/main/java/com/minyook/sllm2/model/LocalModel.kt) | 범용 생성 모델의 ID·파일·URL/revision·크기·문맥 한도 카탈로그. Worker와 Runtime이 공유한다. |
| [app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt](../app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt) | 질문 생성의 직렬화·프롬프트·예산·엔진 재사용·NPU/GPU/CPU 선택과 지표 전달을 담당한다. |
| [app/src/main/java/com/minyook/sllm2/model/ModelComparison.kt](../app/src/main/java/com/minyook/sllm2/model/ModelComparison.kt) | 설정의 비교 요청을 실제 모델별 생성으로 실행하고 원문·오류·시간·품질 표시를 별도 AtomicFile 보고서로 저장한다. |
| [app/src/main/java/com/minyook/sllm2/model/ModelDownloadAccess.kt](../app/src/main/java/com/minyook/sllm2/model/ModelDownloadAccess.kt) | Hugging Face 읽기 토큰을 Keystore AES-GCM 키로 암호화해 Worker에 제공한다. 다운로드 인자/보고서에 원문 토큰을 넣지 않는다. |
| [app/src/main/java/com/minyook/sllm2/model/ModelDownloadWorker.kt](../app/src/main/java/com/minyook/sllm2/model/ModelDownloadWorker.kt) | 모델별 고유 WorkManager 작업·HTTP Range 이어받기·크기 검증·완성 파일 확정과 진행 알림을 처리한다. |
| [app/src/main/java/com/minyook/sllm2/model/ModelState.kt](../app/src/main/java/com/minyook/sllm2/model/ModelState.kt) | 기기 자원 정보·모델 파일 위치·설치 상태 저장 계약. READY는 엔진 적재가 아닌 파일 저장 상태다. |
| [app/src/main/java/com/minyook/sllm2/model/NpuInferenceClient.kt](../app/src/main/java/com/minyook/sllm2/model/NpuInferenceClient.kt) | 기본 프로세스의 NPU IPC 요청/응답·시간 제한·종료 확인을 담당한다. 실패는 LocalModelRuntime의 전환 정책으로 전달한다. |
| [app/src/main/java/com/minyook/sllm2/model/NpuInferenceService.kt](../app/src/main/java/com/minyook/sllm2/model/NpuInferenceService.kt) | :npu 보조 프로세스에서 칩셋 전용 엔진을 실행한다. Messenger TEXT/DONE/ERROR 응답과 ABORT를 처리한다. |
| [app/src/main/java/com/minyook/sllm2/model/NpuModel.kt](../app/src/main/java/com/minyook/sllm2/model/NpuModel.kt) | 모델·Qualcomm SoC에 맞는 전용 파일 크기/문맥/라이브러리를 선택한다. 범용 파일과 NPU 파일을 구분한다. |
| [app/src/main/java/com/minyook/sllm2/ui/AnswerProgressTimer.kt](../app/src/main/java/com/minyook/sllm2/ui/AnswerProgressTimer.kt) | 화면에 보이는 pending 답변의 경과 시간을 단조 시계로 갱신한다. 전체 추론 상태를 매초 다시 기록하지 않는다. |
| [app/src/main/java/com/minyook/sllm2/ui/ChatTimeline.kt](../app/src/main/java/com/minyook/sllm2/ui/ChatTimeline.kt) | 질문/헤더/본문/푸터를 안정 키의 lazy 항목으로 나누고 본문 파싱을 캐시한다. WorkspaceChat이 사용한다. |
| [app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt](../app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt) | 화면 이벤트를 문맥·검색·생성·기록·다운로드·AP로 조정하고 Compose용 StateFlow를 제공한다. |
| [app/src/main/java/com/minyook/sllm2/ui/FieldWorkspaceUi.kt](../app/src/main/java/com/minyook/sllm2/ui/FieldWorkspaceUi.kt) | 홈·사이드바·문서/설정 등의 공통 화면 구성. 상태는 읽고 기능 명령은 FieldGuardActions로 전달한다. |
| [app/src/main/java/com/minyook/sllm2/ui/HybridTechApp.kt](../app/src/main/java/com/minyook/sllm2/ui/HybridTechApp.kt) | FieldGuardActions 계약과 별도 UI 구현을 보유한다. 실제 MainActivity의 화면 루트는 TossFieldGuardApp이다. |
| [app/src/main/java/com/minyook/sllm2/ui/HybridTechTheme.kt](../app/src/main/java/com/minyook/sllm2/ui/HybridTechTheme.kt) | Compose 테마/색상 정의. 이 파일을 쓰는 화면과 실제 TossFieldGuardApp의 별도 테마 적용을 구분한다. |
| [app/src/main/java/com/minyook/sllm2/ui/MarkdownBlocks.kt](../app/src/main/java/com/minyook/sllm2/ui/MarkdownBlocks.kt) | Markdown을 블록/목록/인라인 표시 모델로 파싱하는 순수 함수. ChatTimeline과 MarkdownText가 사용한다. |
| [app/src/main/java/com/minyook/sllm2/ui/MarkdownText.kt](../app/src/main/java/com/minyook/sllm2/ui/MarkdownText.kt) | 파싱된 Markdown의 텍스트·표·코드·목록을 Compose로 표시한다. 넓은 표의 가로 스크롤도 이 계층에서 처리한다. |
| [app/src/main/java/com/minyook/sllm2/ui/ModelComparisonPanel.kt](../app/src/main/java/com/minyook/sllm2/ui/ModelComparisonPanel.kt) | 모델 비교 진행·문항 원문·지표·검토/공유를 표시한다. 실제 생성은 ViewModel이 Runner로 위임한다. |
| [app/src/main/java/com/minyook/sllm2/ui/NativeWorkPage.kt](../app/src/main/java/com/minyook/sllm2/ui/NativeWorkPage.kt) | 작업 점검/감독관 인증만 같은 origin의 WebView로 연결한다. 업무 권한과 승인은 서버가 검증한다. |
| [app/src/main/java/com/minyook/sllm2/ui/SourceDocumentFormatter.kt](../app/src/main/java/com/minyook/sllm2/ui/SourceDocumentFormatter.kt) | 추출한 PDF 텍스트에 표시용 문단 경계만 추가한다. 원문 단어·수치·DB 본문은 재작성하지 않는다. |
| [app/src/main/java/com/minyook/sllm2/ui/SourcePdfPage.kt](../app/src/main/java/com/minyook/sllm2/ui/SourcePdfPage.kt) | 선택한 문서/쪽의 PDF 렌더 상태를 화면에 반영한다. 원본 Bitmap은 SourcePdfRepository에서 요청한다. |
| [app/src/main/java/com/minyook/sllm2/ui/StreamingTextReveal.kt](../app/src/main/java/com/minyook/sllm2/ui/StreamingTextReveal.kt) | 검증된 생성 접두부를 프레임별 작은 묶음으로 표시한다. 원문/기록과 모델 출력 한도는 바꾸지 않는다. |
| [app/src/main/java/com/minyook/sllm2/ui/TossFieldGuardApp.kt](../app/src/main/java/com/minyook/sllm2/ui/TossFieldGuardApp.kt) | 실제 앱 화면 셸. 목적지·뒤로 가기·반응형 메뉴·키보드/시스템 여백을 조정해 기능 화면으로 연결한다. |
| [app/src/main/java/com/minyook/sllm2/ui/VoiceConversationController.kt](../app/src/main/java/com/minyook/sllm2/ui/VoiceConversationController.kt) | 로컬 STT/TTS와 순수 음성 상태 머신을 연결한다. 인식된 질문은 공통 ViewModel 경로로 넘긴다. |
| [app/src/main/java/com/minyook/sllm2/ui/VoiceConversationLoop.kt](../app/src/main/java/com/minyook/sllm2/ui/VoiceConversationLoop.kt) | 듣기·답변·읽기·재시도 상태 전이와 시도 ID를 관리한다. OS API는 Port 구현에 맡겨 JVM에서 검증 가능하다. |
| [app/src/main/java/com/minyook/sllm2/ui/WebDesignTokens.kt](../app/src/main/java/com/minyook/sllm2/ui/WebDesignTokens.kt) | 웹 기준 스타일에서 동기화한 Android 색상·치수·아이콘 리소스 매핑. Workspace 화면 구성요소가 참조한다. |
| [app/src/main/java/com/minyook/sllm2/ui/WorkServerAddress.kt](../app/src/main/java/com/minyook/sllm2/ui/WorkServerAddress.kt) | 서버 origin과 같은 출처 탐색 조건을 검증한다. 저장/진입 주소 검사이며 감독관 권한 검사는 아니다. |
| [app/src/main/java/com/minyook/sllm2/ui/WorkspaceComponents.kt](../app/src/main/java/com/minyook/sllm2/ui/WorkspaceComponents.kt) | 화면 폭 규칙·버튼·필드·카드·아이콘의 공통 Compose 구성요소. 도메인 처리는 호출자가 전달한 콜백으로 연결한다. |
| [app/src/main/java/com/minyook/sllm2/ui/WorkspaceScreens.kt](../app/src/main/java/com/minyook/sllm2/ui/WorkspaceScreens.kt) | 채팅·음성 대화 목록과 문서/기록 화면을 구성한다. 입력/출처 이벤트는 actions로 넘기고 목록 따라가기는 표시 상태로 관리한다. |

### Android 디버그 진입점

| 파일 | 역할 |
| --- | --- |
| [app/src/debug/java/com/minyook/sllm2/WorkspacePreviewActivity.kt](../app/src/debug/java/com/minyook/sllm2/WorkspacePreviewActivity.kt) | Compose 계측 화면을 호스팅하는 debug 전용 Activity. 제품 ViewModel·모델·AP를 자동 시작하지 않는다. |

### Android 등록·리소스·빌드

| 파일 | 역할 |
| --- | --- |
| [app/build.gradle](../app/build.gradle) | Android SDK·의존성·build type·JNI 패키징과 검색 자산 무결성 preBuild 검사를 정의한다. |
| [app/src/debug/AndroidManifest.xml](../app/src/debug/AndroidManifest.xml) | debug 전용 계측 Activity 등록. 제품 릴리스 진입과 구분한다. |
| [app/src/main/AndroidManifest.xml](../app/src/main/AndroidManifest.xml) | 앱 권한·Application/Activity·서비스·:npu 프로세스·FileProvider·위젯의 Android 등록 계약이다. |
| [app/src/main/res/drawable/bg_gas_widget.xml](../app/src/main/res/drawable/bg_gas_widget.xml) | Android 그림/런처 리소스 bg_gas_widget. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/bg_notification_gas.xml](../app/src/main/res/drawable/bg_notification_gas.xml) | Android 그림/런처 리소스 bg_notification_gas. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/ic_launcher_background.xml](../app/src/main/res/drawable/ic_launcher_background.xml) | Android 그림/런처 리소스 ic_launcher_background. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/ic_launcher_foreground.xml](../app/src/main/res/drawable/ic_launcher_foreground.xml) | Android 그림/런처 리소스 ic_launcher_foreground. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/ic_stat_safety.xml](../app/src/main/res/drawable/ic_stat_safety.xml) | Android 그림/런처 리소스 ic_stat_safety. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_arrow.xml](../app/src/main/res/drawable/workspace_icon_arrow.xml) | Android 그림/런처 리소스 workspace_icon_arrow. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_book.xml](../app/src/main/res/drawable/workspace_icon_book.xml) | Android 그림/런처 리소스 workspace_icon_book. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_chat.xml](../app/src/main/res/drawable/workspace_icon_chat.xml) | Android 그림/런처 리소스 workspace_icon_chat. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_check.xml](../app/src/main/res/drawable/workspace_icon_check.xml) | Android 그림/런처 리소스 workspace_icon_check. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_chevron.xml](../app/src/main/res/drawable/workspace_icon_chevron.xml) | Android 그림/런처 리소스 workspace_icon_chevron. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_close.xml](../app/src/main/res/drawable/workspace_icon_close.xml) | Android 그림/런처 리소스 workspace_icon_close. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_cpu.xml](../app/src/main/res/drawable/workspace_icon_cpu.xml) | Android 그림/런처 리소스 workspace_icon_cpu. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_download.xml](../app/src/main/res/drawable/workspace_icon_download.xml) | Android 그림/런처 리소스 workspace_icon_download. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_history.xml](../app/src/main/res/drawable/workspace_icon_history.xml) | Android 그림/런처 리소스 workspace_icon_history. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_home.xml](../app/src/main/res/drawable/workspace_icon_home.xml) | Android 그림/런처 리소스 workspace_icon_home. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_info.xml](../app/src/main/res/drawable/workspace_icon_info.xml) | Android 그림/런처 리소스 workspace_icon_info. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_plus.xml](../app/src/main/res/drawable/workspace_icon_plus.xml) | Android 그림/런처 리소스 workspace_icon_plus. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_search.xml](../app/src/main/res/drawable/workspace_icon_search.xml) | Android 그림/런처 리소스 workspace_icon_search. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_settings.xml](../app/src/main/res/drawable/workspace_icon_settings.xml) | Android 그림/런처 리소스 workspace_icon_settings. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_shield.xml](../app/src/main/res/drawable/workspace_icon_shield.xml) | Android 그림/런처 리소스 workspace_icon_shield. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_trash.xml](../app/src/main/res/drawable/workspace_icon_trash.xml) | Android 그림/런처 리소스 workspace_icon_trash. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/drawable/workspace_icon_wifi.xml](../app/src/main/res/drawable/workspace_icon_wifi.xml) | Android 그림/런처 리소스 workspace_icon_wifi. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/layout/notification_gas_monitoring.xml](../app/src/main/res/layout/notification_gas_monitoring.xml) | 가스 측정 알림용 사용자 정의 레이아웃 리소스. 현재 GasMonitoringService는 NotificationCompat 표준 알림을 구성하며 이 레이아웃을 직접 참조하지 않는다. |
| [app/src/main/res/layout/widget_gas_reading.xml](../app/src/main/res/layout/widget_gas_reading.xml) | 홈 위젯 채널/상태의 RemoteViews 배치. GasWidgetProvider가 갱신한다. |
| [app/src/main/res/mipmap-anydpi/ic_launcher.xml](../app/src/main/res/mipmap-anydpi/ic_launcher.xml) | Android 그림/런처 리소스 ic_launcher. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/mipmap-anydpi/ic_launcher_round.xml](../app/src/main/res/mipmap-anydpi/ic_launcher_round.xml) | Android 그림/런처 리소스 ic_launcher_round. 참조하는 화면/테마가 표시하며 자체 기능 처리나 네트워크 동작은 없다. |
| [app/src/main/res/values/colors.xml](../app/src/main/res/values/colors.xml) | Android 색상 리소스. 참조하는 테마/벡터의 색상 계약이다. |
| [app/src/main/res/values/strings.xml](../app/src/main/res/values/strings.xml) | 앱 이름·화면/위젯 등이 참조하는 문자열 리소스. |
| [app/src/main/res/values/themes.xml](../app/src/main/res/values/themes.xml) | Activity의 기본 Android 테마. Compose 내부 테마 정의와 적용 위치를 구분한다. |
| [app/src/main/res/xml/backup_rules.xml](../app/src/main/res/xml/backup_rules.xml) | 앱 백업에 포함/제외할 파일 경계. 모델·인증 자료의 복원을 데이터 수명과 함께 확인한다. |
| [app/src/main/res/xml/data_extraction_rules.xml](../app/src/main/res/xml/data_extraction_rules.xml) | Android 데이터 추출/전송의 백업 제외 계약. Manifest의 참조와 함께 유지한다. |
| [app/src/main/res/xml/gas_widget_info.xml](../app/src/main/res/xml/gas_widget_info.xml) | 시스템에 등록하는 가스 홈 위젯 크기·갱신·레이아웃 메타데이터. |
| [app/src/main/res/xml/model_comparison_paths.xml](../app/src/main/res/xml/model_comparison_paths.xml) | 비교 보고서 공유 FileProvider가 노출할 파일 경로의 최소 범위. |
| [app/src/main/res/xml/work_network_security.xml](../app/src/main/res/xml/work_network_security.xml) | 작업 서버 WebView의 네트워크 보안/개발 loopback 연결 정책. |

### Android JVM 검사

| 파일 | 역할 |
| --- | --- |
| [app/src/test/java/com/minyook/sllm2/ExampleUnitTest.kt](../app/src/test/java/com/minyook/sllm2/ExampleUnitTest.kt) | 기본 JUnit 실행 fixture을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/AnswerMarkdownTest.kt](../app/src/test/java/com/minyook/sllm2/data/AnswerMarkdownTest.kt) | 완결 경계·수치/조건·열린 Markdown 정리을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/ChatHistoryJsonTest.kt](../app/src/test/java/com/minyook/sllm2/data/ChatHistoryJsonTest.kt) | 대화 JSON의 기존 형식과 문맥/출처 메타데이터 복원을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/ConversationContextTest.kt](../app/src/test/java/com/minyook/sllm2/data/ConversationContextTest.kt) | 대화의 대상·후속 초점·선택지와 프롬프트 예산을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/ConversationRetrievalEvaluationTest.kt](../app/src/test/java/com/minyook/sllm2/data/ConversationRetrievalEvaluationTest.kt) | 연속 발화의 실제 검색 근거와 확인 선택지을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/EquipmentDialogueEvaluationTest.kt](../app/src/test/java/com/minyook/sllm2/data/EquipmentDialogueEvaluationTest.kt) | 장비의 수량/용도 후속 질문과 실제 문서 응답을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/EquipmentDialogueTest.kt](../app/src/test/java/com/minyook/sllm2/data/EquipmentDialogueTest.kt) | 장비 대상·질문 초점과 상세 응답의 경계을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/EquipmentOverviewTest.kt](../app/src/test/java/com/minyook/sllm2/data/EquipmentOverviewTest.kt) | 준비물 질문의 다중 장비 절 선택을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/FocusedEvidenceTest.kt](../app/src/test/java/com/minyook/sllm2/data/FocusedEvidenceTest.kt) | 문단 분리·원문 선택·관련 조건 연결을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/GasDescriptionAnswerTest.kt](../app/src/test/java/com/minyook/sllm2/data/GasDescriptionAnswerTest.kt) | 단일 가스/속성의 직접 응답 범위을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/GasScopeEvaluationTest.kt](../app/src/test/java/com/minyook/sllm2/data/GasScopeEvaluationTest.kt) | 산소 후속 질문의 원문/표/관련 없는 내용 배제을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/GeneralKnowledgeAnswerTest.kt](../app/src/test/java/com/minyook/sllm2/data/GeneralKnowledgeAnswerTest.kt) | 일반 설명의 프롬프트·규칙 검사·부분 공개·항목별 길이을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/HybridRetrievalTest.kt](../app/src/test/java/com/minyook/sllm2/data/HybridRetrievalTest.kt) | 어휘/의미 후보 결합·범위·출처 선택을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/LiveQuestionEvaluationTest.kt](../app/src/test/java/com/minyook/sllm2/data/LiveQuestionEvaluationTest.kt) | 새 표현 질문의 실제 제품 검색 결과을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/NativeAnswerCorpusTest.kt](../app/src/test/java/com/minyook/sllm2/data/NativeAnswerCorpusTest.kt) | 고정 문항의 문서 본문·출처·필수 조건을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/QuestionAnswerVerifierTest.kt](../app/src/test/java/com/minyook/sllm2/data/QuestionAnswerVerifierTest.kt) | 기존 답변 모순 검사을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/QuestionEvidencePlannerTest.kt](../app/src/test/java/com/minyook/sllm2/data/QuestionEvidencePlannerTest.kt) | 기존 페이지 순위/질문 분리 어댑터을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/QuestionSearchCoordinatorTest.kt](../app/src/test/java/com/minyook/sllm2/data/QuestionSearchCoordinatorTest.kt) | 오류/모호성/일반 생성 허용과 복합 질문 검색 순서을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/ReviewedRetrievalEvaluationTest.kt](../app/src/test/java/com/minyook/sllm2/data/ReviewedRetrievalEvaluationTest.kt) | 실제 의미 인코더를 사용하는 검색 회귀을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/SemanticTokenizerTest.kt](../app/src/test/java/com/minyook/sllm2/data/SemanticTokenizerTest.kt) | 고정 BPE 토큰화의 기준 구현과 일치을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/SourcePdfCatalogTest.kt](../app/src/test/java/com/minyook/sllm2/data/SourcePdfCatalogTest.kt) | 문서 ID·PDF 판본·쪽 대응을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/data/SourceTablesTest.kt](../app/src/test/java/com/minyook/sllm2/data/SourceTablesTest.kt) | 검수 표/도식의 해시·셀·범위 복원을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/gas/GasReadingFreshnessTest.kt](../app/src/test/java/com/minyook/sllm2/gas/GasReadingFreshnessTest.kt) | 세션·시간·누락 채널에 따른 최신 측정 표시을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/gas/WifiMeterProtocolTest.kt](../app/src/test/java/com/minyook/sllm2/gas/WifiMeterProtocolTest.kt) | LF 프레임·인증·필드/값·순번·신선도을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/gas/WifiMeterServerTest.kt](../app/src/test/java/com/minyook/sllm2/gas/WifiMeterServerTest.kt) | 실제 loopback TCP의 분할/병합·동시 연결·정리을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/gas/WifiMeterStressTest.kt](../app/src/test/java/com/minyook/sllm2/gas/WifiMeterStressTest.kt) | 동시 송신·재접속·장기 프레임 스트레스을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/model/AnswerOutputPolicyTest.kt](../app/src/test/java/com/minyook/sllm2/model/AnswerOutputPolicyTest.kt) | 일반/보충 및 자동/수동 토큰 예산을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/model/EngineReusePolicyTest.kt](../app/src/test/java/com/minyook/sllm2/model/EngineReusePolicyTest.kt) | 사전 적재의 모델/장치/메모리 조건과 출력 길이 변경 시 엔진 유지, 엔진 설정 변경 시 재적재 정책을 검증한다. |
| [app/src/test/java/com/minyook/sllm2/model/FieldComparisonSuiteTest.kt](../app/src/test/java/com/minyook/sllm2/model/FieldComparisonSuiteTest.kt) | 공통 비교 문항/예산/근거 병합을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/model/LocalModelTest.kt](../app/src/test/java/com/minyook/sllm2/model/LocalModelTest.kt) | 모델 ID·파일·크기·문맥 카탈로그을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/model/NpuModelTest.kt](../app/src/test/java/com/minyook/sllm2/model/NpuModelTest.kt) | SoC 전용 파일·라이브러리·문맥 오류 분류을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/ui/ChatTimelineTest.kt](../app/src/test/java/com/minyook/sllm2/ui/ChatTimelineTest.kt) | 블록 lazy 키·파싱 캐시·메시지 제거을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/ui/MarkdownBlocksTest.kt](../app/src/test/java/com/minyook/sllm2/ui/MarkdownBlocksTest.kt) | 표 셀·중첩 목록·강조/코드 파싱을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/ui/SourceDocumentFormatterTest.kt](../app/src/test/java/com/minyook/sllm2/ui/SourceDocumentFormatterTest.kt) | 원문 단어/수치를 보존한 표시용 문단 분리을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/ui/StreamingTextRevealTest.kt](../app/src/test/java/com/minyook/sllm2/ui/StreamingTextRevealTest.kt) | 검증된 본문의 프레임 표시·완료·정정·이모지/표을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/ui/VoiceConversationLoopTest.kt](../app/src/test/java/com/minyook/sllm2/ui/VoiceConversationLoopTest.kt) | 음성 상태 전이·시도 ID·정지/재시도을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/ui/VoiceResponseFormatterTest.kt](../app/src/test/java/com/minyook/sllm2/ui/VoiceResponseFormatterTest.kt) | 조건과 표 값을 보존한 TTS 정리/분할을 검증하는 JVM 회귀 검사다. |
| [app/src/test/java/com/minyook/sllm2/ui/WorkServerAddressTest.kt](../app/src/test/java/com/minyook/sllm2/ui/WorkServerAddressTest.kt) | HTTPS/디버그 loopback origin과 탐색 제한을 검증하는 JVM 회귀 검사다. |

### Android 기기·Compose 검사

| 파일 | 역할 |
| --- | --- |
| [app/src/androidTest/java/com/minyook/sllm2/AndroidQuestionLatencyEvaluationTest.kt](../app/src/androidTest/java/com/minyook/sllm2/AndroidQuestionLatencyEvaluationTest.kt) | 단말 검색/답변의 문항별 처리 지연을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/ChatScreenTest.kt](../app/src/androidTest/java/com/minyook/sllm2/ChatScreenTest.kt) | 실제 Compose 목록·표시 프레임·키보드 크기 변화·복사/출처을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/ComposeNavigationTest.kt](../app/src/androidTest/java/com/minyook/sllm2/ComposeNavigationTest.kt) | 별도 UI 구현의 화면 이동·콜백을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/ExampleInstrumentedTest.kt](../app/src/androidTest/java/com/minyook/sllm2/ExampleInstrumentedTest.kt) | 대상 앱 패키지와 Android 계측 실행을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/FieldModelComparisonTest.kt](../app/src/androidTest/java/com/minyook/sllm2/FieldModelComparisonTest.kt) | 공통 현장 문항을 사용한 실제 모델 비교을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/GasTopicSearchTest.kt](../app/src/androidTest/java/com/minyook/sllm2/GasTopicSearchTest.kt) | 단말 단일 가스 주제별 실제 검색을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/GemmaDecodingPathBenchmarkTest.kt](../app/src/androidTest/java/com/minyook/sllm2/GemmaDecodingPathBenchmarkTest.kt) | 같은 프롬프트/근거에서 native 추측 생성 플래그와 실제 토큰 지표을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/GemmaDialogueIntegrationTest.kt](../app/src/androidTest/java/com/minyook/sllm2/GemmaDialogueIntegrationTest.kt) | 실제 모델과 문맥을 포함한 일반/후속 질문 통합을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/GemmaGenerationBenchmarkTest.kt](../app/src/androidTest/java/com/minyook/sllm2/GemmaGenerationBenchmarkTest.kt) | 실제 모델 backend·초기화·생성 지연을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/NativeAnswerEvaluationTest.kt](../app/src/androidTest/java/com/minyook/sllm2/NativeAnswerEvaluationTest.kt) | 단말의 실제 문서 검색/답변 기준을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/NativeVoiceSupportTest.kt](../app/src/androidTest/java/com/minyook/sllm2/NativeVoiceSupportTest.kt) | 단말의 로컬 STT/TTS 서비스/한국어 지원 조회을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/NativeVoiceUiTest.kt](../app/src/androidTest/java/com/minyook/sllm2/NativeVoiceUiTest.kt) | 음성 전용 화면·설정·텍스트 복귀을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/ReferenceAnswerStreamingTest.kt](../app/src/androidTest/java/com/minyook/sllm2/ReferenceAnswerStreamingTest.kt) | 실제 E2B 직접 생성의 첫 공개 본문·완료 지표와 임시 채팅 표시을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/SourceTableRenderingTest.kt](../app/src/androidTest/java/com/minyook/sllm2/SourceTableRenderingTest.kt) | Compose 표 셀·가로 스크롤·표시/복사을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/SourceTableSearchTest.kt](../app/src/androidTest/java/com/minyook/sllm2/SourceTableSearchTest.kt) | 단말 검색 결과와 검수 표의 연결을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/TossNavigationTest.kt](../app/src/androidTest/java/com/minyook/sllm2/TossNavigationTest.kt) | 제품 화면 셸의 목적지·뒤로 가기을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/WorkspaceDesignTest.kt](../app/src/androidTest/java/com/minyook/sllm2/WorkspaceDesignTest.kt) | 제품 공통 화면의 배치/반응형·콜백을 검증하는 Android 계측 검사다. |
| [app/src/androidTest/java/com/minyook/sllm2/data/SourcePdfRepositoryTest.kt](../app/src/androidTest/java/com/minyook/sllm2/data/SourcePdfRepositoryTest.kt) | Android PdfRenderer·판본·쪽 렌더을 검증하는 Android 계측 검사다. |

### 작업 서버

| 파일 | 역할 |
| --- | --- |
| [web/server/ai-recovery-page.ts](../web/server/ai-recovery-page.ts) | 보존된 웹 AI 복구 안내 HTML을 생성한다. API가 nonce 정책과 함께 제공하며 서버 추론을 수행하는 페이지는 아니다. |
| [web/server/api.ts](../web/server/api.ts) | 세션 쿠키·origin/CSRF·JSON 검증 후 store 명령으로 연결하는 HTTP/SSE 경계. 실제 업무 권한은 저장소/도메인에서 재검사한다. |
| [web/server/iphone-https.ts](../web/server/iphone-https.ts) | 보존된 iPhone 로컬 HTTPS 시험의 주소·인증서·설치 안내 서버 도우미. 운영 인증서 발급/배포와 구분한다. |
| [web/server/manage.ts](../web/server/manage.ts) | 서버 관리자 CLI의 당일 코드 확인·교체·회수 진입점. 같은 store 규칙을 사용하며 클라이언트에 기본 코드를 넣지 않는다. |
| [web/server/plugin.ts](../web/server/plugin.ts) | Vite 개발/미리보기 서버에 동일한 API/SQLite 저장소를 등록하고 서버 종료 시 닫는다. |
| [web/server/store.ts](../web/server/store.ts) | SQLite의 작업·세션·QR·당일 인증 코드·revision·만료/취소를 소유한다. API/CLI가 주입한 store를 사용한다. |

### 보존 웹 클라이언트

| 파일 | 역할 |
| --- | --- |
| [web/src/App.tsx](../web/src/App.tsx) | 웹 라우트·대화·문서·설정·점검 화면을 조합한다. 저장소/검색/AI 모듈과 화면 이벤트를 연결하는 보존 웹 셸이다. |
| [web/src/ErrorBoundary.tsx](../web/src/ErrorBoundary.tsx) | React 렌더 오류를 복구 화면으로 격리한다. 모델 native 프로세스 종료를 직접 처리하는 런타임 가드는 아니다. |
| [web/src/ai.css](../web/src/ai.css) | 모델 설치/진행/복구 및 AI 설정 화면의 스타일 정의. 해당 React 화면과 클래스 이름을 함께 변경해야 한다. |
| [web/src/ai/capabilities.ts](../web/src/ai/capabilities.ts) | 보안 컨텍스트·브라우저 저장/GPU 등 기능을 조회해 사용 가능 설명을 만든다. 기기 내 메모리 한도를 해제하는 기능은 아니다. |
| [web/src/ai/catalog.ts](../web/src/ai/catalog.ts) | 웹용 Gemma 배포 메타데이터와 다운로드 진입점을 정의한다. 파일 저장/검증은 model.ts에 위임한다. |
| [web/src/ai/diagnostics.ts](../web/src/ai/diagnostics.ts) | 허용한 실행 단계/지표만 로컬 진단으로 기록·내보낸다. 프롬프트·답변·원시 오류 같은 사용자 내용을 저장하지 않는 경계다. |
| [web/src/ai/engine-lease.ts](../web/src/ai/engine-lease.ts) | 같은 origin의 여러 화면이 GPU 엔진을 중복 적재하지 않도록 브라우저 잠금을 획득/해제한다. |
| [web/src/ai/engine.worker.ts](../web/src/ai/engine.worker.ts) | 일반 웹 생성 Worker 진입점. 메시지를 WorkerEngine에 전달해 메인 UI와 무거운 SDK 실행을 분리한다. |
| [web/src/ai/execution.ts](../web/src/ai/execution.ts) | 일반/Apple 모바일 실행 프로필을 SDK executor 설정으로 바꾼다. 적용 대상은 웹 LiteRT 경로이며 Android 설정과 별개다. |
| [web/src/ai/file-model-reader.ts](../web/src/ai/file-model-reader.ts) | 로컬 Blob의 필요한 범위만 읽어 SDK의 파일 읽기 계약을 구현한다. 모델 크기 전체의 JS 복사 버퍼를 만들지 않는다. |
| [web/src/ai/light-answer.ts](../web/src/ai/light-answer.ts) | 작은 모델용 근거 묶음·설명 정렬·빠진 수치/조건·중복을 관리한다. 최종 답변은 문서 근거를 보존한 순서로 합친다. |
| [web/src/ai/light-engine.ts](../web/src/ai/light-engine.ts) | 경량 WebLLM Worker 생성 실행기. 근거 계획과 선택적 보충 정책을 사용해 응답 프로토콜로 돌려준다. |
| [web/src/ai/light-model.ts](../web/src/ai/light-model.ts) | 경량 모델 manifest·조각별 무결성·CacheStorage 다운로드/삭제를 관리한다. 경량 실행 경로의 설치 상태를 제공한다. |
| [web/src/ai/light-output.ts](../web/src/ai/light-output.ts) | 경량 모델의 생각 템플릿과 반복된 절차 출력을 표시용으로 정리한다. 생성된 사실 자체의 정확성을 보장하지 않는다. |
| [web/src/ai/light-prompt.ts](../web/src/ai/light-prompt.ts) | 실제 tokenizer로 입력 예산을 계산하고 근거·출력 여유를 보존한 경량 프롬프트를 구성한다. |
| [web/src/ai/light.worker.ts](../web/src/ai/light.worker.ts) | 경량 웹 생성 Worker 진입점. UI의 요청을 LightWorkerEngine에 전달하고 응답을 메인 런타임으로 보낸다. |
| [web/src/ai/model-stream.ts](../web/src/ai/model-stream.ts) | 로컬 모델의 범위 읽기/ReadableStream을 구성하고 읽기 지표를 모은다. 파일 검증이나 질문 생성 정책과 별도 계층이다. |
| [web/src/ai/model.ts](../web/src/ai/model.ts) | 웹 모델 설치·OPFS/저장소 상태·검증 영수증·삭제를 관리한다. catalog와 runtime이 같은 설치 결과를 사용한다. |
| [web/src/ai/output-quality.ts](../web/src/ai/output-quality.ts) | 웹 생성 출력의 반복/깨진 패턴을 제한된 규칙으로 탐지한다. 문서 사실 검증 전체를 대신하지 않는다. |
| [web/src/ai/preferences.ts](../web/src/ai/preferences.ts) | 웹 AI 문맥/출력/복구 프로필과 로컬 설정 저장·정규화를 관리한다. Android InferenceSettings와 저장소가 다르다. |
| [web/src/ai/protocol.ts](../web/src/ai/protocol.ts) | 메인 화면과 생성 Worker 사이의 요청/부분 응답/완료/오류 타입 계약. 양쪽 런타임이 공유한다. |
| [web/src/ai/recovery.ts](../web/src/ai/recovery.ts) | 이전 AI 작업의 시작·단계·완료 여부를 기록해 미완료 작업의 자동 재시도 루프를 제어한다. 기록만으로 OS 종료 원인을 확정하지 않는다. |
| [web/src/ai/runtime.ts](../web/src/ai/runtime.ts) | 웹 AI 상태·Worker 요청·준비·생성·중단·복구를 조정한다. 화면 훅은 이 모듈 상태를 구독한다. |
| [web/src/ai/sdk-stream-reader.ts](../web/src/ai/sdk-stream-reader.ts) | SDK가 기대하는 ReadableStream 데이터 읽기 인터페이스를 어댑트한다. 범위/수명 계약을 모델 스트림 경로와 맞춘다. |
| [web/src/ai/useAIStatus.ts](../web/src/ai/useAIStatus.ts) | runtime의 외부 AI 상태를 React 구독 생명주기에 연결하는 훅. 화면 해제 시 구독을 정리한다. |
| [web/src/ai/worker-engine.ts](../web/src/ai/worker-engine.ts) | 일반 웹 Worker 안의 모델 열기·executor·생성/정리를 소유한다. protocol 응답을 메인 runtime으로 전달한다. |
| [web/src/ai/worker-resources.ts](../web/src/ai/worker-resources.ts) | Worker의 모델/SDK 자원 정리 상태를 관리한다. 중복 정리·실패 후 자원 수명은 생성 루프와 분리한다. |
| [web/src/chat.css](../web/src/chat.css) | 채팅 목록·입력·최근 답변 이동의 스타일 정의. 해당 React 화면과 클래스 이름을 함께 변경해야 한다. |
| [web/src/chat/scroll.ts](../web/src/chat/scroll.ts) | 웹 채팅의 사용자 읽기 위치·끝 따라가기 판단을 DOM 어댑터와 분리한 순수 제어기다. |
| [web/src/chat/useChatScroll.ts](../web/src/chat/useChatScroll.ts) | 채팅 스크롤 제어기를 React ref/effect와 연결한다. 목록 변경/사용자 이동을 전달하고 이벤트를 정리한다. |
| [web/src/chat/useChatViewport.ts](../web/src/chat/useChatViewport.ts) | 브라우저 viewport 제어기를 React 상태/수명에 연결한다. 키보드·화면 변화가 채팅 배치에 반영되게 한다. |
| [web/src/chat/viewport.ts](../web/src/chat/viewport.ts) | visual/layout viewport 변화로 채팅의 표시 가능 영역을 계산한다. 브라우저 이벤트와 상태 전달을 제어한다. |
| [web/src/features/PdfPage.tsx](../web/src/features/PdfPage.tsx) | 문서 ID와 쪽을 PDF.js 렌더로 연결하는 웹 원문 화면. Android PdfRenderer 화면과 별도 구현이다. |
| [web/src/features/QrScanner.tsx](../web/src/features/QrScanner.tsx) | 카메라 또는 이미지의 QR 내용을 디코딩해 인증 화면 콜백으로 전달한다. QR 해독 자체가 감독관 권한을 부여하지 않는다. |
| [web/src/features/Settings.tsx](../web/src/features/Settings.tsx) | 웹 자산/모델/음성/저장소/인증 설정 화면. 비동기 작업과 진단 내보내기를 해당 서비스 모듈에 위임한다. |
| [web/src/features/SupervisorAccess.tsx](../web/src/features/SupervisorAccess.tsx) | 감독관 QR 발급·교환·취소와 초기 코드 인증 화면. 최종 권한은 workflow/access → 서버가 확인한다. |
| [web/src/features/VoicePanel.tsx](../web/src/features/VoicePanel.tsx) | 웹 로컬 음성 준비·진행·시작/중단 UI. 실제 오디오/인식 수명은 voice 계층에 위임한다. |
| [web/src/features/WorkChecklist.tsx](../web/src/features/WorkChecklist.tsx) | 작업자 요청과 감독관 점검/승인 화면. 인증된 명령·SSE 갱신·업무 상태 모델을 조합한다. |
| [web/src/main.tsx](../web/src/main.tsx) | 브라우저 React 진입점. 앱 루트와 스타일/오프라인 등록을 연결한다. Android Activity 진입점과 별개인 보존 웹 코드다. |
| [web/src/markdown.css](../web/src/markdown.css) | 답변의 제목·표·목록·코드 표시의 스타일 정의. 해당 React 화면과 클래스 이름을 함께 변경해야 한다. |
| [web/src/offline/assets.ts](../web/src/offline/assets.ts) | 문서/정적 자산 manifest·무결성·캐시 준비·조회와 Service Worker 등록을 관리한다. |
| [web/src/qr.css](../web/src/qr.css) | 감독관 QR 스캐너/인증 화면의 스타일 정의. 해당 React 화면과 클래스 이름을 함께 변경해야 한다. |
| [web/src/rag/answer-plan.ts](../web/src/rag/answer-plan.ts) | 웹 검색 결과의 본문·필수 근거·보충 프롬프트/검증을 공유하는 답변 계획. 원문 본문을 보충 생성으로 교체하지 않는다. |
| [web/src/rag/evidence.ts](../web/src/rag/evidence.ts) | 웹 문서의 근거 문단 분리·선택·표시 순서를 만든다. 검색과 모델 답변 계획이 같은 근거 단위를 사용한다. |
| [web/src/rag/hybrid.ts](../web/src/rag/hybrid.ts) | 웹 문단 후보의 어휘/의미 순위를 결합하고 직접 근거·모호성·출처 범위를 결정한다. |
| [web/src/rag/search.ts](../web/src/rag/search.ts) | 웹의 원문 문단 어휘 점수·질문 범위·근거 선택·기존 답변 검증을 제공한다. 신경망 질의 인코딩은 semantic 계층에 있다. |
| [web/src/rag/semantic-assets.ts](../web/src/rag/semantic-assets.ts) | 고정 의미 검색 모델의 다운로드·검증·캐시/복구 상태를 관리한다. runtime이 설치된 검색 자산을 사용한다. |
| [web/src/rag/semantic-passages.ts](../web/src/rag/semantic-passages.ts) | 원문을 의미 검색용 항목/절로 구성한다. PC 인덱스 생성과 검색 결과의 원문 연결을 일치시키는 경계다. |
| [web/src/rag/semantic-runtime.ts](../web/src/rag/semantic-runtime.ts) | 메인 화면의 질문 벡터 요청을 의미 검색 Worker로 전달하고 완료/오류·수명을 관리한다. |
| [web/src/rag/semantic.worker.ts](../web/src/rag/semantic.worker.ts) | 의미 검색 Worker에서 tokenizer/ONNX 질문 인코딩을 수행해 벡터를 반환한다. 생성 모델 Worker와 구분한다. |
| [web/src/rag/text.ts](../web/src/rag/text.ts) | 웹 검색의 별칭·어휘·원문/질문 정규화 유틸리티. 질문별 정답 페이지를 저장하는 매핑은 아니다. |
| [web/src/responsive.css](../web/src/responsive.css) | 화면 폭별 메뉴·레이아웃 전환의 스타일 정의. 해당 React 화면과 클래스 이름을 함께 변경해야 한다. |
| [web/src/storage/db.ts](../web/src/storage/db.ts) | IndexedDB의 대화·설정·로컬 점검·모델 메타데이터/Blob 저장 계약. 서버 승인 DB와 별도 저장소다. |
| [web/src/style.css](../web/src/style.css) | 웹 앱 공통 색상·치수·기본 레이아웃의 스타일 정의. 해당 React 화면과 클래스 이름을 함께 변경해야 한다. |
| [web/src/types.ts](../web/src/types.ts) | 웹 문서·출처·대화 등 모듈 사이의 데이터 계약. 저장소·검색·화면이 같은 타입을 사용한다. |
| [web/src/ui.tsx](../web/src/ui.tsx) | 웹 공통 아이콘·안내·제목·Markdown 표시와 이동/포맷 유틸리티. 기능 처리는 전달받은 데이터/콜백으로 연결한다. |
| [web/src/voice.css](../web/src/voice.css) | 음성 준비/대화 상태 패널의 스타일 정의. 해당 React 화면과 클래스 이름을 함께 변경해야 한다. |
| [web/src/voice/assets.ts](../web/src/voice/assets.ts) | 웹 로컬 음성 모델 manifest·설치 확인·다운로드를 관리한다. 인식 Worker가 준비된 자산을 사용한다. |
| [web/src/voice/browser-audio.ts](../web/src/voice/browser-audio.ts) | 브라우저 마이크 캡처와 로컬 한국어 읽기를 감싼 장치 어댑터. 오디오/읽기 종료와 텍스트 분할을 관리한다. |
| [web/src/voice/catalog.ts](../web/src/voice/catalog.ts) | 웹 음성 모델의 고정 revision·파일·캐시 키/크기 카탈로그. assets와 recognizer가 공유한다. |
| [web/src/voice/conversation.ts](../web/src/voice/conversation.ts) | 웹 음성 듣기·질문·읽기·재시도 흐름을 조정한다. 질문 처리는 호출자 콜백으로 기존 채팅/RAG에 연결한다. |
| [web/src/voice/recognizer.ts](../web/src/voice/recognizer.ts) | 로컬 인식 Worker 요청·자산 준비·복구·정리를 메인 화면에서 조정한다. |
| [web/src/voice/recognizer.worker.ts](../web/src/voice/recognizer.worker.ts) | 웹 인식 Worker의 오디오 입력을 로컬 STT 모델로 처리해 결과를 돌려준다. Android OS 인식기 구현과 별개다. |
| [web/src/voice/segmenter.ts](../web/src/voice/segmenter.ts) | 입력 오디오의 발화 구간과 16 kHz 리샘플링을 처리한다. 인식기에 넘길 샘플 경계를 만든다. |
| [web/src/workflow.css](../web/src/workflow.css) | 작업 요청·점검·승인·이력 화면의 스타일 정의. 해당 React 화면과 클래스 이름을 함께 변경해야 한다. |
| [web/src/workflow/access.ts](../web/src/workflow/access.ts) | 세션/감독관 인증 상태와 CSRF를 사용하는 업무 API 클라이언트. React 구독과 서버 명령/응답을 연결한다. |
| [web/src/workflow/invitation.ts](../web/src/workflow/invitation.ts) | QR 인증 링크의 생성/파싱을 담당한다. 추출한 토큰은 access 모듈을 통해 서버에서 교환해야 한다. |
| [web/src/workflow/live.ts](../web/src/workflow/live.ts) | 권한별 SSE 변경 신호·재조회·연결 실패 폴백·만료 콜백을 관리한다. 화면 해제 시 구독/타이머를 정리한다. |
| [web/src/workflow/native.ts](../web/src/workflow/native.ts) | Android WebView embedded 표시와 네이티브 목적지 링크를 판단한다. 웹 AI 중복 초기화와 네이티브 화면 이동 경계를 구분한다. |
| [web/src/workflow/presentation.ts](../web/src/workflow/presentation.ts) | 현재 작업/종료 이력과 선택 작업을 화면 목록에 맞춰 정리한다. 과거 승인을 새 작업의 승인으로 재사용하지 않는다. |
| [web/src/workflow/work.ts](../web/src/workflow/work.ts) | 점검 항목·업무 상태·역할별 전이·승인 조건의 공통 도메인 규칙. UI의 안내와 서버 store의 최종 검사가 함께 사용한다. |

### 보존 웹 빌드·평가

| 파일 | 역할 |
| --- | --- |
| [web/index.html](../web/index.html) | 보존된 웹 React 마운트와 초기 메타데이터의 HTML 진입점이다. |
| [web/scripts/build-ai-worker.mjs](../web/scripts/build-ai-worker.mjs) | 웹 생성 SDK/Worker를 배포 자산으로 번들링한다. runtime이 참조할 실행 파일 준비 단계다. |
| [web/scripts/build-pwa.mjs](../web/scripts/build-pwa.mjs) | 빌드 결과의 정적 자산을 수집해 PWA 오프라인 파일/manifest를 만든다. 서버 API의 영속 저장을 대체하지 않는다. |
| [web/scripts/build-semantic-index.mjs](../web/scripts/build-semantic-index.mjs) | PC에서 원문 항목/절을 인코딩해 웹/Android가 읽는 고정 의미 인덱스를 만든다. prepare-semantic의 모델 파일 준비와 다른 단계다. |
| [web/scripts/evaluate-150.mjs](../web/scripts/evaluate-150.mjs) | 고정 150문항을 현재 검색/답변 경로로 평가하고 문항별 결과를 기록하는 PC 진입 스크립트다. |
| [web/scripts/evaluate-focused.mjs](../web/scripts/evaluate-focused.mjs) | 세부 주제 질문의 선택 문단/조건/출처를 평가한다. 전체 모델 일반 지식의 사실 정확도 측정과 구분한다. |
| [web/scripts/evaluate-semantic.mjs](../web/scripts/evaluate-semantic.mjs) | 의미 검색 질의/후보 결과를 고정 평가 사례와 비교한다. 입력 자산과 실제 모델 인코딩 조건을 보존해 해석한다. |
| [web/scripts/evaluate.mjs](../web/scripts/evaluate.mjs) | 웹 검색 평가의 결과를 집계/출력하는 개발 스크립트. 평가 데이터와 실행한 검색 코드의 조합이 기준이다. |
| [web/scripts/iphone-test.mjs](../web/scripts/iphone-test.mjs) | 보존된 iPhone HTTPS 시험 서버/자산 준비를 실행하고 종료를 정리하는 개발 진입점이다. |
| [web/scripts/light-worker-bridge.mjs](../web/scripts/light-worker-bridge.mjs) | 경량 Worker가 사용할 SDK/토크나이저 브리지 코드를 구성한다. 입력 SDK와 생성 결과 계약을 시험에서 확인한다. |
| [web/scripts/make-evaluation-150.mjs](../web/scripts/make-evaluation-150.mjs) | 검토용 150문항 평가 자료를 구성하는 개발 도구. 회귀 집합 변경은 성능 측정과 별도 변경으로 취급한다. |
| [web/scripts/package-knowledge.mjs](../web/scripts/package-knowledge.mjs) | PC의 문서/검색 자료를 배포용 패키지로 정리한다. 원문 판본·메타데이터·검증 자료의 대응을 유지한다. |
| [web/scripts/prepare-assets.mjs](../web/scripts/prepare-assets.mjs) | 기본 문서/정적 리소스와 웹 실행 자산을 준비한다. 앱의 Android-only 검색 자산 준비와 구분한다. |
| [web/scripts/prepare-voice.mjs](../web/scripts/prepare-voice.mjs) | 웹 로컬 STT에 필요한 고정 모델/Worker 자산을 준비한다. Android의 OS 로컬 음성 데이터는 이 파일로 설치하지 않는다. |
| [web/scripts/sw-template.js](../web/scripts/sw-template.js) | 웹 오프라인 정적 자산 캐시의 Service Worker 템플릿. API/인증 요청과 자산 캐시의 경계를 유지한다. |
| [web/vite.config.ts](../web/vite.config.ts) | 기존 웹 dev/preview의 포트·보안 헤더·작업 API 플러그인·Vitest 대상을 정의한다. |

### 웹·작업 API 검사

| 파일 | 역할 |
| --- | --- |
| [web/tests/ai-capabilities.test.ts](../web/tests/ai-capabilities.test.ts) | 웹 회귀 검사: HTTPS 신뢰 문제를 WebGPU 미지원과 구분한다 / 아이폰의 WebGPU 미지원에는 iOS 업데이트 절차를 안내한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/ai-diagnostics.test.ts](../web/tests/ai-diagnostics.test.ts) | 웹 회귀 검사: 중단 표시와 실제 힙 크기를 보존하고 질문·답변·원시 오류를 보고서에서 제외한다 / 최근 48개만 남기고 변조된 저장 내용과 잘못된 메모리 수치를 제외한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/ai-preferences.test.ts](../web/tests/ai-preferences.test.ts) | 웹 회귀 검사: 저장하지 않은 경우와 손상된 값에는 유효한 기본 토큰 설정을 사용한다 / 답변 한도와 문맥 크기를 저장하고 새 조회에서 복원한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/ai-recovery.test.ts](../web/tests/ai-recovery.test.ts) | 웹 회귀 검사: 준비 중 페이지가 종료된 뒤 새 런타임에서 미완료 단계가 복원된다 / 정상 완료는 자기 기록만 지우고 다른 화면의 진행 기록은 지우지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/ai-runtime.test.ts](../web/tests/ai-runtime.test.ts) | 웹 회귀 검사: 음성 인식이 메모리를 사용할 때 답변 모델을 정리하고 자동 적재를 막는다 / 답변 모델 준비 중 음성 인식을 시작하면 늦은 적재가 겹치지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/answer-plan.test.ts](../web/tests/answer-plan.test.ts) | 웹 회귀 검사: 실제 가스 측정 본문은 한 번에 표시되고 모델 입력과 동일한 조건·출처를 보존한다 / 실제 CPR의 조건·행동·반복 종료 시점과 회복자세를 한 답변에 유지한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/catalog.test.ts](../web/tests/catalog.test.ts) | 웹 회귀 검사: 한 번의 다운로드 호출이 고정 버전과 검증 정보를 스트리밍 설치에 전달한다 / 저장 공간 부족이면 모델을 받기 시작하지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/chat-scroll.test.ts](../web/tests/chat-scroll.test.ts) | 웹 회귀 검사: 새 질문은 최신 위치를 보여주고 여러 토큰 갱신을 한 프레임에 한 번만 따라간다 / 사용자가 스크롤하면 예약된 자동 이동을 취소하고 생성은 계속돼도 읽던 위치를 보존한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/chat-viewport.test.ts](../web/tests/chat-viewport.test.ts) | 웹 회귀 검사: Safari visual viewport shrink puts the composer above the keyboard, then restores even with focus retained / Chrome layout resize and browsers without VisualViewport use the remembered unobstructed height. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/evaluation-150.test.ts](../web/tests/evaluation-150.test.ts) | 웹 회귀 검사: 동결한 150문항은 문서 출처·필수 구절·금지 표현을 갖는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/evaluation.test.ts](../web/tests/evaluation.test.ts) | 웹 회귀 검사: 18개 단일·8개 복합·4개 문서 밖 질문의 원문 페이지 회귀 평가. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/evidence.test.ts](../web/tests/evidence.test.ts) | 웹 회귀 검사: 실제 CPR 원문에서 앞·중간·끝 절차와 수치를 빼먹지 않고 목록으로 표시한다 / 실제 가스 측정 원문에서 준비·측정·주의사항과 장비 확인 수치를 보존한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/file-model-reader.test.ts](../web/tests/file-model-reader.test.ts) | 웹 회귀 검사: 3GB 파일의 끝부분을 읽어도 앞부분을 순차 읽기하거나 캐시하지 않는다 / 보존·되감기·폐기 후 읽기는 같은 로컬 바이트를 다시 읽고 앞선 데이터를 보관하지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/focused-retrieval.test.ts](../web/tests/focused-retrieval.test.ts) | 웹 회귀 검사: 추가 질문의 기대 구절은 검색 결과가 아닌 원문에 존재한다 / 전등 질문은 첫 문장에서 방폭 조건을 직접 답하고 원본 쪽은 수정하지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/hybrid-retrieval.test.ts](../web/tests/hybrid-retrieval.test.ts) | 웹 회귀 검사: hybrid local document discovery / finds a colloquial lamp question missed by strict lexical retrieval. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/iphone-https.test.ts](../web/tests/iphone-https.test.ts) | 웹 회귀 검사: 사설 IPv4만 허용하며 공개 IP·호스트·변형 문자열은 거부한다 / 실제 TLS 인증서가 PC IP·localhost와 일치하고 다른 IP는 인증하지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/light-answer.test.ts](../web/tests/light-answer.test.ts) | 웹 회귀 검사: 문맥에 들어가지 않는 큰 단위는 누락하거나 GPU에 제출하지 않고 같은 답변의 원문 표현으로 남긴다 / 문단에 있는 수치·단위와 119가 생성 설명에서 빠졌는지 확인한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/light-bridge.test.ts](../web/tests/light-bridge.test.ts) | 웹 회귀 검사: 실제 고정 SDK에서 네이티브 Cache.add 네트워크 경로를 차단하고 입력 배치를 제한한다 / SDK 계약이 바뀌면 메모리 제한 없이 빌드하는 대신 명시적으로 실패한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/light-engine.test.ts](../web/tests/light-engine.test.ts) | 웹 회귀 검사: 모델 누락·문맥 초과 시 엔진을 만들지 않는다 / 일반 절차 질문은 본문을 먼저 표시하고 생성 없이 정확한 0 토큰을 반환한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/light-model.test.ts](../web/tests/light-model.test.ts) | 웹 회귀 검사: 경량 모델은 공식 고정 리비전의 13개 파일과 해시를 포함한다 / 스트리밍 검증 완료 후에만 설치로 인식하고 완료 파일은 재다운로드하지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/light-output.test.ts](../web/tests/light-output.test.ts) | 웹 회귀 검사: 부분 템플릿과 빈 thinking 표시를 숨기고 Markdown 답변을 보존한다 / thinking이 생성돼도 본문만 노출한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/light-prompt.test.ts](../web/tests/light-prompt.test.ts) | 웹 회귀 검사: 전체 문장이 들어가는 근거만 발췌하고 질문과 쪽수를 유지한다 / 문맥에 근거가 하나도 들어가지 않거나 질문이 너무 길면 GPU 작업 전에 중단한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/markdown.test.tsx](../web/tests/markdown.test.tsx) | 웹 회귀 검사: 제목, 강조, 목록, GFM 표와 코드를 의미 있는 HTML로 렌더링한다 / 모델이 만든 HTML이나 이미지로 코드를 실행하거나 외부 요청을 만들지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/model-stream.test.ts](../web/tests/model-stream.test.ts) | 웹 회귀 검사: 큰 Blob의 기본 stream을 쓰지 않고 1MiB 이하 입력으로 같은 바이트를 읽는다 / SDK가 큰 BYOB 버퍼를 요청해도 한 번에 읽는 파일 범위를 제한한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/model-verification.test.ts](../web/tests/model-verification.test.ts) | 웹 회귀 검사: 기존 설치는 한 번 전체 검사하고 새 조회에서도 결과를 복원해 전체 파일 읽기를 생략한다 / 같은 크기의 수정 파일은 수정시각이 바뀌면 전체 검사하고 손상을 거부한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/model.test.ts](../web/tests/model.test.ts) | 웹 회귀 검사: 검증을 통과한 파일만 설치하고 새로운 조회에서 복원한다 / 해시 불일치로 실패한 파일은 기존 모델을 덮어쓰지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/native-workflow.test.ts](../web/tests/native-workflow.test.ts) | 웹 회귀 검사: 일반 브라우저에서는 Android 전환 주소를 만들지 않는다 / 앱 점검 화면의 질문·모델 준비만 네이티브 화면으로 연결한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/offline.test.ts](../web/tests/offline.test.ts) | 웹 회귀 검사: 불완전한 다운로드는 오프라인 준비 완료로 표시하지 않는다 / 완료된 파일은 다음 준비에서 다시 검증하고 다운로드를 재사용한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/output-quality.test.ts](../web/tests/output-quality.test.ts) | 웹 회귀 검사: 태블릿 사진처럼 한국어·영어·아랍어·데바나가리어가 뒤섞인 출력을 거부한다 / 영문으로 이탈하거나 같은 문장만 반복한 출력을 거부한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/qr-supervisor.test.ts](../web/tests/qr-supervisor.test.ts) | 웹 회귀 검사: 작업자는 QR을 발급할 수 없으며 형식이 맞는 임의 토큰으로 권한을 얻을 수 없다 / 발급·스캔 1회 교환·세션 회전·작업자 소유권 보존·이중 사용 방지를 처리한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/search.test.ts](../web/tests/search.test.ts) | 웹 회귀 검사: Android 검색 이식 / 같은 Java UTF-16 해시와 384차원 정규화 벡터를 사용한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/semantic-assets.test.ts](../web/tests/semantic-assets.test.ts) | 웹 회귀 검사: installs only after the entire streamed body has passed its hash and size checks / does not mark corrupt bytes as ready and deletes the partial cache entry. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/semantic-runtime.test.ts](../web/tests/semantic-runtime.test.ts) | 웹 회귀 검사: closes the encoder before releasing its exclusive lease and clears a completed operation / keeps an incomplete marker after abort so another question cannot silently reload the model. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/service-worker.test.ts](../web/tests/service-worker.test.ts) | 웹 회귀 검사: 인증과 작업 API는 서비스 워커 캐시를 사용하지 않는다 / 오프라인 새로고침은 같은 배포 버전의 화면을 반환한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/storage.test.ts](../web/tests/storage.test.ts) | 웹 회귀 검사: 기기 기록 / 대화가 새 조회에서도 복원되며 같은 ID를 중복 저장하지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/voice-assets.test.ts](../web/tests/voice-assets.test.ts) | 웹 회귀 검사: 손상되거나 잘못된 모델 파일을 설치 완료로 표시하지 않는다 / 설치 완료 표식만 있고 필수 파일이 없으면 미준비로 판단한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/voice-conversation.test.ts](../web/tests/voice-conversation.test.ts) | 웹 회귀 검사: 짧은 충격음·침묵은 질문으로 보내지 않고 말이 끝나면 자동으로 세그먼트를 반환한다 / 긴 발화는 중간을 잘라 자동 전송하지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/voice-runtime.test.ts](../web/tests/voice-runtime.test.ts) | 웹 회귀 검사: 음성 모델이 없으면 마이크 준비 뒤 임의 다운로드나 Worker 적재를 하지 않는다 / 화면 진입·종료·재시도 클릭만으로 다른 세션의 완료 미확인 기록을 지우지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/work-api.test.ts](../web/tests/work-api.test.ts) | 웹 회귀 검사: ; / ;. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/work-live.test.ts](../web/tests/work-live.test.ts) | 웹 회귀 검사: 서버 변경 알림은 재조회하고 정상 스트림에서는 반복 폴링을 하지 않는다 / 연결 실패 중 5초마다 다시 확인하고 포커스·재연결에서도 갱신하며 이탈 시 정리한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/work-presentation.test.ts](../web/tests/work-presentation.test.ts) | 웹 회귀 검사: 종료된 과거 승인은 기본 요청 목록과 상세 화면에 나타나지 않는다 / 새 작업 요청을 열 때 이전 작업을 선택했어도 승인이나 점검 내용을 물려받지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/work-storage.test.ts](../web/tests/work-storage.test.ts) | 웹 회귀 검사: 권한 인증 도입 이전의 로컬 테스트 기록을 읽기 전용 이력으로 보존한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/worker-engine.test.ts](../web/tests/worker-engine.test.ts) | 웹 회귀 검사: 무결성 검증 후 저장된 문맥 한도로 실제 SDK 엔진을 만든다 / OPFS 모델은 워커에서 열고 실제 파일 크기를 확인한 뒤 스트리밍 적재한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/worker-resources.test.ts](../web/tests/worker-resources.test.ts) | 웹 회귀 검사: 현재 힙 뷰에서 크기를 읽고 WebGPU 오류를 내용 없이 종류로만 기록한다 / 예상하지 못한 장치 손실만 기록하고 명시적 destroy는 오류로 기록하지 않는다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |
| [web/tests/workflow.test.ts](../web/tests/workflow.test.ts) | 웹 회귀 검사: 작업 요청·감독관 점검·투입 / 작업자가 요청하고 감독관만 승인한 뒤 작업자가 투입을 확인한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다. |

### 도구·디자인 시안·루트 빌드

| 파일 | 역할 |
| --- | --- |
| [build.gradle](../build.gradle) | 루트 Gradle 플러그인 선언. 실제 Android 모듈 설정은 app/build.gradle로 이어진다. |
| [docs/webapp-ui/app.js](../docs/webapp-ui/app.js) | 보존된 정적 디자인 시안의 화면 전환/예시 상호작용. 실제 측정기/감독관 서버의 동작 코드와 구분한다. |
| [docs/webapp-ui/index.html](../docs/webapp-ui/index.html) | 정적 디자인 시안의 DOM·아이콘/스크립트 진입점. 실제 Android UI는 Compose로 구성한다. |
| [docs/webapp-ui/style.css](../docs/webapp-ui/style.css) | 정적 디자인 시안의 배치/색상/반응형 스타일. 앱 기능 로직은 포함하지 않는다. |
| [settings.gradle](../settings.gradle) | 저장소/플러그인 해석과 app 모듈 포함을 정의하는 Gradle 진입 설정이다. |
| [tools/embed-android-evaluation.mjs](../tools/embed-android-evaluation.mjs) | Android 평가 결과를 평가 페이지가 읽는 데이터로 준비하는 개발 도구. 제품 질문/학습 데이터와 구분한다. |
| [tools/evaluation-150.html](../tools/evaluation-150.html) | 150문항 및 단말 평가의 진행·성공/실패·답변/시간을 읽기 위한 로컬 관찰 페이지다. 실제 제품 질문 UI와 별개다. |
| [tools/extract-source-tables.py](../tools/extract-source-tables.py) | PDF 원본의 표/도식 셀을 대조해 구조 자료와 검증 근거를 만드는 PC 도구. 실행 모델의 답변을 학습/생성하지 않는다. |
| [tools/prepare-semantic.mjs](../tools/prepare-semantic.mjs) | 고정 검색 모델을 크기/SHA-256 검증 후 자산으로 복사한다. --android-only로 Android만 준비하며 문서 인덱스를 다시 만들지 않는다. |
| [tools/summarize-android-evaluation.mjs](../tools/summarize-android-evaluation.mjs) | Android 질문 평가 결과를 집계해 사람이 읽을 요약 자료로 만든다. 기록된 실행 조건을 새 측정으로 바꾸지 않는다. |
| [tools/summarize-model-comparison.py](../tools/summarize-model-comparison.py) | 모델 비교 JSON/추가 진단을 모아 지표·원문·표 형식 보고서를 구성한다. 실패/빈 출력을 정상 답변 속도와 구분한다. |
| [tools/sync-web-design.mjs](../tools/sync-web-design.mjs) | 웹 스타일/아이콘 기준을 Android 디자인 토큰/리소스로 동기화하는 개발 도구. 생성 대상 변경은 실제 앱 화면과 함께 확인한다. |
| [tools/watch-device-evaluation.mjs](../tools/watch-device-evaluation.mjs) | 단말 평가 결과의 진행/파일 변경을 관찰해 로컬 평가 페이지 자료를 갱신하는 도구다. |
| [tools/wifi-ap-sender.mjs](../tools/wifi-ap-sender.mjs) | 명시적인 테스트 장비 ID와 값을 TCP v1으로 전송하는 PC 송신기. 실제 센서값이나 현장 검증 결과가 아니다. |
