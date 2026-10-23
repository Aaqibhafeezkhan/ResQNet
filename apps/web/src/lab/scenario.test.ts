import { describe, expect, it } from 'vitest';

import {
  DEFAULT_SCENARIO,
  TOPOLOGY_SHAPES,
  buildCommand,
  describeScenario,
  shellQuote,
  validateScenario,
  type ScenarioParams,
} from './scenario';

function scenario(overrides: Partial<ScenarioParams> = {}): ScenarioParams {
  return { ...DEFAULT_SCENARIO, ...overrides };
}

describe('validateScenario', () => {
  it('accepts the defaults, which mirror the simulator defaults', () => {
    const result = validateScenario(DEFAULT_SCENARIO);
    expect(result.ok).toBe(true);
    expect(result.errors).toEqual({});
  });

  it('requires at least two nodes, as the topology does', () => {
    expect(validateScenario(scenario({ nodes: 1 })).errors.nodes).toBeTruthy();
    expect(validateScenario(scenario({ nodes: 2 })).ok).toBe(true);
  });

  it('rejects non-integer counts rather than silently truncating them', () => {
    expect(validateScenario(scenario({ nodes: 4.5 })).errors.nodes).toBeTruthy();
    expect(validateScenario(scenario({ messages: 1.2 })).errors.messages).toBeTruthy();
    expect(validateScenario(scenario({ rounds: Number.NaN })).errors.rounds).toBeTruthy();
  });

  it('applies the hop limit the router enforces', () => {
    expect(validateScenario(scenario({ hopLimit: 0 })).errors.hopLimit).toBeTruthy();
    expect(validateScenario(scenario({ hopLimit: 33 })).errors.hopLimit).toBeTruthy();
    expect(validateScenario(scenario({ hopLimit: 32 })).ok).toBe(true);
  });

  it('requires probabilities in [0, 1]', () => {
    expect(validateScenario(scenario({ dropProbability: -0.1 })).errors.dropProbability).toBeTruthy();
    expect(validateScenario(scenario({ dropProbability: 1.1 })).errors.dropProbability).toBeTruthy();
    expect(validateScenario(scenario({ broadcastRatio: -1 })).errors.broadcastRatio).toBeTruthy();
    expect(validateScenario(scenario({ broadcastRatio: 2 })).errors.broadcastRatio).toBeTruthy();
    expect(validateScenario(scenario({ dropProbability: 0.05, broadcastRatio: 1 })).ok).toBe(true);
  });

  it('allows churn to be disabled with zero', () => {
    expect(validateScenario(scenario({ churnEveryRounds: 0 })).ok).toBe(true);
    expect(validateScenario(scenario({ churnEveryRounds: -2 })).errors.churnEveryRounds).toBeTruthy();
  });

  it('requires a positive number of rounds and a positive step', () => {
    expect(validateScenario(scenario({ rounds: 0 })).errors.rounds).toBeTruthy();
    expect(validateScenario(scenario({ stepMillis: 0 })).errors.stepMillis).toBeTruthy();
  });

  it('rejects a seed JavaScript cannot represent exactly', () => {
    expect(validateScenario(scenario({ seed: Number.MAX_SAFE_INTEGER + 1 })).errors.seed).toBeTruthy();
    expect(validateScenario(scenario({ seed: 42 })).ok).toBe(true);
  });

  it('reports every invalid field at once', () => {
    const result = validateScenario(
      scenario({ nodes: 0, messages: -1, hopLimit: 0, rounds: 0, dropProbability: 2 }),
    );
    expect(result.ok).toBe(false);
    expect(Object.keys(result.errors).sort()).toEqual([
      'dropProbability',
      'hopLimit',
      'messages',
      'nodes',
      'rounds',
    ]);
  });

  it('knows every topology the simulator implements', () => {
    for (const topology of TOPOLOGY_SHAPES) {
      expect(validateScenario(scenario({ topology })).ok).toBe(true);
    }
  });
});

describe('buildCommand', () => {
  it('writes a command with every parameter and the output path', () => {
    const command = buildCommand(DEFAULT_SCENARIO);

    expect(command.startsWith('mvn -q -pl packages/mesh-simulator exec:java')).toBe(true);
    expect(command).toContain("-Dexec.args='");
    expect(command).toContain('--topology ring');
    expect(command).toContain('--nodes 8');
    expect(command).toContain('--messages 24');
    expect(command).toContain('--seed 42');
    expect(command).toContain('--drop 0');
    expect(command).toContain('--churn 12');
    expect(command).toContain('--hops 8');
    expect(command).toContain('--rounds 60');
    expect(command).toContain('--step 250');
    expect(command).toContain('--broadcast 0.25');
    expect(command).toContain('--out report.json');
    expect(command).not.toContain('--partitioned');
  });

  it('adds the partitioned switch only when the scenario starts partitioned', () => {
    const command = buildCommand(scenario({ partitioned: true, topology: 'two-cluster' }));
    expect(command).toContain('--partitioned');
    expect(command).toContain('--topology two-cluster');
    expect(command.indexOf('--partitioned')).toBeLessThan(command.indexOf('--out'));
  });

  it('uses a custom output path without changing the arguments', () => {
    expect(buildCommand(DEFAULT_SCENARIO, 'lab/run-42.json')).toContain('--out lab/run-42.json');
  });

  it('can be copied and run without scientific notation for small losses', () => {
    const command = buildCommand(scenario({ dropProbability: 0.00001, broadcastRatio: 0.5 }));
    expect(command).toContain('--drop 0.00001');
    expect(command).not.toContain('e-');
  });

  it('produces a command whose own arguments survive a shell-ish split', () => {
    const command = buildCommand(DEFAULT_SCENARIO);
    const args = /-Dexec\.args='([^']*)'/.exec(command)?.[1];
    if (!args) throw new Error('command does not contain a quoted argument list');
    expect(args.split(' ')).toContain('--seed');
    expect(args.split(' ')).toContain('--out');
  });

  it('quotes a caller-controlled output path instead of executing it', () => {
    const command = buildCommand(DEFAULT_SCENARIO, "report'; touch /tmp/pwned; echo '");
    expect(command).toContain("report'\\''; touch /tmp/pwned; echo '\\'''");
    expect(command).not.toContain('-Dexec.args="');
  });
});

describe('describeScenario', () => {
  it('summarises the parameters in plain words', () => {
    const description = describeScenario(scenario({ topology: 'two-cluster', partitioned: true }));
    expect(description).toContain('two-cluster topology');
    expect(description).toContain('seed 42');
    expect(description).toContain('starts partitioned');
  });

  it('says churn is disabled rather than showing a dash in a sentence', () => {
    expect(describeScenario(scenario({ churnEveryRounds: 0 }))).toContain('churn disabled');
  });
});

describe('shellQuote', () => {
  it('quotes a value containing a single quote', () => {
    expect(shellQuote("it's")).toBe("'it'\\''s'");
  });
});
