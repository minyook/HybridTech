import { useLayoutEffect, useRef, useState } from 'react';
import { createChatScroll } from './scroll';

export function useChatScroll(enabled:boolean,requestId:string|undefined){
  const messagesRef=useRef<HTMLDivElement>(null);
  const composerRef=useRef<HTMLFormElement>(null);
  const controller=useRef<ReturnType<typeof createChatScroll>|undefined>(undefined);
  const [showLatest,setShowLatest]=useState(false);
  useLayoutEffect(()=>{
    if(!enabled||!messagesRef.current)return;
    const chatScroll=createChatScroll({
      read:()=>({top:window.scrollY,maximum:Math.max(0,document.documentElement.scrollHeight-window.innerHeight)}),
      // Repeated smooth animations compete with wheel/touch scrolling. Follow
      // layout changes once per frame with an immediate, cancellable move.
      scroll:top=>window.scrollTo({top,behavior:'instant'}),
      requestFrame:callback=>requestAnimationFrame(callback),cancelFrame:cancelAnimationFrame,
      showLatest:visible=>setShowLatest(visible&&Boolean(messagesRef.current?.querySelector('.exchange'))),
    });
    controller.current=chatScroll;
    const wheel=(event:WheelEvent)=>{if(event.deltaY!==0)chatScroll.pause();};
    const touch=()=>chatScroll.pause();
    const keyboard=(event:KeyboardEvent)=>{
      const target=event.target;
      if(target instanceof HTMLElement&&(target.isContentEditable||target.closest('input,textarea,select,button')))return;
      if(['ArrowUp','ArrowDown','PageUp','PageDown','Home','End',' '].includes(event.key))chatScroll.pause();
    };
    const pointer=(event:PointerEvent)=>{if(event.clientX>=document.documentElement.clientWidth)chatScroll.pause();};
    const scrolled=()=>chatScroll.scrolled();const resized=()=>chatScroll.resized();
    const observer=new ResizeObserver(resized);observer.observe(messagesRef.current);if(composerRef.current)observer.observe(composerRef.current);
    window.addEventListener('wheel',wheel,{passive:true,capture:true});window.addEventListener('touchmove',touch,{passive:true,capture:true});
    window.addEventListener('keydown',keyboard,true);window.addEventListener('pointerdown',pointer,true);
    window.addEventListener('scroll',scrolled,{passive:true});window.addEventListener('resize',resized);
    window.visualViewport?.addEventListener('resize',resized);
    return()=>{
      chatScroll.dispose();controller.current=undefined;observer.disconnect();
      window.removeEventListener('wheel',wheel,true);window.removeEventListener('touchmove',touch,true);
      window.removeEventListener('keydown',keyboard,true);window.removeEventListener('pointerdown',pointer,true);
      window.removeEventListener('scroll',scrolled);window.removeEventListener('resize',resized);window.visualViewport?.removeEventListener('resize',resized);
    };
  },[enabled]);
  useLayoutEffect(()=>{if(enabled&&requestId)controller.current?.start();},[enabled,requestId]);
  return {messagesRef,composerRef,showLatest,scrollToLatest:()=>controller.current?.latest()};
}
