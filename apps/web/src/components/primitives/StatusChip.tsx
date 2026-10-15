import type { ReactNode } from 'react';

type Tone = 'network' | 'delivered' | 'pending' | 'emergency' | 'neutral';

interface StatusChipProps {
  tone: Tone;
  children: ReactNode;
  /** Optional pulsing dot, for "live" states only. */
  pulse?: boolean;
  className?: string;
}

const TONES: Record<Tone, string> = {
  network: 'bg-network-soft text-network',
  delivered: 'bg-delivered-soft text-delivered',
  pending: 'bg-pending-soft text-pending',
  emergency: 'bg-emergency-soft text-emergency',
  neutral: 'bg-base-700 text-ink-tertiary',
};

/**
 * Status chip.
 *
 * Always paired with a word. A coloured dot alone is unreadable for roughly one
 * in twelve men, which is not an acceptable failure rate for a status display
 * in an emergency tool.
 */
export function StatusChip({ tone, children, pulse, className = '' }: StatusChipProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] ` +
        `font-semibold uppercase tracking-widest ${TONES[tone]} ${className}`}
    >
      {pulse ? (
        <span className="relative flex h-1.5 w-1.5" aria-hidden="true">
          <span className="absolute inline-flex h-full w-full animate-signal-pulse rounded-full bg-current" />
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-current" />
        </span>
      ) : null}
      {children}
    </span>
  );
}
