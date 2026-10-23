# 웹 BLE 수신·측정값 공유·감독관 QR 설계

갱신: 2026-10-23. 네이티브 전환 없이 기존 React/PWA와 Node SQLite 서버를 확장한다.

## 기기와 실행

S24 Ultra 및 지원 Android 태블릿의 Chrome, PC Chrome/Edge에서 HTTPS 또는 localhost로 접속해 `설정 → 측정기 연결`을 연다. 사용자의 버튼 클릭으로 기기 선택창을 열며 자동 연결하지 않는다. iPhone 13 Pro Safari/PWA에서는 직접 Web Bluetooth를 사용할 수 없다. 지원 기기를 송신기로 사용하고 아이폰은 같은 서버의 공유 QR로 값을 확인한다. Android 설치형의 기존 네이티브 BLE는 별도이며 WebView에 네이티브 브리지를 추가하지 않았다.

개발은 `web/`에서 `npm.cmd run dev`, iPhone HTTPS 시험은 기존 `npm.cmd run iphone:test`를 사용한다. 다른 기기에서도 접속 가능한 같은 HTTPS 주소가 필요하며 localhost로 발급한 QR은 다른 기기에서 열리지 않는다. 변경한 서버 API를 사용하려면 기존 Vite/미리보기 프로세스도 재시작한다. 정적 파일만 배포하면 QR 인증·공유는 동작하지 않는다.

## 직접 BLE

1. 서비스 UUID를 비우면 연결 여부만 확인한다. 제조사 UUID를 알면 입력하고 연결한다. 짧은 16/32비트 또는 전체 UUID를 지원한다.
2. 지정 서비스의 특성을 조회하고, 수신 채널을 선택한다. `실시간 수신 시작`은 notify/indicate, `한 번 읽기`는 read 특성에만 활성화된다.
3. 기본 형식은 raw다. 시험용 JSON은 숫자형 o2/h2s/co/co2/lel, key=value 텍스트 및 CSV는 O₂,H₂S,CO,LEL 순서다. CO₂의 단위는 %, LEL은 % LEL이며 서로 대체하지 않는다. 누락 채널은 빈 값으로 표시한다.
4. 프레임은 알림 하나 또는 줄바꿈까지 합치기를 선택한다. 잘못된 값·범위·과대 패킷은 이전 수치를 현재 값으로 유지하지 않는다. 최근 20개 진단과 로컬 JSON 저장을 제공한다.
5. 중지·해제·끊김·늦게 완료된 연결을 구분하며 해제 후 예전 특성을 재사용하지 않는다. 수신 시각이 10초 이상 오래되면 숫자를 숨긴다. 백그라운드 지속 수신은 보장하지 않는다.

제조사·모델·GATT UUID·바이너리 필드/단위·CRC·측정 시작/종료 명령·검교정이 아직 없다. 임의 write 명령을 보내지 않으며, 범용 텍스트 형식을 제조사 드라이버라고 주장하지 않는다. BLE 데이터가 작업 점검의 수동 기록이나 승인 상태를 자동 변경하지 않는다.

## 아이폰용 읽기 전용 공유

송신기는 BLE 연결과 해석 형식을 선택한 후 명시적으로 QR을 만든다. 서버는 소유 세션과 분리된 32바이트 읽기 비밀키를 발급하고 해시만 저장한다. QR은 URL fragment에 스트림 ID·키를 담으며 읽을 때 fragment를 지운다. API 조회에는 `X-Fieldguard-Sensor` 헤더로 키를 전달한다. 인증 권한은 부여하지 않는다.

30분 유효, 같은 송신기의 이전 스트림은 중단한다. 송신·조회는 2초 간격이며 송신 화면을 떠나면 중단한다. 숨김·끊김·오래된 값은 null을 전송한다. 서버에는 원시 패킷/이력이 아닌 최신 해석값만 저장한다. 수신기도 10초 신선도를 검사한다. 전송 서버/네트워크가 필요하나 외부 인터넷 AI를 사용하지 않는다. 서버 장애·잠금·백그라운드에서 경보 전달을 보장하지 않는다.

## 감독관 QR

최초 담당자는 기존 서버 CLI의 오늘 코드로 인증한다. 이후 인증된 감독관은 화면에서 이름을 지정해 5분 유효 QR을 발급한다. 작업자는 QR을 발급할 수 없다. 카메라·사진 판독은 jsQR로 기기 안에서 수행하며 사진을 서버에 업로드하지 않는다. 카메라가 불가능하면 전달받은 링크를 입력한다.

32바이트 무작위 토큰은 해시만 DB에 보관하고 단 1회 원자적으로 교환한다. 인증 버튼을 눌러 서버 검증 후 세션 쿠키·CSRF를 회전하며 기존 작업자 소유권은 유지한다. 권한은 한국 시간 자정에 만료한다. 미사용 QR의 5분 만료와 이미 인증한 권한의 당일 만료는 다르다. 발급자는 오늘 발급 목록에서 자신이 발급한 QR/권한을 취소한다. 다음 QR 발급이 이전 감독관의 권한을 취소하지 않는다. 당일 코드 교체/폐기는 해당 세대의 QR·권한도 무효화한다.

서버는 조회·변경 때마다 권한을 검사한다. 동일 출처·CSRF·HttpOnly/SameSite 쿠키, 실패 시도 제한을 유지한다. QR 소유가 인증 수단이므로 지정 감독관에게만 전달해야 한다. 개인 계정/신원 증명·현장별 배정·계정 복구는 미구현이다. 현재 인증 감독관은 다른 감독관에게도 QR을 발급할 수 있다. 운영상 발급 전용 관리자 역할이 필요하면 별도 역할 정책을 추가해야 한다.

## 실제 소스 경로

| 경로 | 역할 |
|---|---|
| `web/src/sensor/ble.ts` | 직접 연결·특성·수신·해석·진단·신선도 |
| `web/src/sensor/sample.ts` | 공유 데이터 검증·링크 구분 |
| `web/src/sensor/DirectSensorSummary.tsx` | 작업 화면 직접 수신 표시 |
| `web/src/features/Sensor.tsx` | BLE 설정·공유 QR·원격 조회 |
| `web/src/features/QrScanner.tsx` | 카메라/사진 로컬 QR 판독 |
| `web/src/features/SupervisorAccess.tsx` | QR 인증·발급·취소·코드 복구 |
| `web/src/workflow/invitation.ts` | 동일 출처 인증 링크 검증 |
| `web/src/workflow/access.ts` | 세션/API 연결 |
| `web/server/store.ts` | SQLite 마이그레이션·원자적 QR 교환·권한·공유 저장 |
| `web/server/api.ts` | 인증/공유 API·CSRF 검사 |
| `web/src/sensor.css` | 모바일/태블릿/PC 배치 |

인증 API는 `/api/supervisor/invitations` GET/POST, `/redeem`, `/revoke-invitation` POST다. 뒤 두 경로도 `/api/supervisor/` 하위다. 공유는 `/api/sensor-streams` POST, `/:id` GET, `/:id/update` 및 `/:id/stop` POST다. 별도 QA DB는 `FIELDGUARD_WORK_DB` 환경변수로 지정할 수 있고 기본은 `web/.local/workflow.sqlite`다.

## 검증과 남은 시험

`web/`에서 `npm.cmd test`, `npm.cmd run build`. BLE 전송 모형, UUID·권한 클릭·분할/과대 패킷·취소/끊김, QR 생성→판독·위조/재사용/만료/취소·교환 경쟁, HTTP CSRF/세션·공유 소유권과 신선도를 검사한다. Windows 브라우저에서 실제 화면과 모의 BLE를 확인했다. 실제 S24/태블릿/아이폰 카메라·측정기의 하드웨어 연결·오프라인/백그라운드 동작은 별도 시험이 필요하다. 상세 결과는 `web/docs/validation.md`에 기록한다.
