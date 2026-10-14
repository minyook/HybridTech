export type Role = 'worker' | 'supervisor';
export interface Actor { id: string; role: Role; name: string }
export type InspectionKey = 'air' | 'ppe' | 'communication' | 'ventilation' | 'worker';
export const inspectionItems: {key:InspectionKey;title:string;detail:string}[] = [
  {key:'air',title:'가스 상태',detail:'측정 장비·시각과 O₂, H₂S, CO, CO₂ 값을 확인'},
  {key:'ppe',title:'개인보호구 (PPE)',detail:'현장에 필요한 호흡용 보호구·안전대 등 착용 상태 확인'},
  {key:'communication',title:'통신 상태',detail:'감시인, 현장 담당자와 연락 방법 확인'},
  {key:'ventilation',title:'환기 상태',detail:'환기 설비 가동과 작업 중 유지 계획 확인'},
  {key:'worker',title:'작업자 상태',detail:'작업 절차 주지·교육과 작업자 상태 확인'},
];
export interface Inspection {
  checked: InspectionKey[];
  gas: {o2:number|null;h2s:number|null;co:number|null;co2:number|null};
  device: string; measuredAt: string; validUntil: string;
  watcher: string; contact: string; note: string;
}
export type WorkStatus = 'requested'|'reviewing'|'returned'|'approved'|'entered'|'supporting'|'alarm'|'evacuated'|'exited'|'closed';
export const statusLabels: Record<WorkStatus,string> = {
  requested:'점검 요청',reviewing:'감독관 점검 중',returned:'보완 요청',approved:'투입 승인 기록',entered:'현장 투입 확인',supporting:'AI 문서 지원 중',alarm:'위험 신고 · 대피 중',evacuated:'탈출 확인',exited:'작업 종료 · 퇴장 확인',closed:'감독관 종료 확인',
};
export interface WorkEvent { id:string;at:string;actor:Actor;action:WorkAction['type']|'request';message:string;inspection?:Inspection }
export interface WorkOrder {
  id:string;revision:number;site:string;location:string;task:string;worker:Actor;
  createdAt:string;updatedAt:string;status:WorkStatus;inspection:Inspection;
  approvedBy?:Actor;approvedAt?:string;reason?:string;exitLocation?:string;
  events:WorkEvent[];
}
export type WorkAction =
  | {type:'save-inspection';inspection:Inspection}
  | {type:'approve'}
  | {type:'return';reason:string}
  | {type:'resubmit';note:string}
  | {type:'revoke';reason:string}
  | {type:'enter';acknowledged:boolean}
  | {type:'start-ai';aiReady:boolean}
  | {type:'report-danger';reason:string}
  | {type:'evacuate';location:string}
  | {type:'exit';location:string}
  | {type:'close';note:string};

export const blankInspection = ():Inspection => ({checked:[],gas:{o2:null,h2s:null,co:null,co2:null},device:'',measuredAt:'',validUntil:'',watcher:'',contact:'',note:''});
export const AIR_RULES_SOURCE = 'https://www.law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1028062345';
// Documented air criteria only. Passing these checks does not certify a worksite.
export function airProblems(gas:Inspection['gas']):string[] {
  const problems:string[]=[];
  for(const [key,label] of [['o2','O₂'],['h2s','H₂S'],['co','CO'],['co2','CO₂']] as const) {
    const value=gas[key]; if(value===null||!Number.isFinite(value)||value<0) problems.push(`${label} 측정값을 확인해 주세요.`);
  }
  if(gas.o2!==null&&Number.isFinite(gas.o2)&&(gas.o2<18||gas.o2>=23.5)) problems.push('O₂가 문서 기준(18% 이상, 23.5% 미만)을 벗어났어요.');
  if(gas.h2s!==null&&Number.isFinite(gas.h2s)&&gas.h2s>=10) problems.push('H₂S가 문서 기준(10 ppm 미만)을 벗어났어요.');
  if(gas.co!==null&&Number.isFinite(gas.co)&&gas.co>=30) problems.push('CO가 문서 기준(30 ppm 미만)을 벗어났어요.');
  if(gas.co2!==null&&Number.isFinite(gas.co2)&&gas.co2>=1.5) problems.push('CO₂가 문서 기준(1.5% 미만)을 벗어났어요.');
  return problems;
}
export function approvalProblems(inspection:Inspection,now=Date.now()):string[] {
  const problems=airProblems(inspection.gas);
  if(inspectionItems.some(item=>!inspection.checked.includes(item.key))) problems.push('감독관이 5개 점검 항목을 모두 확인해야 해요.');
  if(!inspection.device.trim()) problems.push('측정 장비와 출처를 기록해 주세요.');
  const measured=Date.parse(inspection.measuredAt);
  if(!Number.isFinite(measured)||measured>now) problems.push('실제 측정 시각을 확인해 주세요.');
  if(!Number.isFinite(Date.parse(inspection.validUntil))||Date.parse(inspection.validUntil)<=now) problems.push('감독관이 투입 확인 유효기한을 지정해야 해요.');
  if(!inspection.watcher.trim()||!inspection.contact.trim()) problems.push('감시인과 연락 방법을 기록해 주세요.');
  return problems;
}
export function createWork(actor:Actor,details:{site:string;location:string;task:string},now=new Date()):WorkOrder {
  if(actor.role!=='worker') throw new Error('작업자 화면에서 작업을 요청해 주세요.');
  if(!actor.name.trim()||Object.values(details).some(value=>!value.trim())) throw new Error('작업자 이름, 현장명, 작업 위치와 내용을 입력해 주세요.');
  const at=now.toISOString();
  return {id:crypto.randomUUID(),revision:0,...details,worker:actor,createdAt:at,updatedAt:at,status:'requested',inspection:blankInspection(),events:[{id:crypto.randomUUID(),at,actor,action:'request',message:'작업자가 감독관에게 점검을 요청했어요.'}]};
}
export function transitionWork(order:WorkOrder,actor:Actor,action:WorkAction,now=new Date()):WorkOrder {
  if(!actor.name.trim()) throw new Error('기록에 남길 이름을 입력해 주세요.');
  const supervisor=()=>{if(actor.role!=='supervisor')throw new Error('감독관 화면에서만 점검·승인을 기록할 수 있어요.');};
  const worker=()=>{if(actor.role!=='worker'||actor.id!==order.worker.id)throw new Error('요청한 작업자만 투입·퇴장 상태를 기록할 수 있어요.');};
  const requireStatus=(...allowed:WorkStatus[])=>{if(!allowed.includes(order.status))throw new Error('작업 상태가 바뀌었어요. 현재 단계에서 진행해 주세요.');};
  const next=structuredClone(order); let message='';
  switch(action.type){
    case 'save-inspection': supervisor();requireStatus('requested','reviewing');next.inspection=structuredClone(action.inspection);next.status='reviewing';message='감독관 점검 내용을 저장했어요.';break;
    case 'approve':{
      supervisor();requireStatus('reviewing');const problems=approvalProblems(order.inspection,now.getTime());if(problems.length)throw new Error(problems.join('\n'));
      next.status='approved';next.approvedBy=actor;next.approvedAt=now.toISOString();next.reason=undefined;message='점검 완료를 확인하고 투입 승인을 기록했어요.';break;
    }
    case 'return': supervisor();requireStatus('requested','reviewing');if(!action.reason.trim())throw new Error('보완이 필요한 내용을 입력해 주세요.');next.status='returned';next.reason=action.reason;message=`보완 요청: ${action.reason}`;break;
    case 'resubmit': worker();requireStatus('returned');if(!action.note.trim())throw new Error('보완한 내용을 입력해 주세요.');next.status='requested';next.inspection.checked=[];message=`재점검 요청: ${action.note}`;break;
    case 'revoke': supervisor();requireStatus('approved');if(!action.reason.trim())throw new Error('승인 취소 사유를 입력해 주세요.');next.status='reviewing';next.approvedAt=undefined;next.approvedBy=undefined;next.inspection.checked=[];next.reason=action.reason;message=`투입 승인 취소: ${action.reason}`;break;
    case 'enter': worker();requireStatus('approved');if(!action.acknowledged)throw new Error('승인 내용과 현장 절차를 확인해 주세요.');if(!Number.isFinite(Date.parse(order.inspection.validUntil))||Date.parse(order.inspection.validUntil)<=now.getTime())throw new Error('투입 확인 유효기한이 지났어요. 감독관 재점검이 필요해요.');next.status='entered';message='작업자가 승인 내용을 확인하고 현장 투입을 기록했어요.';break;
    case 'start-ai': worker();requireStatus('entered');if(!action.aiReady)throw new Error('Gemma 모델을 다운로드하고 AI를 실행해 주세요.');next.status='supporting';message='기기 내 AI 문서 지원 모드를 시작했어요. 가스 예측·경보는 Android 측정 경로에서 확인해 주세요.';break;
    case 'report-danger': if(actor.role==='worker')worker();else supervisor();requireStatus('approved','entered','supporting');if(!action.reason.trim())throw new Error('직접 확인한 위험 상황을 입력해 주세요.');next.status='alarm';next.reason=action.reason;message=`수동 위험 신고 · 대피 시작: ${action.reason}`;break;
    case 'evacuate': worker();requireStatus('alarm');if(!action.location.trim())throw new Error('실제 탈출 후 집결 위치를 기록해 주세요.');next.status='evacuated';next.exitLocation=action.location;message=`작업자가 탈출을 확인했어요. 집결 위치: ${action.location}`;break;
    case 'exit': worker();requireStatus('entered','supporting');if(!action.location.trim())throw new Error('실제 퇴장 후 위치를 기록해 주세요.');next.status='exited';next.exitLocation=action.location;message=`작업자가 퇴장을 확인했어요. 위치: ${action.location}`;break;
    case 'close': supervisor();requireStatus('evacuated','exited');next.status='closed';message=`감독관이 퇴장 기록을 확인하고 작업을 종료했어요.${action.note.trim()?` ${action.note}`:''}`;break;
  }
  next.revision++;next.updatedAt=now.toISOString();next.events.push({id:crypto.randomUUID(),at:next.updatedAt,actor,action:action.type,message,...(action.type==='approve'||action.type==='save-inspection'?{inspection:structuredClone(next.inspection)}:{})});return next;
}
