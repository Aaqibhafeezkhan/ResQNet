/**
 * Replay state built from a parsed simulator report.
 *
 * <p>Two things happen here that the report does not do for itself.
 *
 * <p>First, the events are put into a deterministic total order. The
 * simulator groups events by the node that produced them, so the array in the
 * file is not a timeline. The order below is total — two runs of the same
 * report always produce the same sequence — and it never changes a recorded
 * timestamp.
 *
 * <p>Second, the events are indexed by message and by node, so selecting a
 * message or scrubbing to a point in time is a lookup rather than a scan.
 */

import type { LabEvent, LabLinkChange, LabMessage, LabReport } from './types';

/** A handoff between two nodes that the simulator recorded. */
export interface RecordedHandoff {
  from: number;
  to: number;
  atEpochMillis: number;
  /**
   * `accepted` means the frame arrived and the receiving node validated it.
   * `forwarded` means the frame was handed to the radio, which is not proof
   * that it arrived.
   */
  kind: 'accepted' | 'forwarded';
}

export interface ReplayModel {
  /** Every event, in the deterministic replay order. */
  orderedEvents: readonly LabEvent[];
  eventsByMessage: Map<string, LabEvent[]>;
  eventsByNode: Map<number, LabEvent[]>;
  messagesById: Map<string, LabMessage>;
  /**
   * Every message id the report names, sorted: composed messages first-class,
   * and ids that appear only in the event stream (acknowledgements, relays)
   * kept visible rather than dropped.
   */
  messageIds: readonly string[];
  nodeCount: number;
  startMillis: number;
  endMillis: number;
  /** Initial topology edges, as recorded by the simulator. */
  initialEdges: readonly [number, number][];
  /** Link changes in deterministic time order. */
  linkChanges: readonly LabLinkChange[];
  /** True when the report recorded the topology edges. */
  hasTopology: boolean;
  /** True when the report recorded link changes. */
  hasLinkChanges: boolean;
  /** True when the report recorded which node composed each message. */
  hasMessageProvenance: boolean;
}

/**
 * Compares two strings without locale rules.
 *
 * <p>`localeCompare` depends on the ICU data shipped with the browser, so two
 * machines could order the same report differently. A replay that is not
 * reproducible across machines is not a replay.
 */
function compareStrings(a: string, b: string): number {
  if (a === b) return 0;
  return a < b ? -1 : 1;
}

/**
 * The deterministic replay order.
 *
 * <p>Timestamp, then round, then node, then kind, then message id, then the
 * detail, then the original position. The last term makes the order total, so
 * two identical events still have a defined order.
 */
export function compareEvents(a: LabEvent, b: LabEvent): number {
  if (a.atEpochMillis !== b.atEpochMillis) return a.atEpochMillis - b.atEpochMillis;
  if (a.round !== b.round) return a.round - b.round;
  if (a.nodeIndex !== b.nodeIndex) return a.nodeIndex - b.nodeIndex;
  const byKind = compareStrings(a.kind, b.kind);
  if (byKind !== 0) return byKind;
  const byMessage = compareStrings(a.messageId ?? '', b.messageId ?? '');
  if (byMessage !== 0) return byMessage;
  return compareStrings(a.detail ?? '', b.detail ?? '');
}

/** Sorts a copy of the events into the deterministic replay order. */
export function orderEvents(events: readonly LabEvent[]): LabEvent[] {
  return events
    .map((event, index) => ({ event, index }))
    .sort((left, right) => {
      const byEvent = compareEvents(left.event, right.event);
      if (byEvent !== 0) return byEvent;
      return left.index - right.index;
    })
    .map((entry) => entry.event);
}

/** Builds the replay model for a report. */
export function buildReplayModel(report: LabReport): ReplayModel {
  const orderedEvents = orderEvents(report.events);

  const eventsByMessage = new Map<string, LabEvent[]>();
  const eventsByNode = new Map<number, LabEvent[]>();
  let maxNode = -1;

  for (const event of orderedEvents) {
    if (event.nodeIndex > maxNode) maxNode = event.nodeIndex;
    for (const peer of event.peers) {
      if (peer > maxNode) maxNode = peer;
    }
    if (event.messageId) {
      const bucket = eventsByMessage.get(event.messageId);
      if (bucket) bucket.push(event);
      else eventsByMessage.set(event.messageId, [event]);
    }
    const nodeBucket = eventsByNode.get(event.nodeIndex);
    if (nodeBucket) nodeBucket.push(event);
    else eventsByNode.set(event.nodeIndex, [event]);
  }

  const messagesById = new Map<string, LabMessage>();
  for (const message of report.messages) {
    messagesById.set(message.id, message);
  }

  const declaredStartSeconds = report.simulation.startEpochSeconds;
  const declaredStartMillis = declaredStartSeconds === null ? null : declaredStartSeconds * 1000;
  const firstEventMillis = orderedEvents[0]?.atEpochMillis ?? null;
  const startMillis =
    declaredStartMillis !== null && firstEventMillis !== null
      ? Math.min(declaredStartMillis, firstEventMillis)
      : (declaredStartMillis ?? firstEventMillis ?? 0);
  const endMillis = orderedEvents.length > 0 ? orderedEvents[orderedEvents.length - 1]!.atEpochMillis : startMillis;

  const linkChanges = [...report.linkChanges].sort((a, b) => {
    if (a.atEpochMillis !== b.atEpochMillis) return a.atEpochMillis - b.atEpochMillis;
    if (a.round !== b.round) return a.round - b.round;
    if (a.from !== b.from) return a.from - b.from;
    return a.to - b.to;
  });

  const declaredNodes = report.simulation.nodeCount ?? 0;
  const nodeCount = Math.max(declaredNodes, maxNode + 1, report.nodes.length);

  // A message can be recorded as composed but have no event in the retained
  // window, and an event can name a message the summary does not list. The
  // picker offers the union so neither becomes invisible.
  const messageIdSet = new Set<string>(eventsByMessage.keys());
  for (const message of report.messages) messageIdSet.add(message.id);
  const messageIds = [...messageIdSet].sort(compareStrings);

  return {
    orderedEvents,
    eventsByMessage,
    eventsByNode,
    messagesById,
    messageIds,
    nodeCount,
    startMillis,
    endMillis,
    initialEdges: report.simulation.edges,
    linkChanges,
    hasTopology: report.simulation.edges.length > 0,
    hasLinkChanges: linkChanges.length > 0,
    hasMessageProvenance: report.messages.length > 0,
  };
}

/** Key for an undirected edge. */
export function edgeKey(a: number, b: number): string {
  return a <= b ? `${a}:${b}` : `${b}:${a}`;
}

/**
 * The links that were up at a point in simulated time.
 *
 * <p>Built from the recorded topology edges and the recorded link changes.
 * Nothing here is inferred: a link is either in the topology or it was
 * recorded as coming up or going down.
 */
export function activeEdgesAt(model: ReplayModel, atEpochMillis: number): Set<string> {
  const active = new Set<string>();
  for (const [from, to] of model.initialEdges) {
    active.add(edgeKey(from, to));
  }
  for (const change of model.linkChanges) {
    if (change.atEpochMillis > atEpochMillis) break;
    const key = edgeKey(change.from, change.to);
    if (change.up) active.add(key);
    else active.delete(key);
  }
  return active;
}

/** The state of every link at a point in time, as up or down. */
export interface LinkState {
  key: string;
  from: number;
  to: number;
  up: boolean;
  /** When the link last changed state, in simulated milliseconds. */
  changedAt: number;
}

/**
 * Per-link state at a point in time.
 *
 * <p>Used to draw a cut link differently from a healthy one, and to say when
 * it changed rather than merely that it is down.
 */
export function linkStatesAt(model: ReplayModel, atEpochMillis: number): LinkState[] {
  const states = new Map<string, LinkState>();
  for (const [from, to] of model.initialEdges) {
    states.set(edgeKey(from, to), { key: edgeKey(from, to), from, to, up: true, changedAt: model.startMillis });
  }
  for (const change of model.linkChanges) {
    if (change.atEpochMillis > atEpochMillis) break;
    const key = edgeKey(change.from, change.to);
    states.set(key, {
      key,
      from: change.from,
      to: change.to,
      up: change.up,
      changedAt: change.atEpochMillis,
    });
  }
  return [...states.values()].sort((a, b) => compareStrings(a.key, b.key));
}

/** The number of events that had happened by a point in simulated time. */
export function cursorForTime(model: ReplayModel, atEpochMillis: number): number {
  const events = model.orderedEvents;
  let low = 0;
  let high = events.length;
  while (low < high) {
    const mid = (low + high) >>> 1;
    const event = events[mid];
    if (event && event.atEpochMillis <= atEpochMillis) low = mid + 1;
    else high = mid;
  }
  return low;
}

/** The simulated time at a cursor position. */
export function simTimeAtCursor(model: ReplayModel, cursor: number): number {
  if (cursor <= 0) return model.startMillis;
  const index = Math.min(cursor, model.orderedEvents.length) - 1;
  const event = model.orderedEvents[index];
  return event ? event.atEpochMillis : model.startMillis;
}

/** How a message ended, judged only from what was recorded. */
export type JourneyOutcome =
  | 'acknowledged'
  | 'delivered'
  | 'rejected'
  | 'expired'
  | 'failed'
  | 'in-flight'
  | 'unknown';

export const JOURNEY_OUTCOME_LABELS: Record<JourneyOutcome, string> = {
  acknowledged: 'Acknowledged end to end',
  delivered: 'Delivered, not acknowledged',
  rejected: 'Refused by a node',
  expired: 'Expired before delivery',
  failed: 'Failed in transit',
  'in-flight': 'Still moving when the run ended',
  unknown: 'No recorded outcome',
};

/** One recorded event in a message's journey. */
export interface JourneyStep {
  event: LabEvent;
  /** A plain-language description of what the node did. */
  description: string;
  /** Nodes this event names, in the order the simulator recorded them. */
  peers: readonly number[];
}

/** What the Lab can and cannot say about one message. */
export interface Journey {
  messageId: string;
  origin: number | null;
  destination: number | null;
  broadcast: boolean;
  steps: JourneyStep[];
  handoffs: RecordedHandoff[];
  outcome: JourneyOutcome;
  /** Plain statements about the limits of this reconstruction. */
  limitations: string[];
}

const OUTCOME_PRIORITY: JourneyOutcome[] = [
  'acknowledged',
  'delivered',
  'rejected',
  'expired',
  'failed',
  'in-flight',
  'unknown',
];

/**
 * Reads the destination state out of a recorded state-change detail.
 *
 * <p>The simulator writes the transition as `PREVIOUS -> CURRENT: note`. The
 * Lab reads the current state from that recorded text rather than inventing
 * a parallel field.
 */
export function parseStateTransition(detail: string | null): string | null {
  if (!detail) return null;
  const match = /^\s*([A-Z_]+)\s*->\s*([A-Z_]+)\s*:/.exec(detail);
  return match?.[2] ?? null;
}

function describeStep(event: LabEvent, nodeLabel: (index: number) => string): string {
  const where = nodeLabel(event.nodeIndex);
  switch (event.kind) {
    case 'accepted': {
      const from = event.peers[0];
      const source = from === undefined ? 'a neighbour' : nodeLabel(from);
      return `${where} accepted the message from ${source}.`;
    }
    case 'forwarded': {
      if (event.peers.length === 0) return `${where} forwarded the message.`;
      const targets = event.peers.map(nodeLabel).join(', ');
      return `${where} handed the message to ${targets}. The radio took the frame; arrival is not confirmed.`;
    }
    case 'delivered':
      return `${where} received the message in its inbox.`;
    case 'acknowledged': {
      const by = event.peers[0];
      const who = by === undefined ? 'the recipient' : nodeLabel(by);
      return `${where} was told that ${who} confirmed receipt.`;
    }
    case 'acknowledgement-sent': {
      const to = event.peers[0];
      const who = to === undefined ? 'the sender' : nodeLabel(to);
      return `${where} sent an acknowledgement back to ${who}.`;
    }
    case 'rejected': {
      const from = event.peers[0];
      const who = from === undefined ? 'a neighbour' : nodeLabel(from);
      return `${where} refused a frame from ${who}${
        event.reason ? ` (${event.reason.toLowerCase().replace(/_/g, ' ')})` : ''
      }.`;
    }
    case 'state': {
      const current = parseStateTransition(event.detail);
      return current
        ? `${where} moved its copy of the message to ${current.toLowerCase().replace(/_/g, ' ')}.`
        : `${where} changed the delivery state of its copy.`;
    }
    case 'peer-connected':
      return `${where} came into range of ${nodeLabel(event.peers[0] ?? -1)}.`;
    case 'peer-disconnected':
      return `${where} went out of range of ${nodeLabel(event.peers[0] ?? -1)}.`;
    case 'triage':
      return `${where} classified the message. Advisory only.`;
    case 'backpressure':
      return `${where} applied backpressure: ${event.detail ?? 'its queue was full'}.`;
    default:
      return `${where}: ${event.detail ?? event.kind}.`;
  }
}

/**
 * Builds the journey for one message.
 *
 * <p>Everything in the result is a recorded event. The handoffs are the
 * relationships the simulator recorded: a `forwarded` event says a frame was
 * handed to the radio for a peer, and an `accepted` event says a frame
 * arrived from a peer and was validated. Neither is a claim about a unique
 * route, and the limitations say so.
 *
 * <p>When `untilEpochMillis` is given, events after that simulated time are
 * excluded and the outcome describes the message at that moment. The replay
 * uses this so the inspector does not reveal an acknowledgement that has not
 * happened yet.
 */
export function buildJourney(
  model: ReplayModel,
  messageId: string,
  options: { untilEpochMillis?: number } = {},
): Journey {
  const allEvents = model.eventsByMessage.get(messageId) ?? [];
  const until = options.untilEpochMillis;
  const events =
    until === undefined ? allEvents : allEvents.filter((event) => event.atEpochMillis <= until);
  const message = model.messagesById.get(messageId);

  const nodeLabel = (index: number): string =>
    index >= 0 ? `node ${index}` : 'an unknown node';

  const steps: JourneyStep[] = events.map((event) => ({
    event,
    description: describeStep(event, nodeLabel),
    peers: event.peers,
  }));

  const handoffs: RecordedHandoff[] = [];
  for (const event of events) {
    if (event.kind === 'accepted') {
      const from = event.peers[0];
      if (from !== undefined) {
        handoffs.push({ from, to: event.nodeIndex, atEpochMillis: event.atEpochMillis, kind: 'accepted' });
      }
    } else if (event.kind === 'forwarded') {
      for (const peer of event.peers) {
        handoffs.push({ from: event.nodeIndex, to: peer, atEpochMillis: event.atEpochMillis, kind: 'forwarded' });
      }
    }
  }

  const outcome = deriveOutcome(events);

  const limitations: string[] = [];
  if (!message) {
    limitations.push(
      'This report does not record which node composed this message, so its origin and destination are unknown.',
    );
  }
  limitations.push(
    'A mesh floods: several copies of a message can travel at once. These are the handoffs the simulator recorded, not one reconstructed route.',
  );
  if (steps.length === 0) {
    limitations.push(
      allEvents.length > 0
        ? 'No event for this message has happened yet in the replay. Move the cursor forward to see it.'
        : 'No recorded event mentions this message id. It may have been composed before the retained event window began.',
    );
  } else if (events.length < allEvents.length) {
    limitations.push(
      `Showing the message up to this point in the replay. ${allEvents.length - events.length} later ${
        allEvents.length - events.length === 1 ? 'event is' : 'events are'
      } hidden.`,
    );
  }

  return {
    messageId,
    origin: message?.origin ?? null,
    destination: message?.destination ?? null,
    broadcast: message?.broadcast ?? false,
    steps,
    handoffs,
    outcome,
    limitations,
  };
}

function deriveOutcome(events: readonly LabEvent[]): JourneyOutcome {
  const candidates = new Set<JourneyOutcome>();
  let sawState = false;

  for (const event of events) {
    if (event.kind === 'acknowledged') candidates.add('acknowledged');
    else if (event.kind === 'delivered') candidates.add('delivered');
    else if (event.kind === 'rejected') candidates.add('rejected');
    else if (event.kind === 'state') {
      sawState = true;
      const current = parseStateTransition(event.detail);
      if (current === 'EXPIRED') candidates.add('expired');
      else if (current === 'FAILED') candidates.add('failed');
    }
  }

  if (candidates.size === 0) return sawState ? 'in-flight' : 'unknown';
  for (const outcome of OUTCOME_PRIORITY) {
    if (candidates.has(outcome)) return outcome;
  }
  return 'unknown';
}
