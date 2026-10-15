import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { randomUUID } from 'node:crypto';
import { rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createWorkMiddleware } from '../server/api';
import { createWorkStore, type WorkSession, type WorkStore } from '../server/store';
import { inspectionItems, type WorkOrder } from '../src/workflow/work';

let server:Server, store:WorkStore, base:string, time:number;
interface Client { cookie:string; session:WorkSession }
beforeEach(async()=>{
  time=Date.parse('2026-10-15T01:00:00Z');store=createWorkStore(':memory:',()=>time);
  const middleware=createWorkMiddleware(store);
  server=createServer((req,res)=>{void middleware(req,res,()=>{res.statusCode=404;res.end();});});
  await new Promise<void>(resolve=>server.listen(0,'127.0.0.1',resolve));
  base=`http://127.0.0.1:${(server.address() as AddressInfo).port}`;
});
afterEach(async()=>{server.closeAllConnections();await new Promise<void>(resolve=>server.close(()=>resolve()));store.close();});
async function client():Promise<Client> {
  const response=await fetch(`${base}/api/session`);
  return {cookie:response.headers.get('set-cookie')!.split(';')[0],session:await response.json()};
}
async function post(who:Client,path:string,payload:unknown,headers:Record<string,string>={}) {
  return fetch(`${base}/api/${path}`,{method:'POST',headers:{Cookie:who.cookie,'Content-Type':'application/json','X-Fieldguard-Csrf':who.session.csrf,...headers},body:JSON.stringify(payload)});
}
async function get(who:Client,view='worker') {return fetch(`${base}/api/work-orders?view=${view}`,{headers:{Cookie:who.cookie}});}
async function grant(who:Client) {
  const invite=store.todayCode();const response=await post(who,'supervisor/register',{code:invite.code});
  expect(response.status).toBe(200);
  who.cookie=response.headers.get('set-cookie')!.split(';')[0];who.session=await response.json();return invite;
}
async function request(who:Client):Promise<WorkOrder> {
  const response=await post(who,'work-orders',{name:'테스트 작업자',details:{site:'테스트 현장',location:'테스트 위치',task:'권한 회귀 검증'},actor:{id:'forged-id',role:'supervisor'}});
  expect(response.status).toBe(201);return response.json();
}
const completeInspection=()=>({checked:inspectionItems.map(item=>item.key),gas:{o2:20.9,h2s:0,co:0,co2:.04},device:'가상 장비',measuredAt:new Date(time-60_000).toISOString(),validUntil:new Date(time+3_600_000).toISOString(),watcher:'가상 감시인',contact:'테스트 연락',note:'가상 입력값'});
function action(who:Client,order:WorkOrder,value:unknown,view='supervisor',name='위조 이름') {return post(who,`work-orders/${order.id}/actions`,{view,name,revision:order.revision,action:value,actor:{role:'supervisor',id:'forged'}});}

describe('서버가 검사하는 감독관 권한',()=>{
  it('처음 접속한 브라우저는 작업자이며 쿠키는 HttpOnly와 SameSite를 사용한다',async()=>{
    const response=await fetch(`${base}/api/session`);const session:WorkSession=await response.json();
    expect(session.supervisor).toBeUndefined();expect(session.workerId).toBeTruthy();
    expect(response.headers.get('set-cookie')).toContain('HttpOnly');expect(response.headers.get('set-cookie')).toContain('SameSite=Strict');expect(response.headers.get('cache-control')).toBe('no-store');
  });
  it('접속 쿠키 없이 작업 목록을 조회할 수 없다',async()=>{expect((await fetch(`${base}/api/work-orders?view=supervisor`)).status).toBe(401);});
  it('작업자는 감독관 목록과 승인 API를 직접 호출해도 거절된다',async()=>{
    const worker=await client();const order=await request(worker);
    expect((await get(worker,'supervisor')).status).toBe(403);
    expect((await action(worker,order,{type:'approve'})).status).toBe(403);
    expect((await action(worker,order,{type:'save-inspection',inspection:completeInspection()},'worker')).status).toBe(403);
    for(const value of [{type:'approve'},{type:'return',reason:'가상 보완'},{type:'revoke',reason:'가상 취소'},{type:'close',note:''}])expect((await action(worker,order,value,'worker')).status).toBe(403);
    expect(((await (await get(worker)).json()) as WorkOrder[])[0].revision).toBe(0);
  });
  it('다른 기기의 작업자에게 요청 내용이 노출되거나 수정되지 않는다',async()=>{
    const worker=await client(),other=await client();const order=await request(worker);
    expect(order.worker.id).toBe(worker.session.workerId);expect(await (await get(other)).json()).toEqual([]);
    expect((await action(other,order,{type:'report-danger',reason:'가상 위험'},'worker','다른 이름')).status).toBe(403);
  });
  it('당일 6자리 코드로 인증하고 다른 감독관도 같은 코드를 쓸 수 있다',async()=>{
    const who=await client();const oldCookie=who.cookie;const invite=await grant(who);
    expect(invite.code).toMatch(/^\d{6}$/);expect(who.session.supervisor?.name).toContain('감독관 · 기기');expect(who.cookie).not.toBe(oldCookie);
    const another=await client();expect((await post(another,'supervisor/register',{code:invite.code,role:'supervisor'})).status).toBe(200);
    expect((await fetch(`${base}/api/work-orders?view=supervisor`,{headers:{Cookie:oldCookie}})).status).toBe(401);
  });
  it('두 기기가 동시에 당일 코드를 사용해도 각자 인증된다',async()=>{
    const a=await client(),b=await client();const invite=store.todayCode();
    const results=await Promise.all([post(a,'supervisor/register',{code:invite.code}),post(b,'supervisor/register',{code:invite.code})]);
    expect(results.map(result=>result.status)).toEqual([200,200]);
  });
  it('한국 시간 자정에 어제 코드와 기존 감독관 권한이 함께 만료된다',async()=>{
    time=Date.parse('2026-10-15T14:59:59.000Z');const who=await client();const previous=await grant(who);
    expect(who.session.supervisor?.expiresAt).toBe('2026-10-15T15:00:00.000Z');
    time+=1000;expect((await get(who,'supervisor')).status).toBe(403);
    const today=store.todayCode();expect(today.day).toBe('2026-10-16');expect(today.code).not.toBe(previous.code);
    expect((await post(who,'supervisor/register',{code:previous.code})).status).toBe(403);
    expect((await post(who,'supervisor/register',{code:today.code})).status).toBe(200);
  });
  it('5회 잘못 입력하면 15분 동안 추가 인증을 제한한다',async()=>{
    const who=await client();const invite=store.todayCode();
    for(let index=0;index<5;index++)expect((await post(who,'supervisor/register',{code:'invalid'})).status).toBe(403);
    expect((await post(who,'supervisor/register',{code:invite.code})).status).toBe(429);
    time+=15*60_000;expect((await post(who,'supervisor/register',{code:invite.code})).status).toBe(200);
  });
  it('잠그면 기존 세션도 무효화되고 작업자 기록의 소유권은 유지된다',async()=>{
    const who=await client();const order=await request(who);await grant(who);const previous=who.cookie;
    const response=await post(who,'supervisor/lock',{});expect(response.status).toBe(200);
    who.cookie=response.headers.get('set-cookie')!.split(';')[0];who.session=await response.json();
    expect(who.session.supervisor).toBeUndefined();expect((await get(who,'supervisor')).status).toBe(403);
    expect(((await (await get(who)).json()) as WorkOrder[])[0].id).toBe(order.id);
    expect((await fetch(`${base}/api/work-orders?view=supervisor`,{headers:{Cookie:previous}})).status).toBe(401);
  });
  it('코드를 교체하거나 중지하면 기존 모든 감독관 권한을 즉시 거부한다',async()=>{
    const a=await client(),b=await client();const previous=await grant(a);await grant(b);const next=store.rotateDailyCode();
    expect(next.code).not.toBe(previous.code);expect((await get(a,'supervisor')).status).toBe(403);expect((await get(b,'supervisor')).status).toBe(403);
    expect((await post(a,'supervisor/register',{code:previous.code})).status).toBe(403);await grant(a);
    store.revokeDailyCode();expect(store.todayCode().code).toBeUndefined();expect((await get(a,'supervisor')).status).toBe(403);
    expect((await post(b,'supervisor/register',{code:next.code})).status).toBe(403);
  });
  it('CSRF 토큰이 없거나 다른 출처에서 보낸 변경 요청은 거부한다',async()=>{
    const who=await client();
    expect((await post(who,'work-orders',{}, {'X-Fieldguard-Csrf':''})).status).toBe(403);
    expect((await post(who,'work-orders',{}, {Origin:'https://other.example'})).status).toBe(403);
    expect(()=>store.verifyCsrf(who.cookie.split('=')[1],'가'.repeat(who.session.csrf.length))).toThrow('다시 요청');
  });
  it('다른 기기 요청을 점검하고 서버가 확인한 감독관 기기 ID로 승인한다',async()=>{
    const worker=await client(),supervisor=await client();let order=await request(worker);await grant(supervisor);
    expect(((await (await get(supervisor,'supervisor')).json()) as WorkOrder[])[0].id).toBe(order.id);
    order=await (await action(supervisor,order,{type:'save-inspection',inspection:completeInspection()})).json();
    order=await (await action(supervisor,order,{type:'approve'})).json();
    expect(order.approvedBy?.name).toBe(supervisor.session.supervisor?.name);expect(order.approvedBy?.id).toBe(supervisor.session.supervisor?.id);
    expect(order.events.at(-1)?.inspection?.gas.o2).toBe(20.9);
    const response=await action(worker,order,{type:'enter',acknowledged:true},'worker','테스트 작업자');expect(response.status).toBe(200);expect((await response.json()).status).toBe('entered');
  });
  it('동일 버전의 동시 갱신을 한 번만 반영한다',async()=>{
    const worker=await client(),supervisor=await client();const order=await request(worker);await grant(supervisor);
    const results=await Promise.all([action(supervisor,order,{type:'return',reason:'첫 점검'}),action(supervisor,order,{type:'return',reason:'중복 점검'})]);
    expect(results.map(result=>result.status).sort()).toEqual([200,409]);
  });
  it('잘못된 입력을 저장하지 않고 서버 검사 없이 승인하지 않는다',async()=>{
    const who=await client();expect((await post(who,'work-orders',{name:'작업자',details:{site:123,location:'위치',task:'내용'}})).status).toBe(400);
    const order=await request(who);await grant(who);
    expect((await action(who,order,{type:'save-inspection',inspection:{}})).status).toBe(400);expect((await action(who,order,{type:'approve'})).status).toBe(400);
    expect(((await (await get(who,'supervisor')).json()) as WorkOrder[])[0].revision).toBe(0);
  });
  it('서버 재시작 뒤에도 당일 코드·요청 소유자·코드 중지를 보존한다',()=>{
    const filename=join(tmpdir(),`fieldguard-auth-${randomUUID()}.sqlite`);
    let persistent=createWorkStore(filename,()=>time);
    try {
      const connected=persistent.connect();const invite=persistent.todayCode();
      const granted=persistent.unlock(connected.token,invite.code,'test-device');
      const order=persistent.createOrder(granted.token,{name:'가상 작업자',details:{site:'가상 현장',location:'가상 위치',task:'재시작 검증'}});
      persistent.close();persistent=createWorkStore(filename,()=>time);
      expect(persistent.listOrders(granted.token,'supervisor')[0].id).toBe(order.id);expect(persistent.todayCode().code).toBe(invite.code);
      const other=persistent.connect();expect(persistent.unlock(other.token,invite.code,'test-device').session.supervisor).toBeDefined();
      persistent.revokeDailyCode();persistent.close();persistent=createWorkStore(filename,()=>time);
      expect(persistent.connect(granted.token).session.supervisor).toBeUndefined();
      expect(()=>persistent.listOrders(granted.token,'supervisor')).toThrow('감독관 인증');
      expect(persistent.listOrders(granted.token)[0].worker.id).toBe(connected.session.workerId);
    } finally { persistent.close();for(const suffix of ['', '-wal', '-shm','.daily-key'])rmSync(filename+suffix,{force:true}); }
  });
  it('잠근 뒤에도 같은 날에는 같은 6자리 코드로 다시 인증할 수 있다',async()=>{
    const who=await client();const today=await grant(who);const locked=await post(who,'supervisor/lock',{});
    who.cookie=locked.headers.get('set-cookie')!.split(';')[0];who.session=await locked.json();
    expect((await post(who,'supervisor/register',{code:today.code})).status).toBe(200);
  });
  it('당일 코드를 다시 조회해도 바꾸지 않고 기기마다 감사 ID를 구분한다',async()=>{
    const first=store.todayCode();expect(store.todayCode()).toEqual(first);const a=await client(),b=await client();await grant(a);await grant(b);
    expect(a.session.supervisor?.id).not.toBe(b.session.supervisor?.id);expect(a.session.supervisor?.expiresAt).toBe(b.session.supervisor?.expiresAt);
  });
});
