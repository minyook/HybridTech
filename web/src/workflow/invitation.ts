const tokenPattern=/^[A-Za-z0-9_-]{43}$/;
export function invitationURL(origin:string,token:string){
  if(!tokenPattern.test(token))throw Error('QR 인증 정보의 형식이 올바르지 않아요.');
  return `${new URL(origin).origin}/#/supervisor-access?invite=${encodeURIComponent(token)}`;
}
export function parseInvitation(value:string,origin:string){
  const raw=value.trim();
  if(tokenPattern.test(raw))return raw;
  let url:URL;try{url=new URL(raw);}catch{throw Error('이 사이트에서 발급한 감독관 인증 QR을 사용해 주세요.');}
  if(url.origin!==new URL(origin).origin||url.pathname!=='/'||url.search||!url.hash.startsWith('#/supervisor-access?'))throw Error('현재 접속한 사이트에서 발급한 QR만 사용할 수 있어요.');
  const token=new URLSearchParams(url.hash.split('?')[1]).get('invite')??'';
  if(!tokenPattern.test(token))throw Error('QR 인증 정보가 올바르지 않아요.');
  return token;
}
