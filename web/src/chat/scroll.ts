export interface ScrollMetrics { top:number;maximum:number;generating:boolean;responseEndVisible:boolean }
export function responseEndIsVisible(end:number,visibleTop:number,visibleBottom:number){
  return visibleBottom>visibleTop&&end>=visibleTop+16&&end<=visibleBottom;
}
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
  let previousTop=port.read().top;
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
    start(){if(disposed)return;previousTop=port.read().top;following=true;port.showLatest(false);schedule();},
    pause(){if(disposed)return;previousTop=port.read().top;following=false;cancel();updateButton();},
    towardResponse(){
      if(disposed)return;
      const metrics=port.read();previousTop=metrics.top;
      following=metrics.generating&&metrics.responseEndVisible;
      cancel();if(following)schedule();updateButton();
    },
    scrolled(){
      if(disposed)return;
      const {top,maximum,generating,responseEndVisible}=port.read();
      const movingUp=top<previousTop-.5;
      const movingDown=top>previousTop+.5;
      const returnedToResponse=movingDown&&generating&&responseEndVisible;
      previousTop=top;
      // Looking upward must stay free even if the newest line is still visible.
      // Coming back to that line resumes a live answer before the page footer.
      if(movingUp)following=false;
      else if(maximum-top<=2||returnedToResponse)following=true;
      else if(movingDown)following=false;
      if(following)schedule();else cancel();updateButton();
    },
    resized(){if(disposed)return;if(following)schedule();else updateButton();},
    latest(){if(disposed)return;following=true;port.showLatest(false);schedule();},
    dispose(){disposed=true;cancel();},
  };
}
