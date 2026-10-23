/**
 * Side-by-side comparison of two simulator reports.
 *
 * <p>The rule this module is built around: a comparison is only described as
 * controlled when every configuration dimension is identical. If two things
 * changed at once, the Lab says so and does not attribute the difference to
 * either of them.
 */

import { formatMetric, type LabMetrics } from './metrics';
import { extractMetrics } from './metrics';
import { TOPOLOGY_SHAPES, shellQuote } from './scenario';
import type { LabReport } from './types';

/** The start clock the Java CLI hardcodes in `Options.toConfig()`. */
const CLI_START_EPOCH_SECONDS = 1_760_000_000;

export interface ComparisonRow {
  key: string;
  label: string;
  kind: 'rate' | 'milliseconds' | 'count' | 'factor';
  left: number | null;
  right: number | null;
  /** `right - left`, or `null` when either side was not measured. */
  delta: number | null;
  /** `delta / left`, or `null` when left is zero or unavailable. */
  relative: number | null;
  /** True when both reports measured this. */
  comparable: boolean;
}

export interface ConfigDifference {
  key: string;
  label: string;
  left: string;
  right: string;
}

export interface Comparison {
  left: LabReport;
  right: LabReport;
  rows: ComparisonRow[];
  differences: ConfigDifference[];
  /**
   * Dimensions that one or both reports did not record. They are not
   * differences, but they prevent the comparison from being called controlled.
   */
  incompleteDimensions: string[];
  /**
   * True only when the two runs used exactly the same recorded configuration,
   * including the seed, and every dimension is present in both reports. A
   * controlled comparison isolates the thing that changed; when nothing
   * changed, a difference is sampling noise from a different seed and the Lab
   * says that.
   */
  controlled: boolean;
  /** One sentence describing what can and cannot be concluded. */
  verdict: string;
}

interface Dimension {
  key: string;
  label: string;
  read: (report: LabReport) => string | number | boolean | null;
}

const DIMENSIONS: readonly Dimension[] = [
  { key: 'topology', label: 'Topology', read: (r) => r.simulation.topology },
  { key: 'nodeCount', label: 'Node count', read: (r) => r.simulation.nodeCount },
  { key: 'linkCount', label: 'Link count', read: (r) => r.simulation.linkCount },
  { key: 'seed', label: 'Seed', read: (r) => r.simulation.seed },
  { key: 'messageCount', label: 'Message count', read: (r) => r.simulation.messageCount },
  { key: 'rounds', label: 'Rounds', read: (r) => r.simulation.rounds },
  { key: 'stepMillis', label: 'Step (ms)', read: (r) => r.simulation.stepMillis },
  { key: 'hopLimit', label: 'Hop limit', read: (r) => r.simulation.hopLimit },
  {
    key: 'linkDropProbability',
    label: 'Packet loss',
    read: (r) => r.simulation.linkDropProbability,
  },
  { key: 'broadcastRatio', label: 'Broadcast share', read: (r) => r.simulation.broadcastRatio },
  {
    key: 'churnEveryRounds',
    label: 'Churn interval',
    read: (r) => r.simulation.churnEveryRounds,
  },
  {
    key: 'messageLifetimeSeconds',
    label: 'Message lifetime (s)',
    read: (r) => r.simulation.messageLifetimeSeconds,
  },
  { key: 'degree', label: 'Target degree', read: (r) => r.simulation.degree },
  {
    key: 'startPartitioned',
    label: 'Starts partitioned',
    read: (r) => r.simulation.startPartitioned,
  },
  {
    key: 'startEpochSeconds',
    label: 'Start epoch (s)',
    read: (r) => r.simulation.startEpochSeconds,
  },
];

function displayDimension(value: string | number | boolean | null): string {
  if (value === null) return '—';
  if (typeof value === 'boolean') return value ? 'yes' : 'no';
  if (typeof value === 'number') {
    if (!Number.isInteger(value)) return value.toString();
    return String(value);
  }
  return value;
}

function rawValue(value: string | number | boolean | null): string {
  if (value === null) return '—';
  return String(value);
}

/**
 * Keeps a report-controlled value from breaking out of a Markdown cell or
 * adding a new block. File names and topology names come from untrusted
 * input, even when the rest of the report was produced by the simulator.
 */
function markdownText(value: string): string {
  return value.replace(/\r?\n/g, ' ').replace(/\|/g, '\\|');
}

/** Escapes a value for a Markdown table cell. */
function markdownCell(value: string): string {
  return markdownText(value);
}

function buildRow(
  key: string,
  label: string,
  kind: ComparisonRow['kind'],
  left: number | null,
  right: number | null,
): ComparisonRow {
  const comparable = left !== null && right !== null;
  const delta = comparable ? (right as number) - (left as number) : null;
  const relative = comparable && (left as number) !== 0 ? (delta as number) / (left as number) : null;
  return { key, label, kind, left, right, delta, relative, comparable };
}

const METRIC_ROWS: readonly {
  key: keyof LabMetrics;
  label: string;
  kind: ComparisonRow['kind'];
}[] = [
  { key: 'acknowledgementRate', label: 'Acknowledgement rate', kind: 'rate' },
  { key: 'medianLatencyMillis', label: 'Median latency', kind: 'milliseconds' },
  { key: 'p95LatencyMillis', label: 'p95 latency', kind: 'milliseconds' },
  { key: 'framesSent', label: 'Frames sent', kind: 'count' },
  { key: 'framesDropped', label: 'Frames dropped', kind: 'count' },
  { key: 'forwards', label: 'Forwards', kind: 'count' },
  { key: 'duplicatesSuppressed', label: 'Duplicates suppressed', kind: 'count' },
  { key: 'amplificationFactor', label: 'Amplification', kind: 'factor' },
  { key: 'linkChanges', label: 'Link changes', kind: 'count' },
  { key: 'partitions', label: 'Partitions at end', kind: 'count' },
  { key: 'originated', label: 'Messages composed', kind: 'count' },
  { key: 'directedOriginated', label: 'Directed messages', kind: 'count' },
  { key: 'broadcastOriginated', label: 'Broadcasts', kind: 'count' },
  { key: 'acknowledged', label: 'Acknowledged', kind: 'count' },
  { key: 'expired', label: 'Expired', kind: 'count' },
  { key: 'failed', label: 'Failed', kind: 'count' },
];

/** Builds the comparison. */
export function buildComparison(left: LabReport, right: LabReport): Comparison {
  const leftMetrics = extractMetrics(left);
  const rightMetrics = extractMetrics(right);

  const rows = METRIC_ROWS.map((row) =>
    buildRow(row.key, row.label, row.kind, leftMetrics[row.key], rightMetrics[row.key]),
  );

  const differences: ConfigDifference[] = [];
  const incompleteDimensions: string[] = [];
  for (const dimension of DIMENSIONS) {
    const leftValue = dimension.read(left);
    const rightValue = dimension.read(right);
    if (leftValue === null || rightValue === null) {
      incompleteDimensions.push(dimension.label);
    } else if (rawValue(leftValue) !== rawValue(rightValue)) {
      differences.push({
        key: dimension.key,
        label: dimension.label,
        left: displayDimension(leftValue),
        right: displayDimension(rightValue),
      });
    }
  }

  const controlled = differences.length === 0 && incompleteDimensions.length === 0;
  const verdict = controlled
    ? 'Both runs used the same configuration, so the differences below are outcome differences within one controlled scenario.'
    : differences.length === 0
      ? `The reports agree on every configuration value they both record, but ${incompleteDimensions.join(', ')} ${
          incompleteDimensions.length === 1 ? 'is' : 'are'
        } missing from one or both. This is not a controlled comparison.`
      : differences.length === 1
        ? `One configuration dimension differs (${differences[0]?.label}). The difference below is associated with that change, not proven to be caused by it.`
        : `${differences.length} configuration dimensions differ. This is not a controlled comparison: the outcome differences cannot be attributed to any one change.`;

  return { left, right, rows, differences, incompleteDimensions, controlled, verdict };
}

/** Formats a delta with its sign, or an em dash when not comparable. */
export function formatDelta(row: ComparisonRow): string {
  if (row.delta === null) return '—';
  const formatted = formatMetric(Math.abs(row.delta), row.kind, { digits: 1 });
  if (row.delta === 0) return `0${row.kind === 'rate' ? '%' : ''}`;
  return `${row.delta > 0 ? '+' : '−'}${formatted}`;
}

/** Formats the relative change, or an em dash when the base is zero. */
export function formatRelative(row: ComparisonRow): string {
  if (row.relative === null) return '—';
  const percent = row.relative * 100;
  if (!Number.isFinite(percent)) return '—';
  const sign = percent > 0 ? '+' : percent < 0 ? '−' : '';
  return `${sign}${Math.abs(percent).toFixed(1)}%`;
}

/** A short label for the report's origin, used in the export. */
function provenance(report: LabReport): string {
  return report.source;
}

/**
 * Builds a Markdown summary that can be saved or pasted into a benchmark log.
 *
 * <p>Includes the exact configuration of both runs and the provenance of each
 * file, because a comparison stripped of its inputs is not reproducible.
 */
export function exportComparisonMarkdown(comparison: Comparison): string {
  const { left, right } = comparison;
  const lines: string[] = [];

  lines.push('# ResQNet Mesh Replay Lab — comparison');
  lines.push('');
  lines.push('> **SIMULATED — NOT REAL RADIO TELEMETRY.**');
  lines.push('>');
  lines.push(
    '> These results come from the deterministic ResQNet Java simulator. They are not measurements of Android device performance or of physical radio range.',
  );
  lines.push('');
  lines.push(`Generated: ${new Date().toISOString()}`);
  lines.push('');

  lines.push('## Provenance');
  lines.push('');
  lines.push(`- **A:** ${markdownText(provenance(left))}`);
  lines.push(`- **B:** ${markdownText(provenance(right))}`);
  lines.push('');

  lines.push('## Configuration');
  lines.push('');
  lines.push('| Dimension | A | B |');
  lines.push('| --- | --- | --- |');
  for (const dimension of DIMENSIONS) {
    const leftValue = dimension.read(left);
    const rightValue = dimension.read(right);
    const marker = rawValue(leftValue) !== rawValue(rightValue) ? ' **≠**' : '';
    lines.push(
      `| ${markdownCell(dimension.label)}${marker} | ${markdownCell(displayDimension(leftValue))} | ${markdownCell(displayDimension(rightValue))} |`,
    );
  }
  lines.push('');

  lines.push('## Comparison');
  lines.push('');
  lines.push('| Metric | A | B | Change | Relative |');
  lines.push('| --- | ---: | ---: | ---: | ---: |');
  for (const row of comparison.rows) {
    lines.push(
      `| ${row.label} | ${formatMetric(row.left, row.kind)} | ${formatMetric(row.right, row.kind)} | ${formatDelta(row)} | ${formatRelative(row)} |`,
    );
  }
  lines.push('');

  lines.push('## Interpretation');
  lines.push('');
  lines.push(comparison.verdict);
  lines.push('');
  if (comparison.differences.length > 0) {
    lines.push('Differing configuration dimensions:');
    lines.push('');
    for (const difference of comparison.differences) {
      lines.push(
        `- ${markdownText(difference.label)}: ${markdownText(difference.left)} → ${markdownText(difference.right)}`,
      );
    }
    lines.push('');
  }
  if (comparison.incompleteDimensions.length > 0) {
    lines.push('Configuration dimensions not recorded by one or both reports:');
    lines.push('');
    for (const label of comparison.incompleteDimensions) {
      lines.push(`- ${markdownText(label)}`);
    }
    lines.push('');
  }

  lines.push('## Configuration commands');
  lines.push('');
  lines.push('```sh');
  lines.push(commandFor(left));
  lines.push('');
  lines.push(commandFor(right));
  lines.push('```');
  lines.push('');
  lines.push(
    'Metric definitions: acknowledgement rate is acknowledged directed messages ÷ directed messages (broadcasts cannot be acknowledged); latency is simulated round-trip time in milliseconds; amplification is forwarded copies per accepted envelope.',
  );
  lines.push('');

  return lines.join('\n');
}

/** Maps a recorded topology name to a CLI shape, or null if it is not known. */
function cliTopology(topology: string): string | null {
  const normalized = topology.toLowerCase().replace(/_/g, '-');
  return (TOPOLOGY_SHAPES as readonly string[]).includes(normalized) ? normalized : null;
}

/** Reconstructs the simulator command for a loaded report, when possible. */
export function commandFor(report: LabReport): string {
  const s = report.simulation;
  const missing: string[] = [];

  const required = (label: string, value: number | null): string => {
    if (value === null) {
      missing.push(label);
      return '';
    }
    return String(value);
  };

  // Never interpolate the recorded topology name directly into a command.
  // A report is user-supplied data, and the value sits inside a shell command
  // that the Lab invites the reader to copy.
  const topology = cliTopology(s.topology);
  if (!topology) missing.push('a recognised topology');
  const nodes = required('node count', s.nodeCount);
  const degree = required('target degree', s.degree);
  const messages = required('message count', s.messageCount);
  const seed = required('seed', s.seed);
  const drop = required('packet loss', s.linkDropProbability);
  const churn = required('churn interval', s.churnEveryRounds);
  const hops = required('hop limit', s.hopLimit);
  const rounds = required('rounds', s.rounds);
  const step = required('step', s.stepMillis);
  const broadcast = required('broadcast share', s.broadcastRatio);
  const lifetime = required('message lifetime', s.messageLifetimeSeconds);
  const startEpoch = required('start epoch', s.startEpochSeconds);
  if (s.startPartitioned === null) missing.push('start partition state');

  if (missing.length > 0 || !topology) {
    return `# An exact command cannot be reconstructed: the report does not record ${missing.join(', ')}.`;
  }
  if (s.startEpochSeconds !== CLI_START_EPOCH_SECONDS) {
    return `# An exact command cannot be reconstructed: the Java CLI always starts at ${CLI_START_EPOCH_SECONDS}, but this report started at ${startEpoch}.`;
  }

  const args = [
    `--topology ${topology}`,
    `--nodes ${nodes}`,
    `--degree ${degree}`,
    `--messages ${messages}`,
    `--seed ${seed}`,
    `--drop ${drop}`,
    `--churn ${churn}`,
    `--hops ${hops}`,
    `--rounds ${rounds}`,
    `--step ${step}`,
    `--broadcast ${broadcast}`,
    `--lifetime ${lifetime}`,
  ];
  if (s.startPartitioned) args.push('--partitioned');
  args.push('--out report.json');

  // Single-quote the whole exec.args value. The values above are numbers or
  // a whitelisted topology, but quoting the entire argument list keeps this
  // safe if the reconstruction gains another report-derived field.
  return `mvn -q -pl packages/mesh-simulator exec:java -Dexec.args=${shellQuote(args.join(' '))}`;
}
