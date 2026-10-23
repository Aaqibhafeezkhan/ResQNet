import { describe, expect, it } from 'vitest';

import {
  buildComparison,
  commandFor,
  exportComparisonMarkdown,
  formatDelta,
  formatRelative,
  type ComparisonRow,
} from './compare';
import { validateReport } from './parseReport';
import type { LabReport } from './types';
import ringSample from './fixtures/ring-6-seed-42.json';
import partitionedSample from './fixtures/two-cluster-6-seed-42.json';

function reportFrom(sample: unknown, source: string): LabReport {
  const result = validateReport(sample, source);
  if (!result.ok) throw new Error(result.error);
  return result.report;
}

function row(comparison: ReturnType<typeof buildComparison>, key: string): ComparisonRow {
  const found = comparison.rows.find((entry) => entry.key === key);
  if (!found) throw new Error(`missing row ${key}`);
  return found;
}

describe('buildComparison', () => {
  it('marks an identical configuration as controlled', () => {
    const left = reportFrom(ringSample, 'A');
    const comparison = buildComparison(left, reportFrom(ringSample, 'B'));

    expect(comparison.controlled).toBe(true);
    expect(comparison.differences).toEqual([]);
    expect(comparison.verdict).toMatch(/same configuration/i);
  });

  it('flags every dimension that changed, and refuses to call it controlled', () => {
    const comparison = buildComparison(
      reportFrom(ringSample, 'ring'),
      reportFrom(partitionedSample, 'partitioned'),
    );

    expect(comparison.controlled).toBe(false);
    const keys = comparison.differences.map((difference) => difference.key);
    expect(keys).toContain('topology');
    expect(keys).toContain('linkCount');
    expect(keys).toContain('startPartitioned');
    expect(comparison.verdict).toMatch(/not a controlled comparison/i);
    expect(comparison.verdict).toMatch(/dimensions differ/);
  });

  it('uses a singular sentence when exactly one dimension changed', () => {
    const left = reportFrom(ringSample, 'A');
    const changed: LabReport = {
      ...left,
      simulation: { ...left.simulation, seed: 43 },
    };
    const comparison = buildComparison(left, changed);

    expect(comparison.differences.map((difference) => difference.key)).toEqual(['seed']);
    expect(comparison.verdict).toMatch(/One configuration dimension differs \(Seed\)/);
  });

  it('computes deltas and relative change from the reported values', () => {
    const comparison = buildComparison(
      reportFrom(ringSample, 'ring'),
      reportFrom(partitionedSample, 'partitioned'),
    );
    const ack = row(comparison, 'acknowledgementRate');

    expect(ack.left).toBe(1);
    expect(ack.right).toBeCloseTo(1 / 3, 5);
    expect(ack.delta).toBeCloseTo(-2 / 3, 5);
    expect(ack.relative).toBeCloseTo(-2 / 3, 5);
    expect(ack.comparable).toBe(true);
    expect(formatDelta(ack)).toMatch(/^−/);
    expect(formatRelative(ack)).toMatch(/^−/);
  });

  it('never fills an unavailable measurement with zero', () => {
    const unavailable = reportFrom(
      {
        simulation: { topology: 'RING', nodeCount: 6, seed: 42, edges: [] },
        events: [],
        latencyMillis: { median: -1 },
      },
      'unavailable',
    );
    const comparison = buildComparison(reportFrom(ringSample, 'ring'), unavailable);
    const median = row(comparison, 'medianLatencyMillis');

    expect(median.right).toBeNull();
    expect(median.comparable).toBe(false);
    expect(median.delta).toBeNull();
    expect(median.relative).toBeNull();
    expect(formatDelta(median)).toBe('—');
    expect(formatRelative(median)).toBe('—');
  });

  it('reports a zero change without a sign or a misleading percent', () => {
    const comparison = buildComparison(
      reportFrom(ringSample, 'A'),
      reportFrom(ringSample, 'B'),
    );
    expect(formatDelta(row(comparison, 'framesSent'))).toBe('0');
    expect(formatRelative(row(comparison, 'framesSent'))).toBe('0.0%');
  });
});

describe('exportComparisonMarkdown', () => {
  it('carries the simulation boundary, provenance and both configurations', () => {
    const comparison = buildComparison(
      reportFrom(ringSample, 'ring fixture'),
      reportFrom(partitionedSample, 'partitioned fixture'),
    );
    const markdown = exportComparisonMarkdown(comparison);

    expect(markdown).toContain('SIMULATED — NOT REAL RADIO TELEMETRY');
    expect(markdown).toContain('ring fixture');
    expect(markdown).toContain('partitioned fixture');
    expect(markdown).toContain('| Topology **≠** | RING | TWO_CLUSTER |');
    expect(markdown).toContain('| Starts partitioned **≠** | no | yes |');
    expect(markdown).toContain('Differing configuration dimensions:');
    expect(markdown.toLowerCase()).toContain('not a controlled comparison');
    expect(markdown).toContain('--partitioned');
    expect(markdown).toContain('broadcasts cannot be acknowledged');
  });

  it('emits the same content on repeated export, apart from the timestamp', () => {
    const comparison = buildComparison(
      reportFrom(ringSample, 'A'),
      reportFrom(partitionedSample, 'B'),
    );
    const strip = (markdown: string): string => markdown.replace(/^Generated:.*$/m, '');
    expect(strip(exportComparisonMarkdown(comparison))).toBe(
      strip(exportComparisonMarkdown(comparison)),
    );
  });
});

describe('commandFor', () => {
  it('reconstructs every recorded configuration parameter', () => {
    const command = commandFor(reportFrom(ringSample, 'ring'));

    expect(command).toContain('--topology ring');
    expect(command).toContain('--nodes 6');
    expect(command).toContain('--degree 3');
    expect(command).toContain('--messages 10');
    expect(command).toContain('--seed 42');
    expect(command).toContain('--drop 0.05');
    expect(command).toContain('--rounds 30');
    expect(command).toContain('--step 250');
    expect(command).toContain('--broadcast 0.4');
    expect(command).not.toContain('--partitioned');
  });

  it('restores the partitioned flag from the report, not from the topology name', () => {
    const command = commandFor(reportFrom(partitionedSample, 'partitioned'));
    expect(command).toContain('--partitioned');
  });

  it('refuses to invent configuration the report did not record', () => {
    const result = validateReport({
      simulation: { topology: 'LINE', nodeCount: 2, seed: 1 },
      events: [],
    });
    if (!result.ok) throw new Error(result.error);
    const command = commandFor(result.report);

    expect(command).toContain('cannot be reconstructed');
    expect(command).toContain('target degree');
    expect(command).toContain('message count');
    expect(command).not.toContain('--degree');
  });
});
