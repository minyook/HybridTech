import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { preview } from 'vite';
import { installerHandler, localAddresses, prepareCertificates } from '../server/iphone-https.ts';

const root = fileURLToPath(new URL('../', import.meta.url));
const addresses = localAddresses();
const address = process.env.FIELDGUARD_TEST_HOST ?? addresses[0];
if (!address || !addresses.includes(address)) throw new Error('PC를 Wi-Fi에 연결한 뒤 다시 실행해 주세요. FIELDGUARD_TEST_HOST는 이 PC의 사설 IP만 사용할 수 있어요.');
const certificates = prepareCertificates(fileURLToPath(new URL('../.local/iphone-https/', import.meta.url)), addresses);
const app = await preview({ root, configFile: fileURLToPath(new URL('../vite.config.ts', import.meta.url)), preview: { host: address, port: 4174, strictPort: true, https: { key: certificates.key, cert: certificates.cert } } });
const installer = createServer(installerHandler(address, certificates.certificate, certificates.fingerprint));
try {
  await new Promise((resolve, reject) => { installer.once('error', reject); installer.listen(4172, address, resolve); });
} catch (error) { app.httpServer.closeAllConnections(); app.httpServer.close(); throw error; }
console.log(`\niPhone Safari setup: http://${address}:4172/\nAfter installing and trusting the profile: https://${address}:4174/\nCertificate: Field Guard Local Test CA\nSHA-256: ${certificates.fingerprint}\nStop: Ctrl+C\n`);
let closing = false;
function stop() {
  if (closing) return; closing = true;
  installer.closeAllConnections(); installer.close();
  app.httpServer.closeAllConnections(); app.httpServer.close();
}
process.on('SIGINT', stop); process.on('SIGTERM', stop);
