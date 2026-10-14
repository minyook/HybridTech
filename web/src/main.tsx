import { createRoot } from 'react-dom/client';
import App from './App';
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
