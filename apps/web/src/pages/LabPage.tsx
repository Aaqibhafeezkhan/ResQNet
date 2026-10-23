import { ArrowRight, GitCompare, PlayCircle, RotateCcw } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';

import { ComparisonPanel } from '@/components/lab/ComparisonPanel';
import { EventStream } from '@/components/lab/EventStream';
import { MessageInspector } from '@/components/lab/MessageInspector';
import { MetricsPanel } from '@/components/lab/MetricsPanel';
import { NetworkMap } from '@/components/lab/NetworkMap';
import { ReplayTimeline } from '@/components/lab/ReplayTimeline';
import { ReportLoader } from '@/components/lab/ReportLoader';
import { ScenarioBuilder } from '@/components/lab/ScenarioBuilder';
import { SimulatedBanner } from '@/components/lab/SimulatedBanner';
import { PageHero } from '@/components/public/PageHero';
import { StatusChip } from '@/components/primitives/StatusChip';
import { useLabStore, type LabTab } from '@/lab/labStore';
import { SAMPLE_REPORTS } from '@/lab/sampleReport';
import { usePageMeta } from '@/lib/seo';

const TABS: { id: LabTab; label: string; icon: typeof PlayCircle }[] = [
  { id: 'replay', label: 'Replay', icon: PlayCircle },
  { id: 'compare', label: 'Compare', icon: GitCompare },
];

export function LabPage() {
  usePageMeta({
    title: 'Mesh replay lab — ResQNet',
    description:
      'Import a deterministic ResQNet Java simulator report and inspect the recorded topology, event stream, message handoffs and metrics. Files are read locally; nothing is uploaded.',
    path: '/lab',
  });

  const report = useLabStore((state) => state.report);
  const model = useLabStore((state) => state.model);
  const cursor = useLabStore((state) => state.cursor);
  const sourceLabel = useLabStore((state) => state.sourceLabel);
  const warnings = useLabStore((state) => state.warnings);
  const tab = useLabStore((state) => state.tab);
  const setTab = useLabStore((state) => state.setTab);
  const load = useLabStore((state) => state.load);
  const selectedMessageId = useLabStore((state) => state.selectedMessageId);
  const selectedNodeIndex = useLabStore((state) => state.selectedNodeIndex);
  const selectNode = useLabStore((state) => state.selectNode);

  const [tabHint, setTabHint] = useState<string | null>(null);

  const selectTab = (next: LabTab): void => {
    setTab(next);
    setTabHint(null);
  };

  return (
    <>
      <PageHero
        eyebrow="Replay lab / 03"
        title={
          <>
            Read the simulation.{' '}
            <span className="font-display font-normal italic">Not</span> the headline.
          </>
        }
        body="Import a report written by the ResQNet Java simulator and inspect the evidence at its own resolution: the recorded topology, every event, the handoffs behind a message, and the metrics the run actually measured."
        aside={
          <div className="rounded-2xl border border-base-600 bg-base-900/70 p-5">
            <StatusChip tone="pending">Simulated</StatusChip>
            <p className="mt-3 text-[10px] font-semibold uppercase tracking-[0.16em] text-pending">
              Not real radio telemetry
            </p>
            <dl className="mt-4 space-y-3 text-xs">
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Source</dt>
                <dd className="text-right text-ink-secondary">ResQNet Java simulator</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Determinism</dt>
                <dd className="text-right text-ink-secondary">Seeded · repeatable</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Import</dt>
                <dd className="text-right text-ink-secondary">Local, never uploaded</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Scope</dt>
                <dd className="text-right text-ink-secondary">Recorded runs only</dd>
              </div>
            </dl>
          </div>
        }
      />

      <section className="border-b border-base-600 pb-20 pt-12 sm:pt-16">
        <div className="container-page">
          <SimulatedBanner className="mb-6" />

          <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
            <div
              role="tablist"
              aria-label="Lab view"
              className="inline-flex rounded-xl border border-base-600 bg-base-900 p-1"
              onKeyDown={(event) => {
                if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
                event.preventDefault();
                const current = TABS.findIndex((entry) => entry.id === tab);
                const offset = event.key === 'ArrowRight' ? 1 : -1;
                const next = TABS[(current + offset + TABS.length) % TABS.length];
                if (!next) return;
                selectTab(next.id);
                setTabHint(next.id);
                const target = document.getElementById(`lab-tab-${next.id}`);
                target?.focus();
              }}
            >
              {TABS.map((entry) => {
                const Icon = entry.icon;
                const active = tab === entry.id;
                return (
                  <button
                    key={entry.id}
                    id={`lab-tab-${entry.id}`}
                    type="button"
                    role="tab"
                    aria-selected={active}
                    aria-controls={`lab-panel-${entry.id}`}
                    tabIndex={active ? 0 : -1}
                    onClick={() => selectTab(entry.id)}
                    className={
                      'inline-flex min-h-[36px] items-center gap-2 rounded-lg px-4 text-[12px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network ' +
                      (active
                        ? 'bg-base-750 text-ink-primary'
                        : 'text-ink-tertiary hover:text-ink-secondary')
                    }
                  >
                    <Icon size={14} aria-hidden="true" />
                    {entry.label}
                  </button>
                );
              })}
            </div>
            <p className="text-[11px] text-ink-tertiary" aria-live="polite">
              {tabHint ? `Switched to ${tabHint}.` : 'Reports are parsed in this tab.'}
            </p>
          </div>

          <div className="grid gap-5 lg:grid-cols-[320px_minmax(0,1fr)] lg:items-start">
            <aside className="min-w-0 space-y-5" aria-label="Report and scenario controls">
              <ReportLoader
                loadedName={report ? sourceLabel : null}
                warnings={warnings}
                onLoad={(result, label) => load(result.report, label, result.warnings)}
              />
              <ScenarioBuilder />
            </aside>

            <div className="min-w-0 space-y-5">
              {tab === 'replay' ? (
                <div
                  id="lab-panel-replay"
                  role="tabpanel"
                  aria-labelledby="lab-tab-replay"
                  className="space-y-5"
                >
                  {report && model ? (
                    <>
                      <ReplayTimeline />
                      <NetworkMap
                        report={report}
                        model={model}
                        cursor={cursor}
                        selectedMessageId={selectedMessageId}
                        selectedNodeIndex={selectedNodeIndex}
                        onSelectNode={selectNode}
                      />
                      <div className="grid gap-5 xl:grid-cols-2">
                        <EventStream />
                        <MessageInspector />
                      </div>
                      <MetricsPanel />
                    </>
                  ) : (
                    <EmptyReplay
                      onLoadSample={() => {
                        const sample = SAMPLE_REPORTS[0];
                        if (!sample) return;
                        const result = sample.load();
                        if (result.ok) load(result.report, sample.name, result.warnings);
                      }}
                    />
                  )}
                </div>
              ) : (
                <div
                  id="lab-panel-compare"
                  role="tabpanel"
                  aria-labelledby="lab-tab-compare"
                >
                  <ComparisonPanel />
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      <section className="section-space">
        <div className="container-page">
          <div className="grid gap-10 lg:grid-cols-[0.75fr_1.25fr] lg:gap-20">
            <div>
              <span className="eyebrow">How to read the evidence</span>
              <h2 className="display-balance mt-4 text-3xl font-semibold leading-[1.05] tracking-tightest text-ink-primary sm:text-4xl">
                A handoff is not a delivery. An unavailable number is not a zero.
              </h2>
              <p className="prose-measure mt-5 text-[14px]">
                The Lab is built around those two distinctions. The simulator writes down what it
                did; this page shows that record without filling in the gaps a compelling story
                would need.
              </p>
              <Link
                to="/demo"
                className="mt-6 inline-flex items-center gap-2 text-[13px] font-medium text-network transition-colors hover:text-ink-primary"
              >
                Try the interactive field lab
                <ArrowRight size={14} aria-hidden="true" />
              </Link>
            </div>

            <div className="divide-y divide-base-600 border-y border-base-600">
              <Explainer
                index="01"
                title="Handoff, delivery, acknowledgement"
                body="A forwarded event means a node passed a frame to its radio. A delivered event means the destination's inbox received it. Only an acknowledged event means the recipient confirmed it. The inspector labels all three separately and never draws a forwarding event as a confirmed route."
              />
              <Explainer
                index="02"
                title="Measured, or marked unavailable"
                body="The simulator reports an unmeasured latency as -1 and the parser turns that into nothing, not zero. A metric the run did not measure is rendered as an em dash with the reason. It is excluded from every comparison delta rather than counting as an improvement."
              />
              <Explainer
                index="03"
                title="Reproducible, with named limits"
                body="Every sample names the exact command that produced it, and the comparison reports the full configuration of both runs. Equal seeds and parameters give identical routing decisions; key material and CPU wall-clock time vary between runs and are never used as evidence."
              />
            </div>
          </div>

          <div className="mt-12 flex flex-col gap-4 rounded-2xl border border-base-600 bg-base-900/50 px-5 py-5 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-[12px] font-medium text-ink-primary">
                Want a report of your own?
              </p>
              <p className="mt-1 text-[11px] leading-relaxed text-ink-tertiary">
                The scenario builder on the left writes the Java command; run it from the
                repository root, then import the JSON it produces. This page never executes Java.
              </p>
            </div>
            <Link
              to="/contribute"
              className="inline-flex min-h-[38px] shrink-0 items-center gap-2 rounded-lg border border-base-600 px-3.5 text-[12px] font-medium text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary"
            >
              <RotateCcw size={13} aria-hidden="true" />
              Build and run it locally
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}

function Explainer({ index, title, body }: { index: string; title: string; body: string }) {
  return (
    <div className="grid grid-cols-[40px_1fr] gap-4 py-6">
      <span className="tabular pt-1 text-[10px] font-semibold tracking-[0.2em] text-network">
        {index}
      </span>
      <div>
        <h3 className="text-base font-semibold text-ink-primary">{title}</h3>
        <p className="mt-1.5 text-[13px] leading-relaxed text-ink-secondary">{body}</p>
      </div>
    </div>
  );
}

function EmptyReplay({ onLoadSample }: { onLoadSample: () => void }) {
  return (
    <div className="surface px-6 py-14 text-center">
      <div className="mx-auto grid h-12 w-12 place-items-center rounded-full border border-network/30 bg-network/[0.06]">
        <PlayCircle size={22} className="text-network" aria-hidden="true" />
      </div>
      <h2 className="mt-4 text-lg font-semibold text-ink-primary">Load a report to begin</h2>
      <p className="mx-auto mt-2 max-w-[52ch] text-[12px] leading-relaxed text-ink-secondary">
        Import a JSON report written by the Java simulator, or start with the committed ring
        sample. The replay, event stream and message inspector all appear once a report is loaded.
      </p>
      <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
        <button
          type="button"
          onClick={onLoadSample}
          className="inline-flex min-h-[38px] items-center gap-2 rounded-lg bg-network px-4 text-[12px] font-semibold text-base-950 transition-transform hover:scale-[1.02] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network focus-visible:ring-offset-2 focus-visible:ring-offset-base-850"
        >
          <PlayCircle size={14} aria-hidden="true" />
          Open the ring sample
        </button>
        <Link
          to="/demo"
          className="inline-flex min-h-[38px] items-center gap-2 rounded-lg border border-base-600 px-4 text-[12px] font-medium text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary"
        >
          Open the field lab
        </Link>
      </div>
      <p className="mt-5 text-[10px] text-ink-tertiary">
        SIMULATED — NOT REAL RADIO TELEMETRY
      </p>
    </div>
  );
}
