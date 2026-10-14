import { build } from 'vite';
import { fileURLToPath } from 'node:url';

// LiteRT's official loader uses importScripts, so bundle a classic worker for
// both dev and production. The PWA shell also precaches this generated script.
await build({
  configFile:false,publicDir:false,logLevel:'warn',
  build:{outDir:fileURLToPath(new URL('../public/',import.meta.url)),emptyOutDir:false,
    lib:{entry:fileURLToPath(new URL('../src/ai/engine.worker.ts',import.meta.url)),name:'FieldGuardAI',formats:['iife'],fileName:()=> 'ai-worker.js'},
    target:'es2022',minify:true},
});
console.log('Prepared background AI worker.');
