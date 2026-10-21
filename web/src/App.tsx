import { lazy, Suspense, useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react';
import type { Chat, Checklist, Chunk, Source } from './types';
import { aiIsReady, generate, getAIStatus, prepareAI, supportsAI } from './ai/runtime';
import { useAIStatus } from './ai/useAIStatus';
import { useChatScroll } from './chat/useChatScroll';
import { useChatViewport } from './chat/useChatViewport';
import { useWorkSession } from './workflow/access';
import { isAndroidEmbedded } from './workflow/native';
import { allChats, allChecklists, deleteChat, getSetting, saveChat, setSetting } from './storage/db';
import { contradictsSources, missingAspects, rank, retrieve, sourceAnswer } from './rag/search';
import { createAnswerPlan } from './rag/answer-plan';
import { usesLightAI } from './ai/light-model';
import { loadKnowledge, preparedGroups } from './offline/assets';
import { formatDate, go, Icon, Notice, PageHead, TextContent } from './ui';

const Settings = lazy(() => import('./features/Settings'));
const PdfPage = lazy(() => import('./features/PdfPage'));
const WorkChecklist = lazy(() => import('./features/WorkChecklist'));
const SupervisorAccess = lazy(() => import('./features/SupervisorAccess'));
const navigation = [ ['/', 'home', '홈'], ['/ask','chat','문서 질문'], ['/library','book','문서 보관함'], ['/checklist','check','작업 전 점검'], ['/history','history','대화 기록'], ['/settings','settings','설정'] ];
const quickQuestions = ['산소 및 유해가스 측정방법 알려줘', '밀폐공간 작업허가 절차 알려줘', '심폐소생술과 산소측정법 알려줘'];

function useRoute() {
  const [route,setRoute] = useState(location.hash.slice(1) || '/');
  useEffect(() => { const update = () => { setRoute(location.hash.slice(1)||'/'); window.scrollTo(0,0); }; window.addEventListener('hashchange',update); return () => window.removeEventListener('hashchange',update); },[]);
  return route;
}
export default function App() {
  const ai=useAIStatus();
  const workAccess=useWorkSession();
  const route = useRoute(); const section = '/'+(route.split('/')[1] ?? '');
  const [chunks,setChunks] = useState<Chunk[]>([]); const [loading,setLoading] = useState(true); const [loadError,setLoadError] = useState('');
  const [chats,setChats] = useState<Chat[]>([]); const [checks,setChecks] = useState<Checklist[]>([]); const [savedIds,setSavedIds] = useState<Set<string>>(new Set());
  const [activeIds,setActiveIds] = useState<string[]>([]); const [question,setQuestion] = useState(''); const [search,setSearch] = useState(''); const [filter,setFilter] = useState('all');
  const [busy,setBusy] = useState(false); const [stream,setStream] = useState(''); const [current,setCurrent] = useState<Chat>();
  const [online,setOnline] = useState(navigator.onLine); const [notice,setNotice] = useState(''); const [menu,setMenu] = useState(false);
  const [offline,setOffline] = useState({knowledge:false,pdf:false,runtime:false}); const [shellReady,setShellReady] = useState(Boolean(navigator.serviceWorker?.controller));
  const [draft,setDraft] = useState({checked:[] as number[],site:'',note:''}); const [draftLoaded,setDraftLoaded] = useState(false);
  const controller = useRef<AbortController | undefined>(undefined); const notifyTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const {messagesRef,composerRef,showLatest,scrollToLatest}=useChatScroll(section==='/ask'&&!loading,current?.id);
  const chatViewport=useChatViewport(section==='/ask');
  useLayoutEffect(()=>{
    const input=composerRef.current?.querySelector('textarea');if(!input)return;
    const style=getComputedStyle(input),lineHeight=parseFloat(style.lineHeight),padding=parseFloat(style.paddingTop)+parseFloat(style.paddingBottom);
    input.style.height='0px';
    input.style.height=`${Math.min(130,lineHeight*4+padding,Math.max(lineHeight*(chatViewport.keyboardOpen?1:2)+padding,input.scrollHeight))}px`;
  },[question,section,loading,chatViewport.keyboardOpen,composerRef]);
  const notify = (message: string) => { setNotice(message); clearTimeout(notifyTimer.current); notifyTimer.current = setTimeout(() => setNotice(''),6000); };
  async function refresh() {
    const results = await Promise.allSettled([allChats(),allChecklists(),preparedGroups(),getSetting<typeof draft>('checklist-draft')]);
    if (results[0].status==='fulfilled') { setChats(results[0].value); setSavedIds(new Set(results[0].value.map(chat => chat.id))); } else notify('대화 기록을 읽지 못했어요. 브라우저 저장 권한을 확인해 주세요.');
    if (results[1].status==='fulfilled') setChecks(results[1].value);
    if (results[2].status==='fulfilled') setOffline(results[2].value);
    if (results[3].status==='fulfilled') setDraft(results[3].value ?? {checked:[],site:'',note:''});
  }
  useEffect(() => {
    let active = true;
    if(!isAndroidEmbedded()){
      const initialRoute=location.hash.slice(1)||'/';
      void prepareAI().then(ready=>{
        // A home-screen PWA has no address bar for the standalone recovery URL.
        // On startup, open its own settings without touching another browser's
        // storage. Later document browsing remains available in recovery mode.
        if(active&&!ready&&getAIStatus().phase==='recovery'&&['/','/ask'].includes(initialRoute)&&(location.hash.slice(1)||'/')===initialRoute)go('/settings');
      }).catch(()=>{});
    }
    loadKnowledge().then(value => { if (active) setChunks(value); }).catch(error => { if (active) setLoadError(error.message); }).finally(() => { if (active) setLoading(false); });
    void refresh();
    getSetting<typeof draft>('checklist-draft').then(value => { if (active && value) setDraft(value); }).catch(() => notify('점검 초안을 복원하지 못했어요.')).finally(() => { if (active) setDraftLoaded(true); });
    const connected = () => setOnline(navigator.onLine); const worker = () => setShellReady(Boolean(navigator.serviceWorker.controller));
    window.addEventListener('online',connected); window.addEventListener('offline',connected); navigator.serviceWorker?.addEventListener('controllerchange',worker);
    return () => { active = false; controller.current?.abort(); clearTimeout(notifyTimer.current); window.removeEventListener('online',connected); window.removeEventListener('offline',connected); navigator.serviceWorker?.removeEventListener('controllerchange',worker); };
  },[]);
  useEffect(() => { setMenu(false); if (route.startsWith('/ask/')) setActiveIds([decodeURIComponent(route.slice(5))]); document.getElementById('main')?.focus({preventScroll:true}); },[route]);
  useEffect(() => {
    if (!draftLoaded) return;
    const timer = setTimeout(() => { setSetting('checklist-draft',draft).catch(() => notify('점검 초안을 저장하지 못했어요.')); },300);
    return () => clearTimeout(timer);
  },[draft,draftLoaded]);
  const newChat = () => { if (busy) return; setActiveIds([]); setQuestion(''); go('/ask'); };
  async function ask(value = question) {
    const trimmed = value.trim(); if (!trimmed || busy || !chunks.length) return;
    setQuestion(''); setBusy(true); setStream(''); go('/ask');
    const sources = retrieve(trimmed,chunks); const missing = missingAspects(trimmed,chunks);
    const chat: Chat = { id:crypto.randomUUID(),question:trimmed,answer:'',citationIds:sources.map(source => source.chunk.id),mode:'source',createdAt:new Date().toISOString() };
    const detailed=usesLightAI();if(detailed)chat.answerFormat='integrated';
    const plan=detailed?createAnswerPlan(trimmed,sources):undefined;
    if(plan?.evidence.some(unit=>unit.requiresFigure))chat.notice='도식의 위치 기호(X·①~③)는 아래 출처의 원문 그림과 함께 확인해 주세요.';
    const documentAnswer=()=>plan?.body??sourceAnswer(sources);
    if(plan){chat.answer=plan.body;setStream(plan.body);}
    controller.current = new AbortController(); const signal = controller.current.signal;
    setCurrent(chat); setActiveIds(previous => [...previous,chat.id]);
    try {
      if (supportsAI() && sources.length && !missing.length) {
        try {
          const result = await generate(trimmed,sources,setStream,signal,plan);
          if (contradictsSources(trimmed,result.answer,sources)) throw new Error('생성된 답변이 선택한 근거와 맞지 않아 원문을 표시해요.');
          chat.answer = result.answer; chat.mode = result.generated===false?'source':'ai';chat.tokenUsage=result.usage;
          if(result.supplementSkipped==='recovery')chat.notice='AI 복구가 필요해 문서 근거로 답변했어요. 설정에서 상태를 확인할 수 있어요.';
          if(result.truncated||(!detailed&&result.usage.outputTokens!==undefined&&result.usage.outputTokens>=result.usage.maxOutputTokens))chat.notice='설정한 답변 토큰 한도에 도달했어요. 내용이 더 필요하면 질문을 나누어 주세요.';
        } catch (error) {
          chat.answer = documentAnswer(); chat.mode = signal.aborted?'cancelled':'source';
          chat.notice = signal.aborted ? '답변 생성을 중단했어요. 검색된 원문은 확인할 수 있어요.' : `${error instanceof Error?error.message:'AI 답변 생성에 실패했어요.'} 검색 원문을 표시해요.`;
        }
      } else {
        chat.answer = documentAnswer();
        if (sources.length) chat.notice = missing.length ? `근거를 찾지 못한 주제: ${missing.join(', ')}. 확인된 문서 원문만 표시해요.` : 'AI가 실행되지 않아 검색된 문서 원문을 표시해요.';
      }
      setChats(previous => [chat,...previous]);
      try { await saveChat(chat); setSavedIds(previous => new Set(previous).add(chat.id)); }
      catch { notify('답변을 기기에 저장하지 못했어요. 화면의 내용을 복사해 보관해 주세요.'); }
    } finally { setCurrent(undefined); setStream(''); setBusy(false); controller.current = undefined; }
  }
  const sourceLink = (source: Chunk) => go(`/sources/${source.documentId}/${source.pageNumber}`);
  const renderSources = (sources: Source[]) => <div className="citations">{sources.map(({chunk},index) => <button key={chunk.id} onClick={() => sourceLink(chunk)}><span className="citation-number">{index+1}</span><span>{chunk.sourceKind}<strong>{chunk.pageNumber}쪽 원문 보기</strong></span><Icon name="chevron"/></button>)}</div>;
  const documents = [...new Set(chunks.map(chunk => chunk.documentId))].map(id => ({id,title:chunks.find(chunk => chunk.documentId===id)!.documentTitle,pages:chunks.filter(chunk => chunk.documentId===id).length}));
  const selectedChats = activeIds.map(id => chats.find(chat => chat.id===id)).filter((chat): chat is Chat => Boolean(chat));
  const exchanges=current&&!selectedChats.some(chat=>chat.id===current.id)?[...selectedChats,current]:selectedChats;
  const searched = search.trim() ? rank(search,chunks).filter(source => filter==='all'||source.chunk.documentId===filter) : [];
  function home() { return <>
    <div className="home-greeting"><span>HYBRIDTECH · FIELD WORKSPACE</span><span className="date">{new Intl.DateTimeFormat('ko-KR',{month:'long',day:'numeric',weekday:'long'}).format(new Date())}</span></div>
    <section className="hero"><div><p className="eyebrow">현장의 질문에, 문서로 답해요</p><h1>안전한 작업의 시작,<br/>근거부터 확인하세요.</h1><p>밀폐공간 안전 문서를 내 기기에서 바로 검색하고<br className="desktop-break"/> 필요한 절차와 기준을 원문으로 확인해요.</p><button className="btn primary" onClick={newChat}>문서에 질문하기 <Icon name="arrow"/></button></div><div className="hero-art" aria-hidden="true"><div className="orbit"/><div className="shield-mark"><Icon name="shield"/></div><span className="art-tag"><span className="dot"/>ON-DEVICE KNOWLEDGE</span><i/></div></section>
    <div className="readiness"><div><span className={`status-dot ${chunks.length?'good':''}`}/><span>문서 검색 <strong>{chunks.length?'사용 가능':'준비 중'}</strong></span></div><div><Icon name="book"/><span>PDF 원본 <strong>{offline.pdf?'오프라인 준비됨':'준비 필요'}</strong></span></div><button onClick={() => go('/settings')}><Icon name="cpu"/><span>기기 내 AI <strong>{aiIsReady()?'실행 중':ai.phase==='checking'?'모델 확인 중':ai.phase==='loading'?'불러오는 중':'준비하기'}</strong></span><Icon name="chevron"/></button></div>
    <div className="section-heading"><h2>오늘의 현장 준비</h2><span>한 번 더 확인해요</span></div>
    <div className="quick-grid"><button className="quick-card" onClick={() => go('/checklist')}><span className="tile-icon"><Icon name="check"/></span><strong>작업 전 점검</strong><p>요청·감독관 점검·승인부터<br/>투입과 퇴장까지 확인해요</p><span className="quick-bottom">작업자 요청 · 감독관 승인<Icon name="arrow"/></span></button><button className="quick-card" onClick={() => go('/library')}><span className="tile-icon mint"><Icon name="book"/></span><strong>문서 보관함</strong><p>안전작업 가이드와 점검표를<br/>원본 그대로 확인해요</p><span className="quick-bottom">문서 {documents.length}종 · {chunks.length}개 근거<Icon name="arrow"/></span></button><button className="quick-card" onClick={() => go('/settings')}><span className="tile-icon violet"><Icon name="download"/></span><strong>오프라인 준비</strong><p>통신이 어려운 현장에서도<br/>필요한 문서를 열어요</p><span className="quick-bottom">{offline.pdf&&shellReady?'문서·PDF 준비됨':'현장에 가기 전 준비하기'}<Icon name="arrow"/></span></button></div>
    <div className="home-lower"><section className="card"><div className="section-heading compact"><h2>이렇게 질문해 보세요</h2><Icon name="chat"/></div><div className="question-list">{quickQuestions.map((text,index) => <button disabled={!chunks.length||busy} key={text} onClick={() => ask(text)}><span className="number">0{index+1}</span><span>{text}</span><Icon name="chevron"/></button>)}</div></section><section className="card android-card"><span className="badge">ANDROID APP</span><h2>가스 측정은<br/>기존 Android 앱에서</h2><p>측정기 연결과 현장 경보는<br/>설치형 Android 앱에서 사용해 주세요.</p><div className="android-note"><Icon name="info"/>웹에서는 실제 가스값을 측정하지 않아요.</div></section></div>
    {chats.length>0 && <><div className="section-heading"><h2>최근 확인한 질문</h2><button className="text-button" onClick={() => go('/history')}>전체 보기 →</button></div><div className="card rows">{chats.slice(0,3).map(chat => <button className="list-row" key={chat.id} onClick={() => go(`/ask/${chat.id}`)}><Icon name="chat"/><div><strong>{chat.question}</strong><p>{formatDate(chat.createdAt)} · {chat.mode==='ai'?'AI 답변':'문서 원문'}</p></div><Icon name="chevron"/></button>)}</div></>}
    <footer className="page-footer"><Icon name="shield"/>질문과 답변은 이 기기에 보관해요.<span>HybridTech O₂ Field Guard</span></footer>
  </>; }
  function askPage() { return <div className="ask-layout"><div className="chat-scroll" role="region" aria-label="대화 내용" tabIndex={0}><PageHead eyebrow="LOCAL DOCUMENT ASSISTANT" title="현장에서 궁금한 점을 물어보세요" description="문서에서 근거를 찾고, 같은 쪽의 원문까지 연결해요." action={<button className="btn secondary" disabled={busy} onClick={newChat}><Icon name="plus"/>새 대화</button>}/><div className="ask-status"><span className="badge green"><span className="dot"/>문서 {chunks.length}개 쪽</span><span className="badge">{aiIsReady()?'AI 기기 내 실행':ai.phase==='checking'?'모델 저장 확인 중':ai.phase==='loading'?'저장된 모델 불러오는 중':'문서 원문 검색 모드'}</span><button className="text-button" onClick={() => go('/settings')}>AI 설정 →</button></div>
    {!selectedChats.length&&!current && <div className="chat-empty"><div className="empty-icon"><Icon name="chat"/></div><h2>질문은 편하게,<br/>근거는 정확하게.</h2><p className="chat-input-hint">궁금한 내용을 입력해 주세요.</p><p>작업 절차, 측정방법, 점검 항목을 물어보세요.<br/>검색된 내용의 문서명과 쪽수를 함께 보여드려요.</p><div className="prompt-chips">{quickQuestions.map(text => <button key={text} disabled={!chunks.length} onClick={() => ask(text)}>{text}<Icon name="arrow"/></button>)}</div></div>}
    <div className="messages" ref={messagesRef}>{exchanges.map(chat=>{
      const writing=chat.id===current?.id;
      return <article className="exchange" key={chat.id}><div className="user-message">{chat.question}</div><div className="answer" aria-busy={writing||undefined}>
        <div className="answer-label">{writing?<><span className="answer-progress"><span className="spinner"/></span><strong>{stream?(chat.answerFormat==='integrated'?'문서 본문을 표시했어요 · 필요한 보충 설명 확인 중':'AI가 답변을 작성하고 있어요'):['checking','loading','generating'].includes(ai.phase)?ai.message:'문서 근거를 확인하고 있어요'}</strong></>:<><div className="mini-brand">O₂</div><strong>{chat.answerFormat==='integrated'?(chat.mode==='ai'?'문서 근거 · AI 보충':'문서 근거 답변'):(chat.mode==='ai'?'AI 답변':'관련 문서 원문')}</strong><span>{savedIds.has(chat.id)?'기기 저장됨':'저장되지 않음'}</span></>}</div>
        {chat.notice&&<Notice>{chat.notice}</Notice>}<TextContent text={writing?stream:chat.answer}/>
        {!writing&&<>{chat.tokenUsage&&<p className="token-usage">{chat.tokenUsage.maxOutputTokens===0?'문서 근거 답변 · AI 생성 0토큰':<>{chat.includesEvidence&&'AI 정리 사용량(문서 발췌 제외) · '}{chat.tokenUsage.inputTokens!==undefined&&`입력 ${chat.tokenUsage.inputTokens.toLocaleString()} · `}{chat.tokenUsage.outputTokens!==undefined&&`생성 ${chat.tokenUsage.outputTokens.toLocaleString()} · `}{chat.tokenUsage.totalTokens!==undefined&&`전체 ${chat.tokenUsage.totalTokens.toLocaleString()} · `}{chat.tokenUsage.passes!==undefined?`순차 작성 ${chat.tokenUsage.passes}회 · 회당 한도 ${chat.tokenUsage.perPassMaxOutputTokens?.toLocaleString()} 토큰`:`답변 한도 ${chat.tokenUsage.maxOutputTokens.toLocaleString()} 토큰`}</>}</p>}{renderSources(chat.citationIds.flatMap(id=>{const chunk=chunks.find(chunk=>chunk.id===id);return chunk?[{chunk,score:0}]:[];}))}</>}
      </div></article>;
    })}</div>
    </div><form className="composer-wrap" ref={composerRef} onSubmit={event => {event.preventDefault(); void ask();}}>{showLatest&&<button type="button" className="jump-latest" onClick={scrollToLatest}><Icon name="arrow"/>최신 답변으로 이동</button>}<div className="composer"><textarea aria-label="문서에 질문하기" placeholder={chunks.length?'궁금한 절차나 기준을 입력해 주세요':'문서를 불러오고 있어요'} maxLength={1500} value={question} disabled={busy||!chunks.length} rows={chatViewport.keyboardOpen?1:2} onChange={event => setQuestion(event.target.value)} onKeyDown={event => {if(event.key==='Enter'&&!event.shiftKey&&!event.nativeEvent.isComposing){event.preventDefault(); void ask();}}}/>{busy?<button type="button" className="send-button stop" aria-label="답변 생성 중단" onClick={() => controller.current?.abort()}><span/></button>:<button className="send-button" type="submit" aria-label="질문 보내기" disabled={!question.trim()||!chunks.length}><Icon name="arrow"/></button>}</div><p>생성 답변의 수치와 조건은 원문에서 확인해 주세요. 실제 측정은 Android 앱을 사용해 주세요.</p></form></div>; }
  function library() { return <><PageHead eyebrow="DOCUMENT LIBRARY" title="필요한 근거를 바로 찾아요" description="Android 앱과 같은 문서 2종, 87개 페이지를 사용해요."/><div className="search-field"><Icon name="search"/><input type="search" aria-label="문서 내용 검색" placeholder="문서 안의 용어나 절차를 검색하세요" value={search} onChange={event => setSearch(event.target.value)}/>{search&&<button className="icon-button" aria-label="검색어 지우기" onClick={() => setSearch('')}><Icon name="close"/></button>}</div><div className="filter-tabs"><button className={filter==='all'?'active':''} onClick={() => setFilter('all')}>전체 문서</button>{documents.map(doc => <button key={doc.id} className={filter===doc.id?'active':''} onClick={() => setFilter(doc.id)}>{doc.id.includes('2022')?'안전작업 가이드':'자율점검표'}</button>)}</div>
    {search.trim()?<><div className="section-heading"><h2>검색 결과 <span className="blue-text">{searched.length}</span></h2><span>관련도 순</span></div>{searched.length?<div className="search-results">{searched.map(({chunk}) => <button className="card result-card" key={chunk.id} onClick={() => sourceLink(chunk)}><span className="badge blue">{chunk.sourceKind} · {chunk.pageNumber}쪽</span><h3>{chunk.documentTitle}</h3><p>{chunk.body.slice(0,230)}{chunk.body.length>230?'…':''}</p><span className="text-button">원문 확인 <Icon name="arrow"/></span></button>)}</div>:<div className="empty-state"><Icon name="search"/><h2>일치하는 근거가 없어요</h2><p>다른 용어로 검색하거나 문서를 직접 열어 주세요.</p></div>}</>:<div className="document-grid">{documents.filter(doc => filter==='all'||filter===doc.id).map((doc,index) => <article className="card document-card" key={doc.id}><div className={`document-cover ${doc.id.includes('2026')?'mint-cover':''}`}><span>MINISTRY OF EMPLOYMENT AND LABOR</span><Icon name={doc.id.includes('2026')?'check':'shield'}/><strong>{doc.id.includes('2026')?'밀폐공간\n자율점검표':'밀폐공간\n안전작업 가이드'}</strong><small>{doc.id.includes('2026')?'2026':'2022'} · PDF</small></div><span className="eyebrow">DOCUMENT 0{index+1}</span><h2>{doc.title}</h2><p>{doc.pages}쪽 · {offline.pdf?'오프라인 PDF 준비됨':'PDF 준비는 설정에서 할 수 있어요'}</p><button className="btn secondary full" onClick={() => go(`/sources/${doc.id}/1`)}>문서 열기 <Icon name="arrow"/></button></article>)}</div>}</>; }
  function historyPage() { return <><PageHead eyebrow="SAVED ON THIS DEVICE" title="이전에 확인한 내용을 다시 봐요" description="대화 기록은 현재 기기의 브라우저에 저장돼요." action={<button className="btn primary" disabled={busy} onClick={newChat}><Icon name="plus"/>새 대화</button>}/>{chats.length?<div className="card rows">{chats.map(chat => <div className="history-row" key={chat.id}><button className="list-row" onClick={() => go(`/ask/${chat.id}`)}><div className="tile-icon"><Icon name="chat"/></div><div><strong>{chat.question}</strong><p>{formatDate(chat.createdAt)} · {chat.mode==='ai'?'AI 답변':'문서 원문'} · 근거 {chat.citationIds.length}개</p></div><Icon name="chevron"/></button><button className="icon-button delete" aria-label={`${chat.question} 기록 삭제`} onClick={async () => {if(window.confirm('이 질문 기록을 삭제할까요?')){try{await deleteChat(chat.id);setChats(previous=>previous.filter(value=>value.id!==chat.id));}catch{notify('기록을 삭제하지 못했어요.');}}}}><Icon name="trash"/></button></div>)}</div>:<div className="empty-state"><Icon name="history"/><h2>아직 저장된 대화가 없어요</h2><p>문서에 질문하면 답변과 출처를 이곳에서 다시 볼 수 있어요.</p><button className="btn primary" onClick={newChat}>첫 질문하기</button></div>}</>; }
  function checklistPage() { return <WorkChecklist key="worker" notify={notify} legacyRecords={checks}/>; }
  const visibleNavigation = workAccess.session?.supervisor ? [...navigation, ['/supervisor','shield','감독관 점검']] : navigation;
  const selectedNav = visibleNavigation.find(([path]) => path===section);
  const pageTitle = section==='/supervisor-access'||section==='/supervisor'&&!workAccess.session?.supervisor?'감독관 인증':selectedNav?.[2]??'근거 문서';
  let content;
  if (section==='/') content=home(); else if(section==='/ask') content=askPage(); else if(section==='/library') content=library(); else if(section==='/history') content=historyPage(); else if(section==='/checklist') content=checklistPage(); else if(section==='/supervisor') content=<WorkChecklist key="supervisor" view="supervisor" notify={notify} legacyRecords={[]}/>; else if(section==='/supervisor-access') content=<SupervisorAccess/>; else if(section==='/settings') content=<Settings notify={notify} changed={refresh}/>; else if(section==='/sources') content=<PdfPage documentId={route.split('/')[2]} page={Number(route.split('/')[3])||1} chunks={chunks}/>; else content=<div className="empty-state"><h1>페이지를 찾을 수 없어요</h1><button className="btn primary" onClick={() => go('/')}>홈으로</button></div>;
  const chatStyle={'--chat-viewport-height':chatViewport.height?`${chatViewport.height}px`:undefined,'--chat-viewport-top':`${chatViewport.offsetTop}px`} as CSSProperties;
  return <div style={chatStyle} className={`app-shell ${section==='/ask'?'chat-route':''} ${chatViewport.keyboardOpen?'keyboard-open':''} ${isAndroidEmbedded()?'embedded-android':''}`}><a className="skip-link" href="#main" onClick={event => {event.preventDefault();document.getElementById('main')?.focus();}}>본문으로 이동</a>{menu&&<button className="scrim" aria-label="메뉴 닫기" onClick={() => setMenu(false)}/>}<aside className={`sidebar ${menu?'open':''}`}><button className="brand" onClick={() => go('/')}><span className="brand-mark">O₂</span><span><strong>Field Guard</strong><small>HYBRIDTECH</small></span></button><p className="nav-label">WORKSPACE</p><nav aria-label="전체 메뉴">{visibleNavigation.map(([path,icon,label]) => <button key={path} aria-current={section===path?'page':undefined} className={`side-link ${section===path?'active':''}`} onClick={() => go(path)}><Icon name={icon}/>{label}{section===path&&<span className="nav-active-dot"/>}</button>)}</nav><div className="sidebar-bottom"><div className="privacy-icon"><Icon name="shield"/></div><strong>현장 지식은 내 기기에</strong><p>문서와 AI는 기기 안에서,<br/>작업 승인은 담당자 확인으로.</p><span className={`badge ${online?'green':''}`}>{online?'온라인':'오프라인'}</span><small>O₂ Field Guard · Web 0.1</small></div></aside><div className="workspace"><header className="topbar"><div className="breadcrumb"><button className="menu-toggle icon-button" onClick={() => setMenu(!menu)} aria-expanded={menu} aria-label="전체 메뉴 열기">☰</button><span>워크스페이스</span><span>/</span><strong>{pageTitle}</strong></div><div className="topbar-right"><span className={`connection ${online?'':'offline'}`}><span className="status-dot"/>{online?'온라인':'오프라인'}</span><button className="topbar-settings" aria-label="오프라인 준비 설정" onClick={() => go('/settings')}><Icon name="download"/><span>오프라인 준비</span></button><span className="profile" aria-label="로컬 기기">HT</span></div></header>{!online&&<div className="offline-banner"><Icon name="wifi"/>오프라인이에요. 준비된 문서와 저장된 기록을 사용할 수 있어요.</div>}<main id="main" tabIndex={-1} className={section==='/ask'?'chat-main':''}>{loadError?<div role="alert" className="notice warning">{loadError}<button className="btn secondary" onClick={() => location.reload()}>다시 시도</button></div>:loading?<div className="loading-screen" role="status"><span className="spinner"/>기기의 문서를 확인하고 있어요…</div>:<Suspense fallback={<div className="loading-screen" role="status"><span className="spinner"/>화면을 여는 중이에요…</div>}>{content}</Suspense>}</main><nav className="bottom-nav" aria-label="모바일 메뉴">{navigation.filter(([path]) => ['/','/ask','/library','/settings'].includes(path)).map(([path,icon,label]) => <button key={path} className={section===path?'active':''} aria-current={section===path?'page':undefined} onClick={() => go(path)}><Icon name={icon}/><span>{label==='문서 보관함'?'문서':label==='문서 질문'?'질문':label}</span></button>)}</nav></div>{notice&&<div className="toast" role="status">{notice}<button aria-label="알림 닫기" onClick={() => setNotice('')}><Icon name="close"/></button></div>}</div>;
}
