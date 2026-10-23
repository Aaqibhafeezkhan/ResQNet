/**
 * Built-in, verified sample reports.
 *
 * <p>Both files were written by the Java simulator itself, with the exact
 * commands recorded below. They are imported into the bundle, so the Lab works
 * with no network access and no server.
 *
 * <p>Regenerating a sample produces the same routing decisions, but two
 * documented fields will differ because they cannot be deterministic: every
 * simulated node generates a fresh key pair, and the CPU wall-clock duration
 * changes. See `SimulationReport#volatileJsonFields()`.
 */

import ringSample from './fixtures/ring-6-seed-42.json';
import partitionedSample from './fixtures/two-cluster-6-seed-42.json';
import { validateReport, type ParseResult } from './parseReport';

export interface SampleReport {
  id: string;
  name: string;
  description: string;
  /** Exact command that produced this file. */
  command: string;
  /** Why this run is interesting. */
  interest: string;
  load: () => ParseResult;
}

function loadFixture(fixture: unknown, source: string): ParseResult {
  return validateReport(fixture, source);
}

export const SAMPLE_REPORTS: readonly SampleReport[] = [
  {
    id: 'ring-6-seed-42',
    name: 'Ring · 6 nodes · seed 42',
    description: 'A healthy ring with packet loss and link churn.',
    interest:
      'Six directed messages, four broadcasts, a few dropped frames and a link that is cut and restored. The directed messages are all acknowledged, so the journey inspector has a complete success to show.',
    command:
      'mvn -q -pl packages/mesh-simulator exec:java -Dexec.args="--topology ring --nodes 6 --degree 3 --messages 10 --seed 42 --rounds 30 --broadcast 0.4 --churn 8 --drop 0.05 --hops 8 --step 250 --out report.json"',
    load: () => loadFixture(ringSample, 'built-in sample · ring · seed 42'),
  },
  {
    id: 'two-cluster-6-seed-42',
    name: 'Two clusters · 6 nodes · seed 42',
    description: 'A partitioned topology where delivery fails cleanly.',
    interest:
      'The busiest relay starts offline, so the two clusters cannot reach each other. Only two of six directed messages are acknowledged, and the report ends with five partitions. Nothing is invented to fill the gap.',
    command:
      'mvn -q -pl packages/mesh-simulator exec:java -Dexec.args="--topology two-cluster --nodes 6 --degree 3 --messages 10 --seed 42 --rounds 30 --broadcast 0.4 --churn 8 --drop 0.05 --hops 8 --step 250 --partitioned --out report.json"',
    load: () => loadFixture(partitionedSample, 'built-in sample · two clusters · seed 42'),
  },
];
