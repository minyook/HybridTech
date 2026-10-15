import { useEffect, useRef, useState } from 'react';
import { prepareAI, releaseModel, supportsAI } from '../ai/runtime';
import { useAIStatus } from '../ai/useAIStatus';
import { CONTEXT_TOKEN_OPTIONS, DEFAULT_AI_SETTINGS, loadAISettings, normalizeAISettings, OUTPUT_TOKEN_OPTIONS, saveAISettings, type AISettings } from '../ai/preferences';
import { installedModel, removeModel } from '../ai/model';
import { downloadGemma, GEMMA4_E2B } from '../ai/catalog';
import { allChats, allChecklists, allWorkOrders, clearRecords } from '../storage/db';
import { getWorkOrders, refreshWorkSession } from '../workflow/access';
import { manifest, prepareAssets, preparedGroups, storageInfo } from '../offline/assets';
import { bytes, go, Icon, Notice, PageHead } from '../ui';
import type { Asset, AssetManifest, ModelRecord } from '../types';

const preparationDuration=(milliseconds:number)=>milliseconds<100?'0.1초 미만':`${(milliseconds/1000).toFixed(1)}초`;

export default function Settings({ notify, changed }: { notify: (message: string) => void; changed: () => Promise<void> }) {
  const [groups,setGroups] = useState({ knowledge:false,pdf:false,runtime:false });
  const [catalog,setCatalog] = useState<AssetManifest>(); const [info,setInfo] = useState({usage:0,quota:0,persisted:false});
  const [model,setModel] = useState<ModelRecord>(); const ai=useAIStatus();
  const [tokens,setTokens]=useState<AISettings>(DEFAULT_AI_SETTINGS);const [savedTokens,setSavedTokens]=useState<AISettings>();
  const [elapsed,setElapsed]=useState(0);
  const [busy,setBusy] = useState(''); const [status,setStatus] = useState(''); const [failure,setFailure] = useState('');
  const controller = useRef<AbortController | undefined>(undefined);
  async function refresh() { const [g,c,i,m] = await Promise.all([preparedGroups(),manifest(),storageInfo(),installedModel()]); setGroups(g); setCatalog(c); setInfo(i); setModel(m?.metadata); }
  useEffect(() => { refresh().catch(error => setFailure(error.message));loadAISettings().then(value=>{setTokens(value);setSavedTokens(value);}).catch(()=>setFailure('AI 토큰 설정을 읽지 못했어요.')); return () => controller.current?.abort(); },[]);
  useEffect(()=>{const update=()=>setElapsed(ai.startedAt?Math.floor((Date.now()-ai.startedAt)/1000):0);update();if(!ai.startedAt)return;const timer=setInterval(update,1000);return()=>clearInterval(timer);},[ai.startedAt]);
  async function task(name: string, action: (signal: AbortSignal) => Promise<void>) {
    if (busy) return;
    controller.current = new AbortController(); setBusy(name); setFailure(''); setStatus('준비 중');
    try { await action(controller.current.signal); setStatus('완료했어요'); await refresh(); await changed(); }
    catch (error) { setFailure(controller.current.signal.aborted ? name === 'model' ? '모델 다운로드를 중단했어요. 다시 누르면 처음부터 다운로드해요. 준비된 실행 파일은 재사용해요.' : '작업을 중단했어요. 완료된 파일은 보관하고 다음 준비에서 이어받아요.' : error instanceof Error ? error.message : '처리하지 못했어요. 다시 시도해 주세요.'); await refresh().catch(()=>{}); }
    finally { setBusy(''); }
  }
  const prepare = (group: Asset['group'][]) => task('assets',async signal => {await prepareAssets(group,setStatus,signal);if(group.includes('runtime'))void prepareAI().catch(()=>{});});
  const groupSize = (group: string) => bytes(catalog?.assets.filter(asset => asset.group===group).reduce((sum,asset) => sum+asset.size,0) ?? 0);
  async function download(signal: AbortSignal) {
    await releaseModel();
    await prepareAssets(['runtime'],setStatus,signal);
    await downloadGemma(signal,setStatus);
    void prepareAI().catch(()=>{});
  }
  async function exportData() {
    try {
      const serverRecords = await refreshWorkSession().then(()=>getWorkOrders('worker')).then(workOrders=>({status:'available',workOrders})).catch(()=>({status:'unavailable',workOrders:[]}));
      const data = { version:3,exportedAt:new Date().toISOString(),chats:await allChats(),checklists:await allChecklists(),legacyWorkOrders:await allWorkOrders(),serverRecords };
      const link = document.createElement('a'); const objectUrl = URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));
      link.href = objectUrl; link.download = `fieldguard-records-${new Date().toISOString().slice(0,10)}.json`; link.click(); setTimeout(() => URL.revokeObjectURL(objectUrl),10000);
    } catch { notify('기록을 내보내지 못했어요.'); }
  }
  return <>
    <PageHead eyebrow="YOUR WORKSPACE" title="현장에 가기 전, 준비해요" description="문서와 AI를 이 기기에 저장하면 인터넷 없이 사용할 수 있어요."/>
    {!isSecureContext && <Notice kind="warning">PWA 설치와 기기 저장에는 HTTPS가 필요해요. PC 개발 환경에서는 localhost를 사용할 수 있어요.</Notice>}
    <div className="settings-grid"><section className="card"><div className="card-title"><div className="tile-icon"><Icon name="download"/></div><div><h2>오프라인 준비</h2><p>파일 크기와 SHA-256 검증 후 저장해요</p></div></div>
      {([['knowledge','문서 검색','87개 페이지의 검색 데이터'],['pdf','PDF 원본','문서 2종과 PDF 표시 파일'],['runtime','AI 실행 파일',`LiteRT-LM ${catalog?.sdkVersion ?? '…'} · Safari 호환 빌드 포함`]] as const).map(([id,label,description]) => <div className="setting-row" key={id}><div><strong>{label}</strong><p>{description} · {groupSize(id)}</p></div><button className={`btn small ${groups[id] ? 'secondary' : 'primary'}`} disabled={Boolean(busy)} onClick={() => prepare([id])}>{groups[id] ? '다시 확인' : '준비하기'}</button></div>)}
      <div className="ready-list">{Object.entries(groups).map(([key,value]) => <span key={key} className={`badge ${value ? 'green' : ''}`}>{value ? '✓' : '○'} {key==='knowledge'?'검색':key==='pdf'?'PDF':'AI 파일'} {value?'준비됨':'미준비'}</span>)}</div>
      <button className="btn primary full" disabled={Boolean(busy)} onClick={() => prepare(['knowledge','pdf','runtime'])}>전체 오프라인 파일 준비</button>
    </section><section className="card"><div className="card-title"><div className="tile-icon"><Icon name="cpu"/></div><div><h2>기기 내 Gemma</h2><p>질문과 답변은 기기 안에서 처리해요</p></div></div>
      <div className="button-row">{model&&<span className="badge green">모델 파일 저장됨</span>}<span className={`badge ${ai.phase==='ready'?'green':model?'blue':''}`}>{ai.phase==='ready'?'AI 사용 가능':ai.phase==='generating'?'AI 답변 작성 중':ai.phase==='checking'?'모델 저장 확인 중':ai.phase==='loading'?'실행 준비 중':model?'AI 실행 대기':'모델 다운로드 필요'}</span></div>
      <div className={`ai-preparation ${ai.phase==='error'?'failed':''}`} role={ai.phase==='error'?'alert':'status'}>{['checking','loading'].includes(ai.phase)&&<span className="spinner"/>}<div><strong>{ai.message}</strong>{['checking','loading'].includes(ai.phase)&&<p>{elapsed}초 경과 · 화면을 이동해도 준비를 계속해요.</p>}</div></div>
      {ai.timing&&<p className="caption">이번 AI 준비 {preparationDuration(ai.timing.totalMs)} · 모델 확인 {preparationDuration(ai.timing.verificationMs)}{ai.timing.verification==='cached'?' · 이전 검증 재사용':''}</p>}
      <p className="setting-description">모델 파일은 한 번 다운로드하면 이 기기에 저장돼요. 다시 열 때는 이전 검증 결과로 파일의 변경 여부를 빠르게 확인하고 실행 메모리에 불러와요. 파일이 바뀌거나 검증 후 7일이 지나면 전체를 다시 검사해요. 앱 안에서 화면만 이동할 때는 준비된 AI를 계속 사용해요.</p>
      {model && <div className="model-detail"><strong>{model.name}</strong><p>{bytes(model.size)} · SHA-256 검증됨 · {model.backend.toUpperCase()}</p><button className="btn danger small" disabled={Boolean(busy)||ai.phase==='generating'} onClick={() => { if (window.confirm('저장된 AI 모델을 삭제할까요? 문서와 기록은 유지돼요.')) void task('delete',async () => { await releaseModel(); await removeModel(); }); }}>모델 삭제</button></div>}
      <div className="model-download-card"><div><Icon name="cpu"/><strong>Gemma 4 E2B</strong><span className="badge blue">웹용 · 2.01 GB</span></div><p>한 번 다운로드하면 바로 AI 준비가 시작돼요. 별도의 실행 버튼을 누를 필요가 없어요.</p><button className="btn primary full" disabled={Boolean(busy)||ai.phase==='generating'||!supportsAI()||model?.sha256===GEMMA4_E2B.sha256} onClick={() => task('model',download)}><Icon name="download"/>{model?.sha256===GEMMA4_E2B.sha256?'Gemma 4 E2B 다운로드 완료':busy==='model'?'Gemma 4 E2B 다운로드 중':'Gemma 4 E2B 다운로드 받기'}</button>{busy==='model'&&<div className="download-progress" role="status"><p>{status}</p><progress aria-label="Gemma 다운로드 진행률" max={100} {...(/(\d+)%/.test(status)?{value:Number(status.match(/(\d+)%/)?.[1])}:{})}/><button className="text-button" onClick={() => controller.current?.abort()}>다운로드 중단</button></div>}<p className="caption">최초 다운로드와 AI 준비에는 시간이 걸릴 수 있어요. ‘AI 사용 가능’으로 바뀌면 바로 질문할 수 있어요. <a href={GEMMA4_E2B.source} target="_blank" rel="noreferrer">모델 출처 ↗</a></p></div>
    </section></div>
    {(busy||status||failure) && <div className={`task-status ${failure?'failed':''}`} role={failure?'alert':'status'}><div>{busy && <span className="spinner"/>}{failure||status}</div>{busy && <button className="btn secondary small" onClick={() => controller.current?.abort()}>중단</button>}</div>}
    <section className="card token-settings"><div className="card-title"><div className="tile-icon"><Icon name="settings"/></div><div><h2>AI 토큰 설정</h2><p>답변 길이와 AI가 사용할 문맥 크기를 조절해요</p></div></div><div className="token-fields"><label className="field">최대 답변 토큰<select value={tokens.maxOutputTokens} disabled={!savedTokens||Boolean(busy)||ai.phase==='generating'} onChange={event=>setTokens(normalizeAISettings({...tokens,maxOutputTokens:Number(event.target.value)}))}>{OUTPUT_TOKEN_OPTIONS.filter(value=>value<=tokens.contextTokens/2).map(value=><option key={value} value={value}>{value.toLocaleString()} 토큰{value===1536?' · 기본값':''}</option>)}</select><small>한 번의 답변에서 생성할 수 있는 최대 길이예요.</small></label><label className="field">문맥 토큰 한도<select value={tokens.contextTokens} disabled={!savedTokens||Boolean(busy)||ai.phase==='generating'} onChange={event=>setTokens(normalizeAISettings({...tokens,contextTokens:Number(event.target.value)}))}>{CONTEXT_TOKEN_OPTIONS.map(value=><option key={value} value={value}>{value.toLocaleString()} 토큰{value===8192?' · 기본값':''}</option>)}</select><small>질문·문서 근거·답변이 사용하는 공간이에요. 값이 클수록 메모리를 더 사용해요.</small></label></div><div className="button-row"><button className="btn primary" disabled={!savedTokens||Boolean(busy)||ai.phase==='generating'||JSON.stringify(tokens)===JSON.stringify(savedTokens)} onClick={()=>task('tokens',async()=>{const saved=await saveAISettings(tokens);setTokens(saved);setSavedTokens(saved);notify('토큰 설정을 저장했어요. 다음 질문부터 자동으로 적용해요.');})}>토큰 설정 저장</button><span className="caption">저장한 설정은 다음 질문부터 적용돼요. 문맥 크기를 바꾸면 AI를 다시 준비할 수 있어요.</span></div><p className="caption">토큰은 AI가 글을 처리하는 단위이며 글자 수와 달라요. 기기 내 실행이므로 토큰별 API 요금은 없어요.</p></section>
    <div className="settings-grid"><section className="card"><h2>이 기기의 저장소</h2><p>{bytes(info.usage)} 사용 / {info.quota?bytes(info.quota):'용량 확인 불가'}</p><div className="storage-bar"><span style={{width:`${info.quota?Math.min(100,info.usage/info.quota*100):0}%`}}/></div><p className="caption">{info.persisted?'영구 저장 권한이 허용되어 있어요.':'브라우저가 저장 데이터를 정리할 수 있어요.'} 사이트 데이터를 직접 지우면 문서와 모델을 다시 준비해야 해요.</p><button className="btn secondary" disabled={Boolean(busy)} onClick={() => task('persist',async () => { const allowed = await navigator.storage?.persist?.(); notify(allowed?'영구 저장 권한을 받았어요.':'브라우저가 영구 저장을 허용하지 않았어요.'); })}>영구 저장 요청</button></section>
    <section className="card"><h2>내 기록</h2><p>대화와 이전 로컬 점검은 이 브라우저에 저장해요. 새 작업 요청·승인 기록은 서버에 보관해요.</p><div className="button-row spaced"><button className="btn secondary" disabled={Boolean(busy)} onClick={exportData}><Icon name="download"/>기록 내보내기</button><button className="btn danger" disabled={Boolean(busy)} onClick={() => { if (window.confirm('이 브라우저의 대화와 이전 점검 기록을 삭제할까요? 서버의 작업·승인 기록은 유지돼요.')) void task('clear',clearRecords); }}><Icon name="trash"/>기록 삭제</button></div></section></div>
    <section className="card supervisor-setting"><div><h2>감독관 인증</h2><p>오늘 전달받은 6자리 암호코드로 감독관 화면을 열어요.</p></div><button className="btn secondary" onClick={() => go('/supervisor-access')}>감독관 화면으로 전환</button></section>
    <section className="card install-guide" id="install"><div><span className="eyebrow">iPHONE · iPAD</span><h2>홈 화면에서 앱처럼 열어요</h2><p>Safari에서 HTTPS 주소를 열고 아래 순서로 추가해 주세요.</p></div><ol><li><span>1</span>Safari의 <strong>공유</strong> 버튼</li><li><span>2</span><strong>홈 화면에 추가</strong> 선택</li><li><span>3</span>이름 확인 후 <strong>추가</strong></li></ol><p className="caption">홈 화면에서 다시 연 뒤 오프라인 파일을 준비해 주세요. AI는 지원 기기에서 별도 모델 준비와 실행 확인이 필요해요.</p></section>
    <Notice>BLE 측정, 가스 경보, Android 위젯은 기존 Android 앱에서 사용해 주세요. 웹에서는 문서 검색과 근거 확인을 제공해요.</Notice>
  </>;
}
