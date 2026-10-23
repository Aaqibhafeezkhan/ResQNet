import { BarChart3 } from 'lucide-react';
import { useMemo } from 'react';

import { useLabStore } from '@/lab/labStore';
import {
  detailMetrics,
  extractMetrics,
  formatMetric,
  headlineMetrics,
  type LabMetric,
} from '@/lab/metrics';

function MetricValue({ metric }: { metric: LabMetric }) {
  if (metric.value === null) {
    return (
      <span
        className="text-ink-tertiary"
        title={metric.unavailableBecause ?? 'This run did not measure this value.'}
      >
        —
        <span className="ml-1 text-[10px] font-normal normal-case tracking-normal">
          not measured
        </span>
      </span>
    );
  }
  return <span>{formatMetric(metric.value, metric.kind)}</span>;
}

/**
 * The reported metrics.
 *
 * <p>These are the numbers the simulator wrote, for the whole run. Scrubbing
 * the replay does not change them and this panel never derives a replacement
 * for a measurement that is absent.
 */
export function MetricsPanel() {
  const report = useLabStore((state) => state.report);
  const sourceLabel = useLabStore((state) => state.sourceLabel);

  const metrics = useMemo(() => (report ? extractMetrics(report) : null), [report]);
  const headline = useMemo(() => (metrics ? headlineMetrics(metrics) : []), [metrics]);
  const detail = useMemo(() => (metrics ? detailMetrics(metrics) : []), [metrics]);

  if (!report || !metrics) return null;

  const window = report.eventWindow;
  const truncated = window.truncated === true || (window.retained !== null && window.total !== null && window.retained < window.total);

  return (
    <section className="surface" aria-labelledby="metrics-title">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-base-700 px-5 py-4">
        <div className="flex items-center gap-2">
          <BarChart3 size={15} className="text-network" aria-hidden="true" />
          <h2 id="metrics-title" className="text-sm font-medium text-ink-primary">
            Reported metrics
          </h2>
        </div>
        <span className="text-[10px] text-ink-tertiary">
          Whole run · not affected by the replay cursor
        </span>
      </header>

      <div className="grid grid-cols-2 gap-px bg-base-700 sm:grid-cols-3">
        {headline.map((metric) => (
          <div key={metric.key} className="bg-base-850 px-5 py-4">
            <p className="text-[10px] font-semibold uppercase tracking-widest text-ink-tertiary">
              {metric.label}
            </p>
            <p className="tabular mt-1.5 text-2xl font-medium tracking-tight text-ink-primary">
              <MetricValue metric={metric} />
            </p>
            {metric.note ? (
              <p className="mt-1.5 text-[10px] leading-relaxed text-ink-tertiary">{metric.note}</p>
            ) : null}
          </div>
        ))}
      </div>

      <div className="grid gap-6 px-5 py-5 lg:grid-cols-[1.15fr_0.85fr]">
        <div>
          <h3 className="eyebrow">Counters</h3>
          <dl className="mt-2 divide-y divide-base-800 border-y border-base-800">
            {detail.map((metric) => (
              <div key={metric.key} className="flex items-start justify-between gap-4 py-1.5">
                <dt className="text-[11px] text-ink-secondary" title={metric.note}>
                  {metric.label}
                </dt>
                <dd className="tabular shrink-0 text-right text-[11px] text-ink-primary">
                  <MetricValue metric={metric} />
                </dd>
              </div>
            ))}
          </dl>
        </div>

        <div>
          <h3 className="eyebrow">Provenance</h3>
          <dl className="mt-2 space-y-2 text-[11px]">
            <div className="flex justify-between gap-3">
              <dt className="text-ink-tertiary">Report</dt>
              <dd className="max-w-[60%] truncate text-right text-ink-secondary" title={sourceLabel}>
                {sourceLabel || report.source}
              </dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-ink-tertiary">Topology</dt>
              <dd className="text-right text-ink-secondary">
                {report.simulation.topology.toLowerCase().replace(/_/g, ' ')} ·{' '}
                {report.simulation.nodeCount ?? '—'} nodes
              </dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-ink-tertiary">Seed</dt>
              <dd className="tabular text-right text-ink-secondary">
                {report.simulation.seed ?? '—'}
              </dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-ink-tertiary">Round time</dt>
              <dd className="tabular text-right text-ink-secondary">
                {formatMetric(metrics.simulatedMillis, 'milliseconds')}
              </dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-ink-tertiary">CPU time</dt>
              <dd className="tabular text-right text-ink-secondary">
                {formatMetric(metrics.wallClockMillis, 'milliseconds')}
              </dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-ink-tertiary">Retained events</dt>
              <dd className="tabular text-right text-ink-secondary">
                {window.retained ?? '—'}
                <span className="text-ink-tertiary"> / {window.total ?? '—'}</span>
              </dd>
            </div>
          </dl>

          {truncated ? (
            <p className="mt-3 rounded-lg border border-pending/30 bg-pending/[0.05] px-2.5 py-2 text-[10px] leading-relaxed text-pending">
              The simulator kept a bounded event window. The replay covers the retained{' '}
              {window.retained ?? '—'} events, not the whole run. Counters above still describe
              the whole run.
            </p>
          ) : null}

          <p className="mt-3 border-t border-base-700 pt-3 text-[10px] leading-relaxed text-ink-tertiary">
            Round time is the configured rounds × step. The simulator then runs eight settling
            passes, so recorded events can extend past it; the replay follows the recorded event
            timestamps. Latency is simulated round-trip time. The Java simulator advances in fixed
            steps, so equal step counts produce equal latencies; that is a property of the model,
            not a measurement of a radio link.
          </p>
        </div>
      </div>
    </section>
  );
}
