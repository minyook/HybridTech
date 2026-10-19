export interface AIFeatures {
  secure: boolean;
  webgpu: boolean;
  worker: boolean;
  appleMobile: boolean;
}
export interface AISupport {
  available: boolean;
  reason?: 'https' | 'webgpu' | 'worker';
  title: string;
  message: string;
  features: AIFeatures;
}

export function browserAIFeatures(): AIFeatures {
  const browser = typeof navigator === 'undefined' ? undefined : navigator;
  return {
    secure: globalThis.isSecureContext === true,
    webgpu: Boolean(browser && 'gpu' in browser && browser.gpu),
    worker: typeof Worker === 'function',
    appleMobile: Boolean(browser && (/iPhone|iPad|iPod/.test(browser.userAgent ?? '') || (browser.platform === 'MacIntel' && browser.maxTouchPoints > 1))),
  };
}

export function getAISupport(features = browserAIFeatures()): AISupport {
  if (!features.secure) return {
    available: false, reason: 'https', features, title: 'AI에 필요한 보안 연결을 확인해 주세요',
    message: '이 페이지가 신뢰된 HTTPS 연결로 열리지 않았어요. 개발용 인증서의 설치·전체 신뢰를 확인한 뒤 HTTPS 앱 주소로 다시 열어 주세요.',
  };
  if (!features.webgpu) return {
    available: false, reason: 'webgpu', features, title: '이 브라우저에서 WebGPU를 사용할 수 없어요',
    message: features.appleMobile
      ? 'iPhone·iPad의 WebGPU 정식 지원은 iOS·iPadOS 26부터예요. 설정 → 일반 → 정보에서 버전을 확인하고, 소프트웨어 업데이트에서 지원되는 최신 버전으로 업데이트한 뒤 Safari로 다시 열어 주세요. 현재 환경에서는 모델을 다운로드해도 기기 내 AI를 실행할 수 없어요.'
      : '현재 브라우저가 AI 실행에 필요한 그래픽 기능(WebGPU)을 제공하지 않아요. WebGPU를 지원하는 최신 브라우저와 기기에서 다시 열어 주세요. 현재 환경에서는 모델을 다운로드해도 기기 내 AI를 실행할 수 없어요.',
  };
  if (!features.worker) return {
    available: false, reason: 'worker', features, title: 'AI의 백그라운드 실행을 사용할 수 없어요',
    message: '현재 브라우저가 AI를 별도로 실행하는 기능을 제공하지 않아요. 앱 내부 브라우저 대신 최신 Safari·Chrome·Edge에서 직접 열어 주세요.',
  };
  return {
    available: true, features, title: 'AI의 기본 실행 조건을 확인했어요',
    message: '모델을 준비한 뒤 실제 기기의 메모리와 GPU에서 실행 여부를 확인해요.',
  };
}
