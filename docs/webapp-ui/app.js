const $ = (selector) => document.querySelector(selector);
const icon = (name, extra = '') => `<svg ${extra} aria-hidden="true"><use href="#i-${name}"/></svg>`;
const escapeHtml = (value) => String(value).replace(/[&<>"']/g, (char) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));

const pages = {
  home: ['홈', 'home'], chat: ['문서 질문', 'chat'], sensor: ['가스 측정', 'pulse'],
  checklist: ['작업 전 점검', 'check'], library: ['문서 보관함', 'book'],
  history: ['대화 기록', 'history'], settings: ['설정', 'settings'],
  onboarding: ['시작 화면', 'shield'], voice: ['음성 질문', 'mic'],
  source: ['근거 문서', 'file'], model: ['로컬 AI 모델', 'download'],
  inference: ['추론 설정', 'settings'], data: ['내 데이터', 'device'], system: ['위젯과 알림', 'list']
};
const primary = ['home','chat','sensor','checklist','library'];
const secondary = ['history','settings','onboarding'];
const mobile = ['home','chat','sensor','settings'];
const parent = {voice:'chat',source:'chat',model:'settings',inference:'settings',data:'settings',system:'settings'};
const promptExamples = [
  '작업허가 전에 어떤 안전조치를 확인해야 하나요?',
  '공기 측정 기준을 알려줘',
  '구조 절차를 확인하고 싶어요'
];
const checklist = [
  ['작업 위치와 출입 경로','현장 계획과 비교해요.'],
  ['공기 측정 기록','검교정된 기기와 측정 기록을 확인해요.'],
  ['환기 상태','작업 중 유지 계획을 확인해요.'],
  ['감시인과 연락 방법','현장 담당자와 연락 방법을 확인해요.'],
  ['구조 장비와 절차','원문 근거와 현장 절차를 확인해요.']
];
const documents = [
  {title:'밀폐공간 질식재해예방 안전작업 가이드 (2022)',count:84,page:20,kind:'안전작업 가이드'},
  {title:'밀폐공간 질식사고 위험작업 자율점검표',count:3,page:2,kind:'자율점검표'}
];
const sourceSamples = [
  {title:'작업허가와 안전조치',doc:0,page:20,text:'사업주 또는 관리책임자가 유해가스 농도와 환기 상태 등 작업장 상황을 확인한 뒤, 작업을 허가하는 내부 절차를 마련해요.'},
  {title:'적정 공기와 측정',doc:0,page:24,text:'산소 및 유해가스 농도를 작업 전, 재개 전, 작업 중에 확인해요. 측정 결과와 수신 시각을 함께 봐요.'},
  {title:'구조와 긴급 대응',doc:0,page:44,text:'공기 상태가 불명확하면 구조를 위해 바로 진입하지 않아요. 현장 절차와 119 연락을 확인해요.'},
  {title:'작업 전 자율점검',doc:1,page:2,text:'측정자 지정, 환기, 보호구, 감시인과 연락설비 등 점검 항목을 확인해요.'}
];
const state = {
  page: location.hash.replace('#','') in pages ? location.hash.replace('#','') : 'home',
  sourceReturn:'chat', sourceMode:'pdf', sourceIndex:0, libraryQuery:'',
  gas:'none', scanning:false, connected:false,
  check:new Set(), checklistSaved:false,
  chatQuestion:'', draft:'', hasAnswer:false, historyCount:1,
  listening:false, wake:false, model:'not-installed',
  backend:'자동', context:4096, automatic:true, response:1024, settingsSaved:false,
  dialog:null
};

function navLink(page, cls='side-link') {
  const active = state.page === page || (state.page === 'source' ? state.sourceReturn === page : parent[state.page] === page);
  return `<button class="${cls}${active?' active':''}" data-go="${page}" aria-current="${active?'page':'false'}">${icon(pages[page][1])}<span>${pages[page][0]}</span></button>`;
}
function renderShell(){
  $('#side-primary').innerHTML = primary.map(p => navLink(p)).join('');
  $('#side-secondary').innerHTML = secondary.map(p => navLink(p)).join('');
  $('#bottom-nav').innerHTML = mobile.map(p => navLink(p,'bottom-link')).join('');
  $('#topbar-title').innerHTML = `<strong>${pages[state.page][0]}</strong> <span class="muted">/ O₂ Field Guard</span>`;
  const status = $('.topbar-status');
  status.innerHTML = state.gas === 'ble' ? `<span class="dot good"></span> BLE 수신 예시 ${icon('chevron')}` : state.gas === 'lost' ? `<span class="dot danger"></span> 연결 끊김 ${icon('chevron')}` : `<span class="dot neutral"></span> 측정 대기 ${icon('chevron')}`;
}
const eyebrow = (text) => `<p class="eyebrow">${text}</p>`;
const head = (label,title,desc='',actions='') => `<div class="page-head"><div>${eyebrow(label)}<h1>${title}</h1>${desc?`<p>${desc}</p>`:''}</div>${actions?`<div class="head-actions">${actions}</div>`:''}</div>`;
const btn = (text,action,kind='primary',extra='') => `<button class="btn ${kind}" data-action="${action}" ${extra}>${text}</button>`;
const badge = (text,tone='') => `<span class="badge ${tone}">${text}</span>`;
const card = (content,extra='') => `<section class="card ${extra}">${content}</section>`;
const back = (target,label='돌아가기') => `<button class="back-link" data-go="${target}">${icon('back')}${label}</button>`;

function gasValues(compact=false){
  const has = state.gas === 'ble' || state.gas === 'demo';
  const values = has ? ['20.9','0.0','2','0'] : ['—','—','—','—'];
  const names = ['O₂','H₂S','CO','LEL'];
  const units = ['%','ppm','ppm','%LEL'];
  return compact ? `<div class="metric-grid">${names.map((n,i)=>`<div class="metric"><strong>${values[i]}</strong><span>${n}</span></div>`).join('')}</div>` : `<div class="sensor-values">${names.map((n,i)=>`<div class="sensor-value"><span>${n}</span><strong>${values[i]}${has?`<em>${units[i]}</em>`:''}</strong></div>`).join('')}</div>`;
}
function gasStatus(){
  if(state.gas==='ble') return badge('BLE 수신 예시 · 실측 아님','blue');
  if(state.gas==='demo') return badge('데모 · 실제 측정값 아님','blue');
  if(state.gas==='lost') return badge('연결 끊김','red');
  return badge('연결·수신 대기');
}
function gasCaution(){
  if(state.gas==='ble'||state.gas==='demo') return '시안에 표시된 값이에요. 실제 계측값으로 사용하지 마세요.';
  return '새 측정값이 없어요. 이전 수치를 현재값으로 사용하지 마세요.';
}
function home(){
  return `<div class="screen">
    <section class="hero"><div class="hero-content">${eyebrow('현장 작업 도우미')}<h1>오늘 현장에서<br>필요한 것을 확인해요</h1><p>안전 문서의 근거를 찾고, 측정기 상태와 작업 전 점검을 한곳에서 확인해요.</p><div class="status-row">${badge('문서 · 오프라인 준비됨','green')}${gasStatus()}</div></div><div class="hero-art" aria-hidden="true"><div class="ring"></div><div class="art-icon">${icon('shield')}</div><div class="small-dot"></div></div></section>
    <div class="grid-main mt-18">
      ${card(`<div class="card-top"><h2>가스 측정</h2>${gasStatus()}</div>${gasValues(true)}<div class="notice">${icon(state.gas==='lost'?'alert':'pulse')}<span>${gasCaution()}</span></div><button class="btn ghost mt-12" data-go="sensor">측정 상태 보기 ${icon('arrow')}</button>`,'gas-card')}
      ${card(`<div class="quick-icon">${icon('chat')}</div><h2>문서에 바로 질문해요</h2><p>측정, 환기, 구조 절차를 제공 문서에서 찾아요. 답변에서 근거 쪽을 열 수 있어요.</p><button class="btn primary mt-18" data-go="chat">질문하기 ${icon('arrow')}</button>`,'blue-soft home-ask')}
    </div>
    <div class="section-title"><h2>빠르게 시작해요</h2><small>주요 작업</small></div>
    <div class="grid grid-3 home-quick">
      <button class="quick-card" data-go="checklist"><div class="quick-icon">${icon('check')}</div><strong>작업 전 점검</strong><span>${state.check.size} / 5개 확인</span></button>
      <button class="quick-card" data-go="library"><div class="quick-icon">${icon('book')}</div><strong>문서 보관함</strong><span>2개 문서 · 87개 근거</span></button>
      <button class="quick-card" data-go="voice"><div class="quick-icon">${icon('mic')}</div><strong>음성으로 질문</strong><span>기기 내 인식 상태 확인</span></button>
    </div>
    <div class="section-title"><h2>준비 상태</h2></div>
    ${card(`<button class="list-row" data-go="model"><div class="row-icon">${icon('download')}</div><div class="row-copy"><strong>로컬 AI 모델</strong><small>${state.model==='ready'?'기기에서 답변할 준비가 됐어요.':'모델 설치 전에도 문서 검색을 사용할 수 있어요.'}</small></div><span class="row-end">${state.model==='ready'?'준비됨':'준비하기'} ${icon('chevron')}</span></button><button class="list-row" data-go="system"><div class="row-icon">${icon('list')}</div><div class="row-copy"><strong>위젯과 알림</strong><small>앱 밖의 측정 상태 표시를 살펴봐요.</small></div><span class="row-end">${icon('chevron')}</span></button>`,'list-card')}
    <p class="fine-print mt-18">이 화면은 웹앱 디자인 시안이에요. 표시된 상태와 데이터는 실제 현장 측정 또는 작업 허가를 뜻하지 않아요.</p>
  </div>`;
}
function answerMarkup(){
  const q = state.chatQuestion;
  const air = /공기|산소|측정/.test(q), rescue = /구조|119/.test(q);
  let body, source;
  if(air){body='제공 문서는 작업 전과 작업 중에 산소 및 유해가스 농도를 측정하고 기록하도록 안내해요. 측정값은 출처와 수신 시각을 함께 확인해요.';source=1;}
  else if(rescue){body='공기 상태가 불명확하거나 적절한 보호구가 없다면 구조를 위해 바로 진입하지 않아요. 119와 현장 안전관리 절차에 따라 대응해요.';source=2;}
  else{body='작업장 상황, 유해가스 농도, 환기 상태를 확인하고 작업을 허가하는 내부 절차를 살펴봐요. 점검 항목은 현장 담당자와 다시 확인해요.';source=0;}
  return `<div class="bubble user">${escapeHtml(q)}</div><div class="bubble answer"><p><strong>문서 근거로 살펴봤어요</strong></p><p>${body}</p><p class="fine-print mb-0">화면 구성용 답변 예시예요. 실제 판단 전 원문과 현장 절차를 확인해요.</p><div class="answer-sources"><button data-open-source="${source}">${sourceSamples[source].title} · ${sourceSamples[source].page}쪽 ${icon('chevron')}</button><button data-open-source="3">자율점검표 · 2쪽 ${icon('chevron')}</button></div></div>`;
}
function chat(){
  return `<div class="screen">${head('문서 기반 질문','현장에서 궁금한 걸 물어보세요','기본 제공 문서에서 관련 쪽을 찾고 답변과 함께 보여줘요.',`<button class="btn secondary" data-go="history">${icon('history')} 대화 기록</button>`)}
    <div class="chat-layout"><div class="chat-panel"><div class="chat-status"><span>가스 상태 · ${state.gas==='none'?'새 측정값 없음':state.gas==='lost'?'연결 끊김':state.gas==='demo'?'데모 수치':'BLE 수신 예시'}</span><button class="btn ghost small" data-go="sensor">상태 보기 ${icon('chevron')}</button></div>
      <div class="chat-feed" id="chat-feed">${state.hasAnswer?answerMarkup():`<div class="welcome"><div class="empty-icon" style="margin:0 0 22px">${icon('chat')}</div><h2>안전 작업에 관해<br>궁금한 걸 물어보세요</h2><p>질문하면 먼저 문서에서 근거를 찾아요.</p><div class="suggestions">${promptExamples.map((q,i)=>`<button class="chip" data-prompt="${i}">${escapeHtml(q)}</button>`).join('')}</div></div>`}</div>
      <form class="chat-input" id="chat-form"><input id="chat-draft" name="question" value="${escapeHtml(state.draft)}" placeholder="안전 작업에 관해 질문해요" autocomplete="off" aria-label="질문 입력"><button class="icon-button" type="button" data-go="voice" aria-label="음성으로 질문">${icon('mic')}</button><button class="icon-button send" type="submit" aria-label="질문 보내기">${icon('send')}</button></form></div>
      <div class="stack"><aside class="side-note"><h3>답변을 확인할 때</h3><div class="summary-item">검색 범위<strong>기본 문서 2개 · 87개 근거</strong></div><div class="summary-item">근거 확인<strong>PDF 원본과 추출 텍스트</strong></div><div class="summary-item">데이터 처리<strong>현재 Android 앱 기준 기기 저장</strong></div><p>이 시안은 질문 흐름을 보여줘요. 실제 웹 서비스의 서버·저장 정책은 구현 시 정해야 해요.</p></aside><aside class="side-note"><div class="notice">${icon('alert')}<span>가스 측정이 없거나 끊긴 상태에서는 이전 값을 현재값으로 쓰지 않아요.</span></div></aside></div>
    </div></div>`;
}
function historyPage(){
  const rows = state.historyCount ? `<button class="list-row" data-action="open-history"><div class="row-icon">${icon('chat')}</div><div class="row-copy"><strong>${state.hasAnswer?escapeHtml(state.chatQuestion):'작업허가 전에 어떤 안전조치를 확인해야 하나요?'}</strong><small>예시 대화 · 질문 1개 · 이 기기에 저장되는 화면 구성</small></div><span class="row-end">${icon('chevron')}</span></button>` : '<div class="empty-state"><h2>저장된 대화가 없어요</h2><p>새 대화를 시작하면 이곳에서 다시 열 수 있어요.</p></div>';
  return `<div class="screen">${head('대화 기록','이전에 물어본 내용을 다시 봐요','현재 앱은 대화를 기기에 보관해요.',`<button class="btn primary" data-action="new-chat">${icon('chat')} 새 대화</button>`)}${card(rows,'list-card')}<div class="alert-box blue mt-18">${icon('shield')}<div>시안의 대화는 브라우저 화면 예시예요. 실제 웹 서비스의 계정과 동기화 정책은 아직 정해지지 않았어요.</div></div></div>`;
}
function source(){
  const s = sourceSamples[state.sourceIndex], d = documents[s.doc];
  const pdf = `../../app/src/main/assets/source-pdfs/${s.doc?'checklist-2026.pdf':'safety-guide-2022.pdf'}#page=${s.page}`;
  const tabs = [['pdf','PDF 원본'],['formatted','읽기 편한 보기'],['raw','추출 텍스트']];
  let content;
  if(state.sourceMode==='pdf') content=`<div class="pdf-preview"><iframe title="${escapeHtml(d.title)} ${s.page}쪽 PDF 원본" src="${pdf}" style="width:100%;max-width:750px;height:590px;border:0;background:white;border-radius:6px"></iframe></div><p class="fine-print">브라우저에서 PDF가 보이지 않으면 원본 파일을 열어 확인해요.</p>`;
  else if(state.sourceMode==='raw') content=`<div class="source-text raw">${escapeHtml(s.text)}\n\n검색과 답변에 사용된 텍스트를 보여주는 화면 시안입니다. 정확한 전체 추출문은 Android 앱의 해당 문서 쪽을 확인하세요.</div>`;
  else content=`<div class="source-text"><h3>${s.title}</h3><p>${s.text}</p><ul><li>답변에 사용된 문서와 쪽수를 함께 확인해요.</li><li>그림과 표의 정확한 내용은 PDF 원본에서 확인해요.</li></ul></div>`;
  return `<div class="screen">${back(state.sourceReturn, '이전 화면으로')}${head('답변 근거','답변이 어디에서 왔는지 확인해요','같은 쪽의 PDF 원본과 검색에 사용된 텍스트를 비교해요.')}
    ${card(`<div class="source-meta"><div class="quick-icon">${icon('file')}</div><div><strong>${escapeHtml(d.title)}</strong><small>${s.page}쪽 · ${s.title}</small></div></div>`)}
    <div class="section-title"><h2>문서 보기</h2>${badge('오프라인 문서','green')}</div>
    <div class="segmented" role="tablist" aria-label="문서 보기 방식">${tabs.map(([key,label])=>`<button role="tab" aria-selected="${state.sourceMode===key}" class="${state.sourceMode===key?'active':''}" data-source-mode="${key}">${label}</button>`).join('')}</div>
    <div class="mt-18">${content}</div>
    <div class="source-tools"><div class="btn-row"><button class="btn secondary" data-action="prev-source">${icon('back')} 이전 부분</button><button class="btn secondary" data-action="next-source">다음 부분 ${icon('arrow')}</button></div><a class="btn secondary" href="${pdf}" target="_blank" rel="noopener">PDF 원본 열기 ${icon('arrow')}</a></div>
    <div class="alert-box blue mt-24">${icon('file')}<div>추출 텍스트는 검색에 사용된 내용을 보여줘요. 표와 그림은 PDF 원본에서 확인해요.</div></div>
  </div>`;
}
function voice(){
  return `<div class="screen">${back('chat','질문 화면으로')}${head('음성 질문',state.listening?'질문을 듣고 있어요':'말로 질문해요','기기 내 한국어 음성 인식이 가능한 환경에서 사용할 수 있어요.')}
    <div class="split"><div class="stack">${card(`<div class="voice-visual"><div class="voice-orb ${state.listening?'listening':''}">${icon('mic')}</div></div><h2 style="text-align:center">${state.listening?'말씀해 주세요':'마이크를 눌러 질문해요'}</h2><p style="text-align:center">${state.listening?'말을 마치면 같은 문서 질문 흐름으로 이어져요.':'음성 인식 결과를 확인한 뒤 문서에서 근거를 찾아요.'}</p><div class="btn-row" style="justify-content:center;margin-top:25px">${btn(state.listening?'듣기 중단':'음성 질문 시작','toggle-listen',state.listening?'secondary':'primary')}</div>`)}${card(`<div class="control-row"><div><strong>‘오투야’ 호출 대기</strong><p>화면을 보고 있을 때만 작동해요.</p></div><input class="switch" type="checkbox" data-action="wake" ${state.wake?'checked':''} aria-label="오투야 호출 대기"></div>`)}<p class="fine-print">현재 Android 앱은 기기 내 한국어 인식기 지원 여부를 확인해야 해요. 이 웹 시안은 인식·녹음을 실행하지 않아요.</p></div>
      ${card(`<h3>음성 질문 흐름</h3><div class="summary-item">1. 음성 입력<strong>로컬 인식 가능 여부 확인</strong></div><div class="summary-item">2. 문서 검색<strong>질문에 맞는 근거 쪽 찾기</strong></div><div class="summary-item">3. 답변 확인<strong>짧은 음성 안내와 전체 텍스트</strong></div><button class="btn secondary block mt-18" data-action="voice-sample">예시 질문 결과 보기</button>`)}</div></div>`;
}
function sensor(){
  const source = state.gas;
  const states = [['none','미수신'],['ble','BLE 수신 예시'],['demo','데모'],['lost','연결 끊김']];
  let intro = source==='lost' ? '측정기 연결이 끊겼어요' : source==='ble' ? '측정값 화면을 살펴봐요' : source==='demo' ? '데모 수치를 살펴봐요' : '가스 측정기를 연결해요';
  return `<div class="screen">${head('가스 측정',intro,source==='lost'?'새 측정값을 받을 수 없어요.':source==='none'?'연결과 수신이 확인되면 값과 출처를 함께 보여줘요.':'아래 값은 화면 구성용 예시이며 실제 계측값이 아니에요.')}
    <div class="section-title mt-0"><h2>상태별 화면</h2><small>시안 전환</small></div><div class="state-selector">${states.map(([key,label])=>`<button class="chip ${source===key?'active':''}" data-gas="${key}">${label}</button>`).join('')}</div>
    <div class="sensor-hero mt-18"><div class="card-top"><div><h2 style="margin:0">실시간 측정</h2><p class="muted" style="font-size:12px;margin:7px 0 0">${source==='ble'?'예시 기기 FG-100 · 방금 수신한 화면 예시':source==='demo'?'시뮬레이션 화면 예시':source==='lost'?'마지막 수신 이후 연결이 끊긴 화면 예시':'연결된 측정기 없음'}</p></div>${gasStatus()}</div>${gasValues()}<div class="divider"></div><div class="notice ${source==='lost'?'red':''}">${icon(source==='lost'?'alert':'pulse')}<span>${gasCaution()}</span></div></div>
    ${source==='lost'?`<div class="alert-box mt-18">${icon('alert')}<div><strong>이전 수치는 현재 상태가 아니에요</strong>측정기 전원과 거리를 확인하고 다시 검색해요. 현장 절차에 따라 계측 상태를 확인해요.</div></div>`:''}
    <div class="grid grid-2 mt-18">${card(`<div class="card-top"><h3>Bluetooth LE 측정기</h3>${badge(state.connected?'연결 예시':'연결 대기')}</div><p>${state.connected?'FG-100 예시 기기 · 시안 연결 상태':'연결된 가스 측정기가 없어요.'}</p><div class="btn-row mt-18">${btn(state.scanning?'검색 결과 닫기':'기기 검색','scan',state.scanning?'secondary':'primary')}${state.connected?btn('연결 해제','disconnect','secondary'):''}</div>${state.scanning?`<div class="device-row"><div class="row-icon">${icon('device')}</div><div class="device-info"><strong>FG-100 예시 측정기</strong><small>가상 기기 · 신호 −58 dBm</small></div><button class="btn secondary small" data-action="connect">연결 예시</button></div>`:''}`)}
      ${card(`<h3>값을 표시하는 기준</h3><p>BLE 수신값과 데모 수치를 분리해요. 새 값이 60초 이상 들어오지 않으면 현재값으로 표시하지 않아요.</p><div class="divider"></div><div class="notice red">${icon('alert')}<span>측정기 제조사 통신 규격과 계측 정확도는 실제 장비에서 검증해야 해요.</span></div>`)}</div>
    <p class="fine-print mt-18">이 시안에서는 BLE를 검색하거나 계측하지 않아요. 상태 전환과 기기 목록은 디자인 검토용이에요.</p>
  </div>`;
}
function checklistPage(){
  const count=state.check.size;
  return `<div class="screen">${head('작업 전 점검','확인할 내용을 모았어요','다섯 가지 항목을 현장 절차와 함께 확인해요.')}
    <div class="grid-main">${card(`<div class="card-top"><h2>점검 항목</h2><span class="number-emphasis">${count} / 5</span></div><div class="progress-track mt-18"><div class="progress-fill" style="width:${count*20}%"></div></div><div class="mt-18">${checklist.map(([title,desc],i)=>`<label class="check-row"><input type="checkbox" data-check="${i}" ${state.check.has(i)?'checked':''}><span><strong>${title}</strong><small>${desc}</small></span></label>`).join('')}</div><button class="btn primary block mt-24" data-action="save-checklist">${state.checklistSaved?'기기에 저장했어요':'점검 기록 저장'}</button>`)}
    <div class="stack">${card(`<h3>확인한 내용</h3><p>${count===0?'아직 확인한 항목이 없어요.':`${count}개 항목을 확인했어요. 현장 담당자와 점검 결과를 다시 살펴봐요.`}</p><div class="divider"></div><p class="fine-print">이 목록은 작업허가 또는 현장 승인 절차를 대신하지 않아요.</p>`,'blue-soft')}${card(`<h3>관련 문서</h3><p>자율점검표에서 항목별 원문을 확인할 수 있어요.</p><button class="btn secondary mt-18" data-open-source="3">자율점검표 2쪽 보기 ${icon('arrow')}</button>`)}</div></div></div>`;
}
function libraryResults(){
  const query = state.libraryQuery.trim().toLowerCase();
  if(!query) return `<div class="grid grid-2">${documents.map((d,i)=>`<button class="card document-card" data-open-source="${i===0?0:3}"><div class="quick-icon">${icon('file')}</div><div><h3>${escapeHtml(d.title)}</h3><p>근거 조각 ${d.count}개 · 기기 내 기본 제공</p>${badge(d.kind,'blue')}</div></button>`).join('')}</div><p class="fine-print mt-18">현재 Android 앱은 기본 제공 문서 2개만 검색해요. 개인 문서 추가는 아직 구현되지 않았어요.</p>`;
  const matches=sourceSamples.filter(s => `${s.title} ${s.text} ${documents[s.doc].title}`.toLowerCase().includes(query));
  return `<div class="section-title"><h2>검색 결과</h2><small>화면 구성용 예시 ${matches.length}개</small></div>${matches.length?`<div class="stack">${matches.map(s=>`<button class="card document-card" data-open-source="${sourceSamples.indexOf(s)}"><div class="quick-icon">${icon('search')}</div><div><h3>${s.title}</h3><p>${escapeHtml(documents[s.doc].title)} · ${s.page}쪽</p><p>${s.text}</p></div></button>`).join('')}</div>`:card('<div class="empty-state"><h2>예시 결과가 없어요</h2><p>다른 키워드로 찾아봐요. 실제 문서 검색은 웹 서비스 구현 후 연결돼요.</p></div>')}`;
}
function library(){
  return `<div class="screen">${head('문서 보관함','인터넷 없이도 문서를 찾아요','기본 제공 문서 2개 · 87개 쪽별 근거')}
    <div class="search-wrap">${icon('search')}<input class="search-field" id="library-query" type="search" value="${escapeHtml(state.libraryQuery)}" placeholder="문서에서 찾기" aria-label="문서 검색"></div><div id="library-results" class="mt-24">${libraryResults()}</div></div>`;
}
function settings(){
  const items=[['model','download','로컬 AI 모델','Gemma 4 E2B 설치와 상태'],['inference','settings','추론 설정','실행 방식과 답변 길이'],['data','device','내 데이터','기기에 저장된 기록'],['system','list','위젯과 알림','앱 밖에서 측정 상태 보기']];
  return `<div class="screen">${head('설정','기기와 데이터를 관리해요','모델, 추론 방식, 저장 범위를 확인해요.')}${card(items.map(([p,i,title,sub])=>`<button class="setting-row w-full" style="background:none;border-left:0;border-right:0;border-top:0;text-align:left" data-go="${p}"><div class="row-icon">${icon(i)}</div><div><strong>${title}</strong><small>${sub}</small></div>${icon('chevron','class="chev"')}</button>`).join(''))}<div class="alert-box blue mt-18">${icon('shield')}<div>현재 Android 앱의 문서 검색과 질문은 기기에서 처리해요. 웹 서비스로 옮길 때는 데이터 저장과 서버 정책을 별도로 설계해야 해요.</div></div></div>`;
}
function model(){
  const phases=[['not-installed','설치 전'],['downloading','다운로드 중'],['ready','준비됨'],['failed','실패']];
  const title={ 'not-installed':'기기에서 답할 모델을 준비해요',downloading:'AI 모델을 받고 있어요',ready:'AI 모델이 준비됐어요',failed:'모델을 받지 못했어요'}[state.model];
  return `<div class="screen">${back('settings','설정으로')}${head('로컬 AI 모델',title,'설치가 끝나면 제공 문서를 참고해 기기에서 답해요.')}
    <div class="section-title mt-0"><h2>설치 상태</h2><small>시안 전환</small></div><div class="state-selector">${phases.map(([p,label])=>`<button class="chip ${state.model===p?'active':''}" data-model="${p}">${label}</button>`).join('')}</div>
    <div class="grid-main mt-18">${card(`<div class="card-top"><h2>Gemma 4 E2B</h2>${badge(state.model==='ready'?'로컬 모델 준비됨':state.model==='failed'?'다운로드 실패':state.model==='downloading'?'다운로드 중':'설치 전',state.model==='ready'?'green':state.model==='failed'?'red':'blue')}</div><p>온디바이스 생성 모델 · 약 2.41GB</p>${state.model==='downloading'?`<div class="card-top mt-24"><strong>다운로드 시안</strong><span class="number-emphasis">63%</span></div><div class="progress-track mt-12"><div class="progress-fill" style="width:63%"></div></div><p>앱을 닫아도 이어받는 화면을 가정해요.</p>`:state.model==='failed'?`<div class="alert-box mt-24">${icon('alert')}<div>다운로드를 완료하지 못했어요. 네트워크와 저장 공간을 확인한 뒤 다시 시도해요.</div></div>`:state.model==='ready'?`<div class="alert-box blue mt-24">${icon('check')}<div>모델이 준비된 화면 예시예요. 실제 모델 파일은 설치되지 않았어요.</div></div>`:`<div class="alert-box blue mt-24">${icon('search')}<div>모델이 없어도 제공 문서에서 근거를 찾을 수 있어요.</div></div>`}<div class="btn-row mt-24">${state.model==='not-installed'||state.model==='failed'?btn(state.model==='failed'?'다시 다운로드':'모델 다운로드','download-model'):state.model==='downloading'?btn('완료 상태 보기','complete-model','secondary'):btn('추론 설정 보기','open-inference','secondary')}</div>`)}
    ${card(`<h3>설치 전에 확인해요</h3><div class="model-stat"><span>저장 공간</span><strong>최소 4GB 여유 권장</strong></div><div class="model-stat"><span>네트워크</span><strong>처음 설치할 때 필요해요</strong></div><div class="model-stat"><span>설치 후 질문</span><strong>기기 내 검색·생성</strong></div><p class="fine-print">이 시안은 다운로드를 실행하지 않아요. 웹 서비스에서 로컬 모델을 제공할 방식은 별도 기술 검토가 필요해요.</p>`)}</div></div>`;
}
function inference(){
  return `<div class="screen">${back('settings','설정으로')}${head('추론 설정','이 기기에 맞게 설정해요','바꾼 설정은 다음 질문부터 적용돼요.')}
    <div class="grid-main">${card(`<h2>실행 방식</h2><p>실행되지 않으면 다른 방식으로 전환해요.</p><div class="segmented mt-18">${['자동','GPU 우선','CPU 우선'].map(x=>`<button class="${state.backend===x?'active':''}" data-backend="${x}">${x}</button>`).join('')}</div><div class="divider"></div><div class="control-row"><div><div class="field-label">총 문맥 한도</div><div class="number-emphasis">${state.context.toLocaleString()} 토큰</div></div></div><input type="range" id="context-range" min="2048" max="8192" step="1024" value="${state.context}"><div class="field-help">기기 RAM에 맞는 안전 범위 안에서 선택해요.</div><div class="divider"></div><div class="control-row"><div><strong>답변 길이 자동</strong><p>모델이 답을 마칠 때까지 생성해요.</p></div><input type="checkbox" class="switch" data-action="automatic" ${state.automatic?'checked':''} aria-label="답변 길이 자동"></div>${state.automatic?'':`<div class="mt-24"><div class="field-label">수동 최대 답변 길이</div><div class="number-emphasis">${state.response.toLocaleString()} 토큰</div><input type="range" id="response-range" min="256" max="2048" step="128" value="${state.response}"><div class="field-help">수동 상한에 도달하면 문장이 중간에 멈출 수 있어요.</div></div>`}<button class="btn primary block mt-24" data-action="save-settings">${state.settingsSaved?'설정 저장했어요':'설정 저장'}</button>`)}
    ${card(`<h3>현재 선택한 값</h3><div class="model-stat"><span>실행</span><strong>${state.backend}</strong></div><div class="model-stat"><span>문맥 한도</span><strong>${state.context.toLocaleString()} 토큰</strong></div><div class="model-stat"><span>답변 길이</span><strong>${state.automatic?'자동':`${state.response.toLocaleString()} 토큰`}</strong></div><p class="fine-print">실제 백엔드와 한도는 단말 성능에 따라 결정돼요. 여기서는 조작 흐름만 확인해요.</p>`)}</div></div>`;
}
function dataPage(){
  return `<div class="screen">${back('settings','설정으로')}${head('내 데이터','내 기록은 기기에 있어요','현재 Android 앱의 저장 범위를 보여주는 화면 시안이에요.')}
    ${card(`<div class="card-top"><h2>계정 없이 사용해요</h2>${badge('기기 저장','green')}</div><p>현재 Android 앱에는 질문·답변을 전송하는 서버가 없어요.</p>`,'green-soft')}
    <div class="section-title"><h2>저장된 항목</h2></div>${card(`<button class="list-row" data-go="history"><div class="row-copy"><strong>대화 기록</strong><small>${state.historyCount}개 예시 세션 · 기기 저장 화면</small></div><span class="row-end">${icon('chevron')}</span></button><button class="list-row" data-go="library"><div class="row-copy"><strong>문서와 검색 색인</strong><small>기본 제공 문서 2개 · 87개 근거</small></div><span class="row-end">${icon('chevron')}</span></button><button class="list-row" data-go="sensor"><div class="row-copy"><strong>마지막 가스값</strong><small>출처와 수신 시각을 함께 저장하는 화면</small></div><span class="row-end">${icon('chevron')}</span></button>`,'list-card')}
    <div class="grid grid-2 mt-18">${card(`<h3>네트워크 사용</h3><p>Android 앱은 모델을 처음 받을 때만 네트워크가 필요해요. 음성 인식은 기기 음성 서비스 정책을 따라요.</p>`)}${card(`<h3>대화 기록 관리</h3><p>삭제하면 저장된 대화를 되돌릴 수 없어요.</p><button class="btn danger mt-18" data-action="confirm-delete">대화 기록 삭제</button>`)}</div>
    <p class="fine-print mt-18">웹 서비스로 확장할 때는 계정, 동기화, 저장 위치, 접근 권한을 별도로 결정해야 해요.</p></div>`;
}
function system(){
  return `<div class="screen">${back('settings','설정으로')}${head('위젯과 알림','앱 밖에서도 측정 상태를 봐요','현재 Android 앱의 위젯과 백그라운드 알림 구성을 웹 화면으로 정리했어요.')}
    <div class="grid grid-2">${card(`<h2>홈 화면 위젯</h2><p>마지막 수신 상태와 출처를 함께 표시해요.</p><div class="widget-preview mt-24"><div class="card-top"><strong>O₂ Field Guard</strong>${gasStatus()}</div>${gasValues(true)}<div class="fine-print">${gasCaution()}</div></div><div class="desktop-mobile-caption">Android 위젯 구성 예시</div>`)}
    ${card(`<h2>백그라운드 알림</h2><p>서비스가 실행되는 동안 알림 패널에서 상태를 확인해요.</p><div class="widget-preview mt-24"><div class="source-meta"><div class="quick-icon">${icon('pulse')}</div><div><strong>${state.gas==='ble'?'측정기 수신 화면 예시':state.gas==='lost'?'측정기 연결 끊김':'새 측정값 없음'}</strong><small>${gasCaution()}</small></div></div></div><div class="desktop-mobile-caption">Android 알림 구성 예시</div>`)}</div>
    <div class="alert-box mt-18">${icon('alert')}<div>OS와 제조사에 따라 위젯·알림 모양이 달라질 수 있어요. 수신이 멈춘 값은 현재값으로 사용하지 마세요.</div></div></div>`;
}
function onboarding(){
  return `<div class="screen"><div class="onboarding-layout"><div class="onboarding-copy">${eyebrow('O₂ FIELD GUARD')}<h1>현장에서 필요한 답,<br>휴대전화 안에 있어요</h1><p>제공 문서를 먼저 찾고, 근거 쪽을 확인할 수 있어요. 가스 측정은 값의 출처와 수신 시각을 분리해 보여줘요.</p><div class="onboarding-list"><div>${icon('check')} 안전 문서 2개 · 87개 근거 조각</div><div>${icon('check')} 로컬 모델 설치 후 기기 내 답변</div><div>${icon('check')} BLE 측정과 데모 상태 구분</div></div><button class="btn primary" data-go="home">시작하기 ${icon('arrow')}</button><p class="fine-print">안전 판단과 작업 허가를 대신하지 않아요.</p></div><div class="onboarding-art"><div class="brand-mark">O<sub>2</sub></div></div></div></div>`;
}
const views={home,chat,history:historyPage,source,voice,sensor,checklist:checklistPage,library,settings,model,inference,data:dataPage,system,onboarding};
function render(){
  renderShell();
  $('#main').innerHTML=views[state.page]();
  $('#dialog-root').innerHTML=state.dialog?`<div class="dialog-backdrop"><div class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><h2 id="dialog-title">대화 기록을 삭제할까요?</h2><p>이 시안의 예시 대화를 지워요. 실제 기기 데이터는 삭제되지 않아요.</p><div class="btn-row"><button class="btn secondary" data-action="cancel-delete">취소</button><button class="btn danger" data-action="delete-history">삭제</button></div></div></div>`:'';
}
function go(page){
  if(!(page in views)) return;
  state.page=page;
  $('.sidebar').classList.remove('open');
  $('#mobile-scrim').classList.remove('visible');
  render(); window.scrollTo(0,0);
}
function openSource(index){state.sourceReturn=state.page;state.sourceIndex=Number(index);state.sourceMode='pdf';go('source');}
function toast(message){const root=$('#toast-root');root.innerHTML=`<div class="toast">${escapeHtml(message)}</div>`;clearTimeout(toast.timer);toast.timer=setTimeout(()=>root.innerHTML='',2600);}
function ask(question){
  const q=question.trim();if(!q){toast('질문을 입력해 주세요.');return;}
  state.chatQuestion=q;state.draft='';state.hasAnswer=true;state.historyCount=1;go('chat');
  const feed=$('#chat-feed');if(feed)feed.scrollTop=feed.scrollHeight;
}
document.addEventListener('click',(event)=>{
  const link=event.target.closest('[data-go]');if(link){go(link.dataset.go);return;}
  const prompt=event.target.closest('[data-prompt]');if(prompt){ask(promptExamples[Number(prompt.dataset.prompt)]);return;}
  const source=event.target.closest('[data-open-source]');if(source){openSource(source.dataset.openSource);return;}
  const mode=event.target.closest('[data-source-mode]');if(mode){state.sourceMode=mode.dataset.sourceMode;render();return;}
  const gas=event.target.closest('[data-gas]');if(gas){state.gas=gas.dataset.gas;state.connected=state.gas==='ble';render();return;}
  const model=event.target.closest('[data-model]');if(model){state.model=model.dataset.model;render();return;}
  const backend=event.target.closest('[data-backend]');if(backend){state.backend=backend.dataset.backend;state.settingsSaved=false;render();return;}
  const action=event.target.closest('[data-action]');if(!action)return;
  switch(action.dataset.action){
    case 'toggle-menu':$('.sidebar').classList.add('open');$('#mobile-scrim').classList.add('visible');break;
    case 'close-menu':$('.sidebar').classList.remove('open');$('#mobile-scrim').classList.remove('visible');break;
    case 'new-chat':state.hasAnswer=false;state.chatQuestion='';go('chat');break;
    case 'open-history':ask(state.hasAnswer?state.chatQuestion:promptExamples[0]);break;
    case 'prev-source':state.sourceIndex=(state.sourceIndex+sourceSamples.length-1)%sourceSamples.length;render();break;
    case 'next-source':state.sourceIndex=(state.sourceIndex+1)%sourceSamples.length;render();break;
    case 'toggle-listen':state.listening=!state.listening;render();break;
    case 'voice-sample':state.listening=false;ask(promptExamples[1]);break;
    case 'scan':state.scanning=!state.scanning;render();break;
    case 'connect':state.connected=true;state.scanning=false;state.gas='ble';render();toast('BLE 연결 화면 예시를 보여줘요.');break;
    case 'disconnect':state.connected=false;state.gas='lost';render();break;
    case 'save-checklist':state.checklistSaved=true;render();toast('점검 저장 화면 예시예요.');break;
    case 'download-model':state.model='downloading';render();toast('다운로드 진행 화면 예시예요.');break;
    case 'complete-model':state.model='ready';render();break;
    case 'open-inference':go('inference');break;
    case 'save-settings':state.settingsSaved=true;render();toast('설정 저장 화면 예시예요.');break;
    case 'confirm-delete':state.dialog='delete';render();break;
    case 'cancel-delete':state.dialog=null;render();break;
    case 'delete-history':state.historyCount=0;state.hasAnswer=false;state.dialog=null;render();toast('시안의 예시 대화를 지웠어요.');break;
  }
});
document.addEventListener('submit',(event)=>{if(event.target.id==='chat-form'){event.preventDefault();ask($('#chat-draft').value);}});
document.addEventListener('input',(event)=>{
  const target=event.target;
  if(target.id==='chat-draft')state.draft=target.value;
  if(target.id==='library-query'){
    state.libraryQuery=target.value;
    $('#library-results').innerHTML=libraryResults();
  }
  if(target.id==='context-range'){$('.number-emphasis').textContent=`${Number(target.value).toLocaleString()} 토큰`;}
});
document.addEventListener('change',(event)=>{
  const target=event.target;
  if(target.dataset.check!==undefined){const i=Number(target.dataset.check);target.checked?state.check.add(i):state.check.delete(i);state.checklistSaved=false;render();}
  if(target.dataset.action==='wake'){state.wake=target.checked;render();}
  if(target.dataset.action==='automatic'){state.automatic=target.checked;state.settingsSaved=false;render();}
  if(target.id==='context-range'){state.context=Number(target.value);state.settingsSaved=false;render();}
  if(target.id==='response-range'){state.response=Number(target.value);state.settingsSaved=false;render();}
});
window.addEventListener('hashchange',()=>{const p=location.hash.slice(1);if(p in views && p!==state.page){state.page=p;render();window.scrollTo(0,0);}});
document.addEventListener('keydown',(event)=>{if(event.key==='Escape'&&state.dialog){state.dialog=null;render();}if(event.key==='Enter'&&event.target.classList.contains('brand'))go('home');});
render();
