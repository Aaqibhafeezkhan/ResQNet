import { ArrowLeftRight, Check, Copy, Download, FileJson, ShieldAlert } from 'lucide-react';
import { useId, useRef, useState } from 'react';

import { useLabStore } from '@/lab/labStore';
import { buildComparison, exportComparisonMarkdown, formatDelta, formatRelative } from '@/lab/compare';
import { formatMetric } from '@/lab/metrics';
import { SAMPLE_REPORTS } from '@/lab/sampleReport';
import { MAX_REPORT_BYTES, parseReportFile } from '@/lab/parseReport';
import type { LabReport } from '@/lab/types';

type Side = 'left' | 'right';

/**
 * Two reports, compared without pretending the comparison is controlled.
 *
 * <p>The verdict is derived from the recorded configuration. If the seed or
 * any other dimension differs, the panel says so before it shows a single
 * delta.
 */
export function ComparisonPanel() {
  const left = useLabStore((state) => state.compareLeft);
  const right = useLabStore((state) => state.compareRight);
  const loaded = useLabStore((state) => state.report);
  const setCompare = useLabStore((state) => state.setCompare);
  const [copied, setCopied] = useState(false);

  const comparison = left && right ? buildComparison(left, right) : null;

  const exportMarkdown = (): void => {
    if (!comparison) return;
    const blob = new Blob([exportComparisonMarkdown(comparison)], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'resqnet-mesh-replay-comparison.md';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  };

  const copyMarkdown = async (): Promise<void> => {
    if (!comparison) return;
    try {
      await navigator.clipboard.writeText(exportComparisonMarkdown(comparison));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div className="space-y-5">
      <section className="surface px-5 py-5" aria-labelledby="compare-picker-title">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <ArrowLeftRight size={15} className="text-network" aria-hidden="true" />
              <h2 id="compare-picker-title" className="text-sm font-medium text-ink-primary">
                Compare two runs
              </h2>
            </div>
            <p className="mt-1.5 max-w-[62ch] text-[11px] leading-relaxed text-ink-tertiary">
              Each slot is a separate report. The comparison reads only what the two reports
              recorded; it does not re-simulate either run.
            </p>
          </div>
          {comparison ? (
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => void copyMarkdown()}
                className="inline-flex min-h-[32px] items-center gap-1.5 rounded-lg border border-base-600 px-2.5 text-[11px] font-medium text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                {copied ? <Check size={12} aria-hidden="true" /> : <Copy size={12} aria-hidden="true" />}
                {copied ? 'Copied' : 'Copy summary'}
              </button>
              <button
                type="button"
                onClick={exportMarkdown}
                className="inline-flex min-h-[32px] items-center gap-1.5 rounded-lg border border-network/40 bg-network/10 px-2.5 text-[11px] font-semibold text-network transition-colors hover:bg-network/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                <Download size={12} aria-hidden="true" />
                Export .md
              </button>
            </div>
          ) : null}
        </div>

        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <Slot
            side="left"
            label="A"
            report={left}
            loaded={loaded}
            onSelect={(report) => setCompare('left', report)}
          />
          <Slot
            side="right"
            label="B"
            report={right}
            loaded={loaded}
            onSelect={(report) => setCompare('right', report)}
          />
        </div>
      </section>

      {!comparison ? (
        <div className="surface px-5 py-10 text-center">
          <FileJson size={22} className="mx-auto text-ink-tertiary" aria-hidden="true" />
          <p className="mt-2 text-[12px] text-ink-secondary">
            Load a report into both slots to compare them.
          </p>
          <p className="mt-1 text-[11px] text-ink-tertiary">
            Two built-in samples are enough to see the difference between a healthy ring and a
            partitioned cluster.
          </p>
        </div>
      ) : (
        <>
          <div
            className={
              'flex items-start gap-3 rounded-xl border px-4 py-3.5 ' +
              (comparison.controlled
                ? 'border-delivered/35 bg-delivered/[0.06]'
                : 'border-pending/35 bg-pending/[0.06]')
            }
          >
            {comparison.controlled ? (
              <Check size={16} className="mt-0.5 shrink-0 text-delivered" aria-hidden="true" />
            ) : (
              <ShieldAlert size={16} className="mt-0.5 shrink-0 text-pending" aria-hidden="true" />
            )}
            <div>
              <p
                className={
                  'text-[10px] font-semibold uppercase tracking-[0.18em] ' +
                  (comparison.controlled ? 'text-delivered' : 'text-pending')
                }
              >
                {comparison.controlled ? 'Same configuration' : 'Not a controlled comparison'}
              </p>
              <p className="mt-1 text-[12px] leading-relaxed text-ink-secondary">
                {comparison.verdict}
              </p>
            </div>
          </div>

          {comparison.differences.length > 0 ? (
            <section className="surface px-5 py-4" aria-labelledby="compare-config-title">
              <h2 id="compare-config-title" className="eyebrow">
                Configuration differences
              </h2>
              <ul className="mt-2 grid gap-1.5 sm:grid-cols-2">
                {comparison.differences.map((difference) => (
                  <li
                    key={difference.key}
                    className="flex items-center justify-between gap-3 rounded-lg border border-base-700 bg-base-900/50 px-3 py-1.5 text-[11px]"
                  >
                    <span className="text-ink-tertiary">{difference.label}</span>
                    <span className="tabular text-right text-ink-secondary">
                      {difference.left} <span className="text-ink-tertiary">→</span>{' '}
                      {difference.right}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          <section className="surface overflow-hidden" aria-labelledby="compare-metrics-title">
            <header className="flex flex-wrap items-center justify-between gap-2 border-b border-base-700 px-5 py-4">
              <h2 id="compare-metrics-title" className="text-sm font-medium text-ink-primary">
                Recorded metrics
              </h2>
              <span className="text-[10px] text-ink-tertiary">
                Change is B − A; relative is against A
              </span>
            </header>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[560px] border-collapse text-[11px]">
                <thead>
                  <tr className="border-b border-base-700 text-left">
                    <th scope="col" className="px-5 py-2 font-medium text-ink-tertiary">
                      Metric
                    </th>
                    <th scope="col" className="px-4 py-2 text-right font-medium text-ink-tertiary">
                      A
                    </th>
                    <th scope="col" className="px-4 py-2 text-right font-medium text-ink-tertiary">
                      B
                    </th>
                    <th scope="col" className="px-4 py-2 text-right font-medium text-ink-tertiary">
                      Change
                    </th>
                    <th scope="col" className="px-5 py-2 text-right font-medium text-ink-tertiary">
                      Relative
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-base-800">
                  {comparison.rows.map((row) => (
                    <tr key={row.key}>
                      <th scope="row" className="px-5 py-1.5 text-left font-normal text-ink-secondary">
                        {row.label}
                      </th>
                      <td className="tabular px-4 py-1.5 text-right text-ink-primary">
                        {formatMetric(row.left, row.kind)}
                      </td>
                      <td className="tabular px-4 py-1.5 text-right text-ink-primary">
                        {formatMetric(row.right, row.kind)}
                      </td>
                      <td className="tabular px-4 py-1.5 text-right text-ink-secondary">
                        {formatDelta(row)}
                      </td>
                      <td className="tabular px-5 py-1.5 text-right text-ink-tertiary">
                        {formatRelative(row)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <footer className="border-t border-base-700 px-5 py-3">
              <p className="text-[10px] leading-relaxed text-ink-tertiary">
                An em dash means the metric was not measured in that report, not that it was zero.
                An unavailable value never contributes to a delta.
              </p>
            </footer>
          </section>
        </>
      )}
    </div>
  );
}

function Slot({
  side,
  label,
  report,
  loaded,
  onSelect,
}: {
  side: Side;
  label: string;
  report: LabReport | null;
  loaded: LabReport | null;
  onSelect: (report: LabReport | null) => void;
}) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const selectId = useId();
  const [error, setError] = useState<string | null>(null);
  const [sampleId, setSampleId] = useState('');

  const loadSample = (id: string): void => {
    const sample = SAMPLE_REPORTS.find((entry) => entry.id === id);
    if (!sample) return;
    const result = sample.load();
    if (result.ok) {
      setError(null);
      setSampleId(id);
      onSelect(result.report);
    } else {
      setError(result.error);
    }
  };

  const importFile = async (file: File): Promise<void> => {
    if (file.size > MAX_REPORT_BYTES) {
      setError('That file is larger than the 16 MB import limit.');
      return;
    }
    const result = await parseReportFile(file);
    if (result.ok) {
      setError(null);
      setSampleId('');
      onSelect(result.report);
    } else {
      setError(result.error);
    }
  };

  return (
    <div className="min-w-0 rounded-xl border border-base-700 bg-base-900/40 p-3.5">
      <div className="flex items-center justify-between gap-2">
        <label htmlFor={selectId} className="eyebrow">
          Report {label}
        </label>
        {report ? (
          <button
            type="button"
            onClick={() => {
              setError(null);
              setSampleId('');
              onSelect(null);
            }}
            className="text-[10px] text-ink-tertiary underline decoration-base-600 underline-offset-2 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            Clear
          </button>
        ) : null}
      </div>

      <select
        id={selectId}
        value={sampleId}
        onChange={(event) => {
          if (event.target.value) loadSample(event.target.value);
        }}
        className="mt-2 min-w-0 w-full rounded-lg border border-base-600 bg-base-900 px-2.5 py-2 text-[12px] text-ink-primary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
      >
        <option value="">Choose a built-in sample…</option>
        {SAMPLE_REPORTS.map((sample) => (
          <option key={sample.id} value={sample.id}>
            {sample.name}
          </option>
        ))}
      </select>

      <div className="mt-2 flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="inline-flex min-h-[30px] items-center gap-1.5 rounded-lg border border-base-600 px-2.5 text-[11px] text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
        >
          <FileJson size={12} aria-hidden="true" />
          Import JSON
        </button>
        {loaded ? (
          <button
            type="button"
            onClick={() => {
              setError(null);
              setSampleId('');
              onSelect(loaded);
            }}
            className="inline-flex min-h-[30px] items-center rounded-lg border border-base-600 px-2.5 text-[11px] text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            Use loaded report
          </button>
        ) : null}
        <input
          ref={inputRef}
          type="file"
          accept="application/json,.json"
          className="sr-only"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) void importFile(file);
            event.target.value = '';
          }}
        />
      </div>

      {report ? (
        <p className="mt-2 truncate text-[11px] text-ink-secondary" title={report.source}>
          {report.source}
          <span className="text-ink-tertiary">
            {' '}
            · {report.simulation.nodeCount ?? '—'} nodes · {report.events.length} events
          </span>
        </p>
      ) : (
        <p className="mt-2 text-[11px] text-ink-tertiary">No report selected.</p>
      )}

      {error ? (
        <p role="alert" className="mt-2 text-[10px] leading-relaxed text-emergency">
          {error}
        </p>
      ) : null}
      <span className="sr-only">{side === 'left' ? 'Slot A' : 'Slot B'}</span>
    </div>
  );
}
