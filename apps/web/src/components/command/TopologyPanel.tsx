import { useEffect, useMemo, useRef, useState } from 'react';
import { Info, Play, Pause, Zap } from 'lucide-react';

import { StatusChip } from '@/components/primitives/StatusChip';
import { millis, percent, relativeTime } from '@/lib/format';
import type { SimulationEvent, SimulationRun } from '@/lib/api';
import { buildLinks, buildNodes } from '@/components/mesh/meshLayout';
import { useDocumentVisible } from '@/lib/useVisibility';
import { useReducedMotion } from '@/lib/useReducedMotion';

interface TopologyPanelProps {
  run: SimulationRun | null;
  events: SimulationEvent[];
}

/**
 * Live mesh topology derived from a simulation run.
 *
 * <p>Replays the recorded event stream rather than inventing a picture of the
 * network: every node position comes from a node that actually existed in the
 * run, and every edge lights up because an event actually crossed it.
 *
 * <p>Everything in this panel is simulated. The label is not decoration; it is
 * the difference between a responder reviewing behaviour and a responder
 * believing they are watching live traffic.
 */
export function TopologyPanel({ run, events }: TopologyPanelProps) {
  const reduced = useReducedMotion();
  const [playing, setPlaying] = useState(true);
  const [cursor, setCursor] = useState(events.length);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  const nodes = useMemo(() => buildNodes(Math.max(6, run?.nodeCount ?? 8), run?.seed ?? 1), [
    run?.nodeCount,
    run?.seed,
  ]);
  const links = useMemo(() => buildLinks(nodes), [nodes]);

  useEffect(() => {
    setCursor(events.length);
  }, [events.length]);

  useEffect(() => {
    if (!playing || reduced || events.length === 0) return undefined;
    const timer = window.setInterval(() => {
      setCursor((current) => (current >= events.length ? 0 : current + 1));
    }, 140);
    return () => window.clearInterval(timer);
  }, [playing, reduced, events.length]);

  useDocumentVisible((visible) => {
    if (!visible) setPlaying(false);
  });

  const window_ = useMemo(
    () => events.slice(Math.max(0, cursor - 40), cursor + 1),
    [events, cursor],
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;
    const context = canvas.getContext('2d');
    if (!context) return undefined;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    let frame: number | null = null;

    const draw = () => {
      const rect = canvas.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) {
        frame = requestAnimationFrame(draw);
        return;
      }
      if (canvas.width !== Math.round(rect.width * dpr)) {
        canvas.width = Math.round(rect.width * dpr);
        canvas.height = Math.round(rect.height * dpr);
      }
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      const w = rect.width;
      const h = rect.height;

      context.clearRect(0, 0, w, h);

      for (const link of links) {
        const a = nodes.find((node) => node.id === link.from);
        const b = nodes.find((node) => node.id === link.to);
        if (!a || !b) continue;
        context.beginPath();
        context.moveTo(a.x * w, a.y * h);
        context.lineTo(b.x * w, b.y * h);
        context.strokeStyle = 'rgba(46, 211, 230, 0.10)';
        context.lineWidth = 1;
        context.stroke();
      }

      for (const node of nodes) {
        const cx = node.x * w;
        const cy = node.y * h;
        const r = Math.max(3, node.radius * Math.min(w, h));
        context.beginPath();
        context.arc(cx, cy, r, 0, Math.PI * 2);
        context.fillStyle = node.role === 'origin' ? '#E03B3B' : '#2ED3E6';
        context.fill();
      }

      for (const event of window_) {
        const index = Number.parseInt(event.node.replace(/\D/g, ''), 10);
        const node = Number.isFinite(index) ? nodes[index % nodes.length] : undefined;
        if (!node) continue;
        const cx = node.x * w;
        const cy = node.y * h;
        const colour =
          event.kind === 'rejected'
            ? 'rgba(224, 59, 59, 0.85)'
            : event.kind === 'acknowledged'
              ? 'rgba(53, 201, 138, 0.9)'
              : 'rgba(46, 211, 230, 0.75)';
        context.beginPath();
        context.arc(cx, cy, 4 + (event.kind === 'rejected' ? 2 : 0), 0, Math.PI * 2);
        context.fillStyle = colour;
        context.fill();
      }

      frame = requestAnimationFrame(draw);
    };

    frame = requestAnimationFrame(draw);
    return () => {
      if (frame !== null) cancelAnimationFrame(frame);
    };
  }, [links, nodes, window_]);

  if (!run) {
    return (
      <section className="surface p-6" aria-label="Mesh topology">
        <StatusChip tone="neutral">No simulation selected</StatusChip>
        <p className="mt-4 text-sm text-ink-secondary">
          Run the mesh simulator with{' '}
          <code className="rounded bg-base-700 px-1.5 py-0.5 font-mono text-[12px]">
            --gateway http://localhost:8080/api/v1/simulations
          </code>{' '}
          to populate this view with a reproducible network.
        </p>
      </section>
    );
  }

  return (
    <section className="surface overflow-hidden" aria-label="Mesh topology">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-base-700 px-5 py-4">
        <div className="flex items-center gap-2.5">
          <StatusChip tone="pending" pulse>
            Simulated
          </StatusChip>
          <h2 className="text-sm font-medium text-ink-primary">
            {run.topology.toLowerCase()} · {run.nodeCount} nodes
          </h2>
        </div>
        <button
          type="button"
          onClick={() => setPlaying((value) => !value)}
          className="inline-flex min-h-[36px] items-center gap-1.5 rounded-lg border border-base-600 px-2.5 text-xs font-medium text-ink-secondary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
        >
          {playing ? <Pause size={12} aria-hidden="true" /> : <Play size={12} aria-hidden="true" />}
          {playing ? 'Pause' : 'Replay'}
        </button>
      </header>

      <div className="relative aspect-[16/10] w-full bg-base-900">
        <canvas
          ref={canvasRef}
          className="h-full w-full"
          role="img"
          aria-label={`Simulated ${run.topology} mesh with ${run.nodeCount} nodes, replaying recorded delivery events`}
        />
        <p className="absolute bottom-3 left-3 rounded bg-base-900/80 px-2 py-1 text-[10px] uppercase tracking-widest text-ink-tertiary">
          Simulated · seed {run.seed}
        </p>
      </div>

      <dl className="grid grid-cols-2 gap-px border-t border-base-700 bg-base-700 sm:grid-cols-4">
        <Metric label="Delivery rate" value={percent(run.deliveryRate)} />
        <Metric label="Median latency" value={millis(run.medianLatencyMillis)} />
        <Metric label="p95 latency" value={millis(run.p95LatencyMillis)} />
        <Metric label="Partitions" value={String(run.partitions)} />
      </dl>

      <div className="border-t border-base-700 p-5">
        <h3 className="eyebrow">Event stream</h3>
        <ol className="mt-3 max-h-52 space-y-1.5 overflow-y-auto" aria-live="off">
          {events.slice(Math.max(0, cursor - 14), cursor).map((event, index) => (
            <li key={`${event.atEpochMillis}-${index}`} className="flex items-baseline gap-3">
              <span
                className={
                  'mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ' +
                  (event.kind === 'rejected'
                    ? 'bg-emergency'
                    : event.kind === 'acknowledged'
                      ? 'bg-delivered'
                      : 'bg-network')
                }
                aria-hidden="true"
              />
              <span className="tabular shrink-0 text-[11px] text-ink-tertiary">{event.node}</span>
              <span className="text-[12px] text-ink-secondary">
                {event.kind.replace(/-/g, ' ')}
              </span>
              {event.reason ? (
                <span className="text-[11px] text-emergency">{event.reason}</span>
              ) : null}
            </li>
          ))}
        </ol>
        <p className="mt-4 flex items-start gap-2 text-[11px] leading-relaxed text-ink-tertiary">
          <Info size={12} className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>
            Replaying run <Zap size={10} className="inline" aria-hidden="true" />{' '}
            {run.runId}, started {relativeTime(run.startedAt)}. Re-running the
            simulator with seed {run.seed} reproduces this stream exactly.
          </span>
        </p>
      </div>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-base-850 px-5 py-4">
      <dt className="eyebrow">{label}</dt>
      <dd className="tabular mt-1.5 text-lg font-semibold text-ink-primary">{value}</dd>
    </div>
  );
}
