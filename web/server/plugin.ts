import type { Plugin } from 'vite';
import { createWorkStore } from './store.ts';
import { createWorkMiddleware } from './api.ts';

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
