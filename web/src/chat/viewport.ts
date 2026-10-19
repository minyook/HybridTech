export interface ChatViewportSample {
  width:number;height:number;layoutHeight:number;offsetTop:number;scale:number;screenHeight:number;editorFocused:boolean;
}
export interface ChatViewport {height:number;offsetTop:number;keyboardOpen:boolean}

// Safari shrinks only the visual viewport; Chrome can resize the layout too.
// Remember the unobstructed height instead of interpreting focus or browser
// toolbar movement alone as an on-screen keyboard.
export function createChatViewport(initial:ChatViewportSample){
  let width=initial.width;
  let baseline=Math.max(initial.height,initial.layoutHeight);
  let chrome=Math.max(0,initial.screenHeight-baseline);
  let current:ChatViewport={height:initial.height,offsetTop:initial.offsetTop,keyboardOpen:false};
  return (sample:ChatViewportSample):ChatViewport=>{
    // Pinch zoom must not collapse the chat layout or masquerade as a keyboard.
    if(Math.abs(sample.scale-1)>.05)return current;
    const fullHeight=Math.max(sample.height,sample.layoutHeight);
    if(Math.abs(sample.width-width)>80){
      baseline=current.keyboardOpen&&sample.editorFocused?Math.max(fullHeight,sample.screenHeight-chrome):fullHeight;
      width=sample.width;
    }else if(!current.keyboardOpen&&!sample.editorFocused){
      baseline=fullHeight;chrome=Math.max(0,sample.screenHeight-baseline);
    }else baseline=Math.max(baseline,fullHeight);
    const reduced=baseline-sample.height>Math.max(120,baseline*.18);
    const keyboardOpen=reduced&&(sample.editorFocused||current.keyboardOpen);
    current={height:Math.max(1,sample.height),offsetTop:Math.max(0,sample.offsetTop),keyboardOpen};
    return current;
  };
}
