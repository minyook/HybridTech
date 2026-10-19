import { createServer as httpServer } from 'node:http';
import { createServer as httpsServer, request } from 'node:https';
import type { AddressInfo } from 'node:net';
import { checkServerIdentity } from 'node:tls';
import { X509Certificate } from 'node:crypto';
import { mkdtempSync, readdirSync, rmSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterAll, beforeAll, expect, it } from 'vitest';
import { installerHandler, prepareCertificates, privateIPv4 } from '../server/iphone-https';

let directory: string, certificates: ReturnType<typeof prepareCertificates>;
beforeAll(()=>{directory=mkdtempSync(join(tmpdir(),'fieldguard-iphone-'));certificates=prepareCertificates(directory,['192.168.0.31']);},15000);
afterAll(()=>{if(directory){for(const file of readdirSync(directory))rmSync(join(directory,file));rmdirSync(directory);}});

it('사설 IPv4만 허용하며 공개 IP·호스트·변형 문자열은 거부한다',()=>{
  expect(['10.0.0.1','172.16.0.1','172.31.255.1','192.168.0.31'].every(privateIPv4)).toBe(true);
  for(const value of ['8.8.8.8','127.0.0.1','172.32.0.1','localhost','192.168.0.31\n','192.168.0.999'])expect(privateIPv4(value)).toBe(false);
});
it('실제 TLS 인증서가 PC IP·localhost와 일치하고 다른 IP는 인증하지 않는다',()=>{
  const root=new X509Certificate(certificates.ca),leaf=new X509Certificate(certificates.cert);
  expect(root.ca).toBe(true);expect(leaf.ca).toBe(false);expect(leaf.verify(root.publicKey)).toBe(true);
  expect(leaf.checkIP('192.168.0.31')).toBe('192.168.0.31');expect(leaf.checkHost('localhost')).toBe('localhost');
  expect(checkServerIdentity('192.168.0.32',leaf.toLegacyObject())).toBeInstanceOf(Error);
});
it('재실행과 PC IP 변경에도 아이폰에서 신뢰한 CA를 임의로 바꾸지 않는다',()=>{
  const reused=prepareCertificates(directory,['192.168.0.31']);expect(reused.fingerprint).toBe(certificates.fingerprint);expect(reused.cert.equals(certificates.cert)).toBe(true);
  const moved=prepareCertificates(directory,['192.168.0.32']);expect(moved.fingerprint).toBe(certificates.fingerprint);expect(new X509Certificate(moved.cert).checkIP('192.168.0.32')).toBeTruthy();
});
it('인증서 검증을 끄지 않고 명시적으로 신뢰한 CA로 HTTPS 연결한다',async()=>{
  const server=httpsServer({key:certificates.key,cert:certificates.cert},(_,response)=>response.end('secure'));
  await new Promise<void>(resolve=>server.listen(0,'127.0.0.1',resolve));const port=(server.address() as AddressInfo).port;
  const read=(ca?:Buffer)=>new Promise<string>((resolve,reject)=>{
    request({hostname:'127.0.0.1',port,path:'/',ca},response=>{let body='';response.on('data',chunk=>{body+=chunk;});response.on('end',()=>resolve(body));}).on('error',reject).end();
  });
  try {await expect(read()).rejects.toThrow();await expect(read(certificates.ca)).resolves.toBe('secure');}
  finally {server.closeAllConnections();await new Promise<void>(resolve=>server.close(()=>resolve()));}
});
it('설치 서버는 공개 인증서 프로파일만 제공하고 개인키·파일 경로·API는 제공하지 않는다',async()=>{
  const server=httpServer(installerHandler('192.168.0.31',certificates.certificate,certificates.fingerprint));
  await new Promise<void>(resolve=>server.listen(0,'127.0.0.1',resolve));const base=`http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  try {
    const page=await fetch(base);expect(await page.text()).toContain('https://192.168.0.31:4174/');
    const profile=await fetch(`${base}/field-guard.mobileconfig`);expect(profile.headers.get('content-type')).toBe('application/x-apple-aspen-config');
    const xml=await profile.text();expect(xml).toContain('com.apple.security.root');expect(xml).toContain(certificates.certificate.toString('base64'));expect(xml).not.toContain('PRIVATE KEY');expect(xml).not.toContain('com.apple.mdm');
    for(const path of ['/ca-key.pem','/.local/iphone-https/ca-key.pem','/server-key.pem','/api/session'])expect((await fetch(base+path)).status).toBe(404);
  } finally {server.closeAllConnections();await new Promise<void>(resolve=>server.close(()=>resolve()));}
});
