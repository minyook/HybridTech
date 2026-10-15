import { useEffect, useSyncExternalStore } from 'react';
import type { WorkAction, WorkOrder } from './work';

export interface WorkSession {
  workerId: string;
  csrf: string;
  supervisor?: { id: string; name: string; expiresAt: string };
}
interface AccessState { session?: WorkSession; loading: boolean; error: string }
let state: AccessState = { loading: true, error: '' };
const listeners = new Set<() => void>();
let pending: Promise<WorkSession> | undefined;
let channel: BroadcastChannel | undefined;
function publish(value: AccessState) { state = value; listeners.forEach(listener => listener()); }
async function request<T>(path: string, payload?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api/${path}`, {
      method: payload === undefined ? 'GET' : 'POST', credentials: 'same-origin', cache: 'no-store',
      headers: payload === undefined ? undefined : { 'Content-Type': 'application/json', 'X-Fieldguard-Csrf': state.session?.csrf ?? '' },
      body: payload === undefined ? undefined : JSON.stringify(payload), signal: AbortSignal.timeout(12_000),
    });
  } catch {
    const message = '작업 서버에 연결하지 못했어요. PC 서버와 연결을 확인한 뒤 다시 시도해 주세요.';
    publish({ loading: false, error: message }); throw new Error(message);
  }
  if (!response.headers.get('content-type')?.includes('application/json')) {
    const message = '작업 서버를 사용할 수 없어요. 최신 버전의 개발·미리보기 서버를 실행해 주세요.';
    publish({ loading: false, error: message }); throw new Error(message);
  }
  const value = await response.json();
  if (!response.ok) {
    if (response.status === 401) publish({ loading: false, error: '접속 정보를 다시 확인해 주세요.' });
    if (response.status === 403 && path.includes('view=supervisor') && state.session) publish({ ...state, session: { ...state.session, supervisor: undefined } });
    throw new Error(value.error ?? '요청을 처리하지 못했어요.');
  }
  return value as T;
}
export function refreshWorkSession(): Promise<WorkSession> {
  if (pending) return pending;
  pending = (async () => {
    try {
      let session = await request<WorkSession>('session');
      if (sessionStorage.getItem('fieldguard-lock-pending')) {
        // A lock requested while offline must complete before showing a grant again.
        publish({ session: { ...session, supervisor: undefined }, loading: true, error: '' });
        session = await request<WorkSession>('supervisor/lock', {});
        sessionStorage.removeItem('fieldguard-lock-pending');
      }
      publish({ session, loading: false, error: '' });
      return session;
    } catch (error) { publish({ loading: false, error: error instanceof Error ? error.message : '접속 정보를 확인하지 못했어요.' }); throw error; }
    finally { pending = undefined; }
  })();
  return pending;
}
export function useWorkSession() {
  const snapshot = useSyncExternalStore(listener => { listeners.add(listener); return () => { listeners.delete(listener); }; }, () => state);
  useEffect(() => {
    void refreshWorkSession().catch(() => {});
    const refresh = () => { void refreshWorkSession().catch(() => {}); };
    if (!channel && 'BroadcastChannel' in window) {
      channel = new BroadcastChannel('fieldguard-access');
      channel.onmessage = () => { publish({ loading: true, error: '' }); refresh(); };
    }
    window.addEventListener('focus', refresh); window.addEventListener('online', refresh);
    const interval = setInterval(refresh, 30_000);
    return () => { window.removeEventListener('focus', refresh); window.removeEventListener('online', refresh); clearInterval(interval); };
  }, []);
  useEffect(() => {
    const expires = snapshot.session?.supervisor?.expiresAt;
    if (!expires) return;
    const timer = setTimeout(() => {
      if (state.session) publish({ ...state, session: { ...state.session, supervisor: undefined } });
    }, Math.max(0, Date.parse(expires) - Date.now()));
    return () => clearTimeout(timer);
  }, [snapshot.session?.supervisor?.expiresAt]);
  return snapshot;
}
export async function registerSupervisor(code: string) {
  if (!state.session) await refreshWorkSession();
  const session = await request<WorkSession>('supervisor/register', { code });
  publish({ session, loading: false, error: '' }); channel?.postMessage('changed');
}
export async function lockSupervisor() {
  sessionStorage.setItem('fieldguard-lock-pending', '1');
  if (state.session) publish({ ...state, session: { ...state.session, supervisor: undefined } });
  try {
    const session = await request<WorkSession>('supervisor/lock', {});
    sessionStorage.removeItem('fieldguard-lock-pending');
    publish({ session, loading: false, error: '' });
  } finally { channel?.postMessage('changed'); }
}
export async function getWorkOrders(view: 'worker' | 'supervisor') { return request<WorkOrder[]>(`work-orders?view=${view}`); }
export async function requestWorkOrder(name: string, details: { site: string; location: string; task: string }) {
  return request<WorkOrder>('work-orders', { name, details });
}
export async function updateWorkOrder(id: string, view: 'worker' | 'supervisor', name: string, action: WorkAction, revision: number) {
  return request<WorkOrder>(`work-orders/${encodeURIComponent(id)}/actions`, { view, name, action, revision });
}
