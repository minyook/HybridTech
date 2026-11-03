// Read-only ADB export for the local evaluation viewer. No device data/settings writes.
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {writeFile,rename} from 'node:fs/promises';
import {resolve} from 'node:path';
const run=promisify(execFile);
const [adb,serial,directory,label='after']=process.argv.slice(2);
if(!adb||!serial||!directory)throw Error('Usage: node watch-device-evaluation.mjs <adb> <serial> <result-directory>');
const deadline=Date.now()+30*60*1000;
let last=-1;
while(Date.now()<deadline){
  try{
    const {stdout}=await run(adb,['-s',serial,'exec-out','cat',`/sdcard/Android/data/com.minyook.sllm2/files/rag-checks/device-${label}.json`],{encoding:'utf8',maxBuffer:8*1024*1024,windowsHide:true});
    const data=JSON.parse(stdout);
    const temp=resolve(directory,'native-latency.json.part'),dest=resolve(directory,'native-latency.json');
    await writeFile(temp,JSON.stringify(data,null,2));
    for(let attempt=0;;attempt++)try{await rename(temp,dest);break;}catch(error){if(attempt===49)throw error;await new Promise(done=>setTimeout(done,20));}
    if(data.completed!==last){console.log(`S24 measured ${data.completed}/${data.total||150}; last=${data.results.at(-1)?.question}`);last=data.completed;}
    if(data.phase==='complete')break;
  }catch{/* The device publishes at question boundaries; tolerate partial/not-yet-created snapshots. */}
  await new Promise(done=>setTimeout(done,3000));
}
