import { afterEach, expect, it, vi } from 'vitest';
import { browserAIFeatures, getAISupport, type AIFeatures } from '../src/ai/capabilities';

const supported: AIFeatures={secure:true,webgpu:true,worker:true,appleMobile:false};
afterEach(()=>vi.unstubAllGlobals());
it('HTTPS 신뢰 문제를 WebGPU 미지원과 구분한다',()=>{
  const support=getAISupport({...supported,secure:false,webgpu:false,appleMobile:true});
  expect(support.reason).toBe('https');expect(support.message).toContain('인증서');expect(support.message).not.toContain('iOS·iPadOS 26');
});
it('아이폰의 WebGPU 미지원에는 iOS 업데이트 절차를 안내한다',()=>{
  const support=getAISupport({...supported,webgpu:false,appleMobile:true});
  expect(support.available).toBe(false);expect(support.reason).toBe('webgpu');expect(support.message).toContain('iOS·iPadOS 26');expect(support.message).toContain('소프트웨어 업데이트');
});
it('데스크톱 WebGPU 미지원에는 iOS 업데이트를 안내하지 않는다',()=>{
  const support=getAISupport({...supported,webgpu:false});
  expect(support.reason).toBe('webgpu');expect(support.message).toContain('최신 브라우저');expect(support.message).not.toContain('iOS');
});
it('백그라운드 실행 불가도 별도로 설명한다',()=>{
  expect(getAISupport({...supported,worker:false}).reason).toBe('worker');
});
it('iOS 사용자 에이전트의 버전 대신 실제 브라우저 기능으로 판단한다',()=>{
  vi.stubGlobal('isSecureContext',true);vi.stubGlobal('Worker',class{});
  vi.stubGlobal('navigator',{gpu:{},userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) Version/26.0 Mobile Safari/604.1'});
  expect(browserAIFeatures().appleMobile).toBe(true);expect(getAISupport().available).toBe(true);
  vi.stubGlobal('navigator',{userAgent:'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6_2 like Mac OS X) Mobile Safari/604.1'});
  expect(getAISupport().reason).toBe('webgpu');
});
it('iPad의 데스크톱 모드도 실제 WebGPU 지원 여부로 판단한다',()=>{
  vi.stubGlobal('isSecureContext',true);vi.stubGlobal('Worker',class{});
  vi.stubGlobal('navigator',{platform:'MacIntel',maxTouchPoints:5,userAgent:'Macintosh'});
  expect(browserAIFeatures().appleMobile).toBe(true);expect(getAISupport().reason).toBe('webgpu');
});
