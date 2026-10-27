import { readdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const dist = fileURLToPath(new URL('../dist/', import.meta.url));
async function walk(directory) {
  return (await Promise.all((await readdir(directory,{withFileTypes:true})).map(entry => entry.isDirectory() ? walk(path.join(directory,entry.name)) : [path.join(directory,entry.name)]))).flat();
}
const files = (await walk(dist)).filter(file => !/[/\\](runtime|voice-runtime|source-pdfs|pdfjs|knowledge)[/\\]/.test(file) && !/[/\\]ort-.*\.wasm$/.test(file) && !file.endsWith('sw.js'));
const digest = createHash('sha256');
for (const file of files) digest.update(await readFile(file));
const template = await readFile(new URL('./sw-template.js',import.meta.url),'utf8');
digest.update(template);
const version = digest.digest('hex').slice(0,16);
const urls = files.map(file => './'+path.relative(dist,file).replaceAll('\\','/'));
await writeFile(path.join(dist,'sw.js'), template.replace('__VERSION__',JSON.stringify(version)).replace('__PRECACHE__',JSON.stringify(urls)));
console.log(`PWA shell ${version}: ${urls.length} files. Large assets are explicitly prepared in Settings.`);
