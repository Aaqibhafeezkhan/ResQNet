import { Cpu, Layers, Radio, Server, Smartphone } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionHeading } from '@/components/primitives/SectionHeading';

/**
 * Architecture section.
 *
 * The diagram is an explicit statement that the backend sits outside the
 * emergency path, because that is the most commonly misread part of the design.
 */
export function Architecture() {
  return (
    <section id="architecture" className="scroll-mt-20 border-b border-base-600 py-20 sm:py-28">
      <div className="mx-auto w-full max-w-7xl px-5 sm:px-8">
        <SectionHeading
          eyebrow="Architecture"
          title="One engine, three places it runs"
          body="The routing engine is pure Java with no dependencies. The same class runs on a handset, inside the deterministic simulator, and in its own test suite. There is no second implementation that could behave differently."
        />

        <Reveal className="mt-14 grid gap-6 lg:grid-cols-[1.15fr_1fr]">
          <div className="surface-raised overflow-hidden p-6 sm:p-8">
            <ArchitectureDiagram />
          </div>

          <div className="flex flex-col gap-4">
            <Layer
              icon={Cpu}
              title="mesh-core"
              body="Protocol envelopes, a bounded binary codec, signature verification, controlled flooding, the durable queue, and the offline classifier. Zero third-party dependencies, so it compiles against the JDK alone."
              tone="network"
            />
            <Layer
              icon={Smartphone}
              title="Android handset"
              body="Java, XML views and Material Components. Google Nearby Connections supplies the byte pipe and nothing else; the relay logic above it is ResQNet's own."
              tone="delivered"
            />
            <Layer
              icon={Layers}
              title="Simulator"
              body="Discrete-event, seeded, deterministic. Runs real routers with real keys and real signatures over a synthetic radio, so a result reported here is a statement about the handset."
              tone="pending"
            />
            <Layer
              icon={Server}
              title="Coordination gateway (optional)"
              body="Spring Boot. A device synchronises incident summaries only when it already has connectivity. Nothing in the mesh depends on it, which is why a backend outage can never become a communications outage."
              tone="neutral"
            />
          </div>
        </Reveal>
      </div>
    </section>
  );
}

function ArchitectureDiagram() {
  return (
    <figure className="relative">
      <figcaption className="eyebrow mb-6">Component map</figcaption>
      <div className="space-y-3">
        <DiagramBand
          label="Optional · requires connectivity"
          tone="neutral"
          items={[{ icon: Server, text: 'Spring Boot gateway' }, { icon: Layers, text: 'Responder dashboard' }]}
          dashed
        />
        <Arrow down label="device synchronises when it can" dashed />
        <DiagramBand
          label="Required · works with the radio alone"
          tone="emergency"
          items={[
            { icon: Smartphone, text: 'Android handset' },
            { icon: Cpu, text: 'mesh-core routing engine' },
            { icon: Radio, text: 'Nearby Connections' },
          ]}
        />
        <Arrow down label="peer to peer, no infrastructure" />
        <div className="grid grid-cols-3 gap-2">
          {['Handset B', 'Handset C', 'Relay D'].map((label) => (
            <div
              key={label}
              className="flex items-center justify-center rounded-xl border border-base-600 bg-base-800 px-3 py-4 text-center text-xs font-medium text-ink-secondary"
            >
              {label}
            </div>
          ))}
        </div>
      </div>
    </figure>
  );
}

function DiagramBand({
  label,
  tone,
  items,
  dashed,
}: {
  label: string;
  tone: 'emergency' | 'neutral';
  items: { icon: typeof Server; text: string }[];
  dashed?: boolean;
}) {
  // The tone is carried by the band's border so the optional stack reads as
  // visually subordinate to the required one.
  const border =
    dashed
      ? 'border-dashed border-base-500'
      : tone === 'emergency'
        ? 'border-emergency/40'
        : 'border-base-500';
  return (
    <div className={`rounded-2xl border ${border} bg-base-800/60 p-4`}>
      <p className="eyebrow mb-3">{label}</p>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
        {items.map((item) => {
          const Icon = item.icon;
          return (
            <div
              key={item.text}
              className="flex items-center gap-2 rounded-lg bg-base-850 px-3 py-2.5 text-xs font-medium text-ink-secondary"
            >
              <Icon size={14} className="shrink-0 text-network" aria-hidden="true" />
              <span className="truncate">{item.text}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Arrow({ label }: { down: boolean; label: string; dashed?: boolean }) {
  return (
    <div className="flex flex-col items-center gap-1 py-1">
      <span className="h-6 w-px bg-base-500" aria-hidden="true" />
      <span
        className="text-[10px] uppercase tracking-widest text-ink-tertiary"
      >
        {label}
      </span>
    </div>
  );
}

function Layer({
  icon: Icon,
  title,
  body,
  tone,
}: {
  icon: typeof Server;
  title: string;
  body: string;
  tone: 'network' | 'delivered' | 'pending' | 'neutral';
}) {
  const colour = {
    network: 'text-network',
    delivered: 'text-delivered',
    pending: 'text-pending',
    neutral: 'text-ink-tertiary',
  }[tone];

  return (
    <div className="surface flex gap-4 p-5">
      <Icon size={18} className={`mt-0.5 shrink-0 ${colour}`} aria-hidden="true" />
      <div>
        <h3 className="text-sm font-semibold text-ink-primary">{title}</h3>
        <p className="mt-1.5 text-[13px] leading-relaxed text-ink-secondary">{body}</p>
      </div>
    </div>
  );
}
