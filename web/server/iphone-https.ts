import { createPrivateKey, randomBytes, randomUUID, X509Certificate } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { networkInterfaces } from 'node:os';
import { isIP } from 'node:net';
import { join } from 'node:path';
import type { IncomingMessage, ServerResponse } from 'node:http';

export function privateIPv4(address: string) {
  if (isIP(address) !== 4) return false;
  const parts = address.split('.').map(Number);
  if (parts.length !== 4 || parts.some(part => !Number.isInteger(part) || part < 0 || part > 255)) return false;
  return parts[0] === 10 || (parts[0] === 192 && parts[1] === 168) || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31);
}

export function localAddresses() {
  return [...new Set(Object.entries(networkInterfaces())
    .sort(([a], [b]) => Number(!/wi-?fi|wireless/i.test(a)) - Number(!/wi-?fi|wireless/i.test(b)))
    .flatMap(([, entries]) => entries?.filter(entry => entry.family === 'IPv4' && !entry.internal && privateIPv4(entry.address)).map(entry => entry.address) ?? []))];
}

export function findOpenSSL() {
  const candidates = [process.env.FIELDGUARD_OPENSSL, 'openssl', 'C:/Program Files/Git/usr/bin/openssl.exe'].filter(Boolean) as string[];
  for (const candidate of candidates) {
    try { execFileSync(candidate, ['version'], { stdio: 'pipe', windowsHide: true }); return candidate; } catch { /* Try the next installed runtime. */ }
  }
  throw new Error('OpenSSL이 필요해요. Git for Windows의 OpenSSL 또는 FIELDGUARD_OPENSSL 경로를 확인해 주세요.');
}

export function prepareCertificates(directory: string, addresses: string[], executable = findOpenSSL()) {
  if (!addresses.length || addresses.some(address => !privateIPv4(address))) throw new Error('같은 Wi-Fi에서 사용할 PC의 사설 IPv4 주소가 필요해요.');
  mkdirSync(directory, { recursive: true, mode: 0o700 });
  const path = (name: string) => join(directory, name);
  const openssl = (...args: string[]) => {
    try { execFileSync(executable, args, { cwd: directory, stdio: 'pipe', windowsHide: true }); }
    catch { throw new Error('개발용 HTTPS 인증서를 만들지 못했어요. OpenSSL과 .local/iphone-https 파일을 확인해 주세요.'); }
  };
  const config = `[req]
distinguished_name=dn
prompt=no
[dn]
CN=Field Guard Local Test CA
[ca_ext]
basicConstraints=critical,CA:TRUE,pathlen:0
keyUsage=critical,keyCertSign,cRLSign
subjectKeyIdentifier=hash
[server_ext]
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid,issuer
subjectAltName=DNS:localhost,IP:127.0.0.1,${addresses.map(address => `IP:${address}`).join(',')}
`;
  writeFileSync(path('openssl.cnf'), config);
  const rootExists = existsSync(path('ca.pem')) || existsSync(path('ca-key.pem'));
  let root: X509Certificate | undefined;
  if (rootExists) {
    try {
      root = new X509Certificate(readFileSync(path('ca.pem')));
      if (!root.ca || !root.checkPrivateKey(createPrivateKey(readFileSync(path('ca-key.pem')))) || !root.verify(root.publicKey)) throw new Error();
    } catch { throw new Error('기존 개발용 인증서와 키가 일치하지 않아요. 임의로 교체하지 않고 중지했어요.'); }
    if (Date.parse(root.validTo) <= Date.now()) throw new Error('개발용 인증서가 만료됐어요. 기존 프로파일을 제거한 뒤 .local/iphone-https를 별도로 보관하고 다시 준비해 주세요.');
  } else {
    openssl('genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', path('ca-key.pem'));
    openssl('req', '-new', '-x509', '-key', path('ca-key.pem'), '-out', path('ca.pem'), '-days', '30', '-sha256', '-config', path('openssl.cnf'), '-extensions', 'ca_ext');
    root = new X509Certificate(readFileSync(path('ca.pem')));
  }
  let reuse = false;
  if (existsSync(path('server.pem')) && existsSync(path('server-key.pem'))) {
    try {
      const leaf = new X509Certificate(readFileSync(path('server.pem')));
      reuse = !leaf.ca && leaf.verify(root.publicKey) && leaf.checkPrivateKey(createPrivateKey(readFileSync(path('server-key.pem'))))
        && Date.parse(leaf.validTo) > Date.now() + 24 * 60 * 60 * 1000 && addresses.every(address => Boolean(leaf.checkIP(address)));
    } catch { /* Reissue only the server certificate; keep the trusted root. */ }
  }
  if (!reuse) {
    openssl('genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', path('server-key.pem'));
    openssl('req', '-new', '-key', path('server-key.pem'), '-out', path('server.csr'), '-config', path('openssl.cnf'), '-subj', '/CN=Field Guard Local Test');
    openssl('x509', '-req', '-in', path('server.csr'), '-CA', path('ca.pem'), '-CAkey', path('ca-key.pem'), '-set_serial', `0x${randomBytes(16).toString('hex')}`, '-out', path('server.pem'), '-days', '7', '-sha256', '-extfile', path('openssl.cnf'), '-extensions', 'server_ext');
  }
  const ca = readFileSync(path('ca.pem'));
  return {
    ca, certificate: root.raw, fingerprint: root.fingerprint256, expires: root.validTo,
    key: readFileSync(path('server-key.pem')), cert: Buffer.concat([readFileSync(path('server.pem')), ca]),
  };
}

export function certificateProfile(certificate: Buffer, fingerprint: string) {
  const identifier = `dev.hybridtech.fieldguard.local.${fingerprint.replaceAll(':', '').slice(0, 16).toLowerCase()}`;
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>PayloadType</key><string>Configuration</string><key>PayloadVersion</key><integer>1</integer>
<key>PayloadIdentifier</key><string>${identifier}</string><key>PayloadUUID</key><string>${randomUUID()}</string>
<key>PayloadDisplayName</key><string>Field Guard Local Test CA</string>
<key>PayloadDescription</key><string>같은 Wi-Fi의 Field Guard 개발 서버를 시험하기 위한 인증서입니다. 시험 후 설정에서 제거할 수 있습니다.</string>
<key>PayloadRemovalDisallowed</key><false/>
<key>PayloadContent</key><array><dict>
<key>PayloadType</key><string>com.apple.security.root</string><key>PayloadVersion</key><integer>1</integer>
<key>PayloadIdentifier</key><string>${identifier}.certificate</string><key>PayloadUUID</key><string>${randomUUID()}</string>
<key>PayloadDisplayName</key><string>Field Guard Local Test CA</string>
<key>PayloadCertificateFileName</key><string>field-guard-local-ca.cer</string>
<key>PayloadContent</key><data>${certificate.toString('base64')}</data>
</dict></array></dict></plist>`;
}

export function installerPage(address: string, fingerprint: string) {
  if (!privateIPv4(address)) throw new Error('사설 IPv4 주소가 필요해요.');
  const app = `https://${address}:4174/`;
  return `<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Field Guard · iPhone 테스트 연결</title>
<style>body{margin:0;background:#f7f9fc;color:#191f28;font:16px/1.8 system-ui,sans-serif}main{max-width:650px;margin:auto;padding:28px 20px}section{padding:24px;background:white;border:1px solid #e8edf3;border-radius:20px;margin:18px 0}h1{font-size:28px;line-height:1.4}h2{font-size:20px}a{color:#1769ce;overflow-wrap:anywhere}.button{display:block;text-align:center;padding:13px;background:#3182f6;color:white;border-radius:12px;text-decoration:none;font-weight:700}li{margin:14px 0}small{display:block;color:#6b7684;overflow-wrap:anywhere}.label{color:#3182f6;font-size:12px;font-weight:700}</style>
<main><span class="label">HYBRIDTECH · LOCAL TEST</span><h1>아이폰에서 안전하게<br>Field Guard를 열어요</h1><p>PC와 같은 Wi-Fi에서 한 번만 연결을 준비해 주세요.</p>
<section><h2>1. 개발용 인증서 다운로드</h2><p>이 PC에서 만든 <strong>Field Guard Local Test CA</strong>를 다운로드해 주세요. 프로파일에는 이 인증서만 들어 있어요.</p><a class="button" href="/field-guard.mobileconfig">개발용 인증서 다운로드</a></section>
<section><h2>2. 아이폰 설정에서 설치</h2><ol><li><strong>설정 → 프로파일이 다운로드됨 → 설치</strong><br>또는 일반 → VPN 및 기기 관리에서 선택해 주세요.</li><li><strong>설정 → 일반 → 정보 → 인증서 신뢰 설정</strong>에서 <strong>Field Guard Local Test CA</strong>의 전체 신뢰를 켜 주세요.</li></ol><p>아이폰 암호 입력과 설치·신뢰 설정은 직접 진행해 주세요.</p></section>
<section><h2>3. HTTPS 테스트 화면 열기</h2><a class="button" href="${app}">Field Guard 열기</a><p><a href="${app}">${app}</a></p><p>Safari 공유 → 홈 화면에 추가로 설치한 뒤 문서·작업 요청·AI를 시험해 주세요. AI 지원 여부는 아이폰과 Safari 버전에 따라 확인해야 해요.</p></section>
<small>이 인증서를 신뢰하면 이 PC가 발급한 서버 인증서를 아이폰이 신뢰합니다. 테스트가 끝나면 설정 → 일반 → VPN 및 기기 관리에서 이 프로파일을 제거할 수 있어요.</small><small>인증서 SHA-256: ${fingerprint}</small></main></html>`;
}

export function installerHandler(address: string, certificate: Buffer, fingerprint: string) {
  const page = installerPage(address, fingerprint);
  const profile = certificateProfile(certificate, fingerprint);
  return (request: IncomingMessage, response: ServerResponse) => {
    response.setHeader('Cache-Control', 'no-store');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    response.setHeader('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
    let pathname: string;
    try { pathname = new URL(request.url ?? '/', 'http://localhost').pathname; }
    catch { response.statusCode = 400; response.end(); return; }
    if (request.method !== 'GET' || !['/', '/field-guard.mobileconfig'].includes(pathname)) { response.statusCode = 404; response.end(); return; }
    if (pathname === '/') { response.setHeader('Content-Type', 'text/html; charset=utf-8'); response.end(page); return; }
    response.setHeader('Content-Type', 'application/x-apple-aspen-config');
    response.setHeader('Content-Disposition', 'attachment; filename="field-guard.mobileconfig"');
    response.end(profile);
  };
}
