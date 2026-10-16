import type { Role } from './work';

export type WorkConnection = 'connecting' | 'live' | 'reconnecting';

// Commands use the authenticated POST API; this stream tells the other device to
// read its own authorized snapshot. No work contents travel in public broadcasts.
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
