import type { ReactNode } from 'react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { nativeDestination } from './workflow/native';

const paths: Record<string, ReactNode> = {
  home: <><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z"/></>,
  chat: <><path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-2 2V11.5A8.5 8.5 0 0 1 10.5 3h2a8.5 8.5 0 0 1 8.5 8.5Z"/><path d="M7 9h9M7 13h6"/></>,
  book: <><path d="M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-3-1-6-1-9 1Zm0 0v15"/></>,
  check: <><path d="m8 12 3 3 5-6"/><rect x="4" y="3" width="16" height="18" rx="3"/></>,
  settings: <><path d="m9 3-.5 3-2 1-2.7-1-2 3.5L4 12l-2.2 2.5 2 3.5 2.7-1 2 1 .5 3h6l.5-3 2-1 2.7 1 2-3.5L20 12l2.2-2.5-2-3.5-2.7 1-2-1L15 3Z"/><circle cx="12" cy="12" r="3"/></>,
  history: <><path d="M3 11a9 9 0 1 1 2 7M3 4v7h7m2-4v5l3 2"/></>,
  arrow: <path d="M5 12h14m-6-6 6 6-6 6"/>,
  chevron: <path d="m9 5 7 7-7 7"/>,
  shield: <><path d="m12 2 9 4v6c0 5-5 9-9 11-4-2-9-6-9-11V6Z"/><path d="m8 12 3 3 5-6"/></>,
  download: <><path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/></>,
  cpu: <><rect x="6" y="6" width="12" height="12" rx="3"/><path d="M9 2v4m6-4v4M9 18v4m6-4v4M2 9h4m-4 6h4m12-6h4m-4 6h4"/><rect x="10" y="10" width="4" height="4"/></>,
  search: <><circle cx="10" cy="10" r="7"/><path d="m15 15 6 6"/></>,
  info: <><circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.1"/></>,
  plus: <path d="M12 4v16M4 12h16"/>,
  trash: <><path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/></>,
  close: <path d="m5 5 14 14M5 19 19 5"/>,
  wifi: <><path d="M2 8a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0m-11 4a6 6 0 0 1 8 0"/><circle cx="12" cy="20" r=".5"/></>,
};
export function Icon({ name, className = '' }: { name: string; className?: string }) {
  return <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] ?? paths.info}</svg>;
}
export const go = (path: string) => { const native = nativeDestination(path); if (native) location.href = native; else location.hash = path; };
export const formatDate = (date: string) => new Intl.DateTimeFormat('ko-KR',{month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(date));
export const bytes = (value: number) => value >= 1024**3 ? `${(value/1024**3).toFixed(2)} GB` : `${(value/1024**2).toFixed(1)} MB`;
export function PageHead({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) {
  return <div className="page-head"><div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h1>{title}</h1>{description && <p className="lead">{description}</p>}</div>{action}</div>;
}
export function Notice({ children, kind = '' }: { children: ReactNode; kind?: string }) {
  return <div className={`notice ${kind}`}><Icon name="info"/><div>{children}</div></div>;
}
// Source text and generated content are always escaped by React; no raw HTML is accepted.
export function TextContent({ text }: { text: string }) {
  return <div className="text-content markdown"><Markdown remarkPlugins={[[remarkGfm,{singleTilde:false}]]} skipHtml components={{
    a: ({children,href}) => <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>,
    img: ({alt}) => <span className="image-description">{alt ? `[이미지: ${alt}]` : '[이미지]'}</span>,
    table: ({children}) => <div className="markdown-table"><table>{children}</table></div>,
  }}>{text}</Markdown></div>;
}
