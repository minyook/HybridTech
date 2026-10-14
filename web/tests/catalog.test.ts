import { expect, it, vi } from 'vitest';
import { downloadGemma, GEMMA4_E2B } from '../src/ai/catalog';

function dependencies(response=new Response(new Uint8Array([1,2,3]))) {
  return {fetchFile:vi.fn(async()=>response) as ReturnType<typeof vi.fn<typeof fetch>>,install:vi.fn(async()=>{}),estimate:async()=>({quota:10e9,usage:0})};
}
it('한 번의 다운로드 호출이 고정 버전과 검증 정보를 스트리밍 설치에 전달한다',async()=>{
  const deps=dependencies();const signal=new AbortController().signal;
  await downloadGemma(signal,vi.fn(),deps);
  expect(deps.fetchFile).toHaveBeenCalledWith(GEMMA4_E2B.url,{signal,credentials:'omit'});
  expect(GEMMA4_E2B.url).not.toContain('/main/');
  expect(deps.install).toHaveBeenCalledWith(expect.any(ReadableStream),expect.objectContaining({name:'gemma-4-E2B-it-web.litertlm',size:2008432640,sha256:GEMMA4_E2B.sha256,signal}));
});
it('저장 공간 부족이면 모델을 받기 시작하지 않는다',async()=>{
  const deps={...dependencies(),estimate:async()=>({quota:1e9,usage:0})};
  await expect(downloadGemma(new AbortController().signal,vi.fn(),deps)).rejects.toThrow('저장할 공간');
  expect(deps.fetchFile).not.toHaveBeenCalled();expect(deps.install).not.toHaveBeenCalled();
});
it('HTTP 오류를 모델 설치 성공으로 표시하지 않는다',async()=>{
  const deps=dependencies(new Response('unavailable',{status:403}));
  await expect(downloadGemma(new AbortController().signal,vi.fn(),deps)).rejects.toThrow('403');
  expect(deps.install).not.toHaveBeenCalled();
});
it('서버 파일 크기가 다르면 스트림을 취소하고 설치하지 않는다',async()=>{
  const deps=dependencies(new Response('bad',{headers:{'Content-Length':'3'}}));
  await expect(downloadGemma(new AbortController().signal,vi.fn(),deps)).rejects.toThrow('파일의 크기');
  expect(deps.install).not.toHaveBeenCalled();
});
it('미리 취소된 요청은 다운로드하지 않는다',async()=>{
  const deps=dependencies();const controller=new AbortController();controller.abort();
  await expect(downloadGemma(controller.signal,vi.fn(),deps)).rejects.toThrow();expect(deps.fetchFile).not.toHaveBeenCalled();
});
it('브라우저 fetch를 잘못된 this 객체로 호출하지 않는다',async()=>{
  vi.stubGlobal('navigator',{storage:{estimate:async()=>({quota:10e9,usage:0})}});
  vi.stubGlobal('fetch',function(this:unknown){expect(this).toBe(globalThis);return Promise.resolve(new Response('denied',{status:403}));});
  try{await expect(downloadGemma(new AbortController().signal,vi.fn())).rejects.toThrow('403');}
  finally{vi.unstubAllGlobals();}
});
