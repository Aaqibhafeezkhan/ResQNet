import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api, authStorage } from './api';

/**
 * The client's most important behaviour is refusing to lie.
 *
 * <p>A dashboard that turns "the gateway is unreachable" into "there are no
 * incidents" would let a responder act on a false picture during exactly the
 * moment the picture matters. These tests pin that distinction.
 */
describe('api client', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    authStorage.clear();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    authStorage.clear();
    vi.restoreAllMocks();
  });

  it('reports an unreachable gateway as such, not as an empty page', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch')) as never;

    await expect(api.incidents()).rejects.toMatchObject({
      status: 0,
      code: 'GATEWAY_UNREACHABLE',
    });
  });

  it('surfaces the server error code and message', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'NOT_FOUND', message: 'no such incident' }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as never;

    await expect(api.incident('RQ-NOPE')).rejects.toMatchObject({
      status: 404,
      code: 'NOT_FOUND',
      message: 'no such incident',
    });
  });

  it('falls back to a usable code when the error body is not JSON', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response('<html>gateway timeout</html>', { status: 504 }),
    ) as never;

    await expect(api.overview()).rejects.toMatchObject({ status: 504, code: 'UNKNOWN' });
  });

  it('marks a 401 as an auth failure so the UI can re-authenticate', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'UNAUTHENTICATED', message: 'nope' }), {
        status: 401,
      }),
    ) as never;

    const error = await api.overview().catch((cause: unknown) => cause as ApiError);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).isAuthFailure).toBe(true);
  });

  it('attaches the bearer token when one is stored', async () => {
    authStorage.save('token-abc', {
      token: 'token-abc',
      username: 'responder',
      role: 'RESPONDER',
    });
    globalThis.fetch = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ open: 0, critical: 0, total: 0, newIncidents: 0, resolved: 0 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as never;

    await api.overview();

    const call = (globalThis.fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0];
    const headers = call?.[1]?.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer token-abc');
  });

  it('tolerates storage being unavailable rather than throwing on load', () => {
    // jsdom's Storage is a getter-only property, so it is replaced outright to
    // model a browser in private mode where access throws.
    const original = Object.getOwnPropertyDescriptor(window, 'localStorage');
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      get() {
        throw new Error('storage disabled');
      },
    });

    try {
      expect(authStorage.token()).toBeNull();
      expect(authStorage.identity()).toBeNull();
      expect(() => authStorage.clear()).not.toThrow();
    } finally {
      if (original) Object.defineProperty(window, 'localStorage', original);
    }
  });
});
