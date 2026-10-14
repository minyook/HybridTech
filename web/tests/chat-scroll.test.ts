import { expect, it, vi } from 'vitest';
import { createChatScroll } from '../src/chat/scroll';

function setup(){
  const metrics={top:0,maximum:1000};const frames=new Map<number,()=>void>();let id=0;
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
