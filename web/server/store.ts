import { DatabaseSync } from 'node:sqlite';
import { createHash, createHmac, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createWork, inspectionItems, transitionWork, type Actor, type Inspection, type WorkAction, type WorkOrder } from '../src/workflow/work.ts';

export const defaultDatabasePath = fileURLToPath(new URL('../.local/workflow.sqlite', import.meta.url));
const SESSION_MS = 30 * 24 * 60 * 60 * 1000;
const digest = (value: string) => createHash('sha256').update(value).digest('hex');
const secret = () => randomBytes(32).toString('base64url');
export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}
export interface WorkSession {
  workerId: string;
  csrf: string;
  supervisor?: { id: string; name: string; expiresAt: string };
}
interface SessionRow { token: string; worker_id: string; csrf: string; expires: number; invite_id: string | null; supervisor_name: string | null; grant_expires: number | null }
interface DailyCodeRow { day: string; generation: string; expires: number; revoked: number | null }
export function koreanDay(time: number) {
  const day = new Date(time + 9 * 60 * 60 * 1000).toISOString().slice(0, 10);
  return { day, expires: Date.parse(`${day}T15:00:00.000Z`) };
}
function text(value: unknown, maximum: number, required = true): string {
  if (typeof value !== 'string' || value.length > maximum || (required && !value.trim())) throw new ApiError(400, '입력 내용을 확인해 주세요.');
  return value.trim();
}
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError(400, '요청 형식을 확인해 주세요.');
  return value as Record<string, unknown>;
}
function inspectionValue(value: unknown): Inspection {
  const input = object(value); const gas = object(input.gas);
  if (!Array.isArray(input.checked) || input.checked.some(key => !inspectionItems.some(item => item.key === key))) throw new ApiError(400, '점검 항목을 확인해 주세요.');
  for (const key of ['o2', 'h2s', 'co', 'co2']) if (gas[key] !== null && (typeof gas[key] !== 'number' || !Number.isFinite(gas[key]))) throw new ApiError(400, '측정값 형식을 확인해 주세요.');
  return {
    checked: [...new Set(input.checked)] as Inspection['checked'],
    gas: { o2: gas.o2, h2s: gas.h2s, co: gas.co, co2: gas.co2 } as Inspection['gas'],
    device: text(input.device, 200, false), measuredAt: text(input.measuredAt, 50, false), validUntil: text(input.validUntil, 50, false),
    watcher: text(input.watcher, 100, false), contact: text(input.contact, 200, false), note: text(input.note, 2000, false),
  };
}
function actionValue(value: unknown): WorkAction {
  const action = object(value);
  switch (action.type) {
    case 'save-inspection': return { type: action.type, inspection: inspectionValue(action.inspection) };
    case 'approve': return { type: action.type };
    case 'return': case 'revoke': case 'report-danger': return { type: action.type, reason: text(action.reason, 1000) };
    case 'resubmit': return { type: action.type, note: text(action.note, 2000) };
    case 'close': return { type: action.type, note: text(action.note, 2000, false) };
    case 'enter': return { type: action.type, acknowledged: action.acknowledged === true };
    case 'start-ai': return { type: action.type, aiReady: action.aiReady === true };
    case 'evacuate': case 'exit': return { type: action.type, location: text(action.location, 200) };
    default: throw new ApiError(400, '지원하지 않는 작업이에요.');
  }
}

export function createWorkStore(filename = defaultDatabasePath, now = () => Date.now()) {
  if (filename !== ':memory:') mkdirSync(dirname(filename), { recursive: true });
  let dailyKey: Buffer;
  if (filename === ':memory:') dailyKey = randomBytes(32);
  else {
    const keyPath = `${filename}.daily-key`;
    try { writeFileSync(keyPath, randomBytes(32), { flag: 'wx', mode: 0o600 }); }
    catch (error) { if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error; }
    dailyKey = readFileSync(keyPath);
    if (dailyKey.length !== 32) throw new Error('감독관 코드 키 파일을 확인해 주세요.');
  }
  const db = new DatabaseSync(filename);
  db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
    CREATE TABLE IF NOT EXISTS sessions (token TEXT PRIMARY KEY, worker_id TEXT NOT NULL, csrf TEXT NOT NULL, expires INTEGER NOT NULL, invite_id TEXT, supervisor_name TEXT, grant_expires INTEGER);
    CREATE TABLE IF NOT EXISTS invites (id TEXT PRIMARY KEY, hash TEXT UNIQUE NOT NULL, name TEXT NOT NULL, expires INTEGER NOT NULL, used INTEGER, revoked INTEGER);
    CREATE TABLE IF NOT EXISTS daily_codes (day TEXT PRIMARY KEY, generation TEXT NOT NULL, expires INTEGER NOT NULL, revoked INTEGER);
    CREATE TABLE IF NOT EXISTS orders (id TEXT PRIMARY KEY, worker_id TEXT NOT NULL, revision INTEGER NOT NULL, updated_at TEXT NOT NULL, body TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS order_changes (sequence INTEGER PRIMARY KEY AUTOINCREMENT, worker_id TEXT NOT NULL);
    CREATE INDEX IF NOT EXISTS order_changes_worker ON order_changes(worker_id, sequence);
    CREATE TABLE IF NOT EXISTS attempts (identity TEXT PRIMARY KEY, failures INTEGER NOT NULL, reset_at INTEGER NOT NULL);`);
  function transaction<T>(action: () => T): T {
    db.exec('BEGIN IMMEDIATE');
    try { const result = action(); db.exec('COMMIT'); return result; } catch (error) { db.exec('ROLLBACK'); throw error; }
  }
  function requireSession(token: string | undefined): SessionRow {
    const row = token ? db.prepare('SELECT * FROM sessions WHERE token = ?').get(digest(token)) as unknown as SessionRow | undefined : undefined;
    if (!row || row.expires <= now()) throw new ApiError(401, '접속 정보를 다시 확인해 주세요.');
    return row;
  }
  function codeFor(row: Pick<DailyCodeRow, 'day' | 'generation'>) {
    const value = createHmac('sha256', dailyKey).update(`fieldguard-supervisor:${row.day}:${row.generation}`).digest('hex');
    return (BigInt(`0x${value}`) % 1_000_000n).toString().padStart(6, '0');
  }
  function today() {
    const window = koreanDay(now());
    let row = db.prepare('SELECT * FROM daily_codes WHERE day = ?').get(window.day) as unknown as DailyCodeRow | undefined;
    if (!row) {
      const previousDay = koreanDay(now() - 24 * 60 * 60 * 1000).day;
      const previous = db.prepare('SELECT * FROM daily_codes WHERE day = ?').get(previousDay) as unknown as DailyCodeRow | undefined;
      let generation: string;
      do { generation = secret(); } while (previous && codeFor({ day: window.day, generation }) === codeFor(previous));
      db.prepare('INSERT OR IGNORE INTO daily_codes VALUES (?, ?, ?, NULL)').run(window.day, generation, window.expires);
      row = db.prepare('SELECT * FROM daily_codes WHERE day = ?').get(window.day) as unknown as DailyCodeRow;
    }
    return row;
  }
  function supervisor(row: SessionRow) {
    if (!row.invite_id || !row.grant_expires || row.grant_expires <= now()) return undefined;
    const daily = today();
    if (daily.revoked !== null || row.invite_id !== `daily:${daily.day}:${daily.generation}`) return undefined;
    return { id: `${row.invite_id}:${row.worker_id}`, name: row.supervisor_name!, expiresAt: new Date(row.grant_expires).toISOString() };
  }
  function publicSession(row: SessionRow): WorkSession { return { workerId: row.worker_id, csrf: row.csrf, supervisor: supervisor(row) }; }
  function requireSupervisor(row: SessionRow) {
    const grant = supervisor(row);
    if (!grant) throw new ApiError(403, '감독관 인증이 필요해요.');
    return grant;
  }
  function rotate(row: SessionRow, grant?: { id: string; name: string; expires: number }) {
    const token = secret(); const csrf = secret();
    db.prepare('DELETE FROM sessions WHERE token = ?').run(row.token);
    db.prepare('INSERT INTO sessions VALUES (?, ?, ?, ?, ?, ?, ?)').run(digest(token), row.worker_id, csrf, now() + SESSION_MS, grant?.id ?? null, grant?.name ?? null, grant?.expires ?? null);
    return { token, session: publicSession(requireSession(token)) };
  }
  function attemptKey(identity: string) { return digest(`attempt:${identity}`); }
  return {
    close() { db.close(); },
    connect(token?: string) {
      try { return { session: publicSession(requireSession(token)), token: undefined }; } catch (error) { if (!(error instanceof ApiError)) throw error; }
      const newToken = secret();
      db.prepare('INSERT INTO sessions VALUES (?, ?, ?, ?, NULL, NULL, NULL)').run(digest(newToken), randomUUID(), secret(), now() + SESSION_MS);
      return { token: newToken, session: publicSession(requireSession(newToken)) };
    },
    verifyCsrf(token: string | undefined, csrf: string | undefined) {
      const expected = requireSession(token).csrf;
      if (!csrf || Buffer.byteLength(csrf) !== Buffer.byteLength(expected) || !timingSafeEqual(Buffer.from(csrf), Buffer.from(expected))) throw new ApiError(403, '이 화면에서 다시 요청해 주세요.');
    },
    todayCode() { const row = today(); return { day: row.day, code: row.revoked === null ? codeFor(row) : undefined, expiresAt: new Date(row.expires).toISOString(), revoked: row.revoked !== null }; },
    rotateDailyCode() {
      const row = today(); let generation: string;
      do { generation = secret(); } while (codeFor({ day: row.day, generation }) === codeFor(row));
      db.prepare('UPDATE daily_codes SET generation = ?, revoked = NULL WHERE day = ?').run(generation, row.day);
      const updated = today(); return { day: updated.day, code: codeFor(updated), expiresAt: new Date(updated.expires).toISOString() };
    },
    revokeDailyCode() { const row = today(); db.prepare('UPDATE daily_codes SET revoked = ? WHERE day = ?').run(now(), row.day); },
    unlock(token: string | undefined, code: unknown, remoteIdentity: string) {
      const row = requireSession(token);
      const keys = [{ key: attemptKey(`${koreanDay(now()).day}:${remoteIdentity}`), maximum: 30 }, { key: attemptKey(`${koreanDay(now()).day}:${row.worker_id}`), maximum: 5 }];
      for (const { key, maximum } of keys) {
        const limit = db.prepare('SELECT failures, reset_at FROM attempts WHERE identity = ?').get(key) as { failures: number; reset_at: number } | undefined;
        if (limit && limit.failures >= maximum && limit.reset_at > now()) throw new ApiError(429, '인증 시도가 많아요. 15분 후 다시 시도해 주세요.');
      }
      const normalized = typeof code === 'string' ? code.trim() : '';
      const daily = today(); const expected = codeFor(daily);
      if (!/^\d{6}$/.test(normalized) || daily.revoked !== null || daily.expires <= now() || !timingSafeEqual(Buffer.from(normalized), Buffer.from(expected))) {
        for (const { key } of keys) db.prepare(`INSERT INTO attempts VALUES (?, 1, ?) ON CONFLICT(identity) DO UPDATE SET failures = CASE WHEN reset_at <= ? THEN 1 ELSE failures + 1 END, reset_at = CASE WHEN reset_at <= ? THEN excluded.reset_at ELSE reset_at END`).run(key, now() + 15 * 60 * 1000, now(), now());
        throw new ApiError(403, '오늘의 6자리 암호코드를 확인해 주세요. 날짜가 바뀌거나 교체된 코드는 사용할 수 없어요.');
      }
      return transaction(() => {
        // Recheck after acquiring the write lock: an administrator may rotate the code.
        const current = today();
        if (current.generation !== daily.generation || current.revoked !== null || current.expires <= now()) throw new ApiError(403, '오늘의 암호코드를 다시 확인해 주세요.');
        db.prepare('DELETE FROM attempts WHERE identity = ?').run(keys[1].key);
        return rotate(row, { id: `daily:${daily.day}:${daily.generation}`, name: `감독관 · 기기 ${row.worker_id.slice(0, 4).toUpperCase()}`, expires: daily.expires });
      });
    },
    lock(token: string | undefined) { return transaction(() => rotate(requireSession(token))); },
    changeVersion(token: string | undefined, view: unknown = 'worker'): number {
      const row = requireSession(token);
      if (view === 'supervisor') requireSupervisor(row);
      else if (view !== 'worker') throw new ApiError(400, '화면 선택을 확인해 주세요.');
      const result = view === 'supervisor'
        ? db.prepare('SELECT MAX(sequence) AS version FROM order_changes').get()
        : db.prepare('SELECT MAX(sequence) AS version FROM order_changes WHERE worker_id = ?').get(row.worker_id);
      return Number(result?.version ?? 0);
    },
    listOrders(token: string | undefined, view: unknown = 'worker'): WorkOrder[] {
      const row = requireSession(token);
      if (view === 'supervisor') requireSupervisor(row);
      else if (view !== 'worker') throw new ApiError(400, '화면 선택을 확인해 주세요.');
      const rows = view === 'supervisor' ? db.prepare('SELECT body FROM orders ORDER BY updated_at DESC').all() : db.prepare('SELECT body FROM orders WHERE worker_id = ? ORDER BY updated_at DESC').all(row.worker_id);
      return rows.map(result => JSON.parse(result.body as string));
    },
    createOrder(token: string | undefined, payload: unknown) {
      const row = requireSession(token); const input = object(payload); const details = object(input.details);
      const order = createWork({ id: row.worker_id, role: 'worker', name: text(input.name, 60) }, { site: text(details.site, 100), location: text(details.location, 150), task: text(details.task, 2000) }, new Date(now()));
      return transaction(() => {
        db.prepare('INSERT INTO orders VALUES (?, ?, ?, ?, ?)').run(order.id, row.worker_id, order.revision, order.updatedAt, JSON.stringify(order));
        db.prepare('INSERT INTO order_changes (worker_id) VALUES (?)').run(order.worker.id);
        return order;
      });
    },
    applyAction(token: string | undefined, id: string, payload: unknown) {
      const row = requireSession(token); const input = object(payload);
      let actor: Actor;
      if (input.view === 'supervisor') { const grant = requireSupervisor(row); actor = { id: grant.id, name: grant.name, role: 'supervisor' }; }
      else if (input.view === 'worker') actor = { id: row.worker_id, name: text(input.name, 60), role: 'worker' };
      else throw new ApiError(400, '화면 선택을 확인해 주세요.');
      const action = actionValue(input.action);
      const supervisorActions = ['save-inspection', 'approve', 'return', 'revoke', 'close'];
      if (actor.role === 'worker' && supervisorActions.includes(action.type)) throw new ApiError(403, '감독관 인증이 필요해요.');
      return transaction(() => {
        const record = db.prepare('SELECT body FROM orders WHERE id = ?').get(id);
        if (!record) throw new ApiError(404, '작업을 찾지 못했어요.');
        const order: WorkOrder = JSON.parse(record.body as string);
        if (actor.role === 'worker' && order.worker.id !== actor.id) throw new ApiError(403, '본인이 요청한 작업만 처리할 수 있어요.');
        if (order.revision !== input.revision) throw new ApiError(409, '다른 화면에서 작업이 갱신되었어요. 새 내용을 확인하고 다시 진행해 주세요.');
        let updated: WorkOrder;
        try { updated = transitionWork(order, actor, action, new Date(now())); } catch (error) { throw new ApiError(400, error instanceof Error ? error.message : '작업 상태를 확인해 주세요.'); }
        db.prepare('UPDATE orders SET revision = ?, updated_at = ?, body = ? WHERE id = ?').run(updated.revision, updated.updatedAt, JSON.stringify(updated), id);
        db.prepare('INSERT INTO order_changes (worker_id) VALUES (?)').run(order.worker.id);
        return updated;
      });
    },
  };
}
export type WorkStore = ReturnType<typeof createWorkStore>;
