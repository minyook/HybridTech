/* 소스 안내
 * 브라우저 React 진입점. 앱 루트와 스타일/오프라인 등록을 연결한다. Android Activity 진입점과 별개인 보존 웹 코드다.
 * 연결 모듈: ./App, ./ErrorBoundary, ./offline/assets.
 */
import { createRoot } from 'react-dom/client';
import App from './App';
import './qr.css';
import './voice.css';
import { ErrorBoundary } from './ErrorBoundary';
import { registerOffline } from './offline/assets';
import './style.css';
import './markdown.css';
import './responsive.css';
import './workflow.css';
import './ai.css';
import './chat.css';

createRoot(document.getElementById('root')!).render(<ErrorBoundary><App/></ErrorBoundary>);
void registerOffline().catch(error => { console.error('오프라인 화면 준비 실패:',error); });
