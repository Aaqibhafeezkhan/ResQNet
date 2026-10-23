import { beforeEach, describe, expect, it } from 'vitest';

import { useLabStore } from './labStore';
import { validateReport } from './parseReport';
import ringSample from './fixtures/ring-6-seed-42.json';
import partitionedSample from './fixtures/two-cluster-6-seed-42.json';

const initialState = useLabStore.getInitialState();

function loadRing(): void {
  const result = validateReport(ringSample, 'ring fixture');
  if (!result.ok) throw new Error(result.error);
  useLabStore.getState().load(result.report, 'ring fixture', result.warnings);
}

function loadPartitioned(): void {
  const result = validateReport(partitionedSample, 'partitioned fixture');
  if (!result.ok) throw new Error(result.error);
  useLabStore.getState().load(result.report, 'partitioned fixture', result.warnings);
}

beforeEach(() => {
  useLabStore.setState(initialState, true);
});

describe('loading a report', () => {
  it('builds the model and resets the transport and selections', () => {
    loadRing();
    const state = useLabStore.getState();

    expect(state.report?.simulation.seed).toBe(42);
    expect(state.model?.nodeCount).toBe(6);
    expect(state.model?.orderedEvents).toHaveLength(558);
    expect(state.sourceLabel).toBe('ring fixture');
    expect(state.cursor).toBe(0);
    expect(state.playing).toBe(false);
    expect(state.selectedMessageId).toBeNull();
    expect(state.selectedNodeIndex).toBeNull();
    expect(state.search).toBe('');
  });

  it('enables every kind present in the report, and only those', () => {
    loadRing();
    const state = useLabStore.getState();
    const kinds = new Set(state.model?.orderedEvents.map((event) => event.kind));

    expect(state.activeKinds).toEqual(kinds);
    expect(state.activeKinds.has('forwarded')).toBe(true);
    expect(state.activeKinds.has('no-such-kind')).toBe(false);
  });

  it('replaces the previous report rather than merging them', () => {
    loadRing();
    loadPartitioned();
    const state = useLabStore.getState();

    expect(state.sourceLabel).toBe('partitioned fixture');
    expect(state.model?.orderedEvents).toHaveLength(129);
    expect(state.cursor).toBe(0);
  });

  it('clears the report without throwing', () => {
    loadRing();
    useLabStore.getState().clear();
    expect(useLabStore.getState().report).toBeNull();
    expect(useLabStore.getState().model).toBeNull();
  });
});

describe('transport', () => {
  it('advances the cursor while playing and stops at the end', () => {
    loadRing();
    const model = useLabStore.getState().model;
    if (!model) throw new Error('expected a model');

    useLabStore.getState().play(1_000);
    expect(useLabStore.getState().playing).toBe(true);

    useLabStore.getState().tick(1_500);
    const after = useLabStore.getState();
    expect(after.playing).toBe(true);
    expect(after.cursor).toBeGreaterThan(0);

    // Far beyond the end of the run.
    useLabStore.getState().tick(1_000_000);
    const finished = useLabStore.getState();
    expect(finished.cursor).toBe(model.orderedEvents.length);
    expect(finished.playing).toBe(false);
  });

  it('does nothing while paused', () => {
    loadRing();
    useLabStore.getState().tick(5_000);
    expect(useLabStore.getState().cursor).toBe(0);
    expect(useLabStore.getState().playing).toBe(false);
  });

  it('restarts from the beginning when play is pressed at the end', () => {
    loadRing();
    const total = useLabStore.getState().model?.orderedEvents.length ?? 0;
    useLabStore.getState().seek(total);
    expect(useLabStore.getState().cursor).toBe(total);

    useLabStore.getState().play(2_000);
    expect(useLabStore.getState().cursor).toBe(0);
    expect(useLabStore.getState().playing).toBe(true);
  });

  it('clamps seeks and pauses, so scrubbing is explicit', () => {
    loadRing();
    const total = useLabStore.getState().model?.orderedEvents.length ?? 0;

    useLabStore.getState().seek(-10);
    expect(useLabStore.getState().cursor).toBe(0);

    useLabStore.getState().seek(total + 10);
    expect(useLabStore.getState().cursor).toBe(total);

    useLabStore.getState().play(1_000);
    useLabStore.getState().seek(10);
    expect(useLabStore.getState().cursor).toBe(10);
    expect(useLabStore.getState().playing).toBe(false);
  });

  it('steps one event at a time and clamps at the edges', () => {
    loadRing();
    const total = useLabStore.getState().model?.orderedEvents.length ?? 0;

    useLabStore.getState().stepForward();
    expect(useLabStore.getState().cursor).toBe(1);
    useLabStore.getState().stepBackward();
    expect(useLabStore.getState().cursor).toBe(0);
    useLabStore.getState().stepBackward();
    expect(useLabStore.getState().cursor).toBe(0);

    useLabStore.getState().seek(total);
    useLabStore.getState().stepForward();
    expect(useLabStore.getState().cursor).toBe(total);
  });

  it('resets to the start and stops playback', () => {
    loadRing();
    useLabStore.getState().seek(30);
    useLabStore.getState().play(1_000);
    useLabStore.getState().reset();

    expect(useLabStore.getState().cursor).toBe(0);
    expect(useLabStore.getState().playing).toBe(false);
  });

  it('re-anchors the clock when the speed changes mid-playback', () => {
    loadRing();
    useLabStore.getState().seek(12);
    useLabStore.getState().play(1_000);
    useLabStore.getState().setSpeed(4);

    expect(useLabStore.getState().speed).toBe(4);
    const anchor = useLabStore.getState().anchorSimMs;
    expect(anchor).toBeGreaterThan(0);
  });

  it('seekToTime lands on the event boundary for that timestamp', () => {
    loadRing();
    const model = useLabStore.getState().model;
    if (!model) throw new Error('expected a model');
    const target = model.orderedEvents[40]?.atEpochMillis;
    if (target === undefined) throw new Error('expected an event');

    useLabStore.getState().seekToTime(target);
    expect(useLabStore.getState().cursor).toBeGreaterThanOrEqual(41);
  });
});

describe('filters and selection', () => {
  it('toggles a single kind without changing the others', () => {
    loadRing();
    useLabStore.getState().toggleKind('forwarded');
    expect(useLabStore.getState().activeKinds.has('forwarded')).toBe(false);
    expect(useLabStore.getState().activeKinds.has('accepted')).toBe(true);

    useLabStore.getState().toggleKind('forwarded');
    expect(useLabStore.getState().activeKinds.has('forwarded')).toBe(true);
  });

  it('shows and hides all kinds', () => {
    loadRing();
    useLabStore.getState().setKinds([]);
    expect(useLabStore.getState().activeKinds.size).toBe(0);

    useLabStore.getState().setKinds(['forwarded', 'accepted']);
    expect(useLabStore.getState().activeKinds.size).toBe(2);
  });

  it('stores the search and the selected message and node', () => {
    loadRing();
    useLabStore.getState().setSearch('m199');
    useLabStore.getState().selectMessage('m199');
    useLabStore.getState().selectNode(3);

    expect(useLabStore.getState().search).toBe('m199');
    expect(useLabStore.getState().selectedMessageId).toBe('m199');
    expect(useLabStore.getState().selectedNodeIndex).toBe(3);
  });

  it('keeps the comparison slots independent of the replay', () => {
    loadRing();
    const result = validateReport(partitionedSample, 'partitioned');
    if (!result.ok) throw new Error(result.error);

    useLabStore.getState().setCompare('left', result.report);
    useLabStore.getState().setCompare('right', result.report);

    expect(useLabStore.getState().compareLeft?.source).toBe('partitioned');
    expect(useLabStore.getState().compareRight?.source).toBe('partitioned');
    expect(useLabStore.getState().sourceLabel).toBe('ring fixture');

    useLabStore.getState().setCompare('left', null);
    expect(useLabStore.getState().compareLeft).toBeNull();
    expect(useLabStore.getState().compareRight).not.toBeNull();
  });

  it('switches tabs without touching the loaded report', () => {
    loadRing();
    useLabStore.getState().setTab('compare');
    expect(useLabStore.getState().tab).toBe('compare');
    expect(useLabStore.getState().report).not.toBeNull();
  });
});
