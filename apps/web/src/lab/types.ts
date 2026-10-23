/**
 * The shape of a ResQNet simulator report, as consumed by the Replay Lab.
 *
 * <p>These types describe the JSON that `SimulationReport#toJson()` writes.
 * They are deliberately narrower than the file: the Lab reads the members it
 * understands and treats everything else as absent rather than failing, so a
 * report produced by a newer simulator still loads.
 *
 * <p>Every optional measurement is `number | null` rather than `number`. The
 * simulator reports "not measured" as `-1`, and a missing member means the
 * same thing. Turning either into `0` would put a fabricated number on a
 * safety-adjacent dashboard.
 */

/** The scenario the run was configured with. */
export interface LabSimulation {
  topology: string;
  nodeCount: number | null;
  linkCount: number | null;
  /** Target degree used by the mesh and random shapes. */
  degree: number | null;
  seed: number | null;
  messageCount: number | null;
  rounds: number | null;
  stepMillis: number | null;
  hopLimit: number | null;
  linkDropProbability: number | null;
  broadcastRatio: number | null;
  churnEveryRounds: number | null;
  messageLifetimeSeconds: number | null;
  startEpochSeconds: number | null;
  startPartitioned: boolean | null;
  /** Initial topology edges as recorded by the simulator. */
  edges: readonly [number, number][];
}

/** How many messages ended in each state. */
export interface LabOutcome {
  originated: number | null;
  directedOriginated: number | null;
  broadcastOriginated: number | null;
  acknowledged: number | null;
  deliveredRecords: number | null;
  expired: number | null;
  failed: number | null;
  stillQueued: number | null;
  duplicatesSuppressed: number | null;
  rejected: number | null;
  forwards: number | null;
}

/** Simulated latency distribution. `-1` in the file means "not measured". */
export interface LabLatency {
  median: number | null;
  p95: number | null;
  p99: number | null;
  max: number | null;
}

/** Synthetic radio statistics. */
export interface LabRadio {
  framesSent: number | null;
  framesDelivered: number | null;
  framesDropped: number | null;
  linkChanges: number | null;
  partitions: number | null;
}

/** Simulated and CPU timings. */
export interface LabTiming {
  simulatedMillis: number | null;
  wallClockMillis: number | null;
  throughputPerSecond: number | null;
}

/** Per-node counters for one simulated handset. */
export interface LabNode {
  index: number;
  label: string;
  originated: number | null;
  accepted: number | null;
  forwarded: number | null;
  delivered: number | null;
  duplicatesSuppressed: number | null;
  rejected: number | null;
  untrustedDrops: number | null;
  rateLimitDrops: number | null;
  hopLimitDrops: number | null;
  stillQueued: number | null;
  medianLatencyMillis: number | null;
  p95LatencyMillis: number | null;
  degreeAtEnd: number | null;
  stateCounts: Record<string, number>;
}

/** One recorded routing event, flattened as the simulator writes it. */
export interface LabEvent {
  atEpochMillis: number;
  round: number;
  /** Node index the event happened on. */
  nodeIndex: number;
  /** Stable short label, e.g. `n4`. */
  node: string;
  kind: string;
  messageId: string | null;
  /** Node indices of the peers this event names. */
  peers: readonly number[];
  hopCount: number;
  reason: string | null;
  detail: string | null;
  simulated: boolean;
}

/** A message that was composed during the run. */
export interface LabMessage {
  id: string;
  origin: number | null;
  /** `null` for a broadcast: a broadcast names nobody. */
  destination: number | null;
  broadcast: boolean;
  originatedAt: number | null;
}

/** One recorded link transition. */
export interface LabLinkChange {
  atEpochMillis: number;
  round: number;
  from: number;
  to: number;
  up: boolean;
}

/** How much of the event history the report kept. */
export interface LabEventWindow {
  retained: number | null;
  total: number | null;
  truncated: boolean | null;
}

/** A parsed simulator report. */
export interface LabReport {
  simulation: LabSimulation;
  outcome: LabOutcome;
  deliveryRate: number | null;
  amplificationFactor: number | null;
  latencyMillis: LabLatency;
  hopDistribution: Record<string, number>;
  radio: LabRadio;
  timing: LabTiming;
  eventWindow: LabEventWindow;
  linkChanges: readonly LabLinkChange[];
  messages: readonly LabMessage[];
  nodes: readonly LabNode[];
  events: readonly LabEvent[];
  /** Where this report came from, for display only. */
  source: string;
}

/** Event kinds the simulator can record. */
export const EVENT_KINDS = [
  'accepted',
  'forwarded',
  'delivered',
  'acknowledged',
  'acknowledgement-sent',
  'peer-connected',
  'peer-disconnected',
  'rejected',
  'state',
  'triage',
  'backpressure',
  'other',
] as const;

export type EventKind = (typeof EVENT_KINDS)[number];

/**
 * What each event kind means, in plain words.
 *
 * <p>Used verbatim in the Lab so that a reader who has never studied mesh
 * networking can tell a handoff from a delivery. The wording is deliberately
 * literal: "handed to" is what the radio did, "delivered" is what the
 * destination did.
 */
export const EVENT_KIND_EXPLANATIONS: Record<EventKind, string> = {
  accepted:
    'This relay validated the envelope and took it on. It says the frame arrived from the named peer.',
  forwarded:
    'This relay handed the message to one or more neighbouring devices. A handoff is not a delivery.',
  delivered: 'The message reached the inbox of the device it was addressed to.',
  acknowledged:
    'A signed acknowledgement came back from the recipient. This is the only end-to-end confirmation.',
  'acknowledgement-sent':
    'This device produced an acknowledgement because it received the message.',
  'peer-connected': 'A neighbouring device came into radio range.',
  'peer-disconnected': 'A neighbouring device went out of range or was switched off.',
  rejected:
    'This node refused the frame. The reason is recorded; it does not mean the message failed to arrive elsewhere.',
  state: 'A stored copy moved between delivery states, such as queued, relayed or expired.',
  triage: 'The offline classifier suggested a category for the message. Advisory only.',
  backpressure: 'A relay had no room in its queue and applied backpressure.',
  other: 'An event the Lab does not interpret. The raw record is shown instead.',
};

/** Kinds that describe a message moving between nodes. */
export const MESSAGE_EVENT_KINDS: readonly EventKind[] = [
  'accepted',
  'forwarded',
  'delivered',
  'acknowledged',
  'acknowledgement-sent',
  'rejected',
  'state',
  'triage',
  'backpressure',
];
