# iPhone에서 같은 Wi-Fi로 시험하기

최종 갱신: 2026-10-19. 앱·AI·PWA의 HTTPS 시험을 위한 개발 전용 안내입니다. 실제 iPhone의 인증서 설치와 Safari 실행은 사용자가 직접 확인해야 합니다.

## PC에서 실행

PC와 iPhone을 같은 Wi-Fi에 연결합니다. Node.js 24.15 이상과 OpenSSL이 필요하며, Windows에서는 설치된 Git for Windows의 `C:/Program Files/Git/usr/bin/openssl.exe`도 자동으로 찾습니다.

저장소 루트에서 실행합니다.

```powershell
cd web
npm.cmd run iphone:test
```

이 명령은 최신 프로덕션/PWA 파일을 빌드한 뒤 현재 PC의 사설 IPv4 주소에 두 서버를 엽니다. 기존 4173 HTTP 미리보기와 5174 개발 서버는 그대로 사용할 수 있습니다.

| 출력 주소 | 역할 |
|---|---|
| `http://<PC-IP>:4172/` | 인증서 설치 안내·공개 인증서 프로파일 다운로드 |
| `https://<PC-IP>:4174/` | 실제 웹/PWA·모델·작업 API 시험 |

**주소와 포트의 HTTP/HTTPS를 구분합니다.** `https://<PC-IP>:4173/`은 HTTP 서버에 TLS 연결을 시도하므로 보안 연결 오류가 납니다. `http://<PC-IP>:4173/`도 인증서 검증·Cache API·WebGPU·서비스 워커가 필요한 전체 앱 시험에는 적합하지 않습니다. HTTP 주소의 최초 문서 로딩 단계에서도 오류가 날 수 있습니다.

터미널에 출력된 실제 주소를 사용합니다. 사설 IP가 여러 개면 `FIELDGUARD_TEST_HOST`에 이 PC의 Wi-Fi IPv4 주소를 지정한 뒤 실행할 수 있습니다. 인터넷 공개 터널·포트 포워딩·클라우드 배포는 이 명령에 포함하지 않습니다.

## iPhone에서 직접 진행

1. Safari에서 `http://<PC-IP>:4172/`을 엽니다. 이 설치 안내 주소는 **http**입니다.
2. **개발용 인증서 다운로드**를 누릅니다. 프로파일 이름은 **Field Guard Local Test CA**입니다.
3. 설정 → **프로파일이 다운로드됨 → 설치**로 이동합니다. 메뉴가 보이지 않으면 일반 → VPN 및 기기 관리에서 다운로드한 프로파일을 선택합니다. 암호 입력과 설치는 사용자가 직접 진행합니다.
4. 설정 → 일반 → 정보 → **인증서 신뢰 설정**에서 **Field Guard Local Test CA**의 전체 신뢰를 켭니다. 프로파일 설치만으로는 SSL/TLS 신뢰가 자동 활성화되지 않습니다.
5. 설치 안내로 돌아와 **Field Guard 열기**를 누르거나 `https://<PC-IP>:4174/`을 엽니다. 이 앱 주소는 **https**입니다.
6. Safari 공유 → **홈 화면에 추가**로 설치하고 홈 화면 아이콘으로 다시 실행합니다. 그 화면에서 문서·모델을 준비합니다.

Apple 공식 안내: [프로파일 설치](https://support.apple.com/ko-kr/102400), [수동 인증서의 SSL/TLS 신뢰](https://support.apple.com/ko-kr/102390), [홈 화면 웹 앱 추가](https://support.apple.com/ko-kr/guide/iphone/iphea86e5236/ios).

이 프로파일에는 공개 개발용 CA 인증서 하나만 들어 있습니다. 해당 CA를 신뢰하면 이 PC가 발급한 서버 인증서를 iPhone이 신뢰합니다. PC의 개인키는 다운로드되지 않습니다. 테스트가 끝나면 설정 → 일반 → VPN 및 기기 관리에서 프로파일을 제거할 수 있습니다. PC의 Windows 인증서 저장소에는 자동 등록하지 않습니다.

## 시험 순서

- 홈·문서 보관함·PDF를 열고 세로/가로 방향, 키보드 표시, 스크롤을 확인합니다.
- iPhone을 작업자, PC를 감독관으로 사용합니다. PC는 `http://localhost:4173/` 미리보기를 함께 실행해도 같은 DB를 사용합니다. 작업 요청 → 감독관 점검 저장 → 직접 승인 → iPhone 승인 수신 → 작업자 투입 → 감독관 결과 수신을 확인합니다.
- 홈 화면 앱의 설정에서 오프라인 파일과 약 2.01GB Gemma 4 E2B 모델을 준비합니다. Safari/WebGPU·기기 메모리 지원을 확인해야 하며, HTTPS 연결만으로 AI 실행 성공을 보장하지 않습니다.
- 준비 후 서버/네트워크를 끊고 문서와 기존 기록의 오프라인 재실행을 확인합니다. 새로운 작업 요청·감독관 승인은 서버 연결이 필요합니다.

## 파일·키·만료와 재실행

| 파일 | 내용 |
|---|---|
| `web/scripts/iphone-test.mjs` | 빌드 후 같은 Wi-Fi용 설치/HTTPS 서버 실행 |
| `web/server/iphone-https.ts` | 사설 IP 선택, 인증서 생성·재사용, 설치 안내·프로파일 |
| `web/tests/iphone-https.test.ts` | TLS 검증·IP 범위·재사용·개인키 비노출 검사 |
| `web/.local/iphone-https/ca-key.pem` | CA 개인키, Git 제외·공유 금지 |
| `web/.local/iphone-https/ca.pem` | 공개 CA 인증서, 30일 만료 |
| `web/.local/iphone-https/server-key.pem` | HTTPS 서버 개인키, Git 제외·공유 금지 |
| `web/.local/iphone-https/server.pem` | 사설 IP·localhost용 서버 인증서, 7일 만료 |

기존 CA와 키를 재사용하므로 서버를 다시 켤 때마다 iPhone에 새 인증서를 설치할 필요가 없습니다. PC IP가 바뀌면 같은 CA로 서버 인증서만 재발급합니다. 키 불일치나 CA 만료 시 자동으로 다른 CA를 신뢰시키지 않고 중지합니다. CA가 만료되면 iPhone의 기존 프로파일을 제거하고 PC의 해당 폴더를 별도로 보관한 뒤 새 인증서를 준비합니다.

서버는 Ctrl+C로 종료합니다. 앱 데이터와 작업 DB는 지우지 않습니다. HTTPS IP 주소는 PC localhost·HTTP 주소와 다른 브라우저 저장소이므로 모델·대화는 별도로 저장됩니다.
