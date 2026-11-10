/* 소스 안내
 * 브라우저 viewport 제어기를 React 상태/수명에 연결한다. 키보드·화면 변화가 채팅 배치에 반영되게 한다.
 * 연결 모듈: ./viewport.
 */
import { useLayoutEffect, useState } from 'react';
import { createChatViewport, type ChatViewport, type ChatViewportSample } from './viewport';

export function useChatViewport(enabled:boolean){
  const [viewport,setViewport]=useState<ChatViewport>({height:0,offsetTop:0,keyboardOpen:false});
  useLayoutEffect(()=>{
    if(!enabled){setViewport({height:0,offsetTop:0,keyboardOpen:false});return;}
    const visual=window.visualViewport;
    const read=():ChatViewportSample=>({
      width:window.innerWidth,height:visual?.height??window.innerHeight,layoutHeight:window.innerHeight,
      offsetTop:visual?.offsetTop??0,scale:visual?.scale??1,screenHeight:window.screen.height,
      editorFocused:Boolean(document.activeElement?.matches('.composer textarea')),
    });
    const measure=createChatViewport(read());let frame:number|undefined;
    const update=()=>{frame=undefined;const next=measure(read());setViewport(previous=>previous.height===next.height&&previous.offsetTop===next.offsetTop&&previous.keyboardOpen===next.keyboardOpen?previous:next);};
    const schedule=()=>{if(frame===undefined)frame=requestAnimationFrame(update);};
    update();
    window.addEventListener('resize',schedule);window.addEventListener('pageshow',schedule);
    document.addEventListener('focusin',schedule);document.addEventListener('focusout',schedule);
    visual?.addEventListener('resize',schedule);visual?.addEventListener('scroll',schedule);
    return()=>{
      if(frame!==undefined)cancelAnimationFrame(frame);
      window.removeEventListener('resize',schedule);window.removeEventListener('pageshow',schedule);
      document.removeEventListener('focusin',schedule);document.removeEventListener('focusout',schedule);
      visual?.removeEventListener('resize',schedule);visual?.removeEventListener('scroll',schedule);
    };
  },[enabled]);
  return viewport;
}
