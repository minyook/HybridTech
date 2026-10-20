import { build } from 'vite';
import { fileURLToPath } from 'node:url';
import { readFile } from 'node:fs/promises';
import path from 'node:path';

// The public Engine API only accepts sequential streams. Its 0.18.0 bridge
// caches skipped/preserved bytes in JS as well as copying them to WASM. Local
// immutable files can fulfill those reads on demand instead. Scope this bridge
// to the generated worker; never modify node_modules or the standard fallback.
const sdkEntry=fileURLToPath(import.meta.resolve('@litert-lm/core'));
const sdkDirectory=path.dirname(sdkEntry);
const sdkPackage=JSON.parse(await readFile(path.join(sdkDirectory,'../package.json'),'utf8'));
if(sdkPackage.version!=='0.18.0')throw new Error('Review the local file reader bridge before changing LiteRT-LM 0.18.0');
const readerSource=await readFile(path.join(sdkDirectory,'readable_stream_data_stream_wrapper.js'),'utf8');
if(!['readAndDiscard','readAndPreserve','discard(offset, count)'].every(part=>readerSource.includes(part)))throw new Error('LiteRT-LM stream bridge contract changed');
let redirected=false;

// LiteRT's official loader uses importScripts, so bundle a classic worker for
// both dev and production. The PWA shell also precaches this generated script.
await build({
  configFile:false,publicDir:false,logLevel:'warn',
  plugins:[{name:'fieldguard-local-model-reader',enforce:'pre',resolveId(source,importer){
    if(source==='./readable_stream_data_stream_wrapper.js'&&importer&&path.resolve(path.dirname(importer))===path.resolve(sdkDirectory)){
      redirected=true;return fileURLToPath(new URL('../src/ai/sdk-stream-reader.ts',import.meta.url));
    }
  }}],
  build:{outDir:fileURLToPath(new URL('../public/',import.meta.url)),emptyOutDir:false,
    lib:{entry:fileURLToPath(new URL('../src/ai/engine.worker.ts',import.meta.url)),name:'FieldGuardAI',formats:['iife'],fileName:()=> 'ai-worker.js'},
    target:'es2022',minify:true},
});
if(!redirected)throw new Error('Local model reader bridge was not included in the AI worker');
console.log('Prepared background AI worker.');
