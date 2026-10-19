import { AI_OPERATION_KEY, AI_STAGES } from '../src/ai/recovery.ts';

// Served below /api/ so already installed service workers also fetch it directly.
// This page never imports the app, the model, WASM or a GPU runtime.
export function aiRecoveryPage(nonce:string){
  return `<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Field Guard · AI 준비 복구</title>
<style>[hidden]{display:none!important}body{margin:0;background:#f7f9fc;color:#191f28;font:16px/1.8 system-ui,sans-serif}main{max-width:600px;margin:auto;padding:28px 20px}section{padding:24px;background:white;border:1px solid #e8edf3;border-radius:20px}h1{font-size:27px;line-height:1.5}button,a{display:block;box-sizing:border-box;width:100%;padding:14px;border-radius:12px;text-align:center;font:inherit}button{background:#3182f6;color:white;border:0;font-weight:700}a{color:#1769ce}small{display:block;color:#6b7684}</style>
<main><h1>저장된 모델을 유지하고<br>AI 준비를 복구해요</h1><section><p>먼저 다른 Field Guard 탭과 홈 화면 앱을 닫아 주세요. 이 페이지는 AI 모델을 불러오지 않아요.</p><p>자동 AI 준비를 잠시 멈추고 오래된 앱 화면의 연결을 갱신해요. 모델·문서·대화·작업 기록은 삭제하지 않아요.</p><button id="recover">자동 재시도를 멈추고 복구 설정 적용</button><p id="status" role="status"></p><a id="app" href="/#/settings" hidden>설정 화면 다시 열기 →</a><small>설정 화면에서 ‘문맥을 줄여 다시 준비’를 선택하면 문맥 2,048·답변 256 토큰으로 저장된 모델을 다시 시험해요. 같은 종료가 반복되면 재시도하지 말고 문서 기능을 이용해 주세요.</small></section></main>
<script nonce="${nonce}">
(()=>{const button=document.getElementById('recover'),recoveryStatus=document.getElementById('status');
button.addEventListener('click',async()=>{
  button.disabled=true;
  try{
    let previous;try{previous=JSON.parse(localStorage.getItem(${JSON.stringify(AI_OPERATION_KEY)})||'null');}catch{}
    const knownStage=${JSON.stringify(AI_STAGES)}.includes(previous?.stage);
    localStorage.setItem(${JSON.stringify(AI_OPERATION_KEY)},JSON.stringify({version:1,id:crypto.randomUUID(),stage:knownStage?previous.stage:'engine',contextTokens:knownStage&&Number.isFinite(previous.contextTokens)?previous.contextTokens:4096,startedAt:knownStage&&Number.isFinite(previous.startedAt)?previous.startedAt:Date.now()}));
    const scope=new URL('/',location.href).href;
    const registration=await navigator.serviceWorker?.getRegistration(scope);
    if(registration?.scope===scope)await registration.unregister();
    recoveryStatus.textContent='복구 설정을 적용했어요. 저장된 모델은 유지돼요. 설정 화면을 다시 열어 주세요.';
    document.getElementById('app').hidden=false;
  }catch(error){recoveryStatus.textContent='복구 설정을 저장하지 못했어요. HTTPS 주소와 브라우저 저장 권한을 확인해 주세요.';button.disabled=false;}
});})();
</script></html>`;
}
