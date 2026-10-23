import { Check, Copy, RotateCcw, Terminal } from 'lucide-react';
import { useId, useMemo, useState } from 'react';

import {
  DEFAULT_SCENARIO,
  TOPOLOGY_SHAPES,
  buildCommand,
  describeScenario,
  validateScenario,
  type ScenarioField,
  type ScenarioParams,
} from '@/lab/scenario';

const TOPOLOGY_LABELS: Record<(typeof TOPOLOGY_SHAPES)[number], string> = {
  line: 'Line',
  ring: 'Ring',
  star: 'Star',
  mesh: 'Mesh',
  'two-cluster': 'Two clusters',
  random: 'Random',
};

const FIELD_IDS: Record<ScenarioField, string> = {
  topology: 'scenario-topology',
  nodes: 'scenario-nodes',
  degree: 'scenario-degree',
  messages: 'scenario-messages',
  seed: 'scenario-seed',
  dropProbability: 'scenario-drop',
  churnEveryRounds: 'scenario-churn',
  hopLimit: 'scenario-hops',
  rounds: 'scenario-rounds',
  stepMillis: 'scenario-step',
  broadcastRatio: 'scenario-broadcast',
  partitioned: 'scenario-partitioned',
};

/**
 * The scenario builder.
 *
 * <p>It builds a command. It does not run the simulator: Java cannot execute
 * in this page, and pretending otherwise would make the results look like a
 * browser toy rather than a reproducible run. The copy is explicit about it.
 */
export function ScenarioBuilder() {
  const [params, setParams] = useState<ScenarioParams>(DEFAULT_SCENARIO);
  const [copied, setCopied] = useState<'idle' | 'copied' | 'failed'>('idle');
  const [advanced, setAdvanced] = useState(false);
  const commandId = useId();

  const validation = useMemo(() => validateScenario(params), [params]);
  const command = useMemo(() => buildCommand(params), [params]);

  const update = <Key extends ScenarioField>(key: Key, value: ScenarioParams[Key]): void => {
    setParams((current) => ({ ...current, [key]: value }));
    setCopied('idle');
  };

  const copy = async (): Promise<void> => {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(command);
      } else {
        const area = document.createElement('textarea');
        area.value = command;
        area.setAttribute('readonly', '');
        area.style.position = 'fixed';
        area.style.opacity = '0';
        document.body.appendChild(area);
        area.select();
        const ok = document.execCommand('copy');
        document.body.removeChild(area);
        if (!ok) throw new Error('copy rejected');
      }
      setCopied('copied');
    } catch {
      setCopied('failed');
    }
  };

  return (
    <section className="surface" aria-labelledby="scenario-title">
      <header className="border-b border-base-700 px-5 py-4">
        <div className="flex items-center gap-2">
          <Terminal size={15} className="text-network" aria-hidden="true" />
          <h2 id="scenario-title" className="text-sm font-medium text-ink-primary">
            Build a scenario
          </h2>
        </div>
        <p className="mt-1.5 text-[11px] leading-relaxed text-ink-tertiary">
          Generates the command for the real Java simulator. It does not run the
          simulation in this browser.
        </p>
      </header>

      <form
        className="space-y-4 px-5 py-5"
        noValidate
        onSubmit={(event) => event.preventDefault()}
      >
        <div>
          <FieldLabel htmlFor={FIELD_IDS.topology}>Topology</FieldLabel>
          <select
            id={FIELD_IDS.topology}
            value={params.topology}
            onChange={(event) => update('topology', event.target.value as ScenarioParams['topology'])}
            aria-invalid={Boolean(validation.errors.topology)}
            aria-describedby={validation.errors.topology ? `${FIELD_IDS.topology}-error` : undefined}
            className={inputClass(Boolean(validation.errors.topology))}
          >
            {TOPOLOGY_SHAPES.map((shape) => (
              <option key={shape} value={shape}>
                {TOPOLOGY_LABELS[shape]}
              </option>
            ))}
          </select>
          <FieldError id={`${FIELD_IDS.topology}-error`} message={validation.errors.topology} />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <NumberField
            id={FIELD_IDS.nodes}
            label="Nodes"
            value={params.nodes}
            min={2}
            max={500}
            step={1}
            onChange={(value) => update('nodes', value)}
            error={validation.errors.nodes}
            hint="2–500"
          />
          <NumberField
            id={FIELD_IDS.messages}
            label="Messages"
            value={params.messages}
            min={0}
            max={5000}
            step={1}
            onChange={(value) => update('messages', value)}
            error={validation.errors.messages}
            hint="composed during the run"
          />
          <NumberField
            id={FIELD_IDS.seed}
            label="Seed"
            value={params.seed}
            step={1}
            onChange={(value) => update('seed', value)}
            error={validation.errors.seed}
            hint="same seed, same decisions"
          />
          <NumberField
            id={FIELD_IDS.rounds}
            label="Rounds"
            value={params.rounds}
            min={1}
            max={10000}
            step={1}
            onChange={(value) => update('rounds', value)}
            error={validation.errors.rounds}
            hint="one tick per round"
          />
          <NumberField
            id={FIELD_IDS.dropProbability}
            label="Packet loss"
            value={params.dropProbability}
            min={0}
            max={1}
            step={0.01}
            onChange={(value) => update('dropProbability', value)}
            error={validation.errors.dropProbability}
            hint="0–1 per frame"
          />
          <NumberField
            id={FIELD_IDS.broadcastRatio}
            label="Broadcast share"
            value={params.broadcastRatio}
            min={0}
            max={1}
            step={0.05}
            onChange={(value) => update('broadcastRatio', value)}
            error={validation.errors.broadcastRatio}
            hint="0 = all directed"
          />
          <NumberField
            id={FIELD_IDS.churnEveryRounds}
            label="Churn every"
            value={params.churnEveryRounds}
            min={0}
            step={1}
            onChange={(value) => update('churnEveryRounds', value)}
            error={validation.errors.churnEveryRounds}
            hint="rounds; 0 disables"
          />
          <NumberField
            id={FIELD_IDS.hopLimit}
            label="Hop limit"
            value={params.hopLimit}
            min={1}
            max={32}
            step={1}
            onChange={(value) => update('hopLimit', value)}
            error={validation.errors.hopLimit}
            hint="1–32"
          />
        </div>

        <div className="rounded-xl border border-base-700 bg-base-900/60 px-3.5 py-3">
          <label className="flex cursor-pointer items-start gap-2.5" htmlFor={FIELD_IDS.partitioned}>
            <input
              id={FIELD_IDS.partitioned}
              type="checkbox"
              checked={params.partitioned}
              onChange={(event) => update('partitioned', event.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-base-500 bg-base-800 text-network focus-visible:ring-2 focus-visible:ring-network"
            />
            <span>
              <span className="block text-[12px] font-medium text-ink-primary">
                Start partitioned
              </span>
              <span className="mt-0.5 block text-[11px] leading-relaxed text-ink-tertiary">
                Takes the busiest relay offline before the first round, so the
                report can show what the protocol does when a cluster is cut off.
              </span>
            </span>
          </label>
        </div>

        <div>
          <button
            type="button"
            onClick={() => setAdvanced((value) => !value)}
            aria-expanded={advanced}
            className="text-[11px] font-medium text-ink-tertiary underline decoration-base-500 underline-offset-4 transition-colors hover:text-ink-primary"
          >
            {advanced ? 'Hide advanced options' : 'Advanced options'}
          </button>
          {advanced ? (
            <div className="mt-3 grid grid-cols-2 gap-3">
              <NumberField
                id={FIELD_IDS.degree}
                label="Target degree"
                value={params.degree}
                min={1}
                max={16}
                step={1}
                onChange={(value) => update('degree', value)}
                error={validation.errors.degree}
                hint="mesh and random"
              />
              <NumberField
                id={FIELD_IDS.stepMillis}
                label="Step (ms)"
                value={params.stepMillis}
                min={1}
                step={1}
                onChange={(value) => update('stepMillis', value)}
                error={validation.errors.stepMillis}
                hint="simulated time per round"
              />
            </div>
          ) : null}
        </div>

        <p className="border-t border-base-700 pt-4 text-[11px] leading-relaxed text-ink-tertiary">
          {describeScenario(params)}
        </p>

        <div>
          <div className="flex items-center justify-between gap-3">
            <label htmlFor={commandId} className="eyebrow">
              Command
            </label>
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => {
                  setParams(DEFAULT_SCENARIO);
                  setCopied('idle');
                }}
                className="inline-flex min-h-[30px] items-center gap-1.5 rounded-lg border border-base-600 px-2.5 text-[11px] font-medium text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                <RotateCcw size={12} aria-hidden="true" />
                Defaults
              </button>
              <button
                type="button"
                onClick={() => void copy()}
                disabled={!validation.ok}
                className="inline-flex min-h-[30px] items-center gap-1.5 rounded-lg border border-network/40 bg-network/10 px-2.5 text-[11px] font-semibold text-network transition-colors hover:bg-network/15 disabled:cursor-not-allowed disabled:opacity-40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                {copied === 'copied' ? (
                  <Check size={12} aria-hidden="true" />
                ) : (
                  <Copy size={12} aria-hidden="true" />
                )}
                {copied === 'copied' ? 'Copied' : 'Copy'}
              </button>
            </div>
          </div>
          <pre
            id={commandId}
            tabIndex={0}
            className="mt-2 max-h-56 overflow-auto rounded-xl border border-base-700 bg-base-950 p-3 text-[11px] leading-relaxed text-ink-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            <code>{command}</code>
          </pre>
          <p aria-live="polite" className="mt-2 text-[11px] text-ink-tertiary">
            {validation.ok
              ? copied === 'failed'
                ? 'Copy was blocked by the browser. Select the command and copy it manually.'
                : 'Run this from the repository root. It writes report.json, which you can import below.'
              : 'Fix the highlighted fields before copying the command.'}
          </p>
        </div>
      </form>
    </section>
  );
}

function FieldLabel({ htmlFor, children }: { htmlFor: string; children: string }) {
  return (
    <label htmlFor={htmlFor} className="mb-1.5 block text-[11px] font-medium text-ink-secondary">
      {children}
    </label>
  );
}

function inputClass(invalid: boolean): string {
  return (
    'w-full rounded-lg border bg-base-900 px-3 py-2 text-[13px] text-ink-primary ' +
    'focus-visible:outline-none focus-visible:ring-1 ' +
    (invalid
      ? 'border-emergency/70 focus-visible:border-emergency focus-visible:ring-emergency'
      : 'border-base-600 focus-visible:border-network focus-visible:ring-network')
  );
}

function FieldError({ id, message }: { id: string; message: string | undefined }) {
  if (!message) return null;
  return (
    <p id={id} role="alert" className="mt-1.5 text-[11px] leading-relaxed text-emergency">
      {message}
    </p>
  );
}

function NumberField({
  id,
  label,
  value,
  min,
  max,
  step,
  hint,
  error,
  onChange,
}: {
  id: string;
  label: string;
  value: number;
  min?: number;
  max?: number;
  step: number;
  hint: string;
  error: string | undefined;
  onChange: (value: number) => void;
}) {
  return (
    <div>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <input
        id={id}
        type="number"
        inputMode="decimal"
        value={Number.isNaN(value) ? '' : value}
        min={min}
        max={max}
        step={step}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : `${id}-hint`}
        onChange={(event) => {
          const raw = event.target.value;
          onChange(raw === '' ? Number.NaN : Number(raw));
        }}
        className={inputClass(Boolean(error))}
      />
      <p id={`${id}-hint`} className="mt-1 text-[10px] leading-snug text-ink-tertiary">
        {hint}
      </p>
      <FieldError id={`${id}-error`} message={error} />
    </div>
  );
}
