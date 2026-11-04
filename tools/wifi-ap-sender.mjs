#!/usr/bin/env node
// Explicitly simulated TCP detectors for Field Guard experimental protocol v1.
import net from 'node:net';
import { parseArgs } from 'node:util';
const { values } = parseArgs({ options: {
  host: { type: 'string' }, port: { type: 'string', default: '8765' }, token: { type: 'string' },
  count: { type: 'string', default: '2' }, interval: { type: 'string', default: '1000' },
  seconds: { type: 'string', default: '30' }, help: { type: 'boolean', short: 'h' },
} });
if (values.help) {
  console.log('Join the phone AP from this PC first. Then:\nFIELDGUARD_AP_TOKEN=<code> node tools/wifi-ap-sender.mjs --host <AP-IP> --count 2 --seconds 30\nPowerShell: $env:FIELDGUARD_AP_TOKEN="<code>"\nOptional: --port 8765 --interval 1000 (100..60000 ms). Always sends test:true.\nThis tests transport only; values are simulated. Credentials are never printed.');
  process.exit(0);
}
const token = values.token ?? process.env.FIELDGUARD_AP_TOKEN;
const port = Number(values.port), count = Number(values.count), interval = Number(values.interval), seconds = Number(values.seconds);
if (!values.host || !token || !Number.isInteger(port) || port < 1 || port > 65535 || !Number.isInteger(count) || count < 1 || count > 16 || !Number.isInteger(interval) || interval < 100 || interval > 60000 || !Number.isInteger(seconds) || seconds < 1 || seconds > 3600) {
  console.error('Invalid arguments. Use --help.'); process.exit(1);
}
const deadline = performance.now() + seconds * 1000;
let accepted = 0, rejected = 0, errors = 0, connections = 0;
const ackTimes = [], sockets = new Set(), timers = new Set();
const later = (fn, ms) => { const timer = setTimeout(() => { timers.delete(timer); fn(); }, ms); timers.add(timer); return timer; };
let stopped = false;
function connect(index, attempt = 0) {
  if (stopped || performance.now() >= deadline) return;
  const id = `test-${String(index).padStart(2, '0')}`;
  const socket = net.createConnection({ host: values.host, port });
  sockets.add(socket); socket.setEncoding('utf8'); socket.setNoDelay(true); socket.setTimeout(15000);
  let buffer = '', seq = 0, welcomed = false, sendTimer;
  const pending = new Map();
  const send = value => socket.write(JSON.stringify(value) + '\n');
  const reading = () => {
    if (stopped || socket.destroyed || performance.now() >= deadline) return;
    const n = seq++; pending.set(n, performance.now());
    send({ type: 'reading', seq: n, sampleAgeMs: 0, o2Percent: 20 + index / 100, h2sPpm: 0, coPpm: index, lelPercent: 0, status: 'ok', alarm: 'none' });
    sendTimer = later(reading, interval);
  };
  socket.on('connect', () => send({ type: 'hello', protocol: 1, token, deviceId: id, name: `테스트 감지기 ${index}`, test: true, intervalMs: interval }));
  socket.on('data', data => {
    buffer += data.toString('utf8');
    if (Buffer.byteLength(buffer) > 65536) { errors++; socket.destroy(); return; }
    let end;
    while ((end = buffer.indexOf('\n')) >= 0) {
      const frame = buffer.slice(0, end); buffer = buffer.slice(end + 1);
      try {
        const value = JSON.parse(frame);
        if (value.type === 'welcome' && !welcomed) { welcomed = true; connections++; reading(); }
        else if (value.type === 'ping') send({ type: 'pong', id: value.id });
        else if (value.type === 'ack') {
          value.accepted ? accepted++ : rejected++;
          const began = pending.get(value.seq);
          if (began !== undefined) { ackTimes.push(performance.now() - began); pending.delete(value.seq); }
        } else if (value.type === 'error') { errors++; console.error(`${id}: server rejected frame (${value.message})`); socket.destroy(); }
      } catch { errors++; socket.destroy(); }
    }
  });
  socket.on('timeout', () => socket.destroy());
  socket.on('error', error => { errors++; console.error(`${id}: socket error ${error.code ?? 'unknown'}`); });
  socket.on('close', () => {
    sockets.delete(socket); clearTimeout(sendTimer); timers.delete(sendTimer);
    later(() => connect(index, welcomed ? 0 : attempt + 1), Math.min(5000, 500 * 2 ** Math.min(attempt, 4)));
  });
}
function finish() {
  if (stopped) return; stopped = true;
  timers.forEach(clearTimeout); sockets.forEach(s => s.destroy());
  ackTimes.sort((a, b) => a - b);
  const percentile = p => ackTimes.length ? Number(ackTimes[Math.ceil(ackTimes.length * p) - 1].toFixed(3)) : null;
  console.log(JSON.stringify({ simulated: true, connections, accepted, rejected, errors, ackRoundTripMs: { median: percentile(.5), p95: percentile(.95), max: ackTimes.length ? Number(ackTimes.at(-1).toFixed(3)) : null }, note: 'ACK RTT includes transport and processing; not one-way sensor latency.' }, null, 2));
  if (!accepted) process.exitCode = 1;
}
for (let i = 1; i <= count; i++) connect(i);
later(finish, seconds * 1000);
process.on('SIGINT', finish);
