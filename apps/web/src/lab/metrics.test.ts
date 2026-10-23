import { describe, expect, it } from 'vitest';

import { detailMetrics, extractMetrics, formatMetric, headlineMetrics } from './metrics';
import { validateReport } from './parseReport';
import ringSample from './fixtures/ring-6-seed-42.json';
import partitionedSample from './fixtures/two-cluster-6-seed-42.json';

function ringReport() {
  const result = validateReport(ringSample);
  if (!result.ok) throw new Error(result.error);
  return result.report;
}

function partitionedReport() {
  const result = validateReport(partitionedSample);
  if (!result.ok) throw new Error(result.error);
  return result.report;
}

describe('extractMetrics', () => {
  it('reads the reported numbers without recomputing them', () => {
    const report = ringReport();
    const metrics = extractMetrics(report);

    expect(metrics.acknowledgementRate).toBe(1);
    expect(metrics.directedOriginated).toBe(6);
    expect(metrics.broadcastOriginated).toBe(4);
    expect(metrics.acknowledged).toBe(6);
    expect(metrics.originated).toBe(10);
    expect(metrics.medianLatencyMillis).toBe(375);
    expect(metrics.p95LatencyMillis).toBe(375);
    expect(metrics.framesSent).toBe(report.radio.framesSent);
    expect(metrics.partitions).toBe(1);
    expect(metrics.amplificationFactor).toBe(report.amplificationFactor);
  });

  it('keeps the partitioned run’s low acknowledgement rate', () => {
    const metrics = extractMetrics(partitionedReport());
    expect(metrics.acknowledgementRate).toBeCloseTo(2 / 6, 5);
    expect(metrics.acknowledged).toBe(2);
    expect(metrics.directedOriginated).toBe(6);
    expect(metrics.partitions).toBe(5);
  });

  it('turns a sentinel into null rather than zero', () => {
    const result = validateReport({
      simulation: {},
      events: [],
      latencyMillis: { median: -1, p95: -1, p99: -1, max: -1 },
      radio: { framesSent: -1 },
      timing: { wallClockMillis: -1 },
    });
    if (!result.ok) throw new Error(result.error);
    const metrics = extractMetrics(result.report);

    expect(metrics.medianLatencyMillis).toBeNull();
    expect(metrics.framesSent).toBeNull();
    expect(metrics.wallClockMillis).toBeNull();
    expect(metrics.acknowledgementRate).toBeNull();
  });
});

describe('formatMetric', () => {
  it('renders an unavailable value as an em dash, never as zero', () => {
    expect(formatMetric(null, 'count')).toBe('—');
    expect(formatMetric(null, 'rate')).toBe('—');
    expect(formatMetric(Number.NaN, 'milliseconds')).toBe('—');
  });

  it('renders a real zero as a zero', () => {
    expect(formatMetric(0, 'count')).toBe('0');
    expect(formatMetric(0, 'rate')).toBe('0.0%');
    expect(formatMetric(0, 'milliseconds')).toBe('0 ms');
  });

  it('uses units that match the scale', () => {
    expect(formatMetric(375, 'milliseconds')).toBe('375 ms');
    expect(formatMetric(7500, 'milliseconds')).toBe('7.50 s');
    expect(formatMetric(0.5, 'rate')).toBe('50.0%');
    expect(formatMetric(1.23456, 'factor')).toBe('1.235');
    expect(formatMetric(1234, 'count')).toBe('1,234');
  });
});

describe('headlineMetrics', () => {
  it('exposes definitions in one place and explains unavailable values', () => {
    const metrics = extractMetrics(ringReport());
    const headline = headlineMetrics(metrics);

    expect(headline).toHaveLength(6);
    expect(headline.map((entry) => entry.key)).toEqual([
      'acknowledgementRate',
      'medianLatencyMillis',
      'p95LatencyMillis',
      'amplificationFactor',
      'framesDropped',
      'partitions',
    ]);
    for (const entry of headline) {
      expect(entry.unavailableBecause).toBeTruthy();
    }
  });

  it('shows every value as unavailable when the run measured none', () => {
    const result = validateReport({ simulation: {}, events: [] });
    if (!result.ok) throw new Error(result.error);
    const metrics = extractMetrics(result.report);

    for (const entry of headlineMetrics(metrics)) {
      expect(entry.value).toBeNull();
      expect(entry.unavailableBecause).toBeTruthy();
    }
  });
});

describe('detailMetrics', () => {
  it('includes the counters a reader needs to audit the headline', () => {
    const keys = detailMetrics(extractMetrics(ringReport())).map((entry) => entry.key);
    expect(keys).toContain('originated');
    expect(keys).toContain('directedOriginated');
    expect(keys).toContain('broadcastOriginated');
    expect(keys).toContain('duplicatesSuppressed');
    expect(keys).toContain('forwards');
    expect(keys).toContain('linkChanges');
  });

  it('does not drop a value merely because it is unavailable', () => {
    const result = validateReport({ simulation: {}, events: [] });
    if (!result.ok) throw new Error(result.error);
    const details = detailMetrics(extractMetrics(result.report));
    expect(details).toHaveLength(12);
    expect(details.every((entry) => entry.value === null)).toBe(true);
  });
});
