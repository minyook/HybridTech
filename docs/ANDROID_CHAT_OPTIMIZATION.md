# Android 채팅 화면 최적화

갱신: 2026-10-30. 대상은 Android Compose 채팅입니다. 검색·생성 정책·문서/모델·저장된 대화 본문을 바꾸는 작업은 아닙니다.

## 이전 문제

1. 자동 따라가기의 `scrollToItem`과 실제 사용자 스크롤을 모두 `isScrollInProgress`로 처리했습니다. 프로그램에 의한 이동도 따라가기를 끌 수 있었습니다.
2. 최신 메시지의 **시작 인덱스**로 이동했습니다. 답변이 화면보다 길면 답변 끝까지 이동하지 못했습니다.
3. 최신 답변 이동 버튼이 목록/입력창 사이의 별도 행이어서 표시 여부에 따라 목록 높이와 입력창 위치가 바뀌었습니다.
4. LazyColumn의 한 항목 안에서 전체 Markdown 답변을 Column으로 구성했습니다. 긴 답변은 화면 밖 문단도 함께 구성했고, 상태 변경 시 본문 분석 결과를 별도로 재사용하지 않았습니다.
5. 입력 중에도 유휴 음성 안내 영역이 남았고 화면 전송과 키보드 전송을 함께 처리하는 경로가 없었습니다.

## 현재 표시와 스크롤

`ChatTimelineBuilder`는 질문·답변 헤더·Markdown 블록·출처/선택지 푸터를 독립 lazy 항목으로 만듭니다. 키는 메시지 ID와 블록 위치로 구성하고 종류별 `contentType`을 지정합니다. 추가 보충이 뒤에 붙어도 앞선 문단의 키가 유지됩니다. 표/코드는 한 블록으로 보존하며 내부 가로 스크롤을 유지합니다. 전체 본문을 요약하거나 화면 길이에 맞춰 잘라 저장하지 않습니다.

메시지 ID/본문이 같으면 분석 결과를 재사용합니다. pending 상태만 바뀌거나 새 질문이 추가될 때 기존 답변을 다시 분석하지 않습니다. 제거된 메시지의 캐시를 비웁니다. 일반 문서 뷰의 `MarkdownText`도 입력 본문별 블록을 기억하며, 인라인 강조/코드 분석은 문구별로 재사용합니다.

`NestedScrollSource.UserInput`의 실제 위쪽 탐색에서 자동 따라가기를 멈춥니다. 자동 스크롤은 멈춤 이벤트가 아닙니다. 목록이 실제 끝에 도달하고 스크롤이 멈추면 다시 따라갑니다. `최신 답변으로 이동`은 대화 영역 위에 뜨며 입력 영역 높이를 바꾸지 않습니다. 답변 추가·키보드 크기 변경 때 따라가기 중이면 마지막 1dp 끝 앵커로 이동합니다. 다른 대화 ID를 열면 목록 상태를 새 대화에 맞춰 초기화합니다.

## 입력·음성·출처

- 화면 버튼과 IME Send는 같은 `canSend`와 동작을 사용합니다. 빈 입력, 답변 처리 중, 음성 대화 중에는 중복 전송을 막습니다. 답변 중 다음 질문을 작성하는 것은 허용합니다.
- 전송 시 포커스를 해제하고 키보드를 닫습니다. 텍스트 입력은 최대 4줄이며 초안을 자르지 않습니다.
- 입력 중 유휴 음성 패널을 접고 마이크 버튼은 유지합니다. 활성 음성 대화의 상태·종료 버튼은 계속 보이며, 음성 시작 시 키보드를 닫습니다.
- 전송/마이크는 48dp 영역이며 버튼의 비활성 상태를 접근성에도 전달합니다. 답변 상태 문구는 polite live region입니다.
- 완료 답변 헤더의 복사는 가상화된 일부 문단 대신 원래 전체 Markdown 본문을 클립보드로 복사합니다. 출처 버튼은 기존 원문 ID와 쪽 표기를 사용합니다.

## 파일 경로

| 저장소 루트 기준 경로 | 역할 |
|---|---|
| `app/src/main/java/com/minyook/sllm2/ui/WorkspaceScreens.kt` | 목록/입력·따라가기·부유 버튼·복사·음성 패널·출처 |
| `app/src/main/java/com/minyook/sllm2/ui/ChatTimeline.kt` | 블록별 항목·안정 키·본문 분석 캐시/정리 |
| `app/src/main/java/com/minyook/sllm2/ui/MarkdownText.kt` | 공통 Markdown 블록 표시·인라인 캐시 |
| `app/src/main/java/com/minyook/sllm2/ui/MarkdownBlocks.kt` | 기존 블록 분석; 이번 변경 없음 |
| `app/src/test/java/com/minyook/sllm2/ui/ChatTimelineTest.kt` | 블록 보존·캐시 재사용/정리·긴 답변·키 검사 |
| `app/src/androidTest/java/com/minyook/sllm2/ChatScreenTest.kt` | 실제 Compose의 끝 이동·읽기 위치·입력창·재개·IME·음성·복사/출처 검사 |
| `tools/prepare-semantic.mjs` | `--android-only`로 웹 자산을 쓰지 않고 Android 검색 모델 준비 |
| `app/build.gradle` | 모델 누락 오류 안내를 Android 전용 준비 명령으로 변경 |
| `docs/validation/android-chat-20261030.json` | 실제 검사·APK 해시·기기 미실행 범위 |

## 검증과 남은 과제

**JVM 검사 64개와 앱/계측 APK 빌드가 통과했습니다.** 새 채팅 JVM 검사 5개는 변경하지 않은 답변의 재분석 방지, 본문 수정 시 갱신, 표/코드/출처 보존, 뒤쪽 추가 시 기존 키 유지, 1,000개 목록 항목의 블록 분리, 대화 제거/본문 비움 후 캐시 해제를 검사합니다. 기존 문서 검색·장비 질문·음성·Markdown 회귀도 함께 실행했습니다.

새 Compose 계측 검사 6개는 모델/BLE/마이크를 시작하지 않는 `WorkspacePreviewActivity`를 사용합니다. **현재 연결 기기와 사용 가능한 AVD가 없어 실행하지 않았습니다.** 계측 코드 컴파일/APK 빌드는 실제 UI 합격과 구분합니다. IME 검사도 조건 분기를 사용하는 fixture이며 실제 단말 키보드 시험을 대체하지 않습니다.

기기 연결 후 다음 계측만 골라 실행할 수 있습니다. 아래 명령은 연결 단말에 앱/검사 APK를 설치하므로 시험용 단말에서 실행합니다.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.minyook.sllm2.ChatScreenTest
```

실제 휴대전화/태블릿의 드래그·휠·키보드·화면 회전·글꼴 확대, 연속 생성 중 프레임 시간과 메모리 측정은 남아 있습니다. 블록 가상화는 매우 큰 단일 표/코드 전체를 쪼개지 않습니다. 기록 전체의 DB 페이지 단위 로딩, 생성 취소 UI, 추론 메모리·검색 정확도는 별도 개발 과제입니다. 이번 변경으로 실기기 속도가 몇 배 빨라졌다는 수치를 주장하지 않습니다.
