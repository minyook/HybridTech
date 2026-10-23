import {useEffect,useState} from 'react';
import QRCode from 'qrcode';
import {issueSupervisorInvitation,lockSupervisor,refreshWorkSession,registerSupervisor,redeemSupervisorInvitation,revokeSupervisorInvitation,listSupervisorInvitations,useWorkSession,type SupervisorInvitation,type SupervisorInvitationRecord} from '../workflow/access';
import {invitationURL,parseInvitation} from '../workflow/invitation';
import {formatDate,go,Icon,PageHead} from '../ui';
import QrScanner from './QrScanner';
export default function SupervisorAccess(){
  const access=useWorkSession(),grant=access.session?.supervisor;
  const [code,setCode]=useState(''),[token,setToken]=useState(''),[pasted,setPasted]=useState(''),[name,setName]=useState('');
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[invite,setInvite]=useState<SupervisorInvitation>(),[qr,setQr]=useState(''),[clock,setClock]=useState(Date.now());
  const [records,setRecords]=useState<SupervisorInvitationRecord[]>([]);
  async function refreshInvites(){setRecords(await listSupervisorInvitations());}
  useEffect(()=>{if(!grant){setRecords([]);return;}let active=true;const refresh=()=>{void listSupervisorInvitations().then(items=>{if(active)setRecords(items);}).catch(()=>{});};refresh();const timer=setInterval(refresh,10_000);return()=>{active=false;clearInterval(timer);};},[grant?.id]);
  useEffect(()=>{const timer=setInterval(()=>setClock(Date.now()),1000);return()=>clearInterval(timer);},[]);
  useEffect(()=>{const hash=location.hash;if(!hash.includes('?invite='))return;
    try{setToken(parseInvitation(`${location.origin}${location.pathname}${hash}`,location.origin));}catch(error){setError((error as Error).message);}
    history.replaceState(null,'',`${location.pathname}${location.search}#/supervisor-access`);
  },[]);
  function scanned(value:string){try{setToken(parseInvitation(value,location.origin));setPasted('');setError('');}catch(error){setError((error as Error).message);}}
  async function execute(action:()=>Promise<void>){if(busy)return;setBusy(true);setError('');try{await action();}catch(error){setError(error instanceof Error?error.message:'요청을 처리하지 못했어요.');}finally{setBusy(false);}}
  const currentRecord=records.find(record=>record.id===invite?.id);
  const valid=invite&&Date.parse(invite.expiresAt)>clock&&!currentRecord?.used&&!currentRecord?.revoked;
  return <><PageHead eyebrow="SUPERVISOR ACCESS" title="QR로 감독관 인증" description="담당자에게 전달받은 일회용 QR을 읽고 감독관 화면으로 전환해요."/>
    <section className="card supervisor-access"><div className="tile-icon"><Icon name="shield"/></div>
    {grant?<><h2>{grant.name}</h2><p>권한 유효기한 {formatDate(grant.expiresAt)} · 한국 시간 자정까지</p><div className="button-row spaced"><button className="btn primary" onClick={()=>go('/supervisor')}>감독관 점검 열기</button><button className="btn secondary" disabled={busy} onClick={()=>execute(async()=>{await lockSupervisor();setInvite(undefined);setQr('');go('/checklist');})}>감독관 화면 잠그기</button></div>
      <hr/><h2>다른 감독관에게 QR 전달</h2><p>지정한 감독관에게만 전달해 주세요. QR을 가진 사람은 1회 인증할 수 있어요.</p><label className="field">전달할 감독관 이름<input maxLength={60} value={name} onChange={event=>setName(event.target.value)} placeholder="예: 현장 감독관 김OO"/></label>
      <button className="btn primary" disabled={busy||!name.trim()} onClick={()=>execute(async()=>{setInvite(undefined);setQr('');const issued=await issueSupervisorInvitation(name);setInvite(issued);setQr(await QRCode.toDataURL(invitationURL(location.origin,issued.token),{width:320,margin:4,errorCorrectionLevel:'M'}));await refreshInvites();})}>5분 유효 일회용 QR 발급</button>
      {invite&&<div className="supervisor-qr">{valid&&qr?<><img src={qr} alt={`${invite.name} 감독관 인증 QR`} width={280} height={280}/><p>{invite.name} · 스캔 기한 {formatDate(invite.expiresAt)}</p><p className="caption">1회만 사용 가능 · 인증된 권한은 {formatDate(invite.grantExpiresAt)}까지</p><button className="btn secondary" onClick={()=>execute(async()=>{await navigator.clipboard.writeText(invitationURL(location.origin,invite.token));})}>인증 링크 복사</button></>:<p>사용되었거나 취소·만료된 QR이에요. 아래 목록에서 권한을 관리할 수 있어요.</p>}<button className="text-button" disabled={busy} onClick={()=>execute(async()=>{await revokeSupervisorInvitation(invite.id);setInvite(undefined);setQr('');})}>이 QR과 이 QR로 부여한 권한 취소</button></div>}
      {!!records.length&&<details className="spaced"><summary>오늘 이 기기에서 발급한 QR·권한 관리 ({records.length})</summary>{records.map(record=><div className="qr-record" key={record.id}><div><strong>{record.name}</strong><p>{record.revoked?'취소됨':record.used?'인증 사용됨 · 당일 권한':Date.parse(record.expiresAt)<=clock?'미사용 만료':'미사용 · 스캔 대기'} · {formatDate(record.expiresAt)}</p></div><button className="btn secondary" disabled={busy||record.revoked} onClick={()=>execute(async()=>{await revokeSupervisorInvitation(record.id);if(invite?.id===record.id){setInvite(undefined);setQr('');}await refreshInvites();})}>QR·권한 취소</button></div>)}</details>}
      {['localhost','127.0.0.1'].includes(location.hostname)&&<p className="notice warning">다른 기기에 전달할 QR은 그 기기에서도 접속 가능한 동일한 HTTPS 주소에서 발급해 주세요. localhost는 다른 기기에서 열리지 않아요.</p>}
    </>:<><h2>감독관 인증 QR 읽기</h2><p>QR 확인 후 인증 버튼을 누르면 서버에서 유효성을 확인해요.</p><QrScanner onCode={scanned}/>
      <details className="spaced"><summary>전달받은 인증 링크 입력</summary><label className="field">감독관 인증 링크<input type="password" autoComplete="off" spellCheck={false} value={pasted} maxLength={2048} onChange={event=>setPasted(event.target.value)}/></label><button className="btn secondary" onClick={()=>scanned(pasted)} disabled={!pasted.trim()}>인증 링크 확인</button></details>
      {token&&<div className="notice">감독관 QR을 읽었어요. <button className="btn primary" disabled={busy||access.loading} onClick={()=>execute(async()=>{await redeemSupervisorInvitation(token);setToken('');go('/supervisor');})}>{busy?'권한 확인 중…':'감독관 인증 완료'}</button><button className="text-button" onClick={()=>setToken('')}>입력 취소</button></div>}
      <details className="spaced"><summary>최초 관리자·기존 6자리 코드로 인증</summary><form onSubmit={event=>{event.preventDefault();void execute(async()=>{await registerSupervisor(code);setCode('');go('/supervisor-access');});}}><label className="field">오늘의 감독관 암호코드<input type="password" inputMode="numeric" pattern="[0-9]{6}" autoComplete="one-time-code" value={code} maxLength={6} onChange={event=>setCode(event.target.value.replace(/\D/g,'').slice(0,6))}/></label><button className="btn secondary" disabled={busy||access.loading||code.length!==6}>기존 코드로 인증</button></form><p className="caption">최초 담당자는 기존 코드로 인증한 후 감독관용 QR을 발급할 수 있어요. 작업자에게 코드·QR을 공개하지 마세요.</p></details>
    </>}
    {(error||access.error)&&<p className="notice warning" role="alert">{error||access.error}</p>}{access.error&&<button className="btn secondary" onClick={()=>{void refreshWorkSession().catch(()=>{});}}>서버 연결 다시 확인</button>}
    <button className="text-button spaced" onClick={()=>go('/checklist')}>작업자 점검으로 돌아가기 →</button></section></>;
}
