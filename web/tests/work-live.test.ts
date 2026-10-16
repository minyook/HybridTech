import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { watchWorkOrders } from '../src/workflow/live';

class FakeSource extends EventTarget {
  static last: FakeSource;
  onopen?:()=>void;onerror?:()=>void;close=vi.fn();
  constructor(public url:string){super();FakeSource.last=this;}
}
beforeEach(()=>{vi.useFakeTimers();vi.stubGlobal('window',new EventTarget());vi.stubGlobal('EventSource',FakeSource);});
afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals();});
it('서버 변경 알림은 재조회하고 정상 스트림에서는 반복 폴링을 하지 않는다',()=>{
  const refresh=vi.fn(),connection=vi.fn();const stop=watchWorkOrders('supervisor',refresh,connection,vi.fn());
  expect(FakeSource.last.url).toBe('/api/work-events?view=supervisor');FakeSource.last.onopen?.();
  FakeSource.last.dispatchEvent(new Event('changed'));expect(refresh).toHaveBeenCalledTimes(1);
  vi.advanceTimersByTime(15000);expect(refresh).toHaveBeenCalledTimes(1);expect(connection).toHaveBeenLastCalledWith('live');stop();
});
it('연결 실패 중 5초마다 다시 확인하고 포커스·재연결에서도 갱신하며 이탈 시 정리한다',()=>{
  const refresh=vi.fn(),connection=vi.fn();const stop=watchWorkOrders('worker',refresh,connection,vi.fn());
  FakeSource.last.onerror?.();vi.advanceTimersByTime(5000);expect(refresh).toHaveBeenCalledTimes(1);
  window.dispatchEvent(new Event('focus'));window.dispatchEvent(new Event('online'));expect(refresh).toHaveBeenCalledTimes(3);
  FakeSource.last.onopen?.();vi.advanceTimersByTime(10000);expect(refresh).toHaveBeenCalledTimes(3);
  stop();vi.advanceTimersByTime(10000);window.dispatchEvent(new Event('focus'));expect(refresh).toHaveBeenCalledTimes(3);expect(FakeSource.last.close).toHaveBeenCalledOnce();
});
it('감독관 권한이 만료되면 스트림을 닫고 세션 재확인을 요청한다',()=>{
  const expired=vi.fn();const stop=watchWorkOrders('supervisor',vi.fn(),vi.fn(),expired);
  FakeSource.last.dispatchEvent(new Event('access-expired'));expect(expired).toHaveBeenCalledOnce();expect(FakeSource.last.close).toHaveBeenCalledOnce();stop();
});
it('EventSource 미지원 환경도 요청·승인 결과를 주기적으로 받는다',()=>{
  vi.stubGlobal('EventSource',undefined);const refresh=vi.fn();const stop=watchWorkOrders('worker',refresh,vi.fn(),vi.fn());
  vi.advanceTimersByTime(10000);expect(refresh).toHaveBeenCalledTimes(2);stop();
});
