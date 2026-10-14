import { defineConfig } from 'vitest/config';

export default defineConfig({
  base: './',
  server: { port: 5174, strictPort: true, headers: { 'Cross-Origin-Opener-Policy': 'same-origin', 'Cross-Origin-Embedder-Policy': 'require-corp' } },
  preview: { port: 4173, headers: { 'Cross-Origin-Opener-Policy': 'same-origin', 'Cross-Origin-Embedder-Policy': 'require-corp' } },
  optimizeDeps: { exclude: ['@litert-lm/core'] },
  test: { include: ['tests/**/*.test.{ts,tsx}'], environment: 'node' },
});
