import type { IncomingMessage, ServerResponse } from 'node:http';
import { ApiError, type WorkStore } from './store.ts';
import { randomBytes } from 'node:crypto';
import { aiRecoveryPage } from './ai-recovery-page.ts';

const COOKIE = 'fieldguard_session';
function cookieToken(request: IncomingMessage) {
  return request.headers.cookie?.split(';').map(value => value.trim()).find(value => value.startsWith(`${COOKIE}=`))?.slice(COOKIE.length + 1);
}
function setCookie(request: IncomingMessage, response: ServerResponse, token: string) {
  const secure = Boolean((request.socket as { encrypted?: boolean }).encrypted);
  response.setHeader('Set-Cookie', `${COOKIE}=${token}; Path=/api; HttpOnly; SameSite=Strict; Max-Age=2592000${secure ? '; Secure' : ''}`);
}
async function body(request: IncomingMessage) {
  if (!request.headers['content-type']?.startsWith('application/json')) throw new ApiError(415, 'JSON 형식으로 요청해 주세요.');
  let input = '';
  for await (const chunk of request) { input += chunk; if (Buffer.byteLength(input) > 32_768) throw new ApiError(413, '입력 내용이 너무 길어요.'); }
  try { return JSON.parse(input); } catch { throw new ApiError(400, '요청 내용을 확인해 주세요.'); }
}
export function createWorkMiddleware(store: WorkStore) {
  return async (request: IncomingMessage, response: ServerResponse, next: () => void) => {
    let url: URL;
    try { url = new URL(request.url ?? '/', 'http://localhost'); }
    catch { response.statusCode = 400; response.end(); return; }
    if (!url.pathname.startsWith('/api/')) return next();
    response.setHeader('Content-Type', 'application/json; charset=utf-8');
    response.setHeader('Cache-Control', 'no-store');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    const send = (status: number, value: unknown) => { response.statusCode = status; response.end(JSON.stringify(value)); };
    try {
      const origin = request.headers.origin;
      if (origin && new URL(origin).host !== request.headers.host) throw new ApiError(403, '같은 사이트에서 요청해 주세요.');
      if (request.headers['sec-fetch-site'] === 'cross-site') throw new ApiError(403, '같은 사이트에서 요청해 주세요.');
      const token = cookieToken(request);
      if(request.method==='GET'&&url.pathname==='/api/ai-recovery'){
        const nonce=randomBytes(18).toString('base64');
        response.setHeader('Content-Type','text/html; charset=utf-8');
        response.setHeader('Content-Security-Policy',`default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'`);
        response.statusCode=200;response.end(aiRecoveryPage(nonce));return;
      }
      if (request.method === 'GET' && url.pathname === '/api/session') {
        const result = store.connect(token); if (result.token) setCookie(request, response, result.token);
        return send(200, result.session);
      }
      if (request.method === 'GET' && url.pathname === '/api/work-events') {
        const view = url.searchParams.get('view') ?? 'worker';
        let version = store.changeVersion(token, view);
        response.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
        response.setHeader('X-Accel-Buffering', 'no');
        response.flushHeaders();
        const event = (name: string, value: unknown) => response.write(`event: ${name}\ndata: ${JSON.stringify(value)}\n\n`);
        // Always refresh on (re)connect: changes may have happened while disconnected.
        event('changed', { version });
        let ticks = 0;
        const timer = setInterval(() => {
          if (response.destroyed || response.writableEnded) { clearInterval(timer); return; }
          try {
            // SQLite also sees writes from the other dev/preview server process.
            const current = store.changeVersion(token, view);
            if (version !== current) { version = current; event('changed', { version }); }
            else if (++ticks % 15 === 0) response.write(': heartbeat\n\n');
          } catch (error) {
            event('access-expired', { error: error instanceof ApiError ? error.message : '연결을 다시 확인해 주세요.' });
            clearInterval(timer); response.end();
          }
        }, 1000);
        response.once('close', () => clearInterval(timer));
        return;
      }
      if (request.method === 'GET' && url.pathname === '/api/work-orders') return send(200, store.listOrders(token, url.searchParams.get('view') ?? 'worker'));
      if(request.method==='GET'&&url.pathname==='/api/supervisor/invitations')return send(200,store.listInvites(token));
      if (request.method !== 'POST') throw new ApiError(404, '요청 경로를 찾지 못했어요.');
      store.verifyCsrf(token, request.headers['x-fieldguard-csrf'] as string | undefined);
      const payload = await body(request);
      if(url.pathname==='/api/supervisor/invitations')return send(201,store.issueInvite(token,payload?.name));
      if(url.pathname==='/api/supervisor/redeem'){
        const result=store.redeemInvite(token,payload?.token,request.socket.remoteAddress??'unknown');
        setCookie(request,response,result.token);return send(200,result.session);
      }
      if(url.pathname==='/api/supervisor/revoke-invitation'){
        store.revokeInvite(token,payload?.id);return send(200,{revoked:true});
      }
      if (url.pathname === '/api/supervisor/register') {
        const result = store.unlock(token, payload?.code, request.socket.remoteAddress ?? 'unknown');
        setCookie(request, response, result.token); return send(200, result.session);
      }
      if (url.pathname === '/api/supervisor/lock') {
        const result = store.lock(token); setCookie(request, response, result.token); return send(200, result.session);
      }
      if (url.pathname === '/api/work-orders') return send(201, store.createOrder(token, payload));
      const match = url.pathname.match(/^\/api\/work-orders\/([a-zA-Z0-9-]+)\/actions$/);
      if (match) return send(200, store.applyAction(token, match[1], payload));
      throw new ApiError(404, '요청 경로를 찾지 못했어요.');
    } catch (error) {
      send(error instanceof ApiError ? error.status : 500, { error: error instanceof ApiError ? error.message : '서버에서 처리하지 못했어요. 잠시 후 다시 시도해 주세요.' });
    }
  };
}
