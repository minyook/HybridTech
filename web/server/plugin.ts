/* 소스 안내
 * Vite 개발/미리보기 서버에 동일한 API/SQLite 저장소를 등록하고 서버 종료 시 닫는다.
 * 연결 모듈: ./store.ts, ./api.ts.
 */
import type { Plugin } from 'vite';
import { createWorkStore } from './store.ts';
import { createWorkMiddleware } from './api.ts';

/**
 * 기존 Vite 개발/미리보기 서버에 동일한 SQLite 작업 API를 붙이는 조립 지점.
 * 서버별 store를 열고 HTTP middleware에 주입한 뒤 서버 close에서 DB를 닫는다.
 * dist 정적 파일만 배포하면 이 플러그인은 실행되지 않으므로 승인 API가 생기지 않는다.
 */
export function workApi(): Plugin {
  return {
    name: 'fieldguard-work-api',
    configureServer(server) {
      const store = createWorkStore(process.env.FIELDGUARD_WORK_DB);
      server.middlewares.use(createWorkMiddleware(store));
      server.httpServer?.once('close', () => store.close());
    },
    configurePreviewServer(server) {
      const store = createWorkStore(process.env.FIELDGUARD_WORK_DB);
      server.middlewares.use(createWorkMiddleware(store));
      server.httpServer.once('close', () => store.close());
    },
  };
}
