import { createServer } from 'node:http';
import { createWorkMiddleware } from './api.ts';
import { createWorkStore } from './store.ts';

const host = process.env.FIELDGUARD_WORK_HOST ?? '127.0.0.1';
const port = Number(process.env.FIELDGUARD_WORK_PORT ?? 5174);
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('FIELDGUARD_WORK_PORT must be an integer between 1 and 65535.');
}
const store = createWorkStore(process.env.FIELDGUARD_WORK_DB);
const handle = createWorkMiddleware(store);
const server = createServer((request, response) => {
  void handle(request, response, () => {
    response.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
    response.end(JSON.stringify({ error: '요청한 경로를 찾을 수 없어요.' }));
  });
});
server.once('close', () => store.close());
server.listen(port, host, () => console.log(`Field Guard work API listening on ${host}:${port}`));
