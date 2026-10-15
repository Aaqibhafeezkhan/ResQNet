/**
 * Test environment setup.
 *
 * jsdom implements neither `matchMedia` nor `ResizeObserver`, both of which the
 * interface uses. Stubbing them here rather than guarding every component means
 * the tests exercise the same code path the browser runs, instead of a branch
 * that only exists under test.
 */
import { afterEach, vi } from 'vitest';

if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });
}

/**
 * Framer Motion's whileInView uses IntersectionObserver, which jsdom does not
 * implement. The stub reports every element as visible so reveal animations
 * resolve rather than leaving content permanently at opacity zero, which would
 * make the smoke test assert on an invisible page.
 */
if (!globalThis.IntersectionObserver) {
  globalThis.IntersectionObserver = class {
    readonly root = null;
    readonly rootMargin = '';
    readonly thresholds: readonly number[] = [];
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
    takeRecords(): [] {
      return [];
    }
  } as unknown as typeof IntersectionObserver;

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).isIntersecting = true;
}

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  } as unknown as typeof ResizeObserver;
}

if (!window.requestAnimationFrame) {
  window.requestAnimationFrame = ((callback: FrameRequestCallback) =>
    window.setTimeout(() => callback(performance.now()), 16)) as typeof requestAnimationFrame;
  window.cancelAnimationFrame = ((handle: number) =>
    window.clearTimeout(handle)) as typeof cancelAnimationFrame;
}

/**
 * A minimal Storage implementation.
 *
 * <p>Installed only when the environment does not provide one. jsdom omits
 * `localStorage` for opaque origins, and without it the session-persistence
 * path would never execute in tests. A conforming shim keeps the tests
 * exercising the same code the browser runs instead of a branch that exists
 * only under test.
 */
if (!window.localStorage) {
  const memory = new Map<string, string>();
  const shim: Storage = {
    get length() {
      return memory.size;
    },
    clear: () => memory.clear(),
    getItem: (key: string) => memory.get(key) ?? null,
    key: (index: number) => [...memory.keys()][index] ?? null,
    removeItem: (key: string) => void memory.delete(key),
    setItem: (key: string, value: string) => void memory.set(key, String(value)),
  };
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    writable: true,
    value: shim,
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});
