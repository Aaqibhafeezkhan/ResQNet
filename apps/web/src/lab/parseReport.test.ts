import { describe, expect, it } from 'vitest';

import {
  MAX_EVENTS,
  MAX_REPORT_BYTES,
  nodeIndexOf,
  parseReportText,
  validateReport,
  type ParseResult,
  type ParseSuccess,
} from './parseReport';
import ringSample from './fixtures/ring-6-seed-42.json';
import partitionedSample from './fixtures/two-cluster-6-seed-42.json';

function expectOk(result: ParseResult): ParseSuccess {
  if (!result.ok) throw new Error(`expected a parsed report, got: ${result.error}`);
  return result;
}

const minimal = {
  simulation: {
    topology: 'RING',
    nodeCount: 2,
    seed: 1,
    edges: [
      [0, 1],
    ],
  },
  events: [
    {
      atEpochMillis: 1000,
      round: 0,
      node: 'n0',
      kind: 'forwarded',
      messageId: 'm1',
      peers: ['1'],
    },
  ],
  messages: [{ id: 'm1', origin: 0, destination: 1, broadcast: false, originatedAt: 1000 }],
  linkChanges: [{ atEpochMillis: 1000, round: 0, from: 0, to: 1, up: true }],
};

describe('validateReport', () => {
  it('parses a real ring report with every recorded field', () => {
    const result = expectOk(validateReport(ringSample, 'ring fixture'));

    expect(result.report.simulation.topology).toBe('RING');
    expect(result.report.simulation.nodeCount).toBe(6);
    expect(result.report.simulation.seed).toBe(42);
    expect(result.report.simulation.degree).toBe(3);
    expect(result.report.simulation.startPartitioned).toBe(false);
    expect(result.report.simulation.edges).toHaveLength(6);
    expect(result.report.events).toHaveLength(558);
    expect(result.report.nodes).toHaveLength(6);
    expect(result.report.messages).toHaveLength(10);
    expect(result.report.linkChanges.length).toBeGreaterThan(0);
    expect(result.report.eventWindow.retained).toBe(558);
    expect(result.report.eventWindow.total).toBe(558);
    expect(result.report.eventWindow.truncated).toBe(false);
    expect(result.report.source).toBe('ring fixture');
    expect(result.warnings).toEqual([]);
  });

  it('parses the partitioned sample without inventing a delivery rate', () => {
    const result = expectOk(validateReport(partitionedSample));

    expect(result.report.simulation.startPartitioned).toBe(true);
    expect(result.report.simulation.edges).toHaveLength(4);
    expect(result.report.outcome.directedOriginated).toBe(6);
    expect(result.report.outcome.acknowledged).toBe(2);
    expect(result.report.radio.partitions).toBe(5);
    expect(result.report.deliveryRate).toBeCloseTo(2 / 6, 5);
    expect(result.report.events).toHaveLength(129);
  });

  it('coerces recorded peer labels to node indices', () => {
    const result = expectOk(validateReport(minimal));

    expect(result.report.events[0]?.peers).toEqual([1]);
    expect(result.report.events[0]?.nodeIndex).toBe(0);
  });

  it('turns the -1 sentinel into unavailable, never zero', () => {
    const result = expectOk(
      validateReport({
        simulation: { topology: 'RING', nodeCount: 2 },
        events: [],
        deliveryRate: -1,
        amplificationFactor: -1,
        outcome: { originated: 3, expired: -1, acknowledged: -1 },
        latencyMillis: { median: -1, p95: -1, p99: -1, max: -1 },
        radio: { framesSent: -1, framesDropped: -1, partitions: -1 },
        timing: { simulatedMillis: -1, wallClockMillis: -1, throughputPerSecond: -1 },
      }),
    );

    expect(result.report.deliveryRate).toBeNull();
    expect(result.report.amplificationFactor).toBeNull();
    expect(result.report.outcome.originated).toBe(3);
    expect(result.report.outcome.expired).toBeNull();
    expect(result.report.outcome.acknowledged).toBeNull();
    expect(result.report.latencyMillis.median).toBeNull();
    expect(result.report.radio.framesSent).toBeNull();
    expect(result.report.radio.partitions).toBeNull();
    expect(result.report.timing.simulatedMillis).toBeNull();
    expect(result.report.timing.wallClockMillis).toBeNull();
  });

  it('keeps a real zero distinct from an unavailable value', () => {
    const result = expectOk(
      validateReport({
        ...minimal,
        deliveryRate: 0,
        latencyMillis: { median: 0, p95: 0, p99: 0, max: 0 },
        radio: { framesSent: 0, framesDropped: 0, partitions: 0 },
      }),
    );

    expect(result.report.deliveryRate).toBe(0);
    expect(result.report.latencyMillis.median).toBe(0);
    expect(result.report.radio.framesSent).toBe(0);
  });

  it('warns instead of failing when newer fields are absent', () => {
    const result = expectOk(
      validateReport({
        simulation: { topology: 'LINE' },
        events: [],
      }),
    );

    expect(result.report.simulation.edges).toEqual([]);
    expect(result.report.messages).toEqual([]);
    expect(result.warnings).toHaveLength(3);
    expect(result.warnings.join(' ')).toMatch(/topology edges/);
    expect(result.warnings.join(' ')).toMatch(/link changes/);
    expect(result.warnings.join(' ')).toMatch(/composed messages/);
  });

  it('rejects an events array where no entry can be placed on a timeline', () => {
    const result = validateReport({
      simulation: { topology: 'LINE' },
      events: [{}, { node: 'n0' }, { atEpochMillis: 1000, node: 'n0' }],
      messages: [],
      linkChanges: [],
    });

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.error).toMatch(/none of its entries has a timestamp, a node and a kind/i);
    }
  });

  it('loads the events it can read and warns about the ones it skipped', () => {
    const result = expectOk(
      validateReport({
        ...minimal,
        events: [...minimal.events, { node: 'n1', kind: 'state' }, {}],
      }),
    );

    expect(result.report.events).toHaveLength(1);
    expect(result.warnings.join(' ')).toMatch(/skipped 2 event entries/i);
  });

  it('skips malformed link changes instead of inventing a cut at epoch 0', () => {
    const result = expectOk(
      validateReport({
        ...minimal,
        linkChanges: [
          { from: 0, to: 1, up: false },
          { atEpochMillis: 1000, from: 0, to: 1 },
          { atEpochMillis: 1000, from: 0, to: 1, up: false },
        ],
      }),
    );

    expect(result.report.linkChanges).toEqual([
      { atEpochMillis: 1000, round: -1, from: 0, to: 1, up: false },
    ]);
    expect(result.warnings.join(' ')).toMatch(/skipped 2 link-change entries/i);
  });

  it('normalises the old -1 broadcast sentinel to no destination', () => {
    const result = expectOk(
      validateReport({
        ...minimal,
        messages: [
          { id: 'm1', origin: 0, destination: -1, broadcast: false, originatedAt: 1000 },
        ],
      }),
    );

    expect(result.report.messages[0]?.broadcast).toBe(true);
    expect(result.report.messages[0]?.destination).toBeNull();
  });

  it('rejects node indices that would make the map unbounded', () => {
    const tooMany = validateReport({
      simulation: { topology: 'LINE', nodeCount: 1_000_000, edges: [] },
      events: [],
    });
    expect(tooMany.ok).toBe(false);
    if (!tooMany.ok) expect(tooMany.error).toMatch(/at most 500 nodes/i);

    const peer = validateReport({
      simulation: { topology: 'LINE', nodeCount: 2, edges: [[0, 1]] },
      events: [
        {
          atEpochMillis: 1000,
          node: 'n0',
          kind: 'forwarded',
          peers: [1_000_000],
        },
      ],
    });
    expect(peer.ok).toBe(false);
    if (!peer.ok) expect(peer.error).toMatch(/node index 1000000/i);
  });

  it('drops an epoch-0 unknown event when the report declares a real start clock', () => {
    const result = expectOk(
      validateReport({
        simulation: { topology: 'LINE', nodeCount: 2, startEpochSeconds: 1_760_000_000 },
        events: [
          { atEpochMillis: 0, node: 'n0', kind: 'other' },
          { atEpochMillis: 1_760_000_000_000, node: 'n0', kind: 'state' },
        ],
      }),
    );

    expect(result.report.events).toHaveLength(1);
    expect(result.warnings.join(' ')).toMatch(/no positive simulated timestamp/i);
  });

  it('rejects a file that is not a report, with an actionable message', () => {
    const notAnObject = validateReport('hello');
    expect(notAnObject.ok).toBe(false);
    if (!notAnObject.ok) expect(notAnObject.error).toMatch(/not a ResQNet simulation report/i);

    const noSimulation = validateReport({ events: [] });
    expect(noSimulation.ok).toBe(false);
    if (!noSimulation.ok) expect(noSimulation.error).toMatch(/"simulation" object/);

    const noEvents = validateReport({ simulation: {} });
    expect(noEvents.ok).toBe(false);
    if (!noEvents.ok) expect(noEvents.error).toMatch(/"events" array/);
  });

  it('refuses more events than it will index', () => {
    const result = validateReport({
      simulation: {},
      // Only the length is read before the refusal, so a sparse array is enough.
      events: new Array(MAX_EVENTS + 1),
    });

    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toMatch(/more than the 200000/);
  });

  it('refuses a report larger than the import limit before parsing it', () => {
    const oversized = 'x'.repeat(MAX_REPORT_BYTES + 1);
    const result = parseReportText(oversized);

    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toMatch(/reads reports up to 16 MB/);
  });

  it('explains malformed JSON and keeps the parser error as detail', () => {
    const result = parseReportText('{"simulation":');

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.error).toMatch(/not valid JSON/);
      expect(result.detail).toBeTruthy();
    }
  });

  it('rejects an empty file', () => {
    const result = parseReportText('');
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.error).toMatch(/empty/);
  });

  it('accepts a valid report encoded as text', () => {
    const result = parseReportText(JSON.stringify(minimal), 'pasted.json');
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.report.source).toBe('pasted.json');
  });
});

describe('nodeIndexOf', () => {
  it('reads the trailing index from the labels the simulator writes', () => {
    expect(nodeIndexOf('n0')).toBe(0);
    expect(nodeIndexOf('n12')).toBe(12);
    expect(nodeIndexOf('node-7')).toBe(7);
  });

  it('returns -1 when there is no index, rather than guessing zero', () => {
    expect(nodeIndexOf(null)).toBe(-1);
    expect(nodeIndexOf('')).toBe(-1);
    expect(nodeIndexOf('field-post')).toBe(-1);
  });
});
