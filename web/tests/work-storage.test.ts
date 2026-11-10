/* 소스 안내
 * 웹 회귀 검사: 권한 인증 도입 이전의 로컬 테스트 기록을 읽기 전용 이력으로 보존한다. 픽스처와 테스트 이름이 다루는 조건을 기준으로 해석한다.
 * 검사 예: 권한 인증 도입 이전의 로컬 테스트 기록을 읽기 전용 이력으로 보존한다
 * 실행: web/에서 npm test. 실제 네트워크/모델 검증과 mock·순수 검사의 범위는 각 fixture를 확인한다.
 */
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
