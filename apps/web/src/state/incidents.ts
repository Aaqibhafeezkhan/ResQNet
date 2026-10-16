import { create } from 'zustand';
import {
  api,
  ApiError,
  type IncidentStatus,
  type IncidentSummary,
  type Overview,
} from '@/lib/api';

interface IncidentFilters {
  status: IncidentStatus | null;
  query: string;
  severity: number | null;
}

interface IncidentState {
  items: IncidentSummary[];
  overview: Overview | null;
  filters: IncidentFilters;
  loading: boolean;
  /** Set when the gateway could not be reached. Distinct from an empty list. */
  unreachable: boolean;
  error: string | null;
  setStatusFilter: (status: IncidentStatus | null) => void;
  setQuery: (query: string) => void;
  setSeverityFilter: (severity: number | null) => void;
  refresh: () => Promise<void>;
  updateStatus: (
    reference: string,
    status: IncidentStatus,
    note?: string,
  ) => Promise<void>;
}

/**
 * Incident list state.
 *
 * `unreachable` exists because "no incidents" and "the gateway is down" are
 * completely different situations, and a responder acting on the first when it
 * is really the second is the worst failure this dashboard could have. They are
 * never conflated in this store.
 */
export const useIncidentStore = create<IncidentState>((set, get) => ({
  items: [],
  overview: null,
  filters: { status: null, query: '', severity: null },
  loading: false,
  unreachable: false,
  error: null,

  setStatusFilter: (status) => set({ filters: { ...get().filters, status } }),
  setQuery: (query) => set({ filters: { ...get().filters, query } }),
  setSeverityFilter: (severity) => set({ filters: { ...get().filters, severity } }),

  /**
   * True only while the first load is outstanding.
   *
   * <p>Not set for background refreshes. The command centre polls every 15
   * seconds, and marking every poll as loading made the header alternate
   * Live, Connecting, Live, Connecting. An operator learns to ignore the one
   * indicator that tells them whether the dashboard is telling the truth, and
   * then misses a real outage. Refreshing data is not the same as losing the
   * connection; only a failed request is.
   */
  async refresh() {
    const { filters } = get();
    if (get().items.length === 0) {
      set({ loading: true });
    }
    set({ error: null });
    try {
      const page = filters.query
        ? await api.search(filters.query)
        : await api.incidents(filters.status ?? undefined);
      const items = filters.severity === null
        ? page.content
        : page.content.filter((item) => item.severity === filters.severity);
      const overview = await api.overview();
      set({ items, overview, loading: false, unreachable: false });
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 0) {
        set({ unreachable: true, loading: false, error: cause.message, items: [] });
        return;
      }
      if (cause instanceof ApiError && cause.isAuthFailure) {
        set({ loading: false, error: 'Session expired. Sign in again.' });
        return;
      }
      set({
        loading: false,
        error: cause instanceof Error ? cause.message : 'Could not load incidents',
      });
    }
  },

  async updateStatus(reference, status, note) {
    await api.updateStatus(reference, status, note);
    await get().refresh();
  },
}));
