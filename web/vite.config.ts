/* 소스 안내
 * 기존 웹 dev/preview의 포트·보안 헤더·작업 API 플러그인·Vitest 대상을 정의한다.
 * 연결 모듈: ./server/plugin.ts.
 */
import { defineConfig } from 'vitest/config';
import { workApi } from './server/plugin.ts';

export default defineConfig({
  base: './',
  plugins: [workApi()],
  server: { port: 5174, strictPort: true, fs: { deny: ['**/.local/**', '**/.git/**', '**/.env', '**/.env.*', '**/*.{crt,pem}', '**/server/**'] }, headers: { 'Cross-Origin-Opener-Policy': 'same-origin', 'Cross-Origin-Embedder-Policy': 'require-corp' } },
  preview: { port: 4173, strictPort: true, headers: { 'Cross-Origin-Opener-Policy': 'same-origin', 'Cross-Origin-Embedder-Policy': 'require-corp' } },
  optimizeDeps: { exclude: ['@litert-lm/core'] },
  test: { include: ['tests/**/*.test.{ts,tsx}'], environment: 'node' },
});
