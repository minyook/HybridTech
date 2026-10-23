import { DatabaseSync } from 'node:sqlite';
import { createHash, createHmac, randomBytes, randomUUID, timingSafeEqual } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createWork, inspectionItems, transitionWork, type Actor, type Inspection, type WorkAction, type WorkOrder } from '../src/workflow/work.ts';
import {validateSample} from '../src/sensor/sample.ts';

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
interface InviteRow { id:string;hash:string;name:string;expires:number;used:number|null;revoked:number|null;generation:string;issuer_id:string }
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
    CREATE TABLE IF NOT EXISTS sensor_streams (id TEXT PRIMARY KEY, owner TEXT NOT NULL, read_hash TEXT NOT NULL, expires INTEGER NOT NULL, active INTEGER NOT NULL, updated_at TEXT, sample TEXT);
    CREATE TABLE IF NOT EXISTS attempts (identity TEXT PRIMARY KEY, failures INTEGER NOT NULL, reset_at INTEGER NOT NULL);`);
  db.exec('BEGIN IMMEDIATE');
  try{
    const inviteColumns=db.prepare('PRAGMA table_info(invites)').all().map(row=>row.name);
    if(!inviteColumns.includes('generation'))db.exec("ALTER TABLE invites ADD COLUMN generation TEXT NOT NULL DEFAULT ''");
    if(!inviteColumns.includes('issuer_id'))db.exec("ALTER TABLE invites ADD COLUMN issuer_id TEXT NOT NULL DEFAULT ''");
    db.exec('COMMIT');
  }catch(error){db.exec('ROLLBACK');db.close();throw error;}
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
    if(row.invite_id.startsWith('qr:')){
      const invite=db.prepare('SELECT * FROM invites WHERE id = ?').get(row.invite_id.slice(3)) as unknown as InviteRow|undefined;
      if(!invite||invite.used===null||invite.revoked!==null||daily.revoked!==null||invite.generation!==`${daily.day}:${daily.generation}`)return undefined;
      return {id:`${row.invite_id}:${row.worker_id}`,name:row.supervisor_name!,expiresAt:new Date(row.grant_expires).toISOString()};
    }
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
    createSensorStream(token:string|undefined){
      const owner=requireSession(token).worker_id;
      const recent=db.prepare('SELECT COUNT(*) AS count FROM sensor_streams WHERE owner=? AND expires>?').get(owner,now()+29*60*1000);
      if(Number(recent?.count??0)>=10)throw new ApiError(429,'공유 생성이 많아요. 1분 후 다시 시도해 주세요.');
      db.prepare('DELETE FROM sensor_streams WHERE expires <= ?').run(now());
      db.prepare('UPDATE sensor_streams SET active=0,sample=NULL WHERE owner=?').run(owner);
      const id=randomUUID(),readKey=secret(),expires=now()+30*60*1000;
      db.prepare('INSERT INTO sensor_streams VALUES (?,?,?,?,1,NULL,NULL)').run(id,owner,digest(readKey),expires);
      return {id,readKey,expiresAt:new Date(expires).toISOString()};
    },
    updateSensorStream(token:string|undefined,id:string,value:unknown){
      const owner=requireSession(token).worker_id;
      let sample;try{sample=value===null?null:validateSample(value,now());}catch(error){throw new ApiError(400,(error as Error).message);}
      const changed=db.prepare('UPDATE sensor_streams SET sample=?,updated_at=? WHERE id=? AND owner=? AND expires>? AND active=1').run(sample?JSON.stringify(sample):null,new Date(now()).toISOString(),id,owner,now());
      if(changed.changes!==1)throw new ApiError(403,'공유 권한이 만료되었어요. 다시 공유를 시작해 주세요.');
    },
    stopSensorStream(token:string|undefined,id:string){
      const owner=requireSession(token).worker_id;
      const changed=db.prepare('UPDATE sensor_streams SET active=0,sample=NULL WHERE id=? AND owner=?').run(id,owner);
      if(changed.changes!==1)throw new ApiError(403,'본인이 시작한 공유만 중지할 수 있어요.');
    },
    readSensorStream(token:string|undefined,id:string,key:unknown){
      requireSession(token);
      const row=db.prepare('SELECT * FROM sensor_streams WHERE id=?').get(id) as {read_hash:string;expires:number;active:number;updated_at:string|null;sample:string|null}|undefined;
      if(!row||typeof key!=='string'||key.length!==43||!timingSafeEqual(Buffer.from(row.read_hash),Buffer.from(digest(key))))throw new ApiError(403,'측정값 공유 링크를 확인해 주세요.');
      if(row.expires<=now())throw new ApiError(410,'측정값 공유가 만료되었어요. 새 링크를 전달받아 주세요.');
      return {active:row.active===1,updatedAt:row.updated_at,expiresAt:new Date(row.expires).toISOString(),sample:row.active&&row.sample?JSON.parse(row.sample):null};
    },
    issueInvite(token:string|undefined,name:unknown){
      const row=requireSession(token);requireSupervisor(row);
      const label=text(name,60);const daily=today();const invite=secret();const id=randomUUID();
      const count=db.prepare('SELECT COUNT(*) AS count FROM invites WHERE issuer_id=? AND generation=?').get(row.worker_id,`${daily.day}:${daily.generation}`);
      if(Number(count?.count??0)>=100)throw new ApiError(429,'오늘 이 기기에서 QR을 100개 발급했어요. 운영 담당자에게 문의해 주세요.');
      const expires=Math.min(now()+5*60*1000,daily.expires);
      db.prepare('INSERT INTO invites (id,hash,name,expires,used,revoked,generation,issuer_id) VALUES (?,?,?,?,NULL,NULL,?,?)').run(id,digest(invite),label,expires,`${daily.day}:${daily.generation}`,row.worker_id);
      return {id,token:invite,name:label,expiresAt:new Date(expires).toISOString(),grantExpiresAt:new Date(daily.expires).toISOString()};
    },
    listInvites(token:string|undefined){
      const row=requireSession(token);requireSupervisor(row);const daily=today();
      return (db.prepare('SELECT id,name,expires,used,revoked FROM invites WHERE issuer_id=? AND generation=? ORDER BY rowid DESC LIMIT 100').all(row.worker_id,`${daily.day}:${daily.generation}`) as unknown as Pick<InviteRow,'id'|'name'|'expires'|'used'|'revoked'>[]).map(invite=>({id:invite.id,name:invite.name,expiresAt:new Date(invite.expires).toISOString(),used:invite.used!==null,revoked:invite.revoked!==null}));
    },
    revokeInvite(token:string|undefined,id:unknown){
      const row=requireSession(token);requireSupervisor(row);
      const invite=db.prepare('SELECT * FROM invites WHERE id = ?').get(text(id,64)) as unknown as InviteRow|undefined;
      if(!invite||invite.issuer_id!==row.worker_id)throw new ApiError(403,'이 기기에서 발급한 QR만 취소할 수 있어요.');
      db.prepare('UPDATE invites SET revoked = ? WHERE id = ?').run(now(),invite.id);
    },
    redeemInvite(token:string|undefined,value:unknown,remoteIdentity:string){
      const row=requireSession(token),key=attemptKey(`qr:${remoteIdentity}`);
      const failures=db.prepare('SELECT failures,reset_at FROM attempts WHERE identity = ?').get(key) as {failures:number;reset_at:number}|undefined;
      if(failures&&failures.failures>=15&&failures.reset_at>now())throw new ApiError(429,'인증 시도가 많아요. 15분 후 다시 시도해 주세요.');
      const normalized=typeof value==='string'&&/^[A-Za-z0-9_-]{43}$/.test(value)?value:'';
      const candidate=db.prepare('SELECT * FROM invites WHERE hash = ?').get(digest(normalized)) as unknown as InviteRow|undefined;
      const daily=today();
      if(!candidate||candidate.used!==null||candidate.revoked!==null||candidate.expires<=now()||daily.revoked!==null||candidate.generation!==`${daily.day}:${daily.generation}`){
        db.prepare('INSERT INTO attempts VALUES (?,1,?) ON CONFLICT(identity) DO UPDATE SET failures=CASE WHEN reset_at<=? THEN 1 ELSE failures+1 END,reset_at=CASE WHEN reset_at<=? THEN excluded.reset_at ELSE reset_at END').run(key,now()+15*60*1000,now(),now());
        throw new ApiError(403,'만료되었거나 이미 사용·취소된 QR이에요. 새 QR을 전달받아 주세요.');
      }
      return transaction(()=>{
        const current=today();
        if(current.revoked!==null||candidate.generation!==`${current.day}:${current.generation}`)throw new ApiError(403,'새 QR을 전달받아 주세요.');
        const changed=db.prepare('UPDATE invites SET used = ? WHERE id = ? AND used IS NULL AND revoked IS NULL AND expires > ?').run(now(),candidate.id,now());
        if(changed.changes!==1)throw new ApiError(403,'이미 사용·취소되었거나 만료된 QR이에요.');
        return rotate(row,{id:`qr:${candidate.id}`,name:candidate.name,expires:current.expires});
      });
    },
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
