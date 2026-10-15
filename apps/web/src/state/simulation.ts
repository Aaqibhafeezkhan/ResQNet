import { create } from 'zustand';
import { api, type SimulationEvent, type SimulationRun } from '@/lib/api';

/**
 * Simulator state for the command centre.
 *
 * Everything in here is labelled as simulated at the point of display, not at
 * the point of storage alone. The dashboard must never let a generated node
 * look like a device that is actually helping someone.
 */
interface SimulationState {
  runs: SimulationRun[];
  activeRun: SimulationRun | null;
  events: SimulationEvent[];
  loading: boolean;
  error: string | null;
  loadRuns: () => Promise<void>;
  selectRun: (runId: string) => Promise<void>;
  reset: () => void;
}

export const useSimulationStore = create<SimulationState>((set, get) => ({
  runs: [],
  activeRun: null,
  events: [],
  loading: false,
  error: null,

  async loadRuns() {
    set({ loading: true, error: null });
    try {
      const runs = await api.simulationRuns();
      set({ runs, loading: false });
      const first = runs[0];
      if (first && !get().activeRun) {
        await get().selectRun(first.runId);
      }
    } catch (cause) {
      set({
        loading: false,
        error: cause instanceof Error ? cause.message : 'Could not load simulation runs',
      });
    }
  },

  async selectRun(runId) {
    const run = get().runs.find((candidate) => candidate.runId === runId) ?? null;
    set({ activeRun: run });
    try {
      const events = await api.simulationEvents(runId);
      set({ events, error: null });
    } catch (cause) {
      set({
        events: [],
        error: cause instanceof Error ? cause.message : 'Could not load simulation events',
      });
    }
  },

  reset() {
    set({ runs: [], activeRun: null, events: [], error: null });
  },
}));
