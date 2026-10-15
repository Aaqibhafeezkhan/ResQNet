import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config';

/**
 * Vitest runs against the same Vite configuration as the app, so a test can
 * never pass against a resolver or transform the production build does not use.
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      // jsdom only provides localStorage for a real origin. Without this the
      // storage-backed session would be undefined in tests and the persistence
      // path would go unexercised.
      environmentOptions: { jsdom: { url: 'http://localhost:5173' } },
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      css: false,
    },
  }),
);
