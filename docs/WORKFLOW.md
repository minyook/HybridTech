# 기기 내 작업 시연

같은 프로젝트에서 작업자 앱 **O₂ Field Guard**와 별도 감독관 앱 **Field Guard 감독관**을 빌드합니다. 두 앱을 같은 기기에 설치하면 작업자 앱에 저장된 시연 작업을 감독관 앱에서 읽을 수 있습니다. 작업자 앱은 요청과 현장 기록을, 감독관 앱은 점검·승인·종료를 담당합니다. 화면의 **기기 내 시연** 표시는 작업 요청, 승인, 탈출 기록이 시연에 사용된다는 뜻입니다.

## 시연 순서

1. 작업자 화면에서 현장·위치·작업 내용을 말하거나 직접 입력하고 **시연 요청 등록**을 누릅니다.
2. 별도 **Field Guard 감독관** 앱을 열고 요청을 선택합니다. 다섯 가지 점검 항목을 확인한 뒤 **점검 내용 저장**을 누릅니다. 측정값과 감시인·현장 관리인 정보는 선택 입력입니다.
3. 저장한 항목을 확인하고 **점검 완료 · 투입 승인 기록**을 누릅니다.
4. **O₂ Field Guard**로 돌아와 시연 승인 내용을 확인하고 **현장 투입 확인**을 누릅니다.
5. **음성 문서 지원 열기**로 문서 질문 화면을 엽니다. EXAONE 사용에는 기기에 모델을 준비해야 합니다.
6. **가스 측정 열기**로 이동합니다. 현재 앱의 Wi‑Fi AP 수신 경로를 사용합니다.
7. 측정 화면에서 장비가 전달한 경보를 확인할 수 있습니다. 시연 중에는 **위험 신고**를 열고 상황을 직접 기록할 수 있습니다.
8. 작업자가 집결 위치를 입력하고 **탈출 확인 기록**을 누릅니다. 감독관 앱에서 **감독관 종료 확인**을 누르면 완료됩니다. 정상 퇴장도 기록할 수 있습니다.

## 저장과 역할

`HomeWorkScenario.kt`가 작업자 홈을 표시하고 `SupervisorActivity.kt`가 별도 감독관 앱을 시작합니다. `LocalWorkScenario.kt`가 기기 내 시연 기록과 상태 전이를 관리합니다. `SupervisorWorkProvider.kt`는 같은 키로 서명된 감독관 앱 패키지만 받습니다. 앱을 설치할 수 있는 사람의 범위와 운영자 인증은 별도 운영 환경에서 정해야 합니다.

기존 HTTP 서버와 SQLite 기록은 별도로 보관되어 있습니다. 현재 Android 시연은 그 서버에 접속하거나 과거 서버 기록을 가져오지 않습니다. 여러 기기에서 작업과 권한을 공유하려면 운영 환경에서 별도의 인증·동기화가 필요합니다.

## Android Studio에서 실행

Gradle을 동기화한 뒤 **Build Variants**에서 `app`의 변형을 선택합니다. 작업자는 `debug`, 감독관은 `supervisor`입니다. 상단의 `app` 실행 구성에서 **Launch → Default Activity**를 선택하고 같은 기기에 각각 실행하면 두 앱이 나란히 설치됩니다.

터미널에서는 `gradlew.bat :app:assembleDebug :app:assembleSupervisor`로 두 APK를 빌드합니다. 작업자 APK는 `app/build/outputs/apk/debug/app-debug.apk`이고 앱 ID는 `com.minyook.sllm2`입니다. 감독관 APK는 `app/build/outputs/apk/supervisor/app-supervisor.apk`이고 앱 ID는 `com.minyook.sllm2.supervisor`입니다. 두 앱의 서명 키는 같아야 합니다.

감독관용 계측 APK를 빌드할 때는 `gradlew.bat :app:assembleSupervisorAndroidTest -PtestSupervisor=true`를 사용합니다.
