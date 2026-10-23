import { describe, expect, it } from 'vitest';

import {
  activeEdgesAt,
  buildJourney,
  buildReplayModel,
  compareEvents,
  cursorForTime,
  edgeKey,
  linkStatesAt,
  orderEvents,
  simTimeAtCursor,
} from './replay';
import { validateReport } from './parseReport';
import type { LabEvent } from './types';
import ringSample from './fixtures/ring-6-seed-42.json';
import partitionedSample from './fixtures/two-cluster-6-seed-42.json';

function ringModel() {
  const result = validateReport(ringSample, 'ring');
  if (!result.ok) throw new Error(result.error);
  return buildReplayModel(result.report);
}

function event(overrides: Partial<LabEvent>): LabEvent {
  return {
    atEpochMillis: 0,
    round: 0,
    nodeIndex: 0,
    node: 'n0',
    kind: 'forwarded',
    messageId: null,
    peers: [],
    hopCount: 0,
    reason: null,
    detail: null,
    simulated: true,
    ...overrides,
  };
}

describe('buildReplayModel', () => {
  it('orders the report into a deterministic total order', () => {
    const model = ringModel();

    for (let index = 1; index < model.orderedEvents.length; index += 1) {
      const previous = model.orderedEvents[index - 1];
      const current = model.orderedEvents[index];
      if (!previous || !current) throw new Error('missing event');
      expect(compareEvents(previous, current)).toBeLessThanOrEqual(0);
    }

    // Rebuilding from the same report produces the identical sequence.
    const again = ringModel();
    expect(again.orderedEvents).toEqual(model.orderedEvents);
  });

  it('breaks ties with the original position so the order is total', () => {
    const first = event({ nodeIndex: 0, kind: 'accepted', messageId: 'b' });
    const second = event({ nodeIndex: 0, kind: 'accepted', messageId: 'a' });
    const identical = event({ nodeIndex: 0, kind: 'accepted', messageId: 'b' });

    const ordered = orderEvents([first, second, identical]);
    expect(ordered.map((entry) => entry.messageId)).toEqual(['a', 'b', 'b']);
    // Two fully identical events keep their input order.
    expect(ordered[1]).toBe(first);
    expect(ordered[2]).toBe(identical);
  });

  it('works when every sort key is equal', () => {
    const events = [event({}), event({}), event({})];
    expect(orderEvents(events)).toEqual(events);
  });

  it('indexes events by message and by node', () => {
    const model = ringModel();

    expect(model.nodeCount).toBe(6);
    expect(model.eventOrder.size).toBe(model.orderedEvents.length);
    expect(model.eventsByNode.get(0)?.length).toBeGreaterThan(0);
    expect(model.messagesById.size).toBe(10);
    // Composed messages plus the acknowledgement ids that appear only in events.
    expect(model.messageIds.filter((id) => model.messagesById.has(id))).toHaveLength(10);
    expect(model.messageIds.length).toBeGreaterThanOrEqual(10);
    expect(model.hasTopology).toBe(true);
    expect(model.hasLinkChanges).toBe(true);
    expect(model.hasMessageProvenance).toBe(true);

    for (const id of model.messageIds) {
      const events = model.eventsByMessage.get(id) ?? [];
      expect(events.length).toBeGreaterThan(0);
      expect(events.every((entry) => entry.messageId === id)).toBe(true);
    }
  });

  it('uses the recorded start and end rather than the wall clock', () => {
    const model = ringModel();
    expect(model.startMillis).toBe(1760000000000);
    expect(model.endMillis).toBeGreaterThan(model.startMillis);
    expect(model.startMillis).toBeLessThan(model.endMillis);
  });

  it('anchors the replay at the recorded clock start, even before the first event', () => {
    const result = validateReport({
      simulation: { topology: 'LINE', nodeCount: 2, startEpochSeconds: 100 },
      events: [
        {
          atEpochMillis: 100_500,
          round: 0,
          node: 'n0',
          kind: 'state',
          messageId: null,
        },
      ],
    });
    if (!result.ok) throw new Error(result.error);
    const model = buildReplayModel(result.report);

    expect(model.startMillis).toBe(100_000);
    expect(model.endMillis).toBe(100_500);
    expect(simTimeAtCursor(model, 0)).toBe(100_000);
  });

  it('draws no topology when the report did not record one', () => {
    const result = validateReport({ simulation: {}, events: [] });
    if (!result.ok) throw new Error(result.error);
    const model = buildReplayModel(result.report);

    expect(model.initialEdges).toEqual([]);
    expect(model.linkChanges).toEqual([]);
    expect(model.hasTopology).toBe(false);
    expect(model.nodeCount).toBe(0);
  });

  it('includes nodes named only by recorded edges or link changes', () => {
    const result = validateReport({
      simulation: {
        topology: 'LINE',
        edges: [
          [0, 1],
          [2, 3],
        ],
      },
      events: [],
      linkChanges: [{ atEpochMillis: 1000, from: 3, to: 4, up: false }],
    });
    if (!result.ok) throw new Error(result.error);
    const model = buildReplayModel(result.report);

    expect(model.nodeCount).toBe(5);
  });
});

describe('link state', () => {
  it('starts with every recorded edge up', () => {
    const model = ringModel();
    expect(model.initialEdges).toHaveLength(6);
    const states = linkStatesAt(model, model.startMillis);
    expect(states).toHaveLength(6);
    expect(states.every((state) => state.up)).toBe(true);
  });

  it('applies a recorded cut at its timestamp, not before', () => {
    const model = ringModel();
    const cut = model.linkChanges.find((change) => !change.up);
    if (!cut) throw new Error('the ring fixture is expected to record a cut');

    const before = linkStatesAt(model, cut.atEpochMillis - 1).find(
      (state) => state.key === edgeKey(cut.from, cut.to),
    );
    const after = linkStatesAt(model, cut.atEpochMillis).find(
      (state) => state.key === edgeKey(cut.from, cut.to),
    );

    expect(before?.up).toBe(true);
    expect(after?.up).toBe(false);
    expect(after?.changedAt).toBe(cut.atEpochMillis);
  });

  it('applies a recorded restore and reports it as up again', () => {
    const model = ringModel();
    const restore = model.linkChanges.find(
      (change) => change.up && change.atEpochMillis > model.startMillis,
    );
    if (!restore) throw new Error('the ring fixture is expected to record a restore');

    const after = linkStatesAt(model, restore.atEpochMillis).find(
      (state) => state.key === edgeKey(restore.from, restore.to),
    );
    expect(after?.up).toBe(true);
    expect(activeEdgesAt(model, restore.atEpochMillis).has(edgeKey(restore.from, restore.to))).toBe(
      true,
    );
  });

  it('never adds an edge that is not in the report', () => {
    const model = ringModel();
    const initialKeys = new Set(model.initialEdges.map(([from, to]) => edgeKey(from, to)));
    const states = linkStatesAt(model, model.endMillis);

    for (const state of states) {
      expect(initialKeys.has(state.key)).toBe(true);
    }
  });

  it('handles an empty report without inventing a link', () => {
    const result = validateReport({ simulation: {}, events: [] });
    if (!result.ok) throw new Error(result.error);
    const model = buildReplayModel(result.report);
    expect(linkStatesAt(model, 0)).toEqual([]);
  });
});

describe('cursor', () => {
  it('maps time to the number of events that had happened', () => {
    const model = ringModel();
    expect(cursorForTime(model, model.startMillis - 1)).toBe(0);
    expect(cursorForTime(model, model.startMillis)).toBeGreaterThan(0);
    expect(cursorForTime(model, model.endMillis)).toBe(model.orderedEvents.length);
    expect(cursorForTime(model, model.endMillis + 1)).toBe(model.orderedEvents.length);
  });

  it('maps a cursor back to the time of the last shown event', () => {
    const model = ringModel();
    expect(simTimeAtCursor(model, 0)).toBe(model.startMillis);
    expect(simTimeAtCursor(model, 1)).toBe(model.orderedEvents[0]?.atEpochMillis);
    expect(simTimeAtCursor(model, model.orderedEvents.length)).toBe(model.endMillis);
  });

  it('clamps a cursor beyond the end', () => {
    const model = ringModel();
    expect(simTimeAtCursor(model, model.orderedEvents.length + 10)).toBe(model.endMillis);
  });
});

describe('buildJourney', () => {
  function ringJourney(messageId: string, untilEpochMillis?: number) {
    const model = ringModel();
    const journey =
      untilEpochMillis === undefined
        ? buildJourney(model, messageId)
        : buildJourney(model, messageId, { untilEpochMillis });
    return { model, journey };
  }

  it('reads provenance from the message summary', () => {
    const model = ringModel();
    const directed = model.messageIds
      .map((id) => model.messagesById.get(id))
      .find((message) => message && !message.broadcast);
    if (!directed) throw new Error('expected a directed message');
    const { journey } = ringJourney(directed.id);

    expect(journey.origin).toBe(directed.origin);
    expect(journey.destination).toBe(directed.destination);
    expect(journey.broadcast).toBe(false);
    expect(journey.steps.length).toBeGreaterThan(0);
    expect(journey.outcome).toBe('acknowledged');
    expect(journey.limitations.join(' ')).toMatch(/not one reconstructed route/);
  });

  it('admits that a flood is not a single route', () => {
    const model = ringModel();
    const id = model.messageIds[0];
    if (!id) throw new Error('expected a message');
    const journey = buildJourney(model, id);
    expect(journey.handoffs.length).toBeGreaterThan(0);

    for (const handoff of journey.handoffs) {
      expect(['accepted', 'forwarded']).toContain(handoff.kind);
      expect(handoff.from).toBeGreaterThanOrEqual(0);
      expect(handoff.from).toBeLessThan(model.nodeCount);
      expect(handoff.to).toBeGreaterThanOrEqual(0);
      expect(handoff.to).toBeLessThan(model.nodeCount);
    }
  });

  it('does not reveal an acknowledgement before it happens', () => {
    const model = ringModel();
    const ack = model.orderedEvents.find((entry) => entry.kind === 'acknowledged');
    if (!ack || !ack.messageId) throw new Error('expected an acknowledgement');

    const before = buildJourney(model, ack.messageId, {
      untilEpochMillis: ack.atEpochMillis - 1,
    });
    const after = buildJourney(model, ack.messageId, {
      untilEpochMillis: ack.atEpochMillis,
    });

    expect(before.outcome).not.toBe('acknowledged');
    expect(after.outcome).toBe('acknowledged');
    expect(before.steps.length).toBeLessThan(after.steps.length);
    expect(before.limitations.join(' ')).toMatch(/later event/);
  });

  it('cuts the journey at the cursor, not at every event sharing its timestamp', () => {
    const result = validateReport({
      simulation: {
        topology: 'LINE',
        nodeCount: 2,
        startEpochSeconds: 1,
        edges: [[0, 1]],
      },
      events: [
        {
          atEpochMillis: 1000,
          round: 0,
          node: 'n0',
          kind: 'state',
          messageId: 'm1',
          detail: 'QUEUED -> RELAYED: first',
        },
        {
          atEpochMillis: 1000,
          round: 0,
          node: 'n1',
          kind: 'acknowledged',
          messageId: 'm1',
        },
      ],
      messages: [{ id: 'm1', origin: 0, destination: 1, broadcast: false, originatedAt: 1000 }],
      linkChanges: [],
    });
    if (!result.ok) throw new Error(result.error);
    const model = buildReplayModel(result.report);

    const atFirst = buildJourney(model, 'm1', { untilEventIndex: 1 });
    const atSecond = buildJourney(model, 'm1', { untilEventIndex: 2 });

    expect(atFirst.steps).toHaveLength(1);
    expect(atFirst.outcome).not.toBe('acknowledged');
    expect(atSecond.steps).toHaveLength(2);
    expect(atSecond.outcome).toBe('acknowledged');
  });

  it('returns no steps at the very start and says so', () => {
    const model = ringModel();
    const id = model.messageIds[0];
    if (!id) throw new Error('expected a message');
    const journey = buildJourney(model, id, { untilEpochMillis: model.startMillis - 1 });

    expect(journey.steps).toEqual([]);
    expect(journey.handoffs).toEqual([]);
    expect(journey.outcome).toBe('unknown');
    expect(journey.limitations.join(' ')).toMatch(/has happened yet/);
  });

  it('treats a broadcast as having no destination', () => {
    const model = ringModel();
    const broadcast = model.messageIds
      .map((id) => model.messagesById.get(id))
      .find((message) => message?.broadcast);
    if (!broadcast) throw new Error('expected a broadcast');

    const journey = buildJourney(model, broadcast.id);
    expect(journey.broadcast).toBe(true);
    expect(journey.destination).toBeNull();
  });

  it('does not invent provenance or steps for an unknown id', () => {
    const journey = buildJourney(ringModel(), 'not-a-recorded-message');

    expect(journey.origin).toBeNull();
    expect(journey.destination).toBeNull();
    expect(journey.steps).toEqual([]);
    expect(journey.outcome).toBe('unknown');
    expect(journey.limitations.join(' ')).toMatch(/does not record which node composed/);
  });

  it('does not turn a lone rejection into a delivery outcome', () => {
    const result = validateReport({
      simulation: { topology: 'LINE', nodeCount: 2, startEpochSeconds: 1, edges: [[0, 1]] },
      events: [
        {
          atEpochMillis: 1000,
          round: 0,
          node: 'n1',
          kind: 'rejected',
          messageId: 'm1',
          reason: 'unknown-message',
        },
      ],
      messages: [{ id: 'm1', origin: 0, destination: 1, broadcast: false, originatedAt: 1000 }],
      linkChanges: [],
    });
    if (!result.ok) throw new Error(result.error);
    const journey = buildJourney(buildReplayModel(result.report), 'm1');

    expect(journey.outcome).toBe('unknown');
    expect(journey.limitations.join(' ')).toMatch(/rejected event/i);
    expect(journey.limitations.join(' ')).toMatch(/not treated as this message's delivery outcome/i);
  });

  it('keeps the partitioned report honest about what did not happen', () => {
    const result = validateReport(partitionedSample);
    if (!result.ok) throw new Error(result.error);
    const model = buildReplayModel(result.report);
    const acknowledged = model.orderedEvents.filter((entry) => entry.kind === 'acknowledged');

    // The sample ends with five partitions and only two acknowledged directed
    // messages. The journey view must not conjure handoffs for the rest.
    expect(acknowledged.length).toBeGreaterThan(0);
    expect(acknowledged.length).toBeLessThan(result.report.outcome.directedOriginated ?? 0);
    const ackedIds = new Set(acknowledged.map((entry) => entry.messageId));
    const unacknowledged = result.report.messages.find(
      (message) => !message.broadcast && !ackedIds.has(message.id),
    );
    if (!unacknowledged) throw new Error('expected a directed message without an acknowledgement');
    const journey = buildJourney(model, unacknowledged.id);
    expect(journey.outcome).not.toBe('acknowledged');
    expect(journey.steps.every((step) => step.event.simulated)).toBe(true);
  });
});
