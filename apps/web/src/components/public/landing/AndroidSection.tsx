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

const DEVICE_STATES = ['SIGNED', 'STORED', 'RELAY'];

export function AndroidSection() {
  return (
    <section
      id="android"
      className="relative scroll-mt-20 overflow-hidden border-b border-white/[0.08] py-24 sm:py-32 lg:py-40"
    >
      <div
        className="pointer-events-none absolute inset-y-0 right-0 w-[55%] bg-[radial-gradient(60%_60%_at_70%_50%,rgba(92,225,223,0.06),transparent_70%)]"
        aria-hidden="true"
      />
      <div className="container-page relative">
        <div className="grid gap-16 lg:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)] lg:gap-20">
          <div>
            <SectionIntro
              index="05"
              eyebrow="Android experience"
              title="A field tool should survive the field."
              body="The Android build is not a concept render. It is a signed, installable experimental preview. It asks for the nearby-devices permission, and it does not join a mesh until its owner says so."
            />

            <div className="mt-10 grid gap-px overflow-hidden rounded-2xl border border-white/[0.08] bg-white/[0.08] sm:grid-cols-2">
              {FEATURES.map((feature) => {
                const Icon = feature.icon;
                return (
                  <div
                    key={feature.label}
                    className="flex items-center gap-3 bg-base-950 px-4 py-4"
                  >
                    <Icon size={14} className="shrink-0 text-network" aria-hidden="true" />
                    <span className="text-[12px] text-ink-secondary">{feature.label}</span>
                  </div>
                );
              })}
            </div>

            <Reveal delay={0.06} className="mt-7 flex items-start gap-3 border-l border-pending/40 pl-5">
              <ShieldAlert size={16} className="mt-0.5 shrink-0 text-pending" aria-hidden="true" />
              <p className="max-w-lg text-[12px] leading-relaxed text-ink-secondary">
                <span className="font-semibold text-ink-primary">The honest boundary:</span>{' '}
                the developer reports two physical phones working together.
                Multi-hop delivery across three or more physical devices remains
                unverified. Simulation is not field evidence.
              </p>
            </Reveal>

            <div className="mt-12 max-w-md">
              <ReleaseSummary />
            </div>
          </div>

          <div className="relative min-h-[480px] lg:min-h-[620px]">
            <div
              className="absolute left-1/2 top-1/2 h-[560px] w-[560px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/[0.05] opacity-70"
              aria-hidden="true"
            />
            <div
              className="absolute left-1/2 top-1/2 h-[420px] w-[420px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-network/[0.08]"
              aria-hidden="true"
            />
            <div
              className="absolute left-1/2 top-1/2 h-[300px] w-[300px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/[0.06]"
              aria-hidden="true"
            />

            <Reveal
              delay={0.08}
              className="absolute left-1/2 top-1/2 w-[248px] -translate-x-1/2 -translate-y-1/2 sm:w-[276px]"
            >
              <div className="relative aspect-[9/19] rotate-[-5deg] rounded-[38px] border border-white/15 bg-base-950 p-[7px] shadow-[0_50px_120px_-40px_rgba(0,0,0,0.9),0_0_60px_-20px_rgba(92,225,223,0.2)]">
                <div className="relative h-full overflow-hidden rounded-[31px] border border-white/[0.07] bg-base-900">
                  <div
                    className="absolute inset-0"
                    style={{
                      backgroundImage:
                        'radial-gradient(50% 42% at 50% 32%, rgba(92,225,223,0.16), transparent 70%), linear-gradient(to bottom, transparent 55%, rgba(4,6,7,0.7))',
                    }}
                    aria-hidden="true"
                  />
                  <div
                    className="absolute inset-0 opacity-50"
                    style={{
                      backgroundImage:
                        'linear-gradient(to right, rgba(244,241,232,0.035) 1px, transparent 1px), linear-gradient(to bottom, rgba(244,241,232,0.035) 1px, transparent 1px)',
                      backgroundSize: '32px 32px',
                    }}
                    aria-hidden="true"
                  />

                  <div className="relative flex h-full flex-col p-5">
                    <div className="flex items-center justify-between">
                      <span className="text-[9px] font-semibold uppercase tracking-[0.2em] text-ink-secondary">
                        ResQNet
                      </span>
                      <span className="inline-flex items-center gap-1.5 text-[7px] font-semibold uppercase tracking-[0.14em] text-delivered">
                        <span className="h-1 w-1 rounded-full bg-delivered" aria-hidden="true" />
                        opt-in
                      </span>
                    </div>

                    <div className="mt-auto">
                      <div className="relative mx-auto mb-8 grid h-32 w-32 place-items-center">
                        <span className="absolute inset-0 rounded-full border border-network/20" aria-hidden="true" />
                        <span className="absolute inset-4 rounded-full border border-network/30" aria-hidden="true" />
                        <span className="absolute inset-8 rounded-full border border-network/50" aria-hidden="true" />
                        <span className="relative grid h-12 w-12 place-items-center rounded-full border border-network/50 bg-network-soft text-network">
                          <RadioTower size={18} aria-hidden="true" />
                        </span>
                        <span className="absolute -right-1 top-4 h-2 w-2 rounded-full bg-emergency shadow-[0_0_12px_rgba(255,92,53,0.8)]" aria-hidden="true" />
                        <span className="absolute -left-2 bottom-6 h-1.5 w-1.5 rounded-full bg-delivered shadow-[0_0_10px_rgba(98,230,181,0.8)]" aria-hidden="true" />
                      </div>
                      <p className="text-center text-[8px] uppercase tracking-[0.18em] text-ink-tertiary">
                        Nearby mesh · illustrative device state
                      </p>
                      <div className="mt-4 grid grid-cols-3 gap-1.5">
                        {DEVICE_STATES.map((state, index) => (
                          <div
                            key={state}
                            className={
                              'rounded-md border px-1.5 py-2 text-center text-[7px] font-semibold uppercase tracking-[0.1em] ' +
                              (index === 1
                                ? 'border-network/40 bg-network-soft text-network'
                                : 'border-white/10 bg-white/[0.02] text-ink-tertiary')
                            }
                          >
                            {state}
                          </div>
                        ))}
                      </div>
                    </div>

                    <p className="mt-6 text-center text-[8px] leading-relaxed text-ink-tertiary">
                      No tower · no server · no account
                    </p>
                  </div>
                </div>
              </div>
              <p className="mt-5 text-center text-[9px] uppercase tracking-[0.16em] text-ink-tertiary">
                Interface concept · not a screenshot
              </p>
            </Reveal>

            <div className="pointer-events-none absolute inset-x-0 bottom-0 flex justify-between text-[8px] uppercase tracking-[0.18em] text-ink-tertiary">
              <span>Radio field / 01</span>
              <span>Signed APK</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
