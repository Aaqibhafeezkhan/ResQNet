import {
  Check,
  CornerDownRight,
  RadioTower,
  RotateCcw,
  Send,
  Smartphone,
  WifiOff,
} from 'lucide-react';
import { motion, useReducedMotion } from 'framer-motion';
import { useEffect, useMemo, useState } from 'react';

import { Button } from '@/components/primitives/Button';
import { StatusChip } from '@/components/primitives/StatusChip';
import {
  DEMO_LINKS,
  DEMO_NODES,
  SENDER_IDS,
  advanceDemo,
  createInitialState,
  currentNode,
  nodeById,
  nodeLabel,
  retryQueued,
  routeFromCurrent,
  routeLabel,
  selectSender,
  sendSos,
  toggleRelay,
  type DemoEventKind,
  type DemoPhase,
  type NodeId,
} from './meshDemoModel';

interface MeshDemoProps {
  className?: string;
}

const PHASE: Record<
  DemoPhase,
  { label: string; tone: 'neutral' | 'network' | 'pending' | 'delivered' }
> = {
  ready: { label: 'Ready', tone: 'neutral' },
  queued: { label: 'Queued locally', tone: 'pending' },
  'in-transit': { label: 'In transit', tone: 'network' },
  delivered: { label: 'Delivered + acknowledged', tone: 'delivered' },
};

const EVENT_TONE: Record<DemoEventKind, string> = {
  queue: 'text-ink-tertiary',
  route: 'text-network',
  hop: 'text-network',
  delivered: 'text-delivered',
  lost: 'text-pending',
  restored: 'text-delivered',
};

export function MeshDemo({ className = '' }: MeshDemoProps) {
  const [state, setState] = useState(() => createInitialState());
  const reduced = useReducedMotion() === true;

  useEffect(() => {
    if (state.phase !== 'in-transit') return undefined;
    const timer = window.setTimeout(
      () => setState((current) => advanceDemo(current)),
      reduced ? 500 : 1050,
    );
    return () => window.clearTimeout(timer);
  }, [reduced, state]);

  const current = currentNode(state);
  const phase = PHASE[state.phase];
  const route = routeFromCurrent(state);
  const hopsLeft = Math.max(route.length - 1, 0);
  const messageActive = state.phase !== 'ready';

  return (
    <div className={'surface-raised overflow-hidden ' + className}>
      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1.65fr)_minmax(320px,0.85fr)]">
        <div className="min-w-0 border-b border-base-600 p-4 sm:p-6 lg:border-b-0 lg:border-r">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="eyebrow inline-flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-network" aria-hidden="true" />
              Simulated link map
            </span>
            <StatusChip tone={phase.tone} pulse={state.phase === 'in-transit'}>
              {phase.label}
            </StatusChip>
          </div>

          <MeshMap
            state={state}
            reduced={reduced}
            onToggle={(id) => setState((value) => toggleRelay(value, id))}
          />

          <div className="mt-3 flex flex-wrap items-center justify-between gap-3 text-[11px] text-ink-tertiary">
            <span>Tap a relay to cut or restore its local link.</span>
            <span className="tabular">
              {messageActive ? `${hopsLeft} hop${hopsLeft === 1 ? '' : 's'} remaining` : 'No message in flight'}
            </span>
          </div>
        </div>

        <div className="flex min-w-0 flex-col p-5 sm:p-6">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-sm font-semibold text-ink-primary">Transmission console</h3>
            <span className="tabular text-[10px] uppercase tracking-widest text-ink-tertiary">
              seed 42
            </span>
          </div>

          <fieldset className="mt-5" disabled={state.phase === 'in-transit'}>
            <legend className="eyebrow">Origin device</legend>
            <div className="mt-2.5 grid grid-cols-3 gap-1.5">
              {SENDER_IDS.map((id) => (
                <button
                  key={id}
                  type="button"
                  onClick={() => setState((value) => selectSender(value, id))}
                  aria-pressed={state.sender === id}
                  className={
                    'rounded-lg border px-2 py-2 text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network disabled:cursor-not-allowed ' +
                    (state.sender === id
                      ? 'border-network/50 bg-network-soft text-network'
                      : 'border-base-600 bg-base-800 text-ink-secondary hover:border-base-500 hover:text-ink-primary')
                  }
                >
                  {nodeLabel(id)}
                </button>
              ))}
            </div>
          </fieldset>

          <div className="mt-4 grid grid-cols-2 gap-2">
            <Button
              onClick={() => setState((value) => sendSos(value))}
              disabled={state.phase === 'in-transit' || state.phase === 'queued'}
              className="w-full"
            >
              <Send size={15} aria-hidden="true" />
              {state.phase === 'delivered' ? 'Send again' : 'Send SOS'}
            </Button>
            {state.phase === 'queued' ? (
              <Button
                variant="secondary"
                onClick={() => setState((value) => retryQueued(value))}
                className="w-full"
              >
                <CornerDownRight size={15} aria-hidden="true" />
                Retry route
              </Button>
            ) : (
              <Button
                variant="secondary"
                onClick={() => setState(createInitialState(state.sender))}
                disabled={state.phase === 'ready'}
                className="w-full"
              >
                <RotateCcw size={15} aria-hidden="true" />
                Reset
              </Button>
            )}
          </div>

          <div className="mt-5 rounded-xl border border-base-600 bg-base-850 p-3.5">
            <div className="flex items-center gap-2">
              <span className="eyebrow">Current route</span>
              {state.phase === 'delivered' ? (
                <Check size={13} className="text-delivered" aria-hidden="true" />
              ) : null}
            </div>
            <p className="mt-2 min-h-[20px] text-[13px] leading-relaxed text-ink-secondary">
              {state.phase === 'ready'
                ? 'Send an SOS to calculate a route.'
                : state.phase === 'queued'
                  ? `Held on ${nodeLabel(state.heldAt ?? state.sender)} until a route returns.`
                  : routeLabel(route)}
            </p>
            <div className="mt-3 flex flex-wrap gap-1.5" aria-hidden="true">
              {(state.phase === 'queued' ? [state.heldAt ?? state.sender] : route).map(
                (id, index) => (
                  <span
                    key={`${id}-${index}`}
                    className={
                      'rounded-md px-2 py-1 text-[10px] font-medium ' +
                      (id === 'post'
                        ? 'bg-delivered-soft text-delivered'
                        : id === current
                          ? 'bg-network-soft text-network'
                          : 'bg-base-700 text-ink-secondary')
                    }
                  >
                    {nodeLabel(id)}
                  </span>
                ),
              )}
            </div>
          </div>

          <div className="mt-5 flex-1">
            <div className="flex items-center justify-between">
              <span className="eyebrow">Route events</span>
              <span className="tabular text-[10px] text-ink-tertiary">
                {String(state.events.length).padStart(2, '0')}
              </span>
            </div>
            <ol className="mt-2.5 space-y-2" aria-live="polite">
              {state.events.length === 0 ? (
                <li className="text-[12px] leading-relaxed text-ink-tertiary">
                  No transmission yet. The mesh is idle.
                </li>
              ) : (
                state.events
                  .slice()
                  .reverse()
                  .map((event) => (
                    <li
                      key={event.id}
                      className="grid grid-cols-[22px_1fr] gap-2 text-[12px] leading-relaxed"
                    >
                      <span className="tabular pt-0.5 text-[10px] text-ink-tertiary">
                        {String(event.id).padStart(2, '0')}
                      </span>
                      <span className={EVENT_TONE[event.kind]}>{event.text}</span>
                    </li>
                  ))
              )}
            </ol>
          </div>

          <p className="mt-5 border-t border-base-700 pt-4 text-[11px] leading-relaxed text-ink-tertiary">
            Software simulation of protocol states. Not live radio telemetry,
            and not a measurement of range or delivery performance.
          </p>
        </div>
      </div>
    </div>
  );
}

function MeshMap({
  state,
  reduced,
  onToggle,
}: {
  state: ReturnType<typeof createInitialState>;
  reduced: boolean;
  onToggle: (id: NodeId) => void;
}) {
  const current = currentNode(state);
  const routePairs = useMemo(() => {
    const pairs = new Set<string>();
    const active = state.route.slice(state.hopIndex);
    for (let index = 0; index < active.length - 1; index += 1) {
      const from = active[index];
      const to = active[index + 1];
      if (from && to) pairs.add(linkKey(from, to));
    }
    return pairs;
  }, [state.hopIndex, state.route]);

  const packet = state.lastHop
    ? {
        from: nodeById(state.lastHop.from),
        to: nodeById(state.lastHop.to),
      }
    : null;
  const resting = nodeById(current);

  return (
    <div
      className="relative mt-4 aspect-[4/3] min-h-[310px] w-full overflow-hidden rounded-xl border border-base-600 bg-base-900 sm:aspect-[16/10]"
      role="group"
      aria-label="Interactive mesh simulation map"
    >
      <div className="grid-field absolute inset-0 opacity-60" aria-hidden="true" />
      <div
        className="absolute inset-0 bg-[radial-gradient(70%_70%_at_20%_30%,rgba(67,216,220,0.06),transparent_60%)]"
        aria-hidden="true"
      />

      <svg
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        className="absolute inset-0 h-full w-full"
        aria-hidden="true"
      >
        {DEMO_LINKS.map(([a, b]) => {
          const from = nodeById(a);
          const to = nodeById(b);
          const disabled = state.disabled.includes(a) || state.disabled.includes(b);
          const onRoute = routePairs.has(linkKey(a, b));
          return (
            <line
              key={`${a}-${b}`}
              x1={from.x}
              y1={from.y}
              x2={to.x}
              y2={to.y}
              stroke={disabled ? '#2a3438' : onRoute ? '#43d8dc' : '#3b474c'}
              strokeWidth={onRoute ? 1.5 : 1}
              strokeDasharray={disabled ? '2 3' : onRoute ? '4 3' : undefined}
              strokeOpacity={disabled ? 0.6 : onRoute ? 0.9 : 0.45}
              vectorEffect="non-scaling-stroke"
              className={onRoute && !reduced ? 'animate-route-dash' : undefined}
            />
          );
        })}
      </svg>

      {DEMO_NODES.map((node) => {
        const disabled = state.disabled.includes(node.id);
        const isCurrent = node.id === current;
        const isSender = node.id === state.sender;
        const isDestination = node.id === 'post';
        const cuttable =
          !isDestination && !isSender && node.id !== current;
        const onRoute = state.route.includes(node.id);

        return (
          <div
            key={node.id}
            className="absolute z-10 -translate-x-1/2 -translate-y-1/2"
            style={{ left: `${node.x}%`, top: `${node.y}%` }}
          >
            <button
              type="button"
              onClick={() => cuttable && onToggle(node.id)}
              disabled={!cuttable}
              aria-pressed={cuttable ? disabled : undefined}
              aria-label={
                cuttable
                  ? `${disabled ? 'Restore' : 'Cut'} ${node.name} relay`
                  : isDestination
                    ? 'Field post destination'
                    : isSender
                      ? `${node.name}, origin device`
                      : `${node.name}, current carrier`
              }
              className={
                'group flex w-16 flex-col items-center gap-1.5 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network disabled:cursor-default sm:w-[92px] ' +
                (disabled ? 'opacity-45' : '')
              }
            >
              <span
                className={
                  'relative grid h-10 w-10 place-items-center rounded-full border transition-all duration-300 sm:h-14 sm:w-14 ' +
                  (disabled
                    ? 'border-base-500 bg-base-850 text-ink-tertiary'
                    : isDestination
                      ? 'border-delivered/45 bg-delivered-soft text-delivered'
                      : isSender
                        ? 'border-emergency/45 bg-emergency-soft text-emergency'
                        : isCurrent
                          ? 'border-network bg-network-soft text-network shadow-glow'
                          : onRoute
                            ? 'border-network/40 bg-base-800 text-network'
                            : 'border-base-500 bg-base-800 text-ink-secondary group-hover:border-base-500')
                }
              >
                {disabled ? (
                  <WifiOff size={17} aria-hidden="true" />
                ) : isDestination ? (
                  <RadioTower size={19} aria-hidden="true" />
                ) : (
                  <Smartphone size={18} aria-hidden="true" />
                )}
                {isCurrent && !disabled ? (
                  <span
                    className="absolute -inset-1 animate-signal-pulse rounded-full border border-network/40"
                    aria-hidden="true"
                  />
                ) : null}
              </span>
              <span
                className={
                  'max-w-full truncate text-[10px] font-medium leading-tight sm:text-[11px] ' +
                  (disabled ? 'text-ink-tertiary line-through' : 'text-ink-secondary')
                }
              >
                {node.name}
              </span>
            </button>
          </div>
        );
      })}

      {packet ? (
        <motion.span
          key={state.eventSeq}
          className="pointer-events-none absolute z-20 -translate-x-1/2 -translate-y-1/2"
          initial={
            reduced
              ? false
              : { left: `${packet.from.x}%`, top: `${packet.from.y}%` }
          }
          animate={{ left: `${packet.to.x}%`, top: `${packet.to.y}%` }}
          transition={{ duration: 0.78, ease: [0.22, 1, 0.36, 1] }}
        >
          <span className="grid h-6 w-6 place-items-center rounded-full border border-network bg-base-950 text-network shadow-glow">
            <Send size={11} aria-hidden="true" />
          </span>
        </motion.span>
      ) : state.phase !== 'ready' ? (
        <span
          className="pointer-events-none absolute z-20 -translate-x-1/2 -translate-y-1/2"
          style={{ left: `${resting.x}%`, top: `${resting.y}%` }}
        >
          <span className="grid h-6 w-6 place-items-center rounded-full border border-pending bg-base-950 text-pending">
            <Send size={11} aria-hidden="true" />
          </span>
        </span>
      ) : null}

      <div className="absolute inset-x-3 bottom-3 flex items-center justify-between gap-2 text-[9px] uppercase tracking-widest text-ink-tertiary">
        <span>Modelled links · not measured range</span>
        <span className="hidden sm:inline">No internet required</span>
      </div>
    </div>
  );
}

function linkKey(a: NodeId, b: NodeId): string {
  return [a, b].sort().join('|');
}
