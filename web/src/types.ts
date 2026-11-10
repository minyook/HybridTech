/* 소스 안내
 * 웹 문서·출처·대화 등 모듈 사이의 데이터 계약. 저장소·검색·화면이 같은 타입을 사용한다.
 */
export interface Chunk {
  id: string;
  documentId: string;
  documentTitle: string;
  sourceKind: string;
  pageNumber: number;
  chunkIndex: number;
  heading: string;
  body: string;
}
export interface Source { chunk: Chunk; score: number; evidenceOrders?:number[]; evidence?:import('./rag/evidence').EvidenceUnit[] }
export interface Chat {
  id: string; question: string; answer: string; citationIds: string[];
  mode: 'source' | 'ai' | 'cancelled'; createdAt: string; notice?: string;
  tokenUsage?:import('./ai/protocol').TokenUsage;
  includesEvidence?:boolean;
  answerFormat?:'integrated';
  suggestions?:{label:string;question:string}[];
  resolvedQuestion?:string;
}
export interface Checklist { id: string; checked: number[]; site: string; note: string; createdAt: string }
export interface Asset { path: string; size: number; sha256: string; group: 'knowledge' | 'pdf' | 'runtime' }
export interface AssetManifest { version: string; sdkVersion: string; assets: Asset[] }
export interface ModelVerification { version:1;sha256:string;size:number;lastModified:number;sampleSha256:string;verifiedAt:number }
export interface ModelRecord { name: string; size: number; sha256: string; installedAt: string; backend: 'opfs' | 'idb'; storageKey: string;verification?:ModelVerification }
