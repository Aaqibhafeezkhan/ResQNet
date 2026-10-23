import { FileJson, Upload } from 'lucide-react';
import { useRef, useState } from 'react';

import { SAMPLE_REPORTS } from '@/lab/sampleReport';
import { MAX_REPORT_BYTES, parseReportFile, type ParseSuccess } from '@/lab/parseReport';

interface ReportLoaderProps {
  loadedName: string | null;
  warnings: readonly string[];
  onLoad: (result: ParseSuccess, label: string) => void;
  /** Rendered without the outer surface, for use inside another card. */
  bare?: boolean;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Report import.
 *
 * <p>The file is read with `File.text()` and parsed in this tab. There is no
 * upload path in this component and no URL parameter that fetches a report:
 * the only network request the Lab makes is the page bundle.
 */
export function ReportLoader({ loadedName, warnings, onLoad, bare = false }: ReportLoaderProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [busy, setBusy] = useState(false);

  const importFile = async (file: File): Promise<void> => {
    setError(null);
    setDetail(null);
    if (file.size === 0) {
      setError('That file is empty. Choose the JSON report written by the simulator.');
      return;
    }
    if (file.size > MAX_REPORT_BYTES) {
      setError(
        `That file is ${formatBytes(file.size)} (${file.size.toLocaleString('en-US')} bytes), over the ${formatBytes(
          MAX_REPORT_BYTES,
        )} limit. The Lab refuses larger reports rather than freezing the tab.`,
      );
      return;
    }
    setBusy(true);
    try {
      const result = await parseReportFile(file);
      if (result.ok) onLoad(result, file.name);
      else {
        setError(result.error);
        setDetail(result.detail ?? null);
      }
    } finally {
      setBusy(false);
    }
  };

  const loadSample = (id: string): void => {
    const sample = SAMPLE_REPORTS.find((entry) => entry.id === id);
    if (!sample) return;
    setError(null);
    setDetail(null);
    const result = sample.load();
    if (result.ok) onLoad(result, sample.name);
    else setError(result.error);
  };

  const body = (
    <div className="space-y-4">
      <div
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          const file = event.dataTransfer.files[0];
          if (file) void importFile(file);
        }}
        className={
          'rounded-xl border border-dashed px-4 py-6 text-center transition-colors ' +
          (dragging
            ? 'border-network bg-network/[0.06]'
            : 'border-base-500 bg-base-900/40 hover:border-base-400')
        }
      >
        <FileJson size={22} className="mx-auto text-network" aria-hidden="true" />
        <p className="mt-2 text-[12px] text-ink-secondary">
          Drop a simulator report here, or
        </p>
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={busy}
          className="mt-2 inline-flex min-h-[34px] items-center gap-2 rounded-lg border border-base-600 bg-base-800 px-3 text-[12px] font-medium text-ink-primary transition-colors hover:border-base-500 hover:bg-base-750 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
        >
          <Upload size={13} aria-hidden="true" />
          {busy ? 'Reading…' : 'Choose a file'}
        </button>
        <input
          ref={inputRef}
          type="file"
          accept="application/json,.json"
          className="sr-only"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) void importFile(file);
            // Allow re-importing the same file after fixing it.
            event.target.value = '';
          }}
        />
        <p className="mt-2 text-[10px] text-ink-tertiary">
          JSON up to {formatBytes(MAX_REPORT_BYTES)} · read locally, never uploaded
        </p>
      </div>

      {error ? (
        <div role="alert" className="rounded-xl border border-emergency/40 bg-emergency/[0.06] px-3.5 py-3">
          <p className="text-[12px] font-medium text-emergency">This report could not be opened.</p>
          <p className="mt-1 text-[11px] leading-relaxed text-ink-secondary">{error}</p>
          {detail ? (
            <details className="mt-2">
              <summary className="cursor-pointer text-[10px] text-ink-tertiary">
                Parser detail
              </summary>
              <p className="mt-1 break-words font-mono text-[10px] text-ink-tertiary">{detail}</p>
            </details>
          ) : null}
        </div>
      ) : null}

      <div>
        <div className="flex items-baseline justify-between gap-3">
          <h3 className="eyebrow">Built-in samples</h3>
          <span className="text-[10px] text-ink-tertiary">real reports, committed</span>
        </div>
        <ul className="mt-2 space-y-1.5">
          {SAMPLE_REPORTS.map((sample) => {
            const active = loadedName === sample.name;
            return (
              <li key={sample.id}>
                <button
                  type="button"
                  onClick={() => loadSample(sample.id)}
                  aria-pressed={active}
                  className={
                    'w-full rounded-xl border px-3.5 py-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network ' +
                    (active
                      ? 'border-network/45 bg-network/[0.06]'
                      : 'border-base-700 bg-base-900/40 hover:border-base-500 hover:bg-base-800')
                  }
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className="text-[12px] font-medium text-ink-primary">{sample.name}</span>
                    {active ? (
                      <span className="text-[9px] font-semibold uppercase tracking-widest text-network">
                        Loaded
                      </span>
                    ) : null}
                  </span>
                  <span className="mt-1 block text-[11px] leading-relaxed text-ink-tertiary">
                    {sample.description}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      </div>

      {loadedName ? (
        <p className="border-t border-base-700 pt-3 text-[11px] text-ink-tertiary">
          Loaded <span className="text-ink-secondary">{loadedName}</span>. Re-importing replaces
          the current replay; your files are never sent anywhere.
        </p>
      ) : null}

      {warnings.length > 0 ? (
        <div className="rounded-xl border border-base-600 bg-base-900/60 px-3.5 py-3">
          <p className="text-[11px] font-medium text-ink-secondary">Import notes</p>
          <ul className="mt-1.5 space-y-1">
            {warnings.map((warning) => (
              <li key={warning} className="text-[11px] leading-relaxed text-ink-tertiary">
                {warning}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );

  if (bare) return body;

  return (
    <section className="surface px-5 py-5" aria-labelledby="report-loader-title">
      <h2 id="report-loader-title" className="text-sm font-medium text-ink-primary">
        Load a report
      </h2>
      <div className="mt-3">{body}</div>
    </section>
  );
}
