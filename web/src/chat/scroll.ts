export interface ScrollMetrics { top:number;maximum:number }
export interface ScrollPort {
  read:()=>ScrollMetrics;
  scroll:(top:number)=>void;
  requestFrame:(callback:()=>void)=>number;
  cancelFrame:(id:number)=>void;
  showLatest:(visible:boolean)=>void;
}

// Only follow a response while the reader stays at its end. User gestures pause
// synchronously, before the next token/layout update can schedule another move.
export function createChatScroll(port:ScrollPort){
  let following=false;
  let frame:number|undefined;
  let disposed=false;
  const nearEnd=()=>{const {top,maximum}=port.read();return maximum-top<=64;};
  const updateButton=()=>port.showLatest(!following&&!nearEnd());
  const cancel=()=>{if(frame!==undefined)port.cancelFrame(frame);frame=undefined;};
  const schedule=()=>{
    if(disposed||!following||frame!==undefined)return;
    frame=port.requestFrame(()=>{
      frame=undefined;if(disposed||!following)return;
      port.scroll(port.read().maximum);port.showLatest(false);
    });
  };
  return {
    start(){if(disposed)return;following=true;port.showLatest(false);schedule();},
    pause(){if(disposed)return;following=false;cancel();updateButton();},
    scrolled(){if(disposed)return;const {top,maximum}=port.read();following=maximum-top<=2;if(!following)cancel();updateButton();},
    resized(){if(disposed)return;if(following)schedule();else updateButton();},
    latest(){if(disposed)return;following=true;port.showLatest(false);schedule();},
    dispose(){disposed=true;cancel();},
  };
}
