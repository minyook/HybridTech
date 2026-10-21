import { useLayoutEffect, useRef, useState } from 'react';
import { createChatScroll, responseEndIsVisible } from './scroll';

export function useChatScroll(enabled:boolean,requestId:string|undefined){
  const messagesRef=useRef<HTMLDivElement>(null);
  const composerRef=useRef<HTMLFormElement>(null);
  const controller=useRef<ReturnType<typeof createChatScroll>|undefined>(undefined);
  const [showLatest,setShowLatest]=useState(false);
  useLayoutEffect(()=>{
    if(!enabled||!messagesRef.current)return;
    const pane=messagesRef.current.closest<HTMLElement>('.chat-scroll');
    const mobile=window.matchMedia('(max-width:850px)');
    const paneActive=()=>Boolean(pane&&(mobile.matches||pane.closest('.app-shell')?.classList.contains('keyboard-open')));
    const chatScroll=createChatScroll({
      read:()=>{
        const response=messagesRef.current?.querySelector('.answer[aria-busy="true"] .markdown');
        const viewportTop=window.visualViewport?.offsetTop??0;
        const viewportBottom=viewportTop+(window.visualViewport?.height??window.innerHeight);
        const paneMode=paneActive();
        const visibleTop=Math.max(viewportTop,document.querySelector('.topbar')?.getBoundingClientRect().bottom??0,paneMode?pane!.getBoundingClientRect().top:0);
        const visibleBottom=Math.min(viewportBottom,composerRef.current?.getBoundingClientRect().top??viewportBottom,paneMode?pane!.getBoundingClientRect().bottom:viewportBottom)-8;
        return {
          top:paneMode?pane!.scrollTop:window.scrollY,maximum:Math.max(0,paneMode?pane!.scrollHeight-pane!.clientHeight:document.documentElement.scrollHeight-window.innerHeight),generating:Boolean(response),
          responseEndVisible:Boolean(response&&responseEndIsVisible(response.getBoundingClientRect().bottom,visibleTop,visibleBottom)),
        };
      },
      // Repeated smooth animations compete with wheel/touch scrolling. Follow
      // layout changes once per frame with an immediate, cancellable move.
      scroll:top=>{if(pane&&paneActive())pane.scrollTo({top,behavior:'instant'});else window.scrollTo({top,behavior:'instant'});},
      requestFrame:callback=>window.requestAnimationFrame(callback),cancelFrame:id=>window.cancelAnimationFrame(id),
      showLatest:visible=>setShowLatest(visible&&Boolean(messagesRef.current?.querySelector('.exchange'))),
    });
    controller.current=chatScroll;
    const wheel=(event:WheelEvent)=>{if(event.deltaY>0)chatScroll.towardResponse();else if(event.deltaY<0)chatScroll.pause();};
    let touchY:number|undefined;
    const touchStart=(event:TouchEvent)=>{touchY=event.touches[0]?.clientY;};
    const touch=(event:TouchEvent)=>{
      const current=event.touches[0]?.clientY;
      if(current!==undefined&&touchY!==undefined&&current<touchY)chatScroll.towardResponse();else chatScroll.pause();
      touchY=current;
    };
    const keyboard=(event:KeyboardEvent)=>{
      const target=event.target;
      if(target instanceof HTMLElement&&(target.isContentEditable||target.closest('input,textarea,select,button')))return;
      if(['ArrowDown','PageDown','End',' '].includes(event.key))chatScroll.towardResponse();
      else if(['ArrowUp','PageUp','Home'].includes(event.key))chatScroll.pause();
    };
    const pointer=(event:PointerEvent)=>{if(event.clientX>=document.documentElement.clientWidth)chatScroll.pause();};
    const scrolled=()=>chatScroll.scrolled();const resized=()=>chatScroll.resized();
    const observer=new ResizeObserver(resized);observer.observe(messagesRef.current);if(composerRef.current)observer.observe(composerRef.current);
    if(pane)observer.observe(pane);
    window.addEventListener('wheel',wheel,{passive:true,capture:true});window.addEventListener('touchstart',touchStart,{passive:true,capture:true});window.addEventListener('touchmove',touch,{passive:true,capture:true});
    window.addEventListener('keydown',keyboard,true);window.addEventListener('pointerdown',pointer,true);
    window.addEventListener('scroll',scrolled,{passive:true});window.addEventListener('resize',resized);
    pane?.addEventListener('scroll',scrolled,{passive:true});
    window.visualViewport?.addEventListener('resize',resized);window.visualViewport?.addEventListener('scroll',resized);
    return()=>{
      chatScroll.dispose();controller.current=undefined;observer.disconnect();
      window.removeEventListener('wheel',wheel,true);window.removeEventListener('touchstart',touchStart,true);window.removeEventListener('touchmove',touch,true);
      window.removeEventListener('keydown',keyboard,true);window.removeEventListener('pointerdown',pointer,true);
      window.removeEventListener('scroll',scrolled);window.removeEventListener('resize',resized);pane?.removeEventListener('scroll',scrolled);window.visualViewport?.removeEventListener('resize',resized);window.visualViewport?.removeEventListener('scroll',resized);
    };
  },[enabled]);
  useLayoutEffect(()=>{if(enabled&&requestId)controller.current?.start();},[enabled,requestId]);
  return {messagesRef,composerRef,showLatest,scrollToLatest:()=>controller.current?.latest()};
}
