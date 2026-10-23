/**
 * Replay Lab state.
 *
 * <p>One store holds the loaded report, the replay cursor, the timeline
 * transport, the filters and the two comparison slots. Keeping the transport
 * here rather than in the page means the timeline can be tested by driving the
 * actions directly, without a browser clock.
 */

import { create } from 'zustand';

import {
  buildReplayModel,
  cursorForTime,
  simTimeAtCursor,
  type ReplayModel,
} from './replay';
import type { LabReport } from './types';

export type LabTab = 'replay' | 'compare';
export type CompareSide = 'left' | 'right';

function now(): number {
  return typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now();
}

interface LabState {
  report: LabReport | null;
  model: ReplayModel | null;
  sourceLabel: string;
  warnings: string[];
  error: string | null;

  tab: LabTab;

  /** Number of events already shown. `0` is before the first event. */
  cursor: number;
  playing: boolean;
  /** Simulated-time multiplier. 1 means one simulated second per real second. */
  speed: number;
  anchorRealMs: number;
  anchorSimMs: number;

  activeKinds: Set<string>;
  search: string;
  selectedMessageId: string | null;
  selectedNodeIndex: number | null;

  compareLeft: LabReport | null;
  compareRight: LabReport | null;

  load: (report: LabReport, sourceLabel: string, warnings: string[]) => void;
  clear: () => void;
  setError: (message: string | null) => void;
  setTab: (tab: LabTab) => void;

  play: (at?: number) => void;
  pause: () => void;
  reset: () => void;
  seek: (cursor: number) => void;
  seekToTime: (atEpochMillis: number) => void;
  stepForward: () => void;
  stepBackward: () => void;
  tick: (at?: number) => void;
  setSpeed: (speed: number) => void;

  toggleKind: (kind: string) => void;
  setKinds: (kinds: Iterable<string>) => void;
  setSearch: (value: string) => void;
  selectMessage: (messageId: string | null) => void;
  selectNode: (index: number | null) => void;

  setCompare: (side: CompareSide, report: LabReport | null) => void;
}

function kindsInReport(report: LabReport): Set<string> {
  return new Set(report.events.map((event) => event.kind));
}

export const useLabStore = create<LabState>((set, get) => ({
  report: null,
  model: null,
  sourceLabel: '',
  warnings: [],
  error: null,

  tab: 'replay',

  cursor: 0,
  playing: false,
  speed: 2,
  anchorRealMs: 0,
  anchorSimMs: 0,

  activeKinds: new Set<string>(),
  search: '',
  selectedMessageId: null,
  selectedNodeIndex: null,

  compareLeft: null,
  compareRight: null,

  load(report, sourceLabel, warnings) {
    const model = buildReplayModel(report);
    set({
      report,
      model,
      sourceLabel,
      warnings,
      error: null,
      cursor: 0,
      playing: false,
      anchorRealMs: 0,
      anchorSimMs: model.startMillis,
      activeKinds: kindsInReport(report),
      search: '',
      selectedMessageId: null,
      selectedNodeIndex: null,
    });
  },

  clear() {
    set({
      report: null,
      model: null,
      sourceLabel: '',
      warnings: [],
      error: null,
      cursor: 0,
      playing: false,
      selectedMessageId: null,
      selectedNodeIndex: null,
      search: '',
    });
  },

  setError(message) {
    set({ error: message });
  },

  setTab(tab) {
    set({ tab });
  },

  play(at) {
    const { model, cursor } = get();
    if (!model || model.orderedEvents.length === 0) return;
    const startedAt = at ?? now();
    if (cursor >= model.orderedEvents.length) {
      // Pressing play at the end restarts rather than doing nothing.
      set({
        cursor: 0,
        playing: true,
        anchorRealMs: startedAt,
        anchorSimMs: model.startMillis,
      });
      return;
    }
    set({
      playing: true,
      anchorRealMs: startedAt,
      anchorSimMs: simTimeAtCursor(model, cursor),
    });
  },

  pause() {
    set({ playing: false });
  },

  reset() {
    const { model } = get();
    set({
      cursor: 0,
      playing: false,
      anchorRealMs: 0,
      anchorSimMs: model?.startMillis ?? 0,
    });
  },

  seek(cursor) {
    const { model } = get();
    if (!model) return;
    const clamped = Math.max(0, Math.min(cursor, model.orderedEvents.length));
    set({
      cursor: clamped,
      playing: false,
      anchorRealMs: 0,
      anchorSimMs: simTimeAtCursor(model, clamped),
    });
  },

  seekToTime(atEpochMillis) {
    const { model } = get();
    if (!model) return;
    get().seek(cursorForTime(model, atEpochMillis));
  },

  stepForward() {
    get().seek(get().cursor + 1);
  },

  stepBackward() {
    get().seek(get().cursor - 1);
  },

  tick(at) {
    const { playing, model, cursor, speed, anchorRealMs, anchorSimMs } = get();
    if (!playing || !model) return;
    const current = at ?? now();
    const target = anchorSimMs + (current - anchorRealMs) * speed;
    const next = cursorForTime(model, target);
    if (next >= model.orderedEvents.length) {
      set({ cursor: model.orderedEvents.length, playing: false });
      return;
    }
    if (next !== cursor) {
      set({ cursor: next });
    }
  },

  setSpeed(speed) {
    const { model, cursor } = get();
    const at = now();
    set({
      speed,
      anchorRealMs: at,
      anchorSimMs: model ? simTimeAtCursor(model, cursor) : 0,
    });
  },

  toggleKind(kind) {
    const next = new Set(get().activeKinds);
    if (next.has(kind)) next.delete(kind);
    else next.add(kind);
    set({ activeKinds: next });
  },

  setKinds(kinds) {
    set({ activeKinds: new Set(kinds) });
  },

  setSearch(value) {
    set({ search: value });
  },

  selectMessage(messageId) {
    set({ selectedMessageId: messageId });
  },

  selectNode(index) {
    set({ selectedNodeIndex: index });
  },

  setCompare(side, report) {
    if (side === 'left') set({ compareLeft: report });
    else set({ compareRight: report });
  },
}));
