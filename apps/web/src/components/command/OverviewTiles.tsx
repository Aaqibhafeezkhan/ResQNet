import { motion, useReducedMotion } from 'framer-motion';
import { Activity, Flame, Inbox, Radio, ShieldCheck } from 'lucide-react';
import type { ReactNode } from 'react';

import type { Overview } from '@/lib/api';

interface Tile {
  label: string;
  value: string;
  hint: string;
  icon: ReactNode;
  tone: 'network' | 'delivered' | 'pending' | 'emergency';
}

interface OverviewTilesProps {
  overview: Overview | null;
  /** Device counts from the simulation run, clearly labelled. */
  nodeCount: number | null;
  eventCount: number | null;
}

/**
 * Command overview.
 *
 * <p>Numbers count up rather than swapping in, because a value that appears
 * instantly is easy to miss when several change at once. Under reduced motion
 * the value simply renders.
 */
export function OverviewTiles({ overview, nodeCount, eventCount }: OverviewTilesProps) {
  const tiles: Tile[] = [
    {
      label: 'Active incidents',
      value: overview ? String(overview.open) : '—',
      hint: 'not resolved or dismissed',
      icon: <Inbox size={16} aria-hidden="true" />,
      tone: 'network',
    },
    {
      label: 'Critical',
      value: overview ? String(overview.critical) : '—',
      hint: 'severity 3, awaiting a responder',
      icon: <Flame size={16} aria-hidden="true" />,
      tone: (overview?.critical ?? 0) > 0 ? 'emergency' : 'pending',
    },
    {
      label: 'Known nodes',
      value: nodeCount === null ? '—' : String(nodeCount),
      hint: 'from the selected simulation run',
      icon: <Radio size={16} aria-hidden="true" />,
      tone: 'pending',
    },
    {
      label: 'Delivery events',
      value: eventCount === null ? '—' : String(eventCount),
      hint: 'recorded in the selected run',
      icon: <Activity size={16} aria-hidden="true" />,
      tone: 'delivered',
    },
  ];

  return (
    <section aria-label="Command overview" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {tiles.map((tile) => (
        <OverviewTile key={tile.label} {...tile} />
      ))}
    </section>
  );
}

const TONE_STYLES = {
  network: 'text-network',
  delivered: 'text-delivered',
  pending: 'text-pending',
  emergency: 'text-emergency',
} as const;

function OverviewTile({ label, value, hint, icon, tone }: Tile) {
  const reduced = useReducedMotion();
  const numeric = Number(value);
  const valid = Number.isFinite(numeric);

  return (
    <div className="surface p-5">
      <div className="flex items-center justify-between">
        <span className="eyebrow">{label}</span>
        <span className={TONE_STYLES[tone]}>{icon}</span>
      </div>
      <p className="tabular mt-3 text-3xl font-semibold leading-none text-ink-primary">
        {reduced || !valid ? (
          value
        ) : (
          <motion.span
            key={numeric}
            initial={{ opacity: 0.4, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
          >
            {value}
          </motion.span>
        )}
      </p>
      <p className="mt-2 flex items-center gap-1.5 text-xs text-ink-tertiary">
        {tone === 'delivered' ? (
          <ShieldCheck size={12} className="shrink-0" aria-hidden="true" />
        ) : null}
        {hint}
      </p>
    </div>
  );
}
