import 'fake-indexeddb/auto';
import { beforeEach, expect, it } from 'vitest';
import { allWorkOrders, applyWorkAction, clearRecords, saveNewWorkOrder } from '../src/storage/db';
import { createWork, type Actor } from '../src/workflow/work';
const worker:Actor={id:'local-worker',role:'worker',name:'검증 작업자'};
const supervisor:Actor={id:'local-supervisor',role:'supervisor',name:'검증 감독관'};
beforeEach(async()=>{await clearRecords();});
it('작업 요청과 사건 기록을 IndexedDB에서 새로 조회한다',async()=>{
  const order=createWork(worker,{site:'검증 현장',location:'검증 위치',task:'검증 작업'});
  await saveNewWorkOrder(order);
  const changed=await applyWorkAction(order.id,supervisor,{type:'return',reason:'측정 확인'},order.revision);
  expect(changed.revision).toBe(1);expect((await allWorkOrders())[0].events.at(-1)?.message).toContain('측정 확인');
});
it('같은 버전의 동시 갱신은 한 번만 커밋된다',async()=>{
  const order=createWork(worker,{site:'검증 현장',location:'검증 위치',task:'검증 작업'});await saveNewWorkOrder(order);
  const results=await Promise.allSettled([applyWorkAction(order.id,supervisor,{type:'return',reason:'첫 기록'},0),applyWorkAction(order.id,supervisor,{type:'return',reason:'중복 기록'},0)]);
  expect(results.filter(result=>result.status==='fulfilled')).toHaveLength(1);expect((await allWorkOrders())[0].events).toHaveLength(2);
});
it('권한 역할이 맞지 않는 작업은 기록을 변경하지 않는다',async()=>{
  const order=createWork(worker,{site:'검증 현장',location:'검증 위치',task:'검증 작업'});await saveNewWorkOrder(order);
  await expect(applyWorkAction(order.id,worker,{type:'return',reason:'잘못된 승인 역할'},0)).rejects.toThrow('감독관');
  expect((await allWorkOrders())[0].revision).toBe(0);
});
