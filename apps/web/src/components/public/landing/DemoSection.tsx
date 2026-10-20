import { ArrowUpRight } from 'lucide-react';

import { MeshDemo } from '@/components/demo/MeshDemo';
import { ButtonLink } from '@/components/primitives/Button';
import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';

export function DemoSection() {
  return (
    <section id="demo-preview" className="section-space scroll-mt-20 border-b border-base-600">
      <div className="container-page">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <SectionIntro
            index="03"
            eyebrow="Field lab"
            title="Cut a link. Watch the route reconsider."
            body="A deterministic protocol simulation you can operate. Choose an origin, send an SOS, inspect each handoff, then remove a relay and see whether the message reroutes or waits."
          />
          <ButtonLink to="/demo" variant="secondary" className="shrink-0">
            Open the full field lab
            <ArrowUpRight size={15} aria-hidden="true" />
          </ButtonLink>
        </div>

        <Reveal delay={0.08} className="mt-12">
          <MeshDemo />
        </Reveal>

        <p className="mt-4 text-[11px] leading-relaxed text-ink-tertiary">
          Simulation only. No live radio telemetry, no location data and no
          message leaves this browser tab.
        </p>
      </div>
    </section>
  );
}
