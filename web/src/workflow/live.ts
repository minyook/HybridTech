/* 소스 안내
 * 권한별 SSE 변경 신호·재조회·연결 실패 폴백·만료 콜백을 관리한다. 화면 해제 시 구독/타이머를 정리한다.
 * 연결 모듈: ./work.
 */
import type { Role } from './work';

export type WorkConnection = 'connecting' | 'live' | 'reconnecting';

/**
 * 작업 화면의 상대 기기 변경 구독. 명령은 인증된 POST이고 SSE는 목록 재조회 신호만 전달한다.
 * 연결 실패에는 5초 폴백, 포커스/온라인 복귀에는 재조회를 사용한다. 화면 해제 시 반환한 정리 함수를 호출한다.
 * 감독관 만료 이벤트는 expired 콜백으로 인증 화면에 전달하며 클라이언트 캐시를 권한 근거로 삼지 않는다.
 */
export function watchWorkOrders(view: Role, refresh: () => void, connection: (value: WorkConnection) => void, expired: () => void) {
  let source: EventSource | undefined;
  let live = false;
  connection('connecting');
  if (typeof EventSource !== 'undefined') {
    source = new EventSource(`/api/work-events?view=${view}`);
    source.onopen = () => { live = true; connection('live'); };
    source.addEventListener('changed', refresh);
    source.onerror = () => { live = false; connection('reconnecting'); };
    source.addEventListener('access-expired', () => {
      source?.close(); live = false; connection('reconnecting'); expired();
    });
  } else connection('reconnecting');
  // A blocked stream/proxy must not silently stop worker/supervisor updates.
  const timer = setInterval(() => { if (!live) refresh(); }, 5000);
  window.addEventListener('focus', refresh);
  window.addEventListener('online', refresh);
  return () => {
    source?.close(); clearInterval(timer);
    window.removeEventListener('focus', refresh);
    window.removeEventListener('online', refresh);
  };
}
