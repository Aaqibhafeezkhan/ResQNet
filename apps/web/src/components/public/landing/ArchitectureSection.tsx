import { ArrowRight, Cloud, Cpu, Globe2, Radio, Server, Smartphone } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';

const LAYERS = [
  {
    icon: Smartphone,
    label: 'Required',
    title: 'The local path',
    body: 'Android handsets, pure-Java mesh-core and nearby radios. No internet, gateway or account is needed for an SOS to be signed, stored and relayed.',
    tone: 'border-delivered/30',
  },
  {
    icon: Server,
    label: 'Optional',
    title: 'The coordination layer',
    body: 'A Spring Boot gateway can sync incident summaries when a device already has connectivity. If it is down, the mesh continues. It is a witness, not a dependency.',
    tone: 'border-base-600',
  },
  {
    icon: Globe2,
    label: 'This site',
    title: 'The public surface',
    body: 'A static Vite site with no operational data. The responder command centre is a separate authenticated build and is not shipped in this public bundle.',
    tone: 'border-base-600',
  },
];

export function ArchitectureSection() {
  return (
    <section id="architecture" className="section-space scroll-mt-20 border-b border-base-600">
      <div className="container-page">
        <SectionIntro
          index="07"
          eyebrow="Architecture"
          title="One engine. Three places it runs. No cloud in the SOS path."
          body="The routing engine is pure Java with no third-party dependencies. The same code runs on a handset and in the deterministic simulator, so a simulator result is a statement about the handset’s engine — not a second implementation."
        />

        <Reveal delay={0.08} className="mt-12">
          <div className="flex flex-wrap items-center gap-2 rounded-xl border border-base-600 bg-base-900 px-4 py-3 text-[10px] font-semibold uppercase tracking-widest text-ink-tertiary">
            <span className="text-ink-secondary">Android</span>
            <ArrowRight size={12} aria-hidden="true" />
            <span>mesh-core</span>
            <ArrowRight size={12} aria-hidden="true" />
            <span>Nearby radio</span>
            <ArrowRight size={12} aria-hidden="true" />
            <span>peer relay</span>
            <ArrowRight size={12} aria-hidden="true" />
            <span className="text-delivered">field post</span>
          </div>
        </Reveal>

        <div className="mt-5 grid gap-5 lg:grid-cols-3">
          {LAYERS.map((layer, index) => {
            const Icon = layer.icon;
            return (
              <Reveal
                key={layer.title}
                delay={index * 0.05}
                className={`rounded-2xl border bg-base-850 p-6 ${layer.tone}`}
              >
                <div className="flex items-center justify-between">
                  <Icon size={18} className="text-network" aria-hidden="true" />
                  <span className="text-[9px] font-semibold uppercase tracking-widest text-ink-tertiary">
                    {layer.label}
                  </span>
                </div>
                <h3 className="mt-8 text-lg font-semibold tracking-tight text-ink-primary">
                  {layer.title}
                </h3>
                <p className="mt-2.5 text-[13px] leading-relaxed text-ink-secondary">
                  {layer.body}
                </p>
              </Reveal>
            );
          })}
        </div>

        <div className="mt-5 grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
          {[
            { icon: Cpu, label: 'mesh-core', value: 'Pure Java · zero dependencies' },
            { icon: Radio, label: 'Transport', value: 'Nearby Connections' },
            { icon: Server, label: 'Gateway', value: 'Spring Boot · optional' },
            { icon: Cloud, label: 'Cloud in SOS path', value: 'None' },
          ].map((item) => {
            const Icon = item.icon;
            return (
              <Reveal
                key={item.label}
                className="flex items-center gap-3 rounded-xl border border-base-700 px-4 py-3.5"
              >
                <Icon size={14} className="shrink-0 text-ink-tertiary" aria-hidden="true" />
                <div className="min-w-0">
                  <p className="text-[9px] font-semibold uppercase tracking-widest text-ink-tertiary">
                    {item.label}
                  </p>
                  <p className="mt-0.5 truncate text-[12px] text-ink-secondary">{item.value}</p>
                </div>
              </Reveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
