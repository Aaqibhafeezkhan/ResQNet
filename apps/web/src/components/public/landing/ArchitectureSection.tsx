import { ArrowRight, ArrowUpRight, Cloud, Cpu, Globe2, Radio, Server, Smartphone } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';
import { GITHUB_ARCHITECTURE } from '@/lib/links';

const LAYERS = [
  {
    icon: Smartphone,
    label: 'Required',
    title: 'The local path',
    body: 'Android handsets, pure-Java mesh-core and nearby radios. No internet, gateway or account is needed for an SOS to be signed, stored and relayed.',
    tone: 'text-delivered',
  },
  {
    icon: Server,
    label: 'Optional',
    title: 'The coordination layer',
    body: 'A Spring Boot gateway can sync incident summaries when a device already has connectivity. If it is down, the mesh continues. It is a witness, not a dependency.',
    tone: 'text-ink-secondary',
  },
  {
    icon: Globe2,
    label: 'This site',
    title: 'The public surface',
    body: 'A static Vite site with no operational data. The responder command centre is a separate authenticated build and is not shipped in this public bundle.',
    tone: 'text-ink-secondary',
  },
];

const FACTS = [
  { icon: Cpu, label: 'mesh-core', value: 'Pure Java · zero dependencies' },
  { icon: Radio, label: 'Transport', value: 'Nearby Connections' },
  { icon: Server, label: 'Gateway', value: 'Spring Boot · optional' },
  { icon: Cloud, label: 'Cloud in SOS path', value: 'None', accent: true },
];

export function ArchitectureSection() {
  return (
    <section
      id="architecture"
      className="relative scroll-mt-20 overflow-hidden border-b border-white/[0.08] py-24 sm:py-32 lg:py-40"
    >
      <div className="container-page">
        <div className="flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">
          <SectionIntro
            index="07"
            eyebrow="Architecture"
            title="One engine. Three places it runs. No cloud in the SOS path."
            body="The routing engine is pure Java with no third-party dependencies. The same code runs on a handset and in the deterministic simulator, so a simulator result is a statement about the handset’s engine — not a second implementation."
          />
          <a
            href={GITHUB_ARCHITECTURE}
            target="_blank"
            rel="noreferrer noopener"
            className="group tap-target inline-flex shrink-0 items-center gap-2 text-[11px] font-medium uppercase tracking-[0.1em] text-network transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            Read the architecture
            <ArrowUpRight
              size={13}
              aria-hidden="true"
              className="transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
            />
          </a>
        </div>

        <Reveal delay={0.06} className="mt-14 lg:mt-20">
          <div className="relative overflow-hidden rounded-2xl border border-white/[0.08] bg-base-900/70 px-4 py-5 sm:px-7 sm:py-6">
            <div
              className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-network/35 to-transparent"
              aria-hidden="true"
            />
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:gap-3">
              {['Android', 'mesh-core', 'Nearby radio', 'peer relay', 'field post'].map(
                (step, index) => (
                  <div key={step} className="flex items-center gap-3">
                    <span
                      className={
                        'inline-flex items-center gap-2 whitespace-nowrap text-[9px] font-semibold uppercase tracking-[0.16em] ' +
                        (index === 0
                          ? 'text-emergency'
                          : index === 4
                            ? 'text-delivered'
                            : index === 3
                              ? 'text-network'
                              : 'text-ink-secondary')
                      }
                    >
                      <span
                        className={
                          'h-1.5 w-1.5 rounded-full ' +
                          (index === 0
                            ? 'bg-emergency'
                            : index === 4
                              ? 'bg-delivered'
                              : 'bg-network')
                        }
                        aria-hidden="true"
                      />
                      {step}
                    </span>
                    {index < 4 ? (
                      <ArrowRight
                        size={11}
                        className="hidden text-ink-tertiary/60 sm:block"
                        aria-hidden="true"
                      />
                    ) : null}
                  </div>
                ),
              )}
            </div>
            <div className="mt-5 flex items-center justify-between text-[8px] uppercase tracking-[0.16em] text-ink-tertiary">
              <span>No infrastructure required on this path</span>
              <span>Modelled / real engine</span>
            </div>
          </div>
        </Reveal>

        <div className="mt-12 grid gap-px overflow-hidden rounded-2xl border border-white/[0.08] bg-white/[0.08] lg:grid-cols-3">
          {LAYERS.map((layer, index) => {
            const Icon = layer.icon;
            return (
              <Reveal
                key={layer.title}
                delay={index * 0.04}
                className="flex flex-col bg-base-950 p-6 sm:p-8"
              >
                <div className="flex items-start justify-between">
                  <Icon size={17} className={layer.tone} aria-hidden="true" />
                  <span className="text-[8px] font-semibold uppercase tracking-[0.18em] text-ink-tertiary">
                    {layer.label}
                  </span>
                </div>
                <h3 className="mt-14 text-[20px] font-medium tracking-[-0.02em] text-ink-primary">
                  {layer.title}
                </h3>
                <p className="mt-3 max-w-sm text-[12px] leading-relaxed text-ink-secondary">
                  {layer.body}
                </p>
              </Reveal>
            );
          })}
        </div>

        <div className="mt-12 grid gap-px overflow-hidden rounded-xl border border-white/[0.08] bg-white/[0.08] sm:grid-cols-2 xl:grid-cols-4">
          {FACTS.map((item) => {
            const Icon = item.icon;
            return (
              <div key={item.label} className="flex items-center gap-3 bg-base-950 px-4 py-4">
                <Icon size={13} className="shrink-0 text-ink-tertiary" aria-hidden="true" />
                <div className="min-w-0">
                  <p className="text-[8px] font-semibold uppercase tracking-[0.18em] text-ink-tertiary">
                    {item.label}
                  </p>
                  <p
                    className={
                      'mt-1 truncate text-[11px] ' +
                      (item.accent ? 'text-delivered' : 'text-ink-secondary')
                    }
                  >
                    {item.value}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
