import { expect, it } from 'vitest';
import { createWork } from '../src/workflow/work';
import { selectedWork, workQueue } from '../src/workflow/presentation';

const requested=()=>createWork({id:'worker',role:'worker',name:'테스트 작업자'},{site:'가상 현장',location:'가상 위치',task:'표시 검증'});
it('종료된 과거 승인은 기본 요청 목록과 상세 화면에 나타나지 않는다',()=>{
  const closed={...requested(),status:'closed' as const,approvedAt:'2026-10-15T01:00:00Z'};
  expect(workQueue([closed],false)).toEqual([]);expect(selectedWork(workQueue([closed],false),'',false)).toBeUndefined();
  expect(workQueue([closed],true)).toEqual([closed]);
});
it('새 작업 요청을 열 때 이전 작업을 선택했어도 승인이나 점검 내용을 물려받지 않는다',()=>{
  const approved={...requested(),status:'approved' as const,approvedAt:new Date().toISOString()};
  expect(selectedWork([approved],approved.id,true)).toBeUndefined();
  const next=requested();expect(next.status).toBe('requested');expect(next.inspection.checked).toEqual([]);expect(next.approvedAt).toBeUndefined();
});
it('진행 중인 선택은 새 요청이 도착해도 유지한다',()=>{
  const reading=requested(),incoming=requested();expect(selectedWork([incoming,reading],reading.id,false)).toBe(reading);
});
