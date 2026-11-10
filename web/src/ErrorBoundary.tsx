/* 소스 안내
 * React 렌더 오류를 복구 화면으로 격리한다. 모델 native 프로세스 종료를 직접 처리하는 런타임 가드는 아니다.
 */
import { Component, type ReactNode } from 'react';

export class ErrorBoundary extends Component<{children:ReactNode},{failed:boolean}> {
  state={failed:false};
  static getDerivedStateFromError() { return {failed:true}; }
  render() {
    return this.state.failed ? <main><div className="card"><h1>화면을 다시 열어 주세요</h1><p>처리 중 문제가 생겼어요. 저장된 기록은 이 기기에 보관되어 있어요.</p><button className="btn primary" onClick={() => location.reload()}>다시 열기</button></div></main> : this.props.children;
  }
}
