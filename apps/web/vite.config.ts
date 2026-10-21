import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

/**
 * Build configuration.
 *
 * Manual chunks rather than a large automatic split: the landing page and the
 * command centre have almost nothing in common, and a visitor who never opens
 * the dashboard should not download MapLibre. The chunk boundaries below mean
 * each route pulls exactly what it needs.
 */
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  define: {
    __PUBLIC_SITE_ONLY__: JSON.stringify(mode === 'public'),
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // The dev server forwards API calls so the browser sees one origin and
      // no CORS preflight. The gateway's own CORS configuration still applies
      // in production, where the two are served from different hosts.
      '/api': {
        target: process.env.RESQNET_API_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: (process.env.RESQNET_WS_URL ?? 'ws://localhost:8080').replace(/^http/, 'ws'),
        ws: true,
      },
    },
  },
  build: {
    target: 'es2022',
    sourcemap: mode !== 'public',
    cssCodeSplit: true,
    reportCompressedSize: false,
    // Three.js is a single lazy chunk loaded only when the WebGL hero mounts.
    // It is intentionally larger than the default warning threshold.
    chunkSizeWarningLimit: 720,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('/three/') || id.includes('three/')) return 'three';
          if (id.includes('framer-motion') || id.includes('motion-dom')
            || id.includes('motion-utils')) return 'motion';
          if (id.includes('react-router')) return 'router';
          if (id.includes('react') || id.includes('scheduler')) return 'react';
          return 'vendor';
        },
      },
    },
  },
}));
