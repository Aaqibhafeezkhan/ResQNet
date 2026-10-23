/**
 * Scenario parameters and the Java simulator command they generate.
 *
 * <p>This module does not simulate anything. It validates the controls against
 * the same ranges `SimulationConfig` and `Topology` enforce, and renders the
 * command that reproduces the scenario with the real Java simulator. The Lab
 * says so in the interface: generating a command is not running a simulation.
 */

export const TOPOLOGY_SHAPES = [
  'line',
  'ring',
  'star',
  'mesh',
  'two-cluster',
  'random',
] as const;

export type TopologyShape = (typeof TOPOLOGY_SHAPES)[number];

export interface ScenarioParams {
  topology: TopologyShape;
  nodes: number;
  degree: number;
  messages: number;
  seed: number;
  /** Probability that any single frame is lost, 0..1. */
  dropProbability: number;
  /** Cut or restore one link every N rounds. 0 disables churn. */
  churnEveryRounds: number;
  hopLimit: number;
  rounds: number;
  stepMillis: number;
  broadcastRatio: number;
  /** Start with the busiest relay offline. */
  partitioned: boolean;
}

/** Matches `SimulationConfig.defaults` and the CLI defaults. */
export const DEFAULT_SCENARIO: ScenarioParams = {
  topology: 'ring',
  nodes: 8,
  degree: 3,
  messages: 24,
  seed: 42,
  dropProbability: 0,
  churnEveryRounds: 12,
  hopLimit: 8,
  rounds: 60,
  stepMillis: 250,
  broadcastRatio: 0.25,
  partitioned: false,
};

export type ScenarioField = keyof ScenarioParams;

export interface ScenarioValidation {
  ok: boolean;
  /** Field-keyed messages, suitable for rendering next to each control. */
  errors: Partial<Record<ScenarioField, string>>;
}

const INTEGER = (value: number): boolean => Number.isSafeInteger(value);

/**
 * Validates the parameters against the simulator's own constraints.
 *
 * <p>If this accepts a scenario, the generated command will parse. The
 * constraints are copied from `SimulationConfig` and `Topology` deliberately:
 * a command that looks plausible and fails in the simulator is worse than a
 * form that refuses it here.
 */
export function validateScenario(params: ScenarioParams): ScenarioValidation {
  const errors: Partial<Record<ScenarioField, string>> = {};

  if (!TOPOLOGY_SHAPES.includes(params.topology)) {
    errors.topology = 'Choose one of the topologies the simulator implements.';
  }
  if (!INTEGER(params.nodes) || params.nodes < 2) {
    errors.nodes = 'A mesh needs at least 2 nodes.';
  } else if (params.nodes > 500) {
    errors.nodes = 'The simulator is not intended for more than 500 nodes in a single run.';
  }
  if (!INTEGER(params.degree) || params.degree < 1) {
    errors.degree = 'Degree must be a whole number of at least 1.';
  } else if (params.degree > 16) {
    errors.degree = 'Degree above 16 turns a random topology into a near-complete graph.';
  }
  if (!INTEGER(params.messages) || params.messages < 0) {
    errors.messages = 'Message count must be zero or more.';
  } else if (params.messages > 5000) {
    errors.messages = 'Keep the message count at 5000 or below for one run.';
  }
  if (!INTEGER(params.seed)) {
    errors.seed = 'Seed must be a whole number.';
  } else if (!Number.isSafeInteger(params.seed)) {
    errors.seed = 'Seed is outside the range JavaScript can represent exactly.';
  }
  if (!Number.isFinite(params.dropProbability) || params.dropProbability < 0 || params.dropProbability > 1) {
    errors.dropProbability = 'Packet loss is a probability between 0 and 1.';
  }
  if (!INTEGER(params.churnEveryRounds) || params.churnEveryRounds < 0) {
    errors.churnEveryRounds = 'Churn every N rounds must be zero or more. Zero disables churn.';
  }
  if (!INTEGER(params.hopLimit) || params.hopLimit < 1 || params.hopLimit > 32) {
    errors.hopLimit = 'Hop limit must be between 1 and 32, as the router enforces.';
  }
  if (!INTEGER(params.rounds) || params.rounds < 1) {
    errors.rounds = 'A run needs at least one round.';
  } else if (params.rounds > 10_000) {
    errors.rounds = 'Keep the run at 10 000 rounds or below.';
  }
  if (!INTEGER(params.stepMillis) || params.stepMillis <= 0) {
    errors.stepMillis = 'Step must be a positive number of simulated milliseconds.';
  }
  if (!Number.isFinite(params.broadcastRatio) || params.broadcastRatio < 0 || params.broadcastRatio > 1) {
    errors.broadcastRatio = 'Broadcast share is a ratio between 0 and 1.';
  }

  return { ok: Object.keys(errors).length === 0, errors };
}

/** Formats a probability without inventing precision or rounding it away. */
function formatProbability(value: number): string {
  return String(value);
}

/**
 * The simulator command that reproduces this scenario.
 *
 * <p>Every argument is the one the CLI reads. The command writes a report the
 * Lab can import directly.
 */
export function buildCommand(params: ScenarioParams, output = 'report.json'): string {
  const args = [
    `--topology ${params.topology}`,
    `--nodes ${params.nodes}`,
    `--degree ${params.degree}`,
    `--messages ${params.messages}`,
    `--seed ${params.seed}`,
    `--drop ${formatProbability(params.dropProbability)}`,
    `--churn ${params.churnEveryRounds}`,
    `--hops ${params.hopLimit}`,
    `--rounds ${params.rounds}`,
    `--step ${params.stepMillis}`,
    `--broadcast ${formatProbability(params.broadcastRatio)}`,
  ];
  if (params.partitioned) args.push('--partitioned');
  args.push(`--out ${output}`);

  // Quote the whole argument list for the shell. The output path is the only
  // caller-controlled string here, but quoting the whole value keeps a future
  // field from silently becoming a command-injection point.
  return `mvn -q -pl packages/mesh-simulator exec:java \\\n  -Dexec.args=${shellQuote(args.join(' '))}`;
}

/** A human summary of the scenario, for the comparison header and exports. */
export function describeScenario(params: ScenarioParams): string {
  return [
    `${params.topology} topology`,
    `${params.nodes} nodes`,
    `${params.messages} messages`,
    `seed ${params.seed}`,
    `drop ${formatProbability(params.dropProbability)}`,
    params.churnEveryRounds === 0
      ? 'churn disabled'
      : `churn every ${params.churnEveryRounds} rounds`,
    `hop limit ${params.hopLimit}`,
    `${params.rounds} rounds`,
    `broadcast ${formatProbability(params.broadcastRatio)}`,
    params.partitioned ? 'starts partitioned' : 'starts connected',
  ].join(' · ');
}

/** Escapes a value for a single-quoted shell string. */
export function shellQuote(value: string): string {
  return `'${value.replace(/'/g, `'\\''`)}'`;
}
