import { Pause, Play, RotateCcw, SkipBack, SkipForward } from 'lucide-react';
import { useEffect, useMemo, type ReactNode } from 'react';

import { useLabStore } from '@/lab/labStore';
import { simTimeAtCursor } from '@/lab/replay';

const SPEEDS = [1, 2, 4, 8] as const;

function formatOffset(milliseconds: number): string {
  const seconds = milliseconds / 1000;
  if (seconds < 60) return `${seconds.toFixed(2)} s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${(seconds - minutes * 60).toFixed(1)}s`;
}

/**
 * Replay transport.
 *
 * <p>The cursor is an index into the deterministic event order, not a
 * wall-clock timer. Scrub to an event and the map, counters and event stream
 * all describe the same moment.
 */
export function ReplayTimeline() {
  const model = useLabStore((state) => state.model);
  const cursor = useLabStore((state) => state.cursor);
  const playing = useLabStore((state) => state.playing);
  const speed = useLabStore((state) => state.speed);
  const play = useLabStore((state) => state.play);
  const pause = useLabStore((state) => state.pause);
  const reset = useLabStore((state) => state.reset);
  const seek = useLabStore((state) => state.seek);
  const stepForward = useLabStore((state) => state.stepForward);
  const stepBackward = useLabStore((state) => state.stepBackward);
  const setSpeed = useLabStore((state) => state.setSpeed);
  const tick = useLabStore((state) => state.tick);

  useEffect(() => {
    if (!playing) return undefined;
    let frame = 0;
    const loop = (): void => {
      tick();
      frame = window.requestAnimationFrame(loop);
    };
    frame = window.requestAnimationFrame(loop);
    return () => window.cancelAnimationFrame(frame);
  }, [playing, tick]);

  const buckets = useMemo(() => {
    if (!model || model.orderedEvents.length === 0) return [];
    const COUNT = 72;
    const span = Math.max(1, model.endMillis - model.startMillis);
    const counts = new Array<number>(COUNT).fill(0);
    for (const event of model.orderedEvents) {
      const ratio = (event.atEpochMillis - model.startMillis) / span;
      const index = Math.min(COUNT - 1, Math.max(0, Math.floor(ratio * COUNT)));
      counts[index] = (counts[index] ?? 0) + 1;
    }
    const highest = Math.max(...counts, 1);
    return counts.map((count) => count / highest);
  }, [model]);

  if (!model) return null;

  const total = model.orderedEvents.length;
  const current = cursor > 0 ? model.orderedEvents[cursor - 1] : undefined;
  const currentTime = simTimeAtCursor(model, cursor);
  const elapsed = currentTime - model.startMillis;
  const duration = Math.max(0, model.endMillis - model.startMillis);

  return (
    <section className="surface px-5 py-4" aria-labelledby="replay-timeline-title">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h2 id="replay-timeline-title" className="eyebrow">
            Replay
          </h2>
          <span className="tabular text-[11px] text-ink-tertiary">
            {cursor} / {total} events
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <label htmlFor="replay-speed" className="sr-only">
            Replay speed
          </label>
          <select
            id="replay-speed"
            value={speed}
            onChange={(event) => setSpeed(Number(event.target.value))}
            className="h-8 rounded-lg border border-base-600 bg-base-900 px-2 text-[11px] text-ink-secondary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
          >
            {SPEEDS.map((entry) => (
              <option key={entry} value={entry}>
                {entry}×
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="mt-3 flex items-center gap-1.5">
        <TransportButton label="Reset to the start" onClick={reset}>
          <RotateCcw size={15} aria-hidden="true" />
        </TransportButton>
        <TransportButton label="Previous event" onClick={stepBackward} disabled={cursor === 0}>
          <SkipBack size={15} aria-hidden="true" />
        </TransportButton>
        <button
          type="button"
          onClick={() => (playing ? pause() : play())}
          disabled={total === 0}
          aria-label={playing ? 'Pause the replay' : 'Play the replay'}
          className="inline-flex h-10 w-10 items-center justify-center rounded-full bg-network text-base-950 transition-transform hover:scale-[1.04] disabled:opacity-40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network focus-visible:ring-offset-2 focus-visible:ring-offset-base-850"
        >
          {playing ? (
            <Pause size={16} fill="currentColor" aria-hidden="true" />
          ) : (
            <Play size={16} fill="currentColor" className="ml-0.5" aria-hidden="true" />
          )}
        </button>
        <TransportButton
          label="Next event"
          onClick={stepForward}
          disabled={cursor >= total}
        >
          <SkipForward size={15} aria-hidden="true" />
        </TransportButton>

        <div className="relative ml-2 flex-1">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 top-1/2 flex h-6 -translate-y-1/2 items-end gap-px opacity-50"
          >
            {buckets.map((height, index) => (
              <span
                key={index}
                className="flex-1 rounded-t-sm bg-network/50"
                style={{ height: `${Math.max(6, height * 100)}%` }}
              />
            ))}
          </div>
          <input
            type="range"
            min={0}
            max={total}
            step={1}
            value={cursor}
            onChange={(event) => seek(Number(event.target.value))}
            aria-label="Replay position"
            aria-valuetext={`${formatOffset(elapsed)} simulated, ${cursor} of ${total} events`}
            className="relative z-10 h-8 w-full cursor-pointer appearance-none bg-transparent accent-network focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network [&::-webkit-slider-runnable-track]:h-1 [&::-webkit-slider-runnable-track]:rounded-full [&::-webkit-slider-runnable-track]:bg-base-600 [&::-webkit-slider-thumb]:mt-[-6px] [&::-webkit-slider-thumb]:h-3.5 [&::-webkit-slider-thumb]:w-3.5 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-network"
          />
        </div>

        <span className="tabular hidden w-[112px] shrink-0 text-right text-[11px] text-ink-secondary sm:block">
          {formatOffset(elapsed)}
          <span className="text-ink-tertiary"> / {formatOffset(duration)}</span>
        </span>
      </div>

      <p className="mt-1 min-h-[18px] text-[11px] leading-relaxed text-ink-tertiary" aria-live="off">
        {current ? (
          <>
            <span className="tabular text-ink-secondary">round {current.round}</span>
            {' · '}
            <span className="text-ink-secondary">{current.kind}</span>
            {' · '}
            {current.node}
            {current.messageId ? (
              <>
                {' · '}
                <span className="font-mono">{current.messageId}</span>
              </>
            ) : null}
            {current.detail ? <> — {current.detail}</> : null}
          </>
        ) : (
          'Before the first recorded event.'
        )}
      </p>
    </section>
  );
}

function TransportButton({
  label,
  onClick,
  disabled,
  children,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-base-600 text-ink-secondary transition-colors hover:border-network/40 hover:text-ink-primary disabled:cursor-not-allowed disabled:opacity-30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
    >
      {children}
    </button>
  );
}
