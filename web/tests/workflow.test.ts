import { describe, expect, it } from 'vitest';
import { airProblems, approvalProblems, createWork, inspectionItems, transitionWork, type Actor, type Inspection, type WorkOrder } from '../src/workflow/work';
const worker:Actor={id:'worker-1',role:'worker',name:'테스트 작업자'};
const supervisor:Actor={id:'supervisor-1',role:'supervisor',name:'테스트 감독관'};
const now=new Date('2026-10-14T01:10:00Z');
const inspection=():Inspection=>({checked:inspectionItems.map(item=>item.key),gas:{o2:20.9,h2s:0,co:0,co2:.04},device:'테스트용 측정 기록',measuredAt:'2026-10-14T01:00:00Z',validUntil:'2026-10-14T02:00:00Z',watcher:'테스트 감시인',contact:'무전 채널',note:'회귀 테스트 자료'});
function requested(){return createWork(worker,{site:'회귀 테스트',location:'테스트 맨홀',task:'시나리오 검증'},now);}
function approved(){return transitionWork(transitionWork(requested(),supervisor,{type:'save-inspection',inspection:inspection()},now),supervisor,{type:'approve'},now);}
describe('작업 요청·감독관 점검·투입',()=>{
  it('작업자가 요청하고 감독관만 승인한 뒤 작업자가 투입을 확인한다',()=>{
    let order=requested();expect(order.status).toBe('requested');
    expect(()=>transitionWork(order,worker,{type:'approve'},now)).toThrow('감독관');
    expect(()=>transitionWork(order,worker,{type:'enter',acknowledged:true},now)).toThrow('현재 단계');
    order=transitionWork(order,supervisor,{type:'save-inspection',inspection:inspection()},now);
    order=transitionWork(order,supervisor,{type:'approve'},now);
    expect(order.approvedBy?.id).toBe(supervisor.id);
    order=transitionWork(order,worker,{type:'enter',acknowledged:true},now);
    expect(order.status).toBe('entered');expect(order.events.map(event=>event.action)).toEqual(['request','save-inspection','approve','enter']);
  });
  it('다른 작업자는 승인된 작업을 투입 상태로 바꿀 수 없다',()=>{
    expect(()=>transitionWork(approved(),{...worker,id:'worker-2'},{type:'enter',acknowledged:true},now)).toThrow('요청한 작업자');
  });
  it('부분 점검이나 측정값 누락으로는 승인할 수 없다',()=>{
    const partial={...inspection(),checked:['air'] as Inspection['checked'],gas:{...inspection().gas,co:null}};
    const order=transitionWork(requested(),supervisor,{type:'save-inspection',inspection:partial},now);
    expect(()=>transitionWork(order,supervisor,{type:'approve'},now)).toThrow('5개 점검');
    expect(approvalProblems(partial,now.getTime()).some(problem=>problem.includes('CO'))).toBe(true);
  });
  it.each([['o2',17.9],['o2',23.5],['h2s',10],['co',30],['co2',1.5]] as const)('문서 기준을 벗어난 %s=%s는 승인을 막는다',(key,value)=>{
    const invalid={...inspection(),gas:{...inspection().gas,[key]:value}};
    const order=transitionWork(requested(),supervisor,{type:'save-inspection',inspection:invalid},now);
    expect(()=>transitionWork(order,supervisor,{type:'approve'},now)).toThrow('문서 기준');
  });
  it('허용 범위의 경계와 음수·NaN·무한대를 구분한다',()=>{
    expect(airProblems({o2:18,h2s:9.99,co:29.99,co2:1.49})).toEqual([]);
    expect(airProblems({o2:NaN,h2s:-1,co:Infinity,co2:null})).toHaveLength(4);
  });
  it('승인 유효기한 이후와 확인 동의 없이 투입을 기록할 수 없다',()=>{
    expect(()=>transitionWork(approved(),worker,{type:'enter',acknowledged:true},new Date('2026-10-14T02:00:00Z'))).toThrow('유효기한');
    expect(()=>transitionWork(approved(),worker,{type:'enter',acknowledged:false},now)).toThrow('승인 내용');
  });
  it('승인을 취소하면 재점검 전까지 투입이 잠긴다',()=>{
    const order=transitionWork(approved(),supervisor,{type:'revoke',reason:'측정 조건 변경'},now);
    expect(order.status).toBe('reviewing');expect(order.approvedBy).toBeUndefined();expect(order.inspection.checked).toEqual([]);
    expect(()=>transitionWork(order,worker,{type:'enter',acknowledged:true},now)).toThrow();
  });
  it('재점검 후에도 이전 승인 당시의 측정값을 사건 기록에 보존한다',()=>{
    let order=transitionWork(approved(),supervisor,{type:'revoke',reason:'재측정'},now);
    order=transitionWork(order,supervisor,{type:'save-inspection',inspection:{...inspection(),gas:{...inspection().gas,o2:21.3}}},now);
    expect(order.inspection.gas.o2).toBe(21.3);expect(order.events.find(event=>event.action==='approve')?.inspection?.gas.o2).toBe(20.9);
  });
  it('보완 요청은 작업자 재요청과 감독관 재점검을 거친다',()=>{
    const returned=transitionWork(requested(),supervisor,{type:'return',reason:'보호구 확인 필요'},now);
    expect(()=>transitionWork(returned,supervisor,{type:'approve'},now)).toThrow();
    expect(transitionWork(returned,worker,{type:'resubmit',note:'확인했어요'},now).status).toBe('requested');
  });
});
describe('지원·위험 신고·탈출 기록',()=>{
  const entered=()=>transitionWork(approved(),worker,{type:'enter',acknowledged:true},now);
  it('실행되지 않은 AI를 전환 완료로 기록하지 않는다',()=>{
    expect(()=>transitionWork(entered(),worker,{type:'start-ai',aiReady:false},now)).toThrow('모델');
    expect(transitionWork(entered(),worker,{type:'start-ai',aiReady:true},now).status).toBe('supporting');
  });
  it('수동 위험 신고·실제 집결 위치·감독관 종료 확인을 순서대로 남긴다',()=>{
    let order:WorkOrder=transitionWork(entered(),worker,{type:'report-danger',reason:'테스트 경보 기록'},now);
    expect(order.status).toBe('alarm');
    expect(()=>transitionWork(order,supervisor,{type:'close',note:''},now)).toThrow('현재 단계');
    order=transitionWork(order,worker,{type:'evacuate',location:'테스트 집결지'},now);
    order=transitionWork(order,supervisor,{type:'close',note:'인원 확인'},now);
    expect(order.status).toBe('closed');expect(order.exitLocation).toBe('테스트 집결지');
    expect(order.events.at(-3)?.message).toContain('수동 위험 신고');
  });
  it('정상 종료도 작업자 퇴장 확인이 있어야 감독관이 마감할 수 있다',()=>{
    const order=transitionWork(entered(),worker,{type:'exit',location:'외부 통제 구역'},now);
    expect(transitionWork(order,supervisor,{type:'close',note:''},now).status).toBe('closed');
  });
  it('실제 위치와 위험 내용이 없는 빈 기록은 거부한다',()=>{
    expect(()=>transitionWork(entered(),worker,{type:'report-danger',reason:' '},now)).toThrow('위험 상황');
    expect(()=>transitionWork(entered(),worker,{type:'exit',location:''},now)).toThrow('실제 퇴장');
  });
});
