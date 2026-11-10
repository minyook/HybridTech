/* 소스 안내
 * 같은 origin의 여러 화면이 GPU 엔진을 중복 적재하지 않도록 브라우저 잠금을 획득/해제한다.
 */
// Keep Safari and the home-screen app from allocating separate GPU engines for
// the same origin. The browser also releases this lock if the page is killed.
export async function acquireEngineLease():Promise<(()=>void)|undefined>{
  if(typeof navigator==='undefined'||!navigator.locks)return ()=>{};
  return new Promise((resolve,reject)=>{
    void navigator.locks.request('fieldguard-ai-engine',{mode:'exclusive',ifAvailable:true},async lock=>{
      if(!lock){resolve(undefined);return;}
      await new Promise<void>(release=>resolve(release));
    }).catch(reject);
  });
}
