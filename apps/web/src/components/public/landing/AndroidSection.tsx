import { Check, RadioTower, ShieldAlert, Smartphone, WifiOff } from 'lucide-react';

import { ReleaseSummary } from '@/components/public/ReleaseSummary';
import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';

const FEATURES = [
  { icon: WifiOff, label: 'Works without mobile data' },
  { icon: RadioTower, label: 'Uses nearby radios only' },
  { icon: Smartphone, label: 'Android 8.0 or newer' },
  { icon: Check, label: 'Mesh participation is opt-in' },
];

export function AndroidSection() {
  return (
    <section id="android" className="section-space scroll-mt-20 border-b border-base-600">
      <div className="container-page">
        <div className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(340px,0.72fr)] lg:items-start lg:gap-20">
          <div>
            <SectionIntro
              index="05"
              eyebrow="Android preview"
              title="A field tool should survive the field."
              body="The Android build is not a concept render. It is a signed, installable experimental preview. It asks for the nearby-devices permission, and it does not join a mesh until its owner says so."
            />

            <div className="mt-9 grid gap-3 sm:grid-cols-2">
              {FEATURES.map((feature) => {
                const Icon = feature.icon;
                return (
                  <div
                    key={feature.label}
                    className="flex items-center gap-3 rounded-xl border border-base-600 bg-base-900/60 px-4 py-3"
                  >
                    <Icon size={15} className="shrink-0 text-network" aria-hidden="true" />
                    <span className="text-[13px] text-ink-secondary">{feature.label}</span>
                  </div>
                );
              })}
            </div>

            <Reveal delay={0.08} className="mt-7 flex items-start gap-3 rounded-xl border border-pending/25 bg-pending-soft p-4">
              <ShieldAlert size={16} className="mt-0.5 shrink-0 text-pending" aria-hidden="true" />
              <p className="text-[12px] leading-relaxed text-ink-secondary">
                <span className="font-semibold text-ink-primary">The honest boundary:</span>{' '}
                the developer reports two physical phones working together.
                Multi-hop delivery across three or more physical devices remains
                unverified. Simulation is not field evidence.
              </p>
            </Reveal>
          </div>

          <Reveal delay={0.1}>
            <ReleaseSummary />
          </Reveal>
        </div>
      </div>
    </section>
  );
}
