import {afterEach,beforeEach,expect,it} from 'vitest';
import QRCode from 'qrcode';import jsQR from 'jsqr';
import {createWorkStore,type WorkStore} from '../server/store';
import {invitationURL,parseInvitation} from '../src/workflow/invitation';
let store:WorkStore,time:number;
beforeEach(()=>{time=Date.parse('2026-10-23T01:00:00Z');store=createWorkStore(':memory:',()=>time);});
afterEach(()=>store.close());
function owner(){const session=store.connect();return store.unlock(session.token,store.todayCode().code,'admin');}
it('작업자는 QR을 발급할 수 없으며 형식이 맞는 임의 토큰으로 권한을 얻을 수 없다',()=>{
  const worker=store.connect();expect(()=>store.issueInvite(worker.token,'위조')).toThrow('감독관 인증');expect(()=>store.redeemInvite(worker.token,'A'.repeat(43),'worker')).toThrow();expect(store.connect(worker.token).session.supervisor).toBeUndefined();
});
it('발급·스캔 1회 교환·세션 회전·작업자 소유권 보존·이중 사용 방지를 처리한다',()=>{
  const admin=owner(),worker=store.connect(),invite=store.issueInvite(admin.token,'테스트 감독관');
  const grant=store.redeemInvite(worker.token,invite.token,'worker');expect(grant.session.workerId).toBe(worker.session.workerId);expect(grant.session.supervisor?.name).toBe('테스트 감독관');expect(grant.token).not.toBe(worker.token);
  expect(store.connect(worker.token).session.supervisor).toBeUndefined();expect(()=>store.redeemInvite(store.connect().token,invite.token,'other')).toThrow();
});
it('QR 발급자의 취소가 이미 부여한 QR 권한에도 즉시 적용된다',()=>{
  const admin=owner(),invite=store.issueInvite(admin.token,'감독관'),grant=store.redeemInvite(store.connect().token,invite.token,'worker');store.revokeInvite(admin.token,invite.id);
  expect(store.connect(grant.token).session.supervisor).toBeUndefined();expect(()=>store.listOrders(grant.token,'supervisor')).toThrow('감독관 인증');
});
it('다른 감독관은 발급자의 QR을 취소할 수 없다',()=>{const admin=owner(),other=owner(),invite=store.issueInvite(admin.token,'감독관');expect(()=>store.revokeInvite(other.token,invite.id)).toThrow();});
it('다음 감독관의 QR을 발급해도 이미 인증한 감독관 권한이 사라지지 않는다',()=>{
  const admin=owner(),first=store.issueInvite(admin.token,'첫 감독관'),grant=store.redeemInvite(store.connect().token,first.token,'first');store.issueInvite(admin.token,'다음 감독관');expect(store.connect(grant.token).session.supervisor).toBeDefined();
  const records=store.listInvites(admin.token);expect(records).toHaveLength(2);expect(records.some(record=>record.used)).toBe(true);expect(JSON.stringify(records)).not.toContain(first.token);
});
it('미사용 QR은 5분 뒤 만료되지만 이미 인증된 권한은 당일 자정까지 유지된다',()=>{
  const admin=owner(),first=store.issueInvite(admin.token,'감독관'),second=store.issueInvite(admin.token,'미사용');const grant=store.redeemInvite(store.connect().token,first.token,'worker');
  time+=5*60*1000;expect(()=>store.redeemInvite(store.connect().token,second.token,'other')).toThrow();expect(store.connect(grant.token).session.supervisor).toBeDefined();
  time=Date.parse('2026-10-23T15:00:00Z');expect(store.connect(grant.token).session.supervisor).toBeUndefined();
});
it('당일 코드 교체·폐기는 기존 QR과 QR로 부여한 권한도 취소한다',()=>{
  const admin=owner(),invite=store.issueInvite(admin.token,'감독관'),unused=store.issueInvite(admin.token,'미사용'),grant=store.redeemInvite(store.connect().token,invite.token,'worker');
  store.rotateDailyCode();expect(store.connect(grant.token).session.supervisor).toBeUndefined();expect(()=>store.redeemInvite(store.connect().token,unused.token,'other')).toThrow();
});
it('만료된 감독관은 새 QR을 발급하지 못하며 인증 실패를 제한한다',()=>{
  const admin=owner(),worker=store.connect();for(let i=0;i<15;i++)expect(()=>store.redeemInvite(worker.token,'bad','remote')).toThrow();expect(()=>store.redeemInvite(worker.token,'bad','remote')).toThrow('15분');
  time=Date.parse('2026-10-23T15:00:00Z');expect(()=>store.issueInvite(admin.token,'감독관')).toThrow();
});
it('QR은 서버에 노출되는 query 대신 fragment로 전달하며 다른 사이트·측정 공유 QR과 구분한다',()=>{
  const origin='https://field.test',token='A'.repeat(43),link=invitationURL(origin,token);expect(new URL(link).search).toBe('');expect(parseInvitation(link,origin)).toBe(token);
  expect(()=>parseInvitation(link,'https://other.test')).toThrow();expect(()=>parseInvitation('javascript:alert(1)',origin)).toThrow();
});
it('로컬 QR 생성 결과를 jsQR로 다시 판독하여 링크를 온전히 보존한다',()=>{
  const content=invitationURL('https://field.test','A'.repeat(43)),qr=QRCode.create(content,{errorCorrectionLevel:'M'}),scale=5,size=(qr.modules.size+8)*scale,pixels=new Uint8ClampedArray(size*size*4).fill(255);
  for(let y=0;y<size;y++)for(let x=0;x<size;x++){const row=Math.floor(y/scale)-4,column=Math.floor(x/scale)-4;if(row>=0&&column>=0&&row<qr.modules.size&&column<qr.modules.size&&qr.modules.get(row,column)){const index=(y*size+x)*4;pixels[index]=pixels[index+1]=pixels[index+2]=0;}}
  expect(jsQR(pixels,size,size)?.data).toBe(content);
});
