import 'fake-indexeddb/auto';
import { openDB } from 'idb';
import { beforeEach, expect, it } from 'vitest';
import { allWorkOrders, clearRecords } from '../src/storage/db';
import { createWork } from '../src/workflow/work';
beforeEach(async()=>{await clearRecords();});
it('권한 인증 도입 이전의 로컬 테스트 기록을 읽기 전용 이력으로 보존한다',async()=>{
  await allWorkOrders();const db=await openDB('fieldguard-web');
  const order=createWork({id:'old-device',role:'worker',name:'이전 작업자'},{site:'이전 현장',location:'이전 위치',task:'로컬 테스트'});
  await db.put('workOrders',order);db.close();
  expect(await allWorkOrders()).toEqual([order]);
});
