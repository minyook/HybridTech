# 웹 기준 Android 공통 디자인

최종 갱신: 2026-10-19. 현재 웹 디자인을 기준으로 설치형 앱의 공통 화면을 전면 수정했습니다. 문서·모델·대화 저장과 BLE·음성·위젯은 기존 네이티브 경로를 사용합니다.

## 디자인 원본과 코드

| 웹 기준 | Android 파일 (`app/src/main/java/com/minyook/sllm2/ui/` 기준) | 역할 |
| --- | --- | --- |
| `web/src/style.css` | `WebDesignTokens.kt` | 색상과 사이드바·상단 바·카드 크기 |
| `web/src/ui.tsx` | `app/src/main/res/drawable/workspace_icon_*.xml` | 같은 SVG 경로로 만든 Android 벡터 17종 |
| 공통 카드·버튼·제목·입력·배지 | `WorkspaceComponents.kt` | 화면마다 달랐던 기본 스타일 통일 |
| 웹 홈·설정·문서 표지 | `FieldWorkspaceUi.kt` | 홈·사이드바·문서 카드·모델 다운로드·토큰 설정 |
| 웹 질문·기록·보관함·원문 | `WorkspaceScreens.kt` | 빈 질문 화면·답변/출처·입력창·검색/필터·기록·원문 도구 |
| `web/src/markdown.css` | `MarkdownText.kt`, `MarkdownBlocks.kt` | 제목·강조·목록·인용·표·코드 표시, 표/코드 내부 가로 스크롤 |
| 전체 탐색·기기 화면 | `TossFieldGuardApp.kt` | 상단 경로·하단 탭·기기 기능에 공통 테마 적용 |
| 공통 작업 화면 | `NativeWorkPage.kt` | 같은 서버의 React 점검·감독관 화면 사용 |

저장소 루트에서 실행합니다.

```powershell
node tools/sync-web-design.mjs
node tools/sync-web-design.mjs --check
```

첫 명령은 웹 색상·크기와 아이콘을 읽어 Android 소스를 갱신합니다. 두 번째는 생성물이 원본과 다르면 종료 코드 1을 반환합니다. 생성 파일을 함께 커밋하므로 Android 앱 빌드에는 Node나 웹 서버가 필요하지 않습니다. 화면의 Compose 레이아웃은 위 파일에서 관리하며, 웹 구조가 바뀌면 대응 화면도 함께 수정합니다.

## 화면별 반영

- 홈: 같은 소개 문구·방패·준비 상태, 세 개의 현장 준비 카드, 빠른 질문·최근 기록. 앱의 가스 측정은 웹 Android 안내 카드 위치에서 진입합니다.
- 질문: 같은 영문 분류·제목·상태 배지, 중앙 빈 화면과 예시 질문, 파란 사용자 말풍선, 배경 없는 답변 본문과 출처 카드, 흰 입력창과 파란 전송 버튼. Android 음성 버튼을 유지합니다.
- 문서: 검색 입력·전체/문서별 필터·파랑/민트 표지·검색 결과 카드. 넓은 화면은 두 열, 작은 화면은 한 열입니다.
- 기록: 같은 제목·날짜·아이콘의 저장 목록과 빈 상태. Android는 세션 단위로 기존 기록을 보존합니다.
- 설정: 오프라인 준비·Gemma 다운로드, 토큰 선택·저장, 기록·감독관 인증·서버 설정. 설정에서 모델 다운로드를 바로 시작합니다. Android 모델과 실행 방식은 유지합니다.
- 원문: 문서 제목·쪽수, PDF/읽기 편한 보기/추출 텍스트, 확대·축소·앞뒤 이동. 실제 번들 PDF를 사용합니다.
- 가스·음성·이전 점검·위젯·데이터·서버: 같은 제목·카드·배지·버튼·간격을 적용합니다. 플랫폼 기능의 실제 동작과 문구는 보존합니다.

850dp 초과에서는 사이드바, 그 이하는 드로어와 홈/질문/문서/설정 하단 탭입니다. 640dp 이하에서는 모바일 배치를 사용합니다. 설정의 두 열 패널은 웹처럼 1150dp 초과에서 표시합니다. 키보드 공간은 질문 화면에만 적용하며 다른 화면으로 이동하면 키보드를 숨깁니다.

웹과 Android는 렌더링 엔진·기본 한국어 글꼴·모델 파일이 다릅니다. 공통 화면의 시각 구조·기준·아이콘을 맞추며 PWA 설치나 브라우저 영구 저장 요청을 네이티브 기능처럼 표시하지 않습니다. 이번 수정에서 웹 디자인 자체는 바꾸지 않았습니다.

## 키보드 입력 배치 (2026-10-19)

앱·웹의 키보드 열린 상태를 공통으로 맞췄습니다. 하단 메뉴·큰 페이지 제목·빈 대화 소개와 예시·하단 안내를 접고 상태 배지와 작은 입력 안내만 남깁니다. 입력창은 키보드 바로 위에 배치하고 여러 줄 입력은 4줄까지 커집니다. 키보드가 닫히면 원래 소개·메뉴가 돌아오며 작성 중인 문장을 유지합니다.

- Android: `TossFieldGuardApp.kt`의 질문 전용 `imePadding`과 하단 메뉴 숨김을 사용하고 Scaffold의 적용 여백을 소비합니다. `WorkspaceScreens.kt`는 `imeVisible`에 따라 소개·문구·최소 입력 높이와 아래 여백을 전환합니다. 대화는 LazyColumn 안에서 스크롤합니다.
- 웹: `web/src/chat/viewport.ts`, `useChatViewport.ts`, `chat.css`가 실제 보이는 높이를 사용합니다. 휴대전화와 키보드가 열린 넓은 화면은 대화 영역만 스크롤하고 입력창을 같은 컨테이너에 둡니다. 일반 PC 창 스크롤도 유지합니다.
- 웹 173개·Android JVM 19개 통과, 웹·일반 앱 APK·계측 테스트 APK 빌드 성공. 새 Android 회귀는 390×420dp에서 소개 접기·입력창/대화 영역 비겹침·문장 보존·복원을 검사하도록 작성하고 컴파일했습니다. 연결 단말이 없어 이번 계측은 실행하지 않았습니다.
- PC Chromium에서 320/390px와 넓은 1280px의 높이 감소·복원, 여러 줄 입력·메뉴 숨김·대화 중 수동 탐색을 확인했습니다. [입력 중 웹](../web/docs/screenshots/chat-keyboard-open.jpg) · [복원](../web/docs/screenshots/chat-keyboard-restored.jpg). 실제 iPhone/Android 소프트 키보드 검증과 구분합니다.
- 새 APK는 `app/build/outputs/apk/debug/app-debug.apk`이며 기존 일반 앱과 같은 디버그 인증서로 서명했습니다. 기기 앱 삭제 없이 업데이트 설치하며 모델을 다시 받지 않습니다.

## 검증과 화면

`WorkspaceDesignTest`는 Galaxy Tab S9 Ultra에서 실제 Compose를 렌더링합니다. 테스트 문서·답변·기록은 가상 상태이며 모델·BLE·실제 작업을 시작하지 않습니다. 디버그 전용 `app/src/debug/java/com/minyook/sllm2/WorkspacePreviewActivity.kt`는 테스트 호스트이고 앱 진입점은 `MainActivity`입니다.

390×844dp 화면은 같은 단말에서 해당 Compose 뷰포트를 제한해 렌더링한 결과입니다. 실제 휴대전화 하드웨어 시험과 구분합니다. Compose 화면 캡처에는 다른 앱·잠금 화면·시스템 UI를 포함하지 않습니다.

| 공통 화면 | 캡처 |
| --- | --- |
| 홈 / 설정 | [홈](screenshots/android-workspace-home.png) / [설정](screenshots/android-workspace-settings.png) |
| 질문 / 답변 | [질문](screenshots/android-workspace-chat.png) / [답변·출처](screenshots/android-workspace-answer.png) |
| 문서 / 기록 / 토큰 | [문서](screenshots/android-workspace-library.png) / [기록](screenshots/android-workspace-history.png) / [토큰](screenshots/android-workspace-tokens.png) |
| 휴대전화 크기 | [홈](screenshots/android-workspace-phone-home.png) / [질문](screenshots/android-workspace-phone-chat.png) |
| 감독관 | [공통 인증](screenshots/android-workspace-supervisor.png) |

일반 앱·디자인 앱 빌드 및 JVM 19개·기기 계측 17개가 통과했습니다. 기존 O₂ Field Guard도 같은 서명으로 데이터 삭제 없이 업데이트했습니다.

실행 결과는 [검증 기록](../web/docs/validation.md), 전체 실행·소스/저장 경로는 [루트 README](../README.md)를 참조합니다.
