import { ArrowUpRight, MousePointer2 } from 'lucide-react';

import { MeshDemo } from '@/components/demo/MeshDemo';
import { ButtonLink } from '@/components/primitives/Button';
import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';

export function DemoSection() {
  return (
    <section
      id="demo-preview"
      className="relative scroll-mt-20 overflow-hidden border-b border-white/[0.08] py-24 sm:py-32 lg:py-40"
    >
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-[520px] bg-[radial-gradient(60%_100%_at_50%_0%,rgba(92,225,223,0.07),transparent_70%)]"
        aria-hidden="true"
      />
      <div className="container-page relative">
        <div className="flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">
          <SectionIntro
            index="03"
            eyebrow="Explore the system"
            title="Cut a link. Watch the route reconsider."
            body="A deterministic protocol simulation you can operate. Choose an origin, send an SOS, inspect each handoff, then remove a relay and see whether the message reroutes or waits."
          />
          <ButtonLink to="/demo" variant="secondary" className="shrink-0">
            Open the full field lab
            <ArrowUpRight size={15} aria-hidden="true" />
          </ButtonLink>
        </div>

        <Reveal delay={0.06} className="mt-12 lg:mt-16">
          <div className="flex flex-wrap items-center justify-between gap-3 border-x border-t border-white/[0.08] bg-base-900/60 px-4 py-3 sm:px-6">
            <span className="inline-flex items-center gap-2 text-[9px] font-semibold uppercase tracking-[0.2em] text-ink-tertiary">
              <span className="relative flex h-1.5 w-1.5" aria-hidden="true">
                <span className="absolute inline-flex h-full w-full animate-signal-pulse rounded-full bg-network" />
                <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-network" />
              </span>
              Live protocol simulation
            </span>
            <span className="inline-flex items-center gap-2 text-[9px] uppercase tracking-[0.16em] text-ink-tertiary">
              <MousePointer2 size={11} aria-hidden="true" />
              Tap a relay to cut it
            </span>
            <span className="hidden text-[9px] uppercase tracking-[0.16em] text-ink-tertiary sm:inline">
              Browser state only · no network calls
            </span>
          </div>
          <MeshDemo className="rounded-none border-x border-white/[0.08] border-t-0" />
        </Reveal>

        <p className="mt-5 max-w-2xl text-[10px] leading-relaxed text-ink-tertiary">
          Simulation only. No live radio telemetry, no location data and no
          message leaves this browser tab. A modelled radio is not a physical
          one.
        </p>
      </div>
    </section>
  );
}
