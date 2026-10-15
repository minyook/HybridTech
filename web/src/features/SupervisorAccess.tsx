import { useState } from 'react';
import { lockSupervisor, refreshWorkSession, registerSupervisor, useWorkSession } from '../workflow/access';
import { formatDate, go, Icon, PageHead } from '../ui';

export default function SupervisorAccess() {
  const access = useWorkSession(); const [code, setCode] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const grant = access.session?.supervisor;
  async function register() {
    if (busy || !code.trim()) return;
    setBusy(true); setError('');
    try { await registerSupervisor(code); setCode(''); go('/supervisor'); }
    catch (error) { setError(error instanceof Error ? error.message : '감독관 인증을 완료하지 못했어요.'); }
    finally { setBusy(false); }
  }
  return <><PageHead eyebrow="SUPERVISOR ACCESS" title="감독관 인증" description="발급받은 개인용 등록 코드로 감독관 권한을 확인해요."/>
    <section className="card supervisor-access"><div className="tile-icon"><Icon name="shield"/></div>
      {grant ? <><h2>{grant.name} 감독관</h2><p>인증 유효기한 {formatDate(grant.expiresAt)}</p><div className="button-row spaced"><button className="btn primary" onClick={() => go('/supervisor')}>감독관 점검 열기</button><button className="btn secondary" disabled={busy} onClick={async () => { setBusy(true); try { await lockSupervisor(); go('/checklist'); } catch (error) { setError(error instanceof Error ? error.message : '잠금을 완료하지 못했어요.'); } finally { setBusy(false); } }}>감독관 화면 잠그기</button></div></> : <>
        <h2>감독관 등록 코드 입력</h2><p>작업자는 작업 요청과 본인의 투입·퇴장 기록을 이용해요. 점검·승인은 인증된 감독관에게만 열려요.</p>
        <form onSubmit={event => { event.preventDefault(); void register(); }}><label className="field">감독관 등록 코드<input type="password" autoComplete="off" autoCapitalize="none" spellCheck={false} value={code} maxLength={200} disabled={busy || access.loading} onChange={event => setCode(event.target.value)} placeholder="전달받은 일회용 코드"/></label><button className="btn primary full" disabled={busy || access.loading || !code.trim()}>{busy ? '권한 확인 중…' : '감독관 인증하기'}</button></form><p className="caption">등록 코드는 서버 관리자에게 받아 주세요. 코드는 한 번만 사용할 수 있고 인증은 8시간 동안 유지돼요.</p>
      </>}
      {(error || access.error) && <p className="notice warning" role="alert">{error || access.error}</p>}
      {access.error && <button className="btn secondary" onClick={() => { void refreshWorkSession().catch(() => {}); }}>서버 연결 다시 확인</button>}
      <button className="text-button spaced" onClick={() => go('/checklist')}>작업자 점검으로 돌아가기 →</button>
    </section></>;
}
