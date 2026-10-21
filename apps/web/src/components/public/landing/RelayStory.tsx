import { Check, CornerDownRight, RadioTower, RotateCcw, Smartphone, WifiOff } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import {
  DEMO_LINKS,
  DEMO_NODES,
  findRoute,
  nodeById,
  nodeLabel,
  type NodeId,
} from '@/components/demo/meshDemoModel';

interface RelayStoryProps {
  activeStep: number;
  reduced: boolean;
}

const STEP_STATE = [
  { label: 'Signed + stored', tone: 'text-pending', chip: 'bg-pending-soft text-pending' },
  { label: 'Holding for a route', tone: 'text-pending', chip: 'bg-pending-soft text-pending' },
  { label: 'In transit', tone: 'text-network', chip: 'bg-network-soft text-network' },
  { label: 'Acknowledged', tone: 'text-delivered', chip: 'bg-delivered-soft text-delivered' },
] as const;

/**
 * A guided illustration of the same routing decisions the field lab exposes.
 *
 * The route is calculated with `findRoute` from the existing simulator model,
 * not a parallel mock. The figure is illustrative: it shows protocol states,
 * never radio range or live telemetry.
 */
export function RelayStory({ activeStep, reduced }: RelayStoryProps) {
  const [disabled, setDisabled] = useState<NodeId[]>([]);
  const [packetIndex, setPacketIndex] = useState(0);

  const route = useMemo(() => findRoute('amina', disabled), [disabled]);
  const routeKey = route?.join('>') ?? 'queued';
  const routePairs = useMemo(() => {
    const pairs = new Set<string>();
    if (!route) return pairs;
    for (let index = 0; index < route.length - 1; index += 1) {
      const from = route[index];
      const to = route[index + 1];
      if (from && to) pairs.add([from, to].sort().join('|'));
    }
    return pairs;
  }, [route]);

  useEffect(() => {
    setPacketIndex(0);
  }, [routeKey]);

  useEffect(() => {
    if (reduced || activeStep !== 2 || !route) return undefined;
    const timer = window.setInterval(() => {
      setPacketIndex((value) => (value + 1) % route.length);
    }, 1080);
    return () => window.clearInterval(timer);
  }, [activeStep, reduced, route]);

  const packetNode = route?.[packetIndex] ?? 'amina';
  const packet = nodeById(packetNode);
  const queued = !route;
  const state = STEP_STATE[activeStep] ?? STEP_STATE[0];
  const delivered = activeStep === 3 && Boolean(route) && !queued;

  const onToggle = (id: NodeId) => {
    setDisabled((current) =>
      current.includes(id) ? current.filter((entry) => entry !== id) : [...current, id],
    );
    setPacketIndex(0);
  };

  return (
    <figure className="overflow-hidden rounded-[24px] border border-white/10 bg-base-900/80 shadow-lift backdrop-blur-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/[0.08] px-4 py-3.5 sm:px-5">
        <span className="inline-flex items-center gap-2 text-[9px] font-semibold uppercase tracking-[0.2em] text-ink-tertiary">
          <span className="h-1.5 w-1.5 rounded-full bg-network" aria-hidden="true" />
          Relay illustration / deterministic seed
        </span>
        <span className={`rounded-full px-2.5 py-1 text-[9px] font-semibold uppercase tracking-[0.16em] ${state.chip}`}>
          {queued && activeStep > 0 ? 'Held locally' : state.label}
        </span>
      </div>

      <div
        className="relative m-3 aspect-[4/3] overflow-hidden rounded-[18px] border border-white/[0.06] bg-base-950 sm:m-4"
        role="group"
        aria-label="Illustrative multi-hop relay diagram. Not live radio telemetry."
      >
        <div
          className="pointer-events-none absolute inset-0 opacity-70"
          aria-hidden="true"
          style={{
            backgroundImage:
              'linear-gradient(to right, rgba(244,241,232,0.03) 1px, transparent 1px), linear-gradient(to bottom, rgba(244,241,232,0.03) 1px, transparent 1px)',
            backgroundSize: '48px 48px',
          }}
        />
        <div
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(65%_65%_at_18%_45%,rgba(255,92,53,0.07),transparent_64%),radial-gradient(55%_65%_at_86%_45%,rgba(98,230,181,0.055),transparent_66%)]"
          aria-hidden="true"
        />

        <svg
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          className="absolute inset-0 h-full w-full"
          aria-hidden="true"
        >
          <defs>
            <linearGradient id="relay-story-route" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="#FF5C35" />
              <stop offset="38%" stopColor="#5CE1DF" />
              <stop offset="100%" stopColor="#62E6B5" />
            </linearGradient>
          </defs>
          {DEMO_LINKS.map(([a, b]) => {
            const from = nodeById(a);
            const to = nodeById(b);
            const onRoute = routePairs.has([a, b].sort().join('|'));
            const isDisabled = disabled.includes(a) || disabled.includes(b);
            return (
              <line
                key={`${a}-${b}`}
                x1={from.x}
                y1={from.y}
                x2={to.x}
                y2={to.y}
                stroke={onRoute ? 'url(#relay-story-route)' : isDisabled ? '#28302f' : '#3b474c'}
                strokeWidth={onRoute ? 1.4 : 0.8}
                strokeOpacity={onRoute ? 0.9 : isDisabled ? 0.35 : 0.42}
                strokeDasharray={isDisabled ? '1.5 2.5' : onRoute ? '3 2.5' : undefined}
                vectorEffect="non-scaling-stroke"
                className={onRoute && !reduced ? 'animate-route-dash' : undefined}
              />
            );
          })}
        </svg>

        {DEMO_NODES.map((node) => {
          const onRoute = route?.includes(node.id) ?? false;
          const isDisabled = disabled.includes(node.id);
          const isSender = node.id === 'amina';
          const isDestination = node.id === 'post';
          const cuttable = node.id === 'jonas' || node.id === 'mei' || node.id === 'tomas';
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
                aria-pressed={cuttable ? isDisabled : undefined}
                aria-label={
                  cuttable
                    ? `${isDisabled ? 'Restore' : 'Cut'} ${node.name} relay in the illustration`
                    : isDestination
                      ? 'Field post, the recipient'
                      : isSender
                        ? 'Amina, the sender'
                        : `${node.name}, relay`
                }
                className={
                  'group flex w-[58px] flex-col items-center gap-1.5 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network sm:w-[76px] ' +
                  (isDisabled ? 'opacity-50' : '')
                }
              >
                <span
                  className={
                    'relative grid h-8 w-8 place-items-center rounded-full border transition-all duration-300 sm:h-11 sm:w-11 ' +
                    (isDisabled
                      ? 'border-white/10 bg-base-850 text-ink-tertiary'
                      : isDestination
                        ? 'border-delivered/45 bg-delivered-soft text-delivered'
                        : isSender
                          ? 'border-emergency/45 bg-emergency-soft text-emergency'
                          : onRoute
                            ? 'border-network/70 bg-network-soft text-network shadow-glow'
                            : 'border-white/15 bg-base-800 text-ink-secondary group-hover:border-white/30')
                  }
                >
                  {isDisabled ? (
                    <WifiOff size={13} aria-hidden="true" />
                  ) : isDestination ? (
                    <RadioTower size={15} aria-hidden="true" />
                  ) : (
                    <Smartphone size={14} aria-hidden="true" />
                  )}
                  {onRoute && !isDisabled && !isSender && !isDestination ? (
                    <span
                      className="absolute -inset-1 animate-signal-pulse rounded-full border border-network/30"
                      aria-hidden="true"
                    />
                  ) : null}
                </span>
                <span
                  className={
                    'max-w-full truncate text-[8px] font-medium uppercase tracking-[0.08em] sm:text-[9px] ' +
                    (isDisabled ? 'text-ink-tertiary line-through' : 'text-ink-secondary')
                  }
                >
                  {node.name}
                </span>
              </button>
            </div>
          );
        })}

        <span
          aria-hidden="true"
          className={
            'pointer-events-none absolute z-20 grid h-6 w-6 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border bg-base-950 shadow-glow ' +
            (queued
              ? 'border-pending text-pending'
              : delivered
                ? 'border-delivered text-delivered'
                : 'border-network text-network') +
            (reduced ? '' : ' transition-[left,top] duration-[820ms] ease-cinematic')
          }
          style={{ left: `${packet.x}%`, top: `${packet.y}%` }}
        >
          {delivered ? <Check size={10} /> : queued ? <CornerDownRight size={10} /> : <span className="h-1.5 w-1.5 rounded-full bg-current" />}
        </span>

      </div>

      <div className="mx-3 mb-3 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-white/[0.07] bg-base-950/80 px-3 py-2 sm:mx-4 sm:mb-4">
        <span className="text-[9px] uppercase tracking-[0.14em] text-ink-tertiary">
          {queued
            ? 'No route · message held on Amina'
            : route.map(nodeLabel).join(' → ')}
        </span>
        <span className="hidden text-[8px] uppercase tracking-[0.14em] text-ink-tertiary sm:block">
          Modelled links, not measured range
        </span>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-white/[0.08] px-4 py-3.5 sm:px-5">
        <span className="inline-flex items-center gap-2 text-[10px] text-ink-tertiary">
          <span className={`h-1.5 w-1.5 rounded-full ${queued ? 'bg-pending' : 'bg-network'}`} aria-hidden="true" />
          {queued ? 'Queued locally — no delivery is claimed.' : 'Route recalculated at the current carrier.'}
        </span>
        <div className="flex items-center gap-2">
          {disabled.length > 0 ? (
            <button
              type="button"
              onClick={() => setDisabled([])}
              className="inline-flex items-center gap-1.5 rounded-full border border-white/10 px-2.5 py-1.5 text-[9px] uppercase tracking-[0.12em] text-ink-secondary transition-colors hover:border-white/25 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              <RotateCcw size={10} aria-hidden="true" />
              Restore links
            </button>
          ) : null}
          <span className="hidden text-[9px] uppercase tracking-[0.12em] text-ink-tertiary sm:inline">
            Tap a relay to cut it
          </span>
        </div>
      </div>
    </figure>
  );
}
