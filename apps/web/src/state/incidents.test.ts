import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { api, ApiError, type IncidentSummary, type LoginResponse, type Page } from '@/lib/api';
import { useAuthStore } from '@/state/auth';
import { useIncidentStore } from '@/state/incidents';

const incident = { reference: 'INC-1', title: 'Flooded basement' } as IncidentSummary;
const page = (items: IncidentSummary[]): Page<IncidentSummary> => ({
  content: items,
  totalElements: items.length,
  totalPages: 1,
  number: 0,
  size: 25,
});
const identity = { token: 'token-1', username: 'ada', role: 'RESPONDER' } as LoginResponse;

beforeEach(() => {
  useIncidentStore.setState({ items: [incident], overview: null, unreachable: false, stale: false, error: null });
  useAuthStore.setState({ token: identity.token, identity, error: null });
  vi.spyOn(api, 'overview').mockResolvedValue({ open: 1, critical: 0, total: 1, newIncidents: 0, resolved: 0 });
});

afterEach(() => {
  vi.restoreAllMocks();
});

// A failed refresh used to leave the previous queue on screen with a healthy
// header and an error nothing rendered (#36).
describe('incident refresh failures', () => {
  it('signs out on a 401 and says why on the sign-in screen', async () => {
    vi.spyOn(api, 'incidents').mockRejectedValue(new ApiError(401, 'UNAUTHORIZED', 'Unauthorized'));

    await useIncidentStore.getState().refresh();

    expect(useIncidentStore.getState().items).toEqual([]);
    expect(useIncidentStore.getState().stale).toBe(false);
    expect(useAuthStore.getState().identity).toBeNull();
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().error).toBe('Your session expired. Sign in again.');
  });

  it('keeps the queue but marks it stale on a 500', async () => {
    vi.spyOn(api, 'incidents').mockRejectedValue(new ApiError(500, 'INTERNAL', 'Something went wrong'));

    await useIncidentStore.getState().refresh();

    const state = useIncidentStore.getState();
    expect(state.items).toEqual([incident]);
    expect(state.stale).toBe(true);
    expect(state.unreachable).toBe(false);
    expect(state.error).toBe('Something went wrong');
    expect(useAuthStore.getState().identity).toEqual(identity);
  });

  it('clears the stale state on the next successful refresh', async () => {
    const incidents = vi.spyOn(api, 'incidents')
      .mockRejectedValueOnce(new ApiError(503, 'UNAVAILABLE', 'Service unavailable'))
      .mockResolvedValueOnce(page([incident]));

    await useIncidentStore.getState().refresh();
    expect(useIncidentStore.getState().stale).toBe(true);

    await useIncidentStore.getState().refresh();
    expect(incidents).toHaveBeenCalledTimes(2);
    expect(useIncidentStore.getState().stale).toBe(false);
    expect(useIncidentStore.getState().error).toBeNull();
  });

  it('still treats an unreachable gateway as unreachable, not stale', async () => {
    vi.spyOn(api, 'incidents').mockRejectedValue(new ApiError(0, 'GATEWAY_UNREACHABLE', 'Gateway unreachable'));

    await useIncidentStore.getState().refresh();

    expect(useIncidentStore.getState().unreachable).toBe(true);
    expect(useIncidentStore.getState().stale).toBe(false);
    expect(useIncidentStore.getState().items).toEqual([]);
  });
});
