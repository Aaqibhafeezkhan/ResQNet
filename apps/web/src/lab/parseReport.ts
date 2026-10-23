/**
 * Parsing and validation of simulator reports.
 *
 * <p>Everything here runs in the browser, on the user's own machine. A report
 * is never uploaded: it is read with the file API, parsed in the tab, and
 * discarded when the tab closes.
 *
 * <p>The parser is written to be forgiving about *missing* data and strict
 * about *wrong* data. A report from a newer simulator that adds a member still
 * loads; a report that is not a report at all is rejected with a message that
 * says why.
 */

import type {
  LabEvent,
  LabEventWindow,
  LabLatency,
  LabLinkChange,
  LabMessage,
  LabNode,
  LabOutcome,
  LabRadio,
  LabReport,
  LabSimulation,
  LabTiming,
} from './types';

/**
 * Largest report the Lab will read.
 *
 * <p>Sixteen megabytes is far beyond any real run: the simulator caps the
 * retained event window at 4 000 events, so a real report is well under a
 * megabyte. The limit exists so that a mistyped path cannot wedge the tab.
 */
export const MAX_REPORT_BYTES = 16 * 1024 * 1024;

/** Largest number of events the Lab will index. */
export const MAX_EVENTS = 200_000;

export interface ParseSuccess {
  ok: true;
  report: LabReport;
  /** Non-fatal observations, e.g. a report from an older simulator. */
  warnings: string[];
}

export interface ParseFailure {
  ok: false;
  /** A message a person can act on. */
  error: string;
  /** The underlying reason, kept for the detail disclosure. */
  detail?: string;
}

export type ParseResult = ParseSuccess | ParseFailure;

type Json = Record<string, unknown>;

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** A number, or `null` when absent or not a number. */
function num(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/**
 * A measurement, where a negative sentinel means "not measured".
 *
 * <p>The simulator reports an unavailable latency as `-1`. Showing that as
 * `-1 ms` would be noise; showing it as `0 ms` would be a lie.
 */
function measured(value: unknown): number | null {
  const parsed = num(value);
  if (parsed === null) return null;
  return parsed < 0 ? null : parsed;
}

function str(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

/**
 * A number, or a string holding one.
 *
 * <p>Used for peer references: the simulator writes them as strings (`"1"`)
 * while every other index in the report is a JSON number. Accepting both is
 * the difference between a journey with handoffs and an empty one.
 */
function numeric(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function bool(value: unknown): boolean | null {
  return typeof value === 'boolean' ? value : null;
}

function obj(value: unknown): Json {
  return isObject(value) ? value : {};
}

/** Reads the node index out of an event's `node` label, e.g. `n4` to 4. */
export function nodeIndexOf(label: string | null): number {
  if (!label) return -1;
  const match = /(?:^|[^0-9])(\d+)\s*$/.exec(label);
  if (!match || match[1] === undefined) return -1;
  const parsed = Number.parseInt(match[1], 10);
  return Number.isFinite(parsed) ? parsed : -1;
}

function readSimulation(raw: Json): LabSimulation {
  const edges: [number, number][] = [];
  const rawEdges = raw.edges;
  if (Array.isArray(rawEdges)) {
    for (const entry of rawEdges) {
      if (!Array.isArray(entry) || entry.length < 2) continue;
      const from = num(entry[0]);
      const to = num(entry[1]);
      if (from === null || to === null) continue;
      edges.push([from, to]);
    }
  }
  return {
    topology: str(raw.topology) ?? 'UNKNOWN',
    nodeCount: num(raw.nodeCount),
    linkCount: num(raw.linkCount),
    degree: num(raw.degree),
    seed: num(raw.seed),
    messageCount: num(raw.messageCount),
    rounds: num(raw.rounds),
    stepMillis: num(raw.stepMillis),
    hopLimit: num(raw.hopLimit),
    linkDropProbability: num(raw.linkDropProbability),
    broadcastRatio: num(raw.broadcastRatio),
    churnEveryRounds: num(raw.churnEveryRounds),
    messageLifetimeSeconds: num(raw.messageLifetimeSeconds),
    startEpochSeconds: num(raw.startEpochSeconds),
    startPartitioned: bool(raw.startPartitioned),
    edges,
  };
}

function readOutcome(raw: Json): LabOutcome {
  return {
    originated: measured(raw.originated),
    directedOriginated: measured(raw.directedOriginated),
    broadcastOriginated: measured(raw.broadcastOriginated),
    acknowledged: measured(raw.acknowledged),
    deliveredRecords: measured(raw.deliveredRecords),
    expired: measured(raw.expired),
    failed: measured(raw.failed),
    stillQueued: measured(raw.stillQueued),
    duplicatesSuppressed: measured(raw.duplicatesSuppressed),
    rejected: measured(raw.rejected),
    forwards: measured(raw.forwards),
  };
}

function readLatency(raw: Json): LabLatency {
  return {
    median: measured(raw.median),
    p95: measured(raw.p95),
    p99: measured(raw.p99),
    max: measured(raw.max),
  };
}

function readRadio(raw: Json): LabRadio {
  return {
    framesSent: measured(raw.framesSent),
    framesDelivered: measured(raw.framesDelivered),
    framesDropped: measured(raw.framesDropped),
    linkChanges: measured(raw.linkChanges),
    partitions: measured(raw.partitions),
  };
}

function readTiming(raw: Json): LabTiming {
  return {
    simulatedMillis: measured(raw.simulatedMillis),
    wallClockMillis: measured(raw.wallClockMillis),
    throughputPerSecond: measured(raw.throughputPerSecond),
  };
}

function readEventWindow(raw: Json, retained: number): LabEventWindow {
  return {
    retained: num(raw.retained) ?? retained,
    total: num(raw.total),
    truncated: bool(raw.truncated),
  };
}

function readEvents(raw: unknown): LabEvent[] {
  const events: LabEvent[] = [];
  if (!Array.isArray(raw)) return events;
  for (const entry of raw) {
    if (!isObject(entry)) continue;
    const atEpochMillis = num(entry.atEpochMillis);
    const label = str(entry.node);
    const kind = str(entry.kind);
    // An event without a time, a node or a kind cannot be placed on a
    // timeline. Skipping it is honest; inventing a timestamp or a node is not.
    if (atEpochMillis === null || !label || !kind) continue;
    const peers: number[] = [];
    if (Array.isArray(entry.peers)) {
      for (const peer of entry.peers) {
        const parsed = numeric(peer);
        if (parsed !== null) peers.push(parsed);
      }
    }
    events.push({
      atEpochMillis,
      round: num(entry.round) ?? -1,
      nodeIndex: nodeIndexOf(label),
      node: label,
      kind,
      messageId: str(entry.messageId),
      peers,
      hopCount: num(entry.hopCount) ?? -1,
      reason: str(entry.reason),
      detail: str(entry.detail),
      simulated: entry.simulated !== false,
    });
  }
  return events;
}

function readNodes(raw: unknown): LabNode[] {
  const nodes: LabNode[] = [];
  if (!Array.isArray(raw)) return nodes;
  for (const entry of raw) {
    if (!isObject(entry)) continue;
    const stateCounts: Record<string, number> = {};
    const rawStates = entry.stateCounts;
    if (isObject(rawStates)) {
      for (const [key, value] of Object.entries(rawStates)) {
        const parsed = num(value);
        if (parsed !== null) stateCounts[key] = parsed;
      }
    }
    nodes.push({
      index: num(entry.index) ?? -1,
      label: str(entry.label) ?? `node-${num(entry.index) ?? '?'}`,
      originated: num(entry.originated),
      accepted: num(entry.accepted),
      forwarded: num(entry.forwarded),
      delivered: num(entry.delivered),
      duplicatesSuppressed: num(entry.duplicatesSuppressed),
      rejected: num(entry.rejected),
      untrustedDrops: num(entry.untrustedDrops),
      rateLimitDrops: num(entry.rateLimitDrops),
      hopLimitDrops: num(entry.hopLimitDrops),
      stillQueued: num(entry.stillQueued),
      medianLatencyMillis: measured(entry.medianLatencyMillis),
      p95LatencyMillis: measured(entry.p95LatencyMillis),
      degreeAtEnd: num(entry.degreeAtEnd),
      stateCounts,
    });
  }
  return nodes;
}

function readMessages(raw: unknown): LabMessage[] {
  const messages: LabMessage[] = [];
  if (!Array.isArray(raw)) return messages;
  for (const entry of raw) {
    if (!isObject(entry)) continue;
    const id = str(entry.id);
    if (!id) continue;
    const broadcast = bool(entry.broadcast) ?? entry.destination === null;
    messages.push({
      id,
      origin: num(entry.origin),
      destination: broadcast ? null : num(entry.destination),
      broadcast,
      originatedAt: num(entry.originatedAt),
    });
  }
  return messages;
}

function readLinkChanges(raw: unknown): LabLinkChange[] {
  const changes: LabLinkChange[] = [];
  if (!Array.isArray(raw)) return changes;
  for (const entry of raw) {
    if (!isObject(entry)) continue;
    const from = num(entry.from);
    const to = num(entry.to);
    if (from === null || to === null) continue;
    changes.push({
      atEpochMillis: num(entry.atEpochMillis) ?? 0,
      round: num(entry.round) ?? -1,
      from,
      to,
      up: bool(entry.up) ?? false,
    });
  }
  return changes;
}

function readHopDistribution(raw: unknown): Record<string, number> {
  const distribution: Record<string, number> = {};
  if (!isObject(raw)) return distribution;
  for (const [key, value] of Object.entries(raw)) {
    const parsed = num(value);
    if (parsed !== null) distribution[key] = parsed;
  }
  return distribution;
}

/**
 * Validates and converts a decoded JSON document into a report.
 *
 * <p>Split from {@link parseReportText} so the validation rules can be tested
 * against hand-written objects without going through the file API.
 */
export function validateReport(input: unknown, source = 'imported report'): ParseResult {
  if (!isObject(input)) {
    return {
      ok: false,
      error: 'This file is not a ResQNet simulation report: the top level is not a JSON object.',
    };
  }

  const warnings: string[] = [];

  if (!isObject(input.simulation)) {
    return {
      ok: false,
      error:
        'This file is not a ResQNet simulation report: it has no "simulation" object describing the scenario.',
    };
  }
  if (!Array.isArray(input.events)) {
    return {
      ok: false,
      error:
        'This file is not a ResQNet simulation report: it has no "events" array to replay.',
    };
  }
  if (input.events.length > MAX_EVENTS) {
    return {
      ok: false,
      error: `This report holds ${input.events.length} events, more than the ${MAX_EVENTS} the Lab will index.`,
    };
  }

  const events = readEvents(input.events);
  if (input.events.length > 0 && events.length === 0) {
    return {
      ok: false,
      error:
        'This report has an "events" array, but none of its entries has a timestamp, a node and a kind. It cannot be replayed.',
    };
  }
  if (events.length < input.events.length) {
    warnings.push(
      `Skipped ${input.events.length - events.length} event ${
        input.events.length - events.length === 1 ? 'entry' : 'entries'
      } that had no timestamp, node or kind.`,
    );
  }
  const nodes = readNodes(input.nodes);
  const messages = readMessages(input.messages);
  const linkChanges = readLinkChanges(input.linkChanges);
  const simulation = readSimulation(obj(input.simulation));

  if (simulation.edges.length === 0) {
    warnings.push(
      'This report does not record the topology edges, so the Lab cannot draw which nodes were connected.',
    );
  }
  if (linkChanges.length === 0) {
    warnings.push(
      'This report does not record link changes, so link failures and recoveries cannot be shown.',
    );
  }
  if (messages.length === 0) {
    warnings.push(
      'This report does not record which node composed each message, so journeys start from the recorded events only.',
    );
  }

  const report: LabReport = {
    simulation,
    outcome: readOutcome(obj(input.outcome)),
    deliveryRate: measured(input.deliveryRate),
    amplificationFactor: measured(input.amplificationFactor),
    latencyMillis: readLatency(obj(input.latencyMillis)),
    hopDistribution: readHopDistribution(input.hopDistribution),
    radio: readRadio(obj(input.radio)),
    timing: readTiming(obj(input.timing)),
    eventWindow: readEventWindow(obj(input.eventWindow), events.length),
    linkChanges,
    messages,
    nodes,
    events,
    source,
  };

  return { ok: true, report, warnings };
}

/**
 * Parses report text.
 *
 * <p>The size check happens before `JSON.parse` so that an enormous file is
 * rejected without being decoded.
 */
export function parseReportText(text: string, source = 'imported report'): ParseResult {
  if (text.length === 0) {
    return { ok: false, error: 'That file is empty.' };
  }
  if (text.length > MAX_REPORT_BYTES) {
    return {
      ok: false,
      error: `That report is ${(text.length / (1024 * 1024)).toFixed(1)} MB. The Lab reads reports up to ${MAX_REPORT_BYTES / (1024 * 1024)} MB.`,
    };
  }
  let decoded: unknown;
  try {
    decoded = JSON.parse(text);
  } catch (cause) {
    return {
      ok: false,
      error: 'That file is not valid JSON.',
      detail: cause instanceof Error ? cause.message : String(cause),
    };
  }
  return validateReport(decoded, source);
}

/**
 * Reads a report from a file the user chose.
 *
 * <p>The file is read locally. Nothing is sent anywhere. `File.text()` is used
 * where the browser provides it; the FileReader path exists for older engines
 * and for the test DOM, which does not implement `text()` on File.
 */
export async function parseReportFile(file: File): Promise<ParseResult> {
  if (file.size > MAX_REPORT_BYTES) {
    return {
      ok: false,
      error: `That file is ${(file.size / (1024 * 1024)).toFixed(1)} MB. The Lab reads reports up to ${MAX_REPORT_BYTES / (1024 * 1024)} MB.`,
    };
  }
  let text: string;
  try {
    text = typeof file.text === 'function' ? await file.text() : await readWithFileReader(file);
  } catch (cause) {
    return {
      ok: false,
      error: 'That file could not be read.',
      detail: cause instanceof Error ? cause.message : String(cause),
    };
  }
  return parseReportText(text, file.name);
}

function readWithFileReader(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      resolve(typeof reader.result === 'string' ? reader.result : '');
    };
    reader.onerror = () => {
      reject(reader.error ?? new Error('The file could not be read.'));
    };
    reader.readAsText(file);
  });
}
