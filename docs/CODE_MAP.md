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
| [app/src/main/java/com/minyook/sllm2/model/InferenceSettings.kt](../app/src/main/java/com/minyook/sllm2/model/InferenceSettings.kt) | 모델/실행 장치/문맥/출력 설정과 기기별 상한·저장을 정의한다. 설정 UI와 Runtime이 같은 값을 사용한다. |
| [app/src/main/java/com/minyook/sllm2/model/LocalModel.kt](../app/src/main/java/com/minyook/sllm2/model/LocalModel.kt) | 범용 생성 모델의 ID·파일·URL/revision·크기·문맥 한도 카탈로그. Worker와 Runtime이 공유한다. |
| [app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt](../app/src/main/java/com/minyook/sllm2/model/LocalModelRuntime.kt) | 질문 생성의 직렬화·프롬프트·예산·엔진 재사용·NPU/GPU/CPU 선택과 지표 전달을 담당한다. |
| [app/src/main/java/com/minyook/sllm2/model/ModelDownloadAccess.kt](../app/src/main/java/com/minyook/sllm2/model/ModelDownloadAccess.kt) | Hugging Face 읽기 토큰을 Keystore AES-GCM 키로 암호화해 Worker에 제공한다. 다운로드 인자/보고서에 원문 토큰을 넣지 않는다. |
| [app/src/main/java/com/minyook/sllm2/model/ModelDownloadWorker.kt](../app/src/main/java/com/minyook/sllm2/model/ModelDownloadWorker.kt) | 모델별 고유 WorkManager 작업·HTTP Range 이어받기·크기 검증·완성 파일 확정과 진행 알림을 처리한다. |
| [app/src/main/java/com/minyook/sllm2/model/ModelState.kt](../app/src/main/java/com/minyook/sllm2/model/ModelState.kt) | 기기 자원 정보·모델 파일 위치·설치 상태 저장 계약. READY는 엔진 적재가 아닌 파일 저장 상태다. |
| [app/src/main/java/com/minyook/sllm2/ui/AnswerProgressTimer.kt](../app/src/main/java/com/minyook/sllm2/ui/AnswerProgressTimer.kt) | 화면에 보이는 pending 답변의 경과 시간을 단조 시계로 갱신한다. 전체 추론 상태를 매초 다시 기록하지 않는다. |
| [app/src/main/java/com/minyook/sllm2/ui/ChatTimeline.kt](../app/src/main/java/com/minyook/sllm2/ui/ChatTimeline.kt) | 질문/헤더/본문/푸터를 안정 키의 lazy 항목으로 나누고 본문 파싱을 캐시한다. WorkspaceChat이 사용한다. |
| [app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt](../app/src/main/java/com/minyook/sllm2/ui/FieldGuardViewModel.kt) | 화면 이벤트를 문맥·검색·생성·기록·다운로드·AP로 조정하고 Compose용 StateFlow를 제공한다. |
| [app/src/main/java/com/minyook/sllm2/ui/FieldWorkspaceUi.kt](../app/src/main/java/com/minyook/sllm2/ui/FieldWorkspaceUi.kt) | 홈·사이드바·문서/설정 등의 공통 화면 구성. 상태는 읽고 기능 명령은 FieldGuardActions로 전달한다. |
| [app/src/main/java/com/minyook/sllm2/ui/HybridTechApp.kt](../app/src/main/java/com/minyook/sllm2/ui/HybridTechApp.kt) | FieldGuardActions 계약과 별도 UI 구현을 보유한다. 실제 MainActivity의 화면 루트는 TossFieldGuardApp이다. |
| [app/src/main/java/com/minyook/sllm2/ui/HybridTechTheme.kt](../app/src/main/java/com/minyook/sllm2/ui/HybridTechTheme.kt) | Compose 테마/색상 정의. 이 파일을 쓰는 화면과 실제 TossFieldGuardApp의 별도 테마 적용을 구분한다. |
| [app/src/main/java/com/minyook/sllm2/ui/MarkdownBlocks.kt](../app/src/main/java/com/minyook/sllm2/ui/MarkdownBlocks.kt) | Markdown을 블록/목록/인라인 표시 모델로 파싱하는 순수 함수. ChatTimeline과 MarkdownText가 사용한다. |
| [app/src/main/java/com/minyook/sllm2/ui/MarkdownText.kt](../app/src/main/java/com/minyook/sllm2/ui/MarkdownText.kt) | 파싱된 Markdown의 텍스트·표·코드·목록을 Compose로 표시한다. 넓은 표의 가로 스크롤도 이 계층에서 처리한다. |
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
| [app/src/test/java/com/minyook/sllm2/model/LocalModelTest.kt](../app/src/test/java/com/minyook/sllm2/model/LocalModelTest.kt) | 모델 ID·파일·크기·문맥 카탈로그을 검증하는 JVM 회귀 검사다. |
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
| [app/src/androidTest/java/com/minyook/sllm2/GasTopicSearchTest.kt](../app/src/androidTest/java/com/minyook/sllm2/GasTopicSearchTest.kt) | 단말 단일 가스 주제별 실제 검색을 검증하는 Android 계측 검사다. |
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
| [server/index.ts](../server/index.ts) | Vite 없이 작업 HTTP API를 실행하고 DB 수명을 관리한다. |
| [server/api.ts](../server/api.ts) | 세션·CSRF·요청 검증과 API/SSE를 담당한다. |
| [server/store.ts](../server/store.ts) | 기존 SQLite 작업·세션·QR·인증 상태를 관리한다. |
| [server/work.ts](../server/work.ts) | 작업 상태 전이와 점검 조건을 검사한다. |
| [server/manage.ts](../server/manage.ts) | 서버 PC에서 당일 감독관 코드를 확인·교체·회수한다. |

### 로컬 생성·문서 답변

- [llama_bridge.cpp](../app/src/main/cpp/llama_bridge.cpp)는 llama.cpp JNI의 로드, 토큰화, 추론과 취소를 담당한다.
- [DocumentGeneration.kt](../app/src/main/java/com/minyook/sllm2/data/DocumentGeneration.kt)는 생성 결과를 검색 근거와 대조한다.
- [SnapdragonAcceleration.kt](../app/src/main/java/com/minyook/sllm2/model/SnapdragonAcceleration.kt)는 가속 후보와 기기별 전환 사유를 정의한다.

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
| [tools/watch-device-evaluation.mjs](../tools/watch-device-evaluation.mjs) | 단말 평가 결과의 진행/파일 변경을 관찰해 로컬 평가 페이지 자료를 갱신하는 도구다. |
| [tools/wifi-ap-sender.mjs](../tools/wifi-ap-sender.mjs) | 명시적인 테스트 장비 ID와 값을 TCP v1으로 전송하는 PC 송신기. 실제 센서값이나 현장 검증 결과가 아니다. |
