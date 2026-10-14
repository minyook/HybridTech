import { cp, mkdir, readdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { deflateSync } from 'node:zlib';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const publicDir = path.join(root, 'public');
const androidAssets = path.join(root, '../app/src/main/assets');
await mkdir(publicDir, { recursive: true });
for (const directory of ['knowledge', 'source-pdfs']) {
  await cp(path.join(androidAssets, directory), path.join(publicDir, directory), { recursive: true });
}
const pdfHashes = { 'safety-guide-2022.pdf':'3d634673ef6a90fb3ece60fec7ef21d3d6654277aff9edef4843a7bd187eab88', 'checklist-2026.pdf':'3114a041b2a97d71622638044d198666ab0efcd5e4c49f40e14827036dc78998' };
for (const [name,hash] of Object.entries(pdfHashes)) {
  if (createHash('sha256').update(await readFile(path.join(publicDir,'source-pdfs',name))).digest('hex') !== hash) throw new Error(`PDF source edition changed: ${name}. Review its text and catalog before updating the expected hash.`);
}
const runtimeRoot = path.join(root, 'node_modules/@litert-lm/core');
const sdk = JSON.parse(await readFile(path.join(runtimeRoot, 'package.json'), 'utf8'));
async function filesIn(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  return (await Promise.all(entries.map(entry => entry.isDirectory() ? filesIn(path.join(directory, entry.name)) : [path.join(directory, entry.name)]))).flat();
}
await mkdir(path.join(publicDir, 'runtime'), { recursive: true });
const runtimeFiles = (await filesIn(runtimeRoot)).filter(file => /\.wasm$/.test(file) || /(?:wasm|feature_detect).*\.(?:m?js)$/.test(path.basename(file)));
if (!runtimeFiles.some(file => file.endsWith('.wasm'))) throw new Error('LiteRT-LM WASM assets are missing');
for (const file of runtimeFiles) await cp(file, path.join(publicDir, 'runtime', path.basename(file)));
await mkdir(path.join(publicDir, 'pdfjs'), { recursive: true });
for (const directory of ['cmaps', 'standard_fonts', 'wasm']) {
  await cp(path.join(root, 'node_modules/pdfjs-dist', directory), path.join(publicDir, 'pdfjs', directory), { recursive: true });
}

const assets = [];
for (const [directory, group] of [['knowledge', 'knowledge'], ['source-pdfs', 'pdf'], ['pdfjs', 'pdf'], ['runtime', 'runtime']]) {
  for (const file of await filesIn(path.join(publicDir, directory))) {
    const content = await readFile(file);
    assets.push({ path: path.relative(publicDir, file).replaceAll('\\', '/'), group, size: content.length, sha256: createHash('sha256').update(content).digest('hex') });
  }
}
assets.sort((a, b) => a.path.localeCompare(b.path));
const version = createHash('sha256').update(JSON.stringify(assets)).digest('hex').slice(0, 16);
await writeFile(path.join(publicDir, 'asset-manifest.json'), JSON.stringify({ version, sdkVersion: sdk.version, assets }, null, 2));

// Render our vector mark to PNG without requiring a host graphics application.
function crc32(buffer) {
  let value = -1;
  for (const byte of buffer) { value ^= byte; for (let bit = 0; bit < 8; bit++) value = (value >>> 1) ^ (0xedb88320 & -(value & 1)); }
  return (value ^ -1) >>> 0;
}
function pngChunk(type, data) {
  const body = Buffer.concat([Buffer.from(type), data]);
  const length = Buffer.alloc(4); length.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}
function distance(x, y, a, b) {
  const t = Math.max(0, Math.min(1, ((x-a[0])*(b[0]-a[0])+(y-a[1])*(b[1]-a[1])) / ((b[0]-a[0])**2+(b[1]-a[1])**2)));
  return Math.hypot(x-a[0]-t*(b[0]-a[0]), y-a[1]-t*(b[1]-a[1]));
}
for (const [name, size] of [['icon-192.png', 192], ['icon-512.png', 512], ['apple-touch-icon.png', 180]]) {
  const raw = Buffer.alloc((size * 4 + 1) * size);
  const strokes = [[[256,110],[369,155],[369,247],[354,308],[316,359],[256,401],[196,359],[158,308],[143,247],[143,155],[256,110]], [[205,254],[240,289],[310,213]]];
  for (let y=0; y<size; y++) for (let x=0; x<size; x++) {
    const white = strokes.some(points => points.slice(1).some((point,i) => distance(x/size*512,y/size*512,points[i],point)<12));
    const offset = y*(size*4+1)+1+x*4;
    raw.set(white ? [255,255,255,255] : [49,130,246,255], offset);
  }
  const header = Buffer.alloc(13); header.writeUInt32BE(size,0); header.writeUInt32BE(size,4); header[8]=8; header[9]=6;
  await writeFile(path.join(publicDir,'icons',name), Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),pngChunk('IHDR',header),pngChunk('IDAT',deflateSync(raw)),pngChunk('IEND',Buffer.alloc(0))]));
}
console.log(`Prepared 87 source pages, PDFs, PDF.js and LiteRT-LM ${sdk.version}. Assets: ${version}`);
