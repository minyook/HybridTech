import { useEffect, useRef, useState } from 'react';
import { getDocument, GlobalWorkerOptions, type PDFDocumentProxy } from 'pdfjs-dist/legacy/build/pdf.mjs';
import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url';
import type { Chunk } from '../types';
import { assetUrl } from '../offline/assets';
import { go, Icon, PageHead } from '../ui';

GlobalWorkerOptions.workerSrc = workerUrl;
export const pdfFiles: Record<string,string> = { confined_space_safety_guide_2022: 'safety-guide-2022.pdf', confined_space_checklist_2026: 'checklist-2026.pdf' };
export default function PdfPage({ documentId, page, chunks }: { documentId: string; page: number; chunks: Chunk[] }) {
  const [mode,setMode] = useState('pdf'); const [pdf,setPdf] = useState<PDFDocumentProxy>();
  const [error,setError] = useState(''); const [loading,setLoading] = useState(true); const [zoom,setZoom] = useState(1);
  const canvas = useRef<HTMLCanvasElement>(null); const container = useRef<HTMLDivElement>(null);
  const pages = chunks.filter(chunk => chunk.documentId === documentId); const source = pages.find(chunk => chunk.pageNumber === page);
  const [width,setWidth] = useState(700);
  useEffect(() => {
    if (!container.current) return;
    const observer = new ResizeObserver(entries => setWidth(entries[0].contentRect.width));
    observer.observe(container.current); return () => observer.disconnect();
  },[]);
  useEffect(() => {
    if (!pdfFiles[documentId]) return;
    setLoading(true); setError(''); setPdf(undefined);
    const task = getDocument({ url: assetUrl(`source-pdfs/${pdfFiles[documentId]}`), cMapUrl: assetUrl('pdfjs/cmaps/'), cMapPacked: true, standardFontDataUrl: assetUrl('pdfjs/standard_fonts/'), wasmUrl: assetUrl('pdfjs/wasm/'), disableRange: true, disableStream: true });
    let active = true;
    task.promise.then(value => { if (active) setPdf(value); }).catch(() => { if (active) { setError('PDF를 열 수 없어요. 설정에서 PDF를 준비한 후 다시 열어 주세요.'); setLoading(false); } });
    return () => { active = false; void task.destroy(); };
  },[documentId]);
  useEffect(() => {
    if (!pdf || mode !== 'pdf' || !canvas.current) return;
    let active = true; let cancel: (() => void) | undefined;
    setLoading(true); setError('');
    pdf.getPage(page).then(pdfPage => {
      if (!active || !canvas.current) return;
      const natural = pdfPage.getViewport({scale:1}); const scale = Math.max(240,width-32)/natural.width*zoom;
      const viewport = pdfPage.getViewport({scale}); const ratio = Math.min(devicePixelRatio || 1,2);
      canvas.current.width = Math.floor(viewport.width*ratio); canvas.current.height = Math.floor(viewport.height*ratio);
      canvas.current.style.width = `${viewport.width}px`; canvas.current.style.height = `${viewport.height}px`;
      const task = pdfPage.render({canvas:canvas.current,viewport,transform:[ratio,0,0,ratio,0,0]}); cancel = () => task.cancel();
      return task.promise;
    }).then(() => { if (active) setLoading(false); }).catch(error => { if (active && error.name !== 'RenderingCancelledException') { setError('이 쪽을 표시하지 못했어요. 추출 텍스트를 확인하거나 다시 시도해 주세요.'); setLoading(false); } });
    return () => { active = false; cancel?.(); };
  },[pdf,page,width,zoom,mode]);
  if (!source) return <><PageHead title="문서를 찾지 못했어요"/><button className="btn primary" onClick={() => go('/library')}>보관함으로</button></>;
  return <>
    <button className="back-link" onClick={() => history.length > 1 ? history.back() : go('/library')}>← 돌아가기</button>
    <PageHead eyebrow="SOURCE DOCUMENT" title="답변의 근거를 확인해요" description={source.documentTitle}/>
    <div className="document-toolbar"><div className="segmented" aria-label="문서 보기 방식">{[['pdf','PDF 원본'],['text','추출 텍스트']].map(([id,label]) => <button key={id} className={mode === id ? 'selected' : ''} onClick={() => setMode(id)} aria-pressed={mode===id}>{label}</button>)}</div><div className="pager"><button className="icon-button" aria-label="이전 쪽" disabled={page<=1} onClick={() => go(`/sources/${documentId}/${page-1}`)}>←</button><span>{page} / {pages.length}쪽</span><button className="icon-button" aria-label="다음 쪽" disabled={page>=pages.length} onClick={() => go(`/sources/${documentId}/${page+1}`)}>→</button></div></div>
    <div className="pdf-container" ref={container}>{mode === 'pdf' ? <><div className="pdf-tools"><span>원본 PDF · {page}쪽</span><label>배율 <select value={zoom} onChange={event => setZoom(Number(event.target.value))}><option value={1}>화면에 맞춤</option><option value={1.5}>150%</option><option value={2}>200%</option></select></label></div>{loading && <p role="status">PDF를 표시하고 있어요…</p>}{error && <div role="alert" className="notice warning">{error}</div>}<div className="canvas-scroll"><canvas ref={canvas} aria-label={`${source.documentTitle} ${page}쪽 원본 PDF`} role="img"/></div></> : <article className="source-text"><span className="badge blue">검색에 사용한 원문</span><h2>{source.heading}</h2><div className="text-content"><p>{source.body}</p></div></article>}</div>
    <p className="caption"><Icon name="info"/>PDF의 그림과 표는 원본 보기에서 확인할 수 있어요.</p>
  </>;
}
