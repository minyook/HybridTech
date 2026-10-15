import { expect, it, vi } from 'vitest';
import { createChatScroll, responseEndIsVisible } from '../src/chat/scroll';

function setup(){
  const metrics={top:0,maximum:1000,generating:true,responseEndVisible:false};const frames=new Map<number,()=>void>();let id=0;
  const scroll=vi.fn((top:number)=>{metrics.top=top;});const showLatest=vi.fn();
  const controller=createChatScroll({read:()=>metrics,scroll,showLatest,requestFrame:callback=>{frames.set(++id,callback);return id;},cancelFrame:frame=>{frames.delete(frame);}});
  const flush=()=>{const callbacks=[...frames.values()];frames.clear();for(const callback of callbacks)callback();};
  return {controller,metrics,frames,scroll,showLatest,flush};
}
it('새 질문은 최신 위치를 보여주고 여러 토큰 갱신을 한 프레임에 한 번만 따라간다',()=>{
  const state=setup();state.controller.start();state.controller.resized();state.metrics.maximum=1300;state.controller.resized();
  expect(state.frames.size).toBe(1);state.flush();expect(state.scroll).toHaveBeenCalledExactlyOnceWith(1300);
});
it('사용자가 스크롤하면 예약된 자동 이동을 취소하고 생성은 계속돼도 읽던 위치를 보존한다',()=>{
  const state=setup();state.controller.start();state.controller.pause();state.metrics.top=400;state.controller.scrolled();
  for(let i=0;i<20;i++){state.metrics.maximum+=50;state.controller.resized();state.flush();}
  expect(state.metrics.top).toBe(400);expect(state.scroll).not.toHaveBeenCalled();expect(state.showLatest).toHaveBeenLastCalledWith(true);
});
it('조금만 위로 움직여도 토큰 업데이트가 사용자의 스크롤을 되돌리지 않는다',()=>{
  const state=setup();state.controller.start();state.flush();state.scroll.mockClear();
  state.controller.pause();state.metrics.top=970;state.controller.scrolled();state.metrics.maximum=1100;state.controller.resized();state.flush();
  expect(state.scroll).not.toHaveBeenCalled();expect(state.metrics.top).toBe(970);
});
it('최신 답변 이동을 누르면 가장 최근 높이로 이동하고 자동 따라가기를 재개한다',()=>{
  const state=setup();state.controller.pause();state.metrics.top=300;state.metrics.maximum=1800;state.controller.latest();state.flush();
  expect(state.metrics.top).toBe(1800);expect(state.showLatest).toHaveBeenLastCalledWith(false);
  state.metrics.maximum=1900;state.controller.resized();state.flush();expect(state.metrics.top).toBe(1900);
});
it('사용자가 직접 끝까지 내려온 경우에만 자동 따라가기를 재개한다',()=>{
  const state=setup();state.controller.pause();state.metrics.top=999;state.controller.scrolled();state.metrics.maximum=1050;state.controller.resized();state.flush();
  expect(state.metrics.top).toBe(1050);
});
it('저장된 대화를 열었을 때와 수동 탐색 중 답변 완료·출처 추가 시 강제로 이동하지 않는다',()=>{
  const state=setup();state.controller.resized();state.flush();expect(state.scroll).not.toHaveBeenCalled();
  state.controller.start();state.flush();state.scroll.mockClear();state.controller.pause();state.metrics.top=250;state.controller.scrolled();
  state.metrics.maximum+=200;state.controller.resized();state.flush();expect(state.metrics.top).toBe(250);expect(state.scroll).not.toHaveBeenCalled();
});
it('화면을 떠나면 예약된 프레임과 늦은 크기 변경이 다른 화면을 스크롤하지 않는다',()=>{
  const state=setup();state.controller.start();state.controller.dispose();state.controller.resized();state.flush();expect(state.frames.size).toBe(0);expect(state.scroll).not.toHaveBeenCalled();
});
it('생성 중인 마지막 줄로 내려오면 페이지 맨 아래에 닿기 전 자동 따라가기를 재개한다',()=>{
  const state=setup();state.controller.start();state.flush();state.controller.pause();state.metrics.top=350;state.controller.scrolled();
  state.metrics.top=820;state.metrics.responseEndVisible=true;state.controller.scrolled();
  expect(state.showLatest).toHaveBeenLastCalledWith(false);state.flush();expect(state.metrics.top).toBe(1000);
  state.metrics.maximum=1120;state.controller.resized();state.flush();expect(state.metrics.top).toBe(1120);
});
it('최신 줄이 보여도 사용자가 위로 스크롤하는 동안에는 다시 끌어내리지 않는다',()=>{
  const state=setup();state.controller.start();state.flush();state.scroll.mockClear();state.controller.pause();
  state.metrics.top=980;state.metrics.responseEndVisible=true;state.controller.scrolled();state.metrics.maximum=1080;state.controller.resized();state.flush();
  expect(state.metrics.top).toBe(980);expect(state.scroll).not.toHaveBeenCalled();
});
it('읽던 위치에서 화면 크기 변경만으로 마지막 줄이 보여도 자동 이동하지 않는다',()=>{
  const state=setup();state.controller.pause();state.metrics.top=700;state.controller.scrolled();state.metrics.responseEndVisible=true;state.controller.resized();state.flush();
  expect(state.metrics.top).toBe(700);expect(state.scroll).not.toHaveBeenCalled();
});
it('입력창에 가려진 생성 끝부분이나 이미 완료된 답변으로 내려올 때는 자유 탐색을 유지한다',()=>{
  const state=setup();state.controller.pause();state.metrics.top=700;state.controller.scrolled();state.flush();expect(state.scroll).not.toHaveBeenCalled();
  state.metrics.top=820;state.metrics.responseEndVisible=true;state.metrics.generating=false;state.controller.scrolled();state.flush();
  expect(state.metrics.top).toBe(820);expect(state.scroll).not.toHaveBeenCalled();
});
it('최신 줄이 이미 보일 때 아래 방향 입력만으로도 이어서 따라간다',()=>{
  const state=setup();state.controller.pause();state.metrics.top=980;state.metrics.responseEndVisible=true;
  state.controller.towardResponse();state.flush();expect(state.metrics.top).toBe(1000);
  state.metrics.maximum=1100;state.controller.resized();state.flush();expect(state.metrics.top).toBe(1100);
});
it('최신 줄이 아직 가려져 있으면 아래 방향 입력도 사용자의 탐색을 유지한다',()=>{
  const state=setup();state.metrics.top=500;state.controller.towardResponse();state.controller.resized();state.flush();expect(state.scroll).not.toHaveBeenCalled();
});
it('자동 재개 직후 같은 위치의 스크롤 이벤트가 반복돼도 예약된 이동을 취소하지 않는다',()=>{
  const state=setup();state.controller.pause();state.metrics.top=800;state.metrics.responseEndVisible=true;
  state.controller.scrolled();state.controller.scrolled();state.metrics.maximum=1100;state.controller.resized();state.flush();
  expect(state.metrics.top).toBe(1100);
});
it.each([
  {end:500,top:76,bottom:650,visible:true},
  {end:660,top:76,bottom:650,visible:false},
  {end:60,top:76,bottom:650,visible:false},
  {end:400,top:76,bottom:350,visible:false},
  {end:650,top:76,bottom:650,visible:true},
])('생성 끝 $end px가 실제 읽기 영역 $top~$bottom 안에 보이는지 검사한다',({end,top,bottom,visible})=>{
  expect(responseEndIsVisible(end,top,bottom)).toBe(visible);
});
