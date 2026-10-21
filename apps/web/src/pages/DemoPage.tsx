import { Check, Unplug, Waypoints } from 'lucide-react';

import { MeshDemo } from '@/components/demo/MeshDemo';
import { PageHero } from '@/components/public/PageHero';
import { SafetyNote } from '@/components/public/SafetyNote';
import { Reveal } from '@/components/primitives/Reveal';
import { usePageMeta } from '@/lib/seo';

const GUIDE = [
  {
    icon: Unplug,
    title: 'Cut a relay',
    body: 'Tap Jonas, Mei, Tomas or Sana. A dashed device is out of the mesh; the route recalculates at the current carrier.',
  },
  {
    icon: Waypoints,
    title: 'Watch the queue',
    body: 'If every path disappears, the message stays on the current phone. It is not marked delivered and it is not silently discarded.',
  },
  {
    icon: Check,
    title: 'Read the acknowledgement',
    body: 'The field post returns a signed acknowledgement. Only then does the console move the transmission to delivered.',
  },
];

export function DemoPage() {
  usePageMeta({
    title: 'Field lab — ResQNet mesh simulator',
    description:
      'Cut relays, send an SOS and inspect every forwarding hop in a deterministic browser simulation of ResQNet’s store-and-forward mesh.',
    path: '/demo',
  });

  return (
    <>
      <PageHero
        eyebrow="Field lab / 01"
        title={
          <>
            Send a message into a{' '}
            <span className="font-display font-normal italic">broken</span> network.
          </>
        }
        body="This is the protocol’s decision-making made visible. Pick an origin, cut a relay and watch the route either recover or hold the message in a local queue."
        aside={
          <div className="rounded-2xl border border-base-600 bg-base-900/70 p-5">
            <p className="eyebrow">Simulation boundary</p>
            <dl className="mt-4 space-y-3 text-xs">
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Topology</dt>
                <dd className="text-right text-ink-secondary">6 devices · 8 links</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Transport</dt>
                <dd className="text-right text-ink-secondary">Browser state only</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Telemetry</dt>
                <dd className="text-right text-ink-secondary">None</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Determinism</dt>
                <dd className="text-right text-ink-secondary">Fixed seed</dd>
              </div>
            </dl>
          </div>
        }
      />

      <section className="section-space border-b border-base-600">
        <div className="container-page">
          <Reveal>
            <MeshDemo />
          </Reveal>
          <p className="mt-4 text-[11px] leading-relaxed text-ink-tertiary">
            This simulator models protocol states, not radio performance. It
            does not connect to a gateway, request location or transmit data.
          </p>
        </div>
      </section>

      <section className="section-space">
        <div className="container-page">
          <div className="grid gap-10 lg:grid-cols-[0.8fr_1.2fr] lg:gap-20">
            <div>
              <span className="eyebrow">How to read the lab</span>
              <h2 className="display-balance mt-4 text-3xl font-semibold leading-[1.05] tracking-tightest text-ink-primary sm:text-4xl">
                The interesting part is what happens when the route fails.
              </h2>
              <SafetyNote className="mt-6">
                This page demonstrates software states. It is not evidence of
                physical range, battery behaviour or multi-hop delivery over real
                radios.
              </SafetyNote>
            </div>

            <div className="divide-y divide-base-600 border-y border-base-600">
              {GUIDE.map((item, index) => {
                const Icon = item.icon;
                return (
                  <Reveal
                    key={item.title}
                    delay={index * 0.04}
                    className="grid grid-cols-[36px_1fr] gap-4 py-6"
                  >
                    <Icon size={17} className="mt-1 text-network" aria-hidden="true" />
                    <div>
                      <h3 className="text-base font-semibold text-ink-primary">{item.title}</h3>
                      <p className="mt-1.5 text-[13px] leading-relaxed text-ink-secondary">
                        {item.body}
                      </p>
                    </div>
                  </Reveal>
                );
              })}
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
