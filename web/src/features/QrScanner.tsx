import {useEffect,useRef,useState} from 'react';
import jsQR from 'jsqr';
/** QR camera frames and photos never leave this device. */
export default function QrScanner({onCode}:{onCode:(text:string)=>void}){
  const video=useRef<HTMLVideoElement>(null),media=useRef<MediaStream|null>(null),epoch=useRef(0),timer=useRef<ReturnType<typeof setInterval>|undefined>(undefined);
  const [active,setActive]=useState(false),[opening,setOpening]=useState(false),[error,setError]=useState('');
  const callback=useRef(onCode);callback.current=onCode;
  function stop(){++epoch.current;clearInterval(timer.current);media.current?.getTracks().forEach(track=>track.stop());media.current=null;if(video.current)video.current.srcObject=null;setActive(false);setOpening(false);}
  useEffect(()=>{const hidden=()=>{if(document.hidden)stop();};document.addEventListener('visibilitychange',hidden);return()=>{stop();document.removeEventListener('visibilitychange',hidden);};},[]);
  function decode(source:CanvasImageSource,width:number,height:number){
    const scale=Math.min(1,800/Math.max(width,height)),canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(width*scale));canvas.height=Math.max(1,Math.round(height*scale));
    const ctx=canvas.getContext('2d',{willReadFrequently:true});if(!ctx)throw Error();
    ctx.drawImage(source,0,0,canvas.width,canvas.height);const image=ctx.getImageData(0,0,canvas.width,canvas.height);
    return jsQR(image.data,image.width,image.height,{inversionAttempts:'attemptBoth'})?.data;
  }
  async function start(){stop();setError('');setOpening(true);const current=epoch.current;
    try{if(!window.isSecureContext||!navigator.mediaDevices?.getUserMedia)throw Error();
      const stream=await navigator.mediaDevices.getUserMedia({audio:false,video:{facingMode:{ideal:'environment'},width:{ideal:640},height:{ideal:480}}});
      if(current!==epoch.current){stream.getTracks().forEach(track=>track.stop());return;}
      media.current=stream;if(!video.current){stop();return;}video.current.srcObject=stream;setActive(true);await video.current.play();if(current!==epoch.current)return;setOpening(false);
      timer.current=setInterval(()=>{const element=video.current;if(!element?.videoWidth||document.hidden)return;try{const value=decode(element,element.videoWidth,element.videoHeight);if(value){stop();callback.current(value);}}catch{stop();setError('QR 판독을 중단했어요. 사진 선택을 이용해 주세요.');}},350);
    }catch{if(current===epoch.current){stop();setError('카메라를 열지 못했어요. HTTPS·카메라 권한을 확인하거나 QR 사진·인증 링크를 이용해 주세요.');}}
  }
  async function image(file:File|undefined){stop();setError('');if(!file)return;
    if(file.size>8*1024*1024||!file.type.startsWith('image/')){setError('8MB 이하의 QR 이미지 파일을 선택해 주세요.');return;}
    const current=epoch.current,url=URL.createObjectURL(file);
    try{const picture=new Image();picture.src=url;await picture.decode();if(current!==epoch.current)return;const value=decode(picture,picture.naturalWidth,picture.naturalHeight);if(!value)throw Error();callback.current(value);}
    catch{if(current===epoch.current)setError('사진에서 QR을 찾지 못했어요. QR 전체가 선명하게 나온 사진을 선택해 주세요.');}finally{URL.revokeObjectURL(url);}
  }
  return <div className="qr-scanner"><video ref={video} autoPlay muted playsInline hidden={!active} aria-label="QR 카메라 미리보기"/><div className="button-row"><button type="button" className="btn primary" onClick={active||opening?stop:start}>{opening?'카메라 준비 취소':active?'카메라 닫기':'카메라로 QR 읽기'}</button><label className="btn secondary qr-file">QR 사진 선택<input type="file" accept="image/*" onChange={event=>{void image(event.target.files?.[0]);event.target.value='';}}/></label></div><p className="caption">카메라와 사진은 이 기기에서만 판독해요.</p>{error&&<p className="notice warning" role="alert">{error}</p>}</div>;
}
