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
export interface Source { chunk: Chunk; score: number }
export interface Chat {
  id: string; question: string; answer: string; citationIds: string[];
  mode: 'source' | 'ai' | 'cancelled'; createdAt: string; notice?: string;
  tokenUsage?:import('./ai/protocol').TokenUsage;
}
export interface Checklist { id: string; checked: number[]; site: string; note: string; createdAt: string }
export interface Asset { path: string; size: number; sha256: string; group: 'knowledge' | 'pdf' | 'runtime' }
export interface AssetManifest { version: string; sdkVersion: string; assets: Asset[] }
export interface ModelRecord { name: string; size: number; sha256: string; installedAt: string; backend: 'opfs' | 'idb'; storageKey: string }
