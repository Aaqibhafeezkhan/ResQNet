/**
 * Metrics extracted from a simulator report.
 *
 * <p>The Java report is the source of truth. Nothing here recomputes a rate
 * the report already measured, and nothing fills a gap with a plausible
 * number. A value the run did not measure is `null`, and every formatter in
 * the Lab renders that as an em dash with a tooltip saying why.
 */

import type { LabReport } from './types';

export interface LabMetric {
  key: string;
  label: string;
  /** `null` means the run did not measure this. */
  value: number | null;
  kind: 'rate' | 'milliseconds' | 'count' | 'factor';
  /** Why the value is unavailable, when it is. */
  unavailableBecause?: string;
  /** A plain-language note shown beside the number. */
  note?: string;
}

/** The numbers the Lab shows, and the only place they are read from. */
export interface LabMetrics {
  acknowledgementRate: number | null;
  deliveryRate: number | null;
  directedOriginated: number | null;
  broadcastOriginated: number | null;
  originated: number | null;
  acknowledged: number | null;
  deliveredRecords: number | null;
  expired: number | null;
  failed: number | null;
  stillQueued: number | null;
  duplicatesSuppressed: number | null;
  rejected: number | null;
  forwards: number | null;
  medianLatencyMillis: number | null;
  p95LatencyMillis: number | null;
  p99LatencyMillis: number | null;
  maxLatencyMillis: number | null;
  framesSent: number | null;
  framesDelivered: number | null;
  framesDropped: number | null;
  linkChanges: number | null;
  partitions: number | null;
  amplificationFactor: number | null;
  simulatedMillis: number | null;
  wallClockMillis: number | null;
}

/**
 * Pulls the reported metrics out of a report.
 *
 * <p>`deliveryRate` is the directed-message acknowledgement rate. The Java
 * report uses the two names for the same measurement and documents why the
 * denominator is directed messages only: a broadcast has no recipient to
 * acknowledge it.
 */
export function extractMetrics(report: LabReport): LabMetrics {
  return {
    acknowledgementRate: report.deliveryRate,
    deliveryRate: report.deliveryRate,
    directedOriginated: report.outcome.directedOriginated,
    broadcastOriginated: report.outcome.broadcastOriginated,
    originated: report.outcome.originated,
    acknowledged: report.outcome.acknowledged,
    deliveredRecords: report.outcome.deliveredRecords,
    expired: report.outcome.expired,
    failed: report.outcome.failed,
    stillQueued: report.outcome.stillQueued,
    duplicatesSuppressed: report.outcome.duplicatesSuppressed,
    rejected: report.outcome.rejected,
    forwards: report.outcome.forwards,
    medianLatencyMillis: report.latencyMillis.median,
    p95LatencyMillis: report.latencyMillis.p95,
    p99LatencyMillis: report.latencyMillis.p99,
    maxLatencyMillis: report.latencyMillis.max,
    framesSent: report.radio.framesSent,
    framesDelivered: report.radio.framesDelivered,
    framesDropped: report.radio.framesDropped,
    linkChanges: report.radio.linkChanges,
    partitions: report.radio.partitions,
    amplificationFactor: report.amplificationFactor,
    simulatedMillis: report.timing.simulatedMillis,
    wallClockMillis: report.timing.wallClockMillis,
  };
}

/** Formats a metric for display, or an em dash when it was not measured. */
export function formatMetric(
  value: number | null,
  kind: LabMetric['kind'],
  options: { digits?: number } = {},
): string {
  if (value === null || Number.isNaN(value)) return '—';
  switch (kind) {
    case 'rate':
      return `${(value * 100).toFixed(options.digits ?? 1)}%`;
    case 'milliseconds':
      if (value < 1000) return `${value} ms`;
      return `${(value / 1000).toFixed(2)} s`;
    case 'factor':
      return value.toFixed(3);
    case 'count':
    default:
      return new Intl.NumberFormat().format(value);
  }
}

/**
 * The headline metric cards.
 *
 * <p>Kept as data so the same definitions are used by the metrics panel and
 * by the comparison table. A metric that means something different in two
 * places is worse than no metric.
 */
export function headlineMetrics(metrics: LabMetrics): LabMetric[] {
  return [
    {
      key: 'acknowledgementRate',
      label: 'Acknowledgement rate',
      value: metrics.acknowledgementRate,
      kind: 'rate',
      unavailableBecause:
        'This run originated no directed messages, so there was nothing that could be acknowledged.',
      note: 'Confirmed acknowledgements ÷ directed messages. Broadcasts are excluded by construction.',
    },
    {
      key: 'medianLatencyMillis',
      label: 'Median simulated latency',
      value: metrics.medianLatencyMillis,
      kind: 'milliseconds',
      unavailableBecause: 'No acknowledgement was measured, so there is no latency sample.',
      note: 'Round trip from composition to acknowledgement, in simulated time.',
    },
    {
      key: 'p95LatencyMillis',
      label: 'p95 simulated latency',
      value: metrics.p95LatencyMillis,
      kind: 'milliseconds',
      unavailableBecause: 'No acknowledgement was measured, so there is no latency sample.',
      note: 'Nearest-rank 95th percentile of the same samples.',
    },
    {
      key: 'amplificationFactor',
      label: 'Amplification',
      value: metrics.amplificationFactor,
      kind: 'factor',
      unavailableBecause: 'The run did not originate a message, so nothing could be amplified.',
      note: 'Forwarded copies per originated message.',
    },
    {
      key: 'framesDropped',
      label: 'Frames dropped',
      value: metrics.framesDropped,
      kind: 'count',
      unavailableBecause: 'The radio statistics were not recorded in this report.',
      note: `Out of ${formatMetric(metrics.framesSent, 'count')} frames sent by the synthetic radio.`,
    },
    {
      key: 'partitions',
      label: 'Partitions at end',
      value: metrics.partitions,
      kind: 'count',
      unavailableBecause: 'The radio statistics were not recorded in this report.',
      note: 'Connected components in the final topology. One means fully connected.',
    },
  ];
}

/** Secondary counters, shown in a compact table. */
export function detailMetrics(metrics: LabMetrics): LabMetric[] {
  return [
    { key: 'originated', label: 'Messages composed', value: metrics.originated, kind: 'count' },
    {
      key: 'directedOriginated',
      label: 'Directed messages',
      value: metrics.directedOriginated,
      kind: 'count',
      note: 'Addressable, and therefore acknowledgement-eligible.',
    },
    {
      key: 'broadcastOriginated',
      label: 'Broadcasts',
      value: metrics.broadcastOriginated,
      kind: 'count',
      note: 'A broadcast names no recipient, so it can never be acknowledged.',
    },
    { key: 'acknowledged', label: 'Acknowledged', value: metrics.acknowledged, kind: 'count' },
    {
      key: 'deliveredRecords',
      label: 'Delivered records',
      value: metrics.deliveredRecords,
      kind: 'count',
    },
    { key: 'forwards', label: 'Forwards', value: metrics.forwards, kind: 'count' },
    {
      key: 'duplicatesSuppressed',
      label: 'Duplicates suppressed',
      value: metrics.duplicatesSuppressed,
      kind: 'count',
    },
    { key: 'rejected', label: 'Rejected frames', value: metrics.rejected, kind: 'count' },
    { key: 'expired', label: 'Expired', value: metrics.expired, kind: 'count' },
    { key: 'failed', label: 'Failed', value: metrics.failed, kind: 'count' },
    { key: 'stillQueued', label: 'Still queued', value: metrics.stillQueued, kind: 'count' },
    {
      key: 'linkChanges',
      label: 'Link changes',
      value: metrics.linkChanges,
      kind: 'count',
      note: 'Cuts and restores recorded by the synthetic radio.',
    },
  ];
}
