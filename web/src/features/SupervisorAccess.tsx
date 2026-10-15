import { useState } from 'react';
import { lockSupervisor, refreshWorkSession, registerSupervisor, useWorkSession } from '../workflow/access';
import { formatDate, go, Icon, PageHead } from '../ui';

export default function SupervisorAccess() {
  const access = useWorkSession(); const [code, setCode] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const grant = access.session?.supervisor;
  async function register() {
    if (busy || !/^\d{6}$/.test(code)) return;
    setBusy(true); setError('');
    try { await registerSupervisor(code); setCode(''); go('/supervisor'); }
    catch (error) { setError(error instanceof Error ? error.message : '감독관 인증을 완료하지 못했어요.'); }
    finally { setBusy(false); }
  }
  return <><PageHead eyebrow="SUPERVISOR ACCESS" title="감독관 화면으로 전환" description="오늘 전달받은 6자리 암호코드를 입력해 주세요."/>
    <section className="card supervisor-access"><div className="tile-icon"><Icon name="shield"/></div>
      {grant ? <><h2>{grant.name}</h2><p>인증 유효기한 {formatDate(grant.expiresAt)}</p><div className="button-row spaced"><button className="btn primary" onClick={() => go('/supervisor')}>감독관 점검 열기</button><button className="btn secondary" disabled={busy} onClick={async () => { setBusy(true); try { await lockSupervisor(); go('/checklist'); } catch (error) { setError(error instanceof Error ? error.message : '잠금을 완료하지 못했어요.'); } finally { setBusy(false); } }}>감독관 화면 잠그기</button></div></> : <>
        <h2>오늘의 암호코드 6자리</h2><p>감독관에게 전달된 오늘의 코드를 입력하면 점검·승인 화면이 열려요.</p>
        <form onSubmit={event => { event.preventDefault(); void register(); }}><label className="field">감독관 암호코드<input className="daily-code-input" type="password" inputMode="numeric" pattern="[0-9]{6}" autoComplete="one-time-code" spellCheck={false} value={code} maxLength={6} disabled={busy || access.loading} onChange={event => setCode(event.target.value.replace(/\D/g,'').slice(0,6))} placeholder="6자리 숫자"/></label><button className="btn primary full" disabled={busy || access.loading || code.length!==6}>{busy ? '권한 확인 중…' : '감독관 화면으로 전환'}</button></form><p className="caption">오늘 코드는 한국 시간 자정까지 사용할 수 있어요. 날짜가 바뀌면 새 코드를 입력해 주세요.</p>
      </>}
      {(error || access.error) && <p className="notice warning" role="alert">{error || access.error}</p>}
      {access.error && <button className="btn secondary" onClick={() => { void refreshWorkSession().catch(() => {}); }}>서버 연결 다시 확인</button>}
      <button className="text-button spaced" onClick={() => go('/checklist')}>작업자 점검으로 돌아가기 →</button>
    </section></>;
}
