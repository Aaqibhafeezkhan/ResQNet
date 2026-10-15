import { Brain, ClipboardList, MapPin, RadioTower, Users, Zap } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionHeading } from '@/components/primitives/SectionHeading';

const CAPABILITIES = [
  {
    icon: RadioTower,
    title: 'Multi-hop emergency messaging',
    body: 'An SOS addressed to someone out of range is carried by whoever is in between, up to a bounded hop budget.',
    status: 'Implemented',
  },
  {
    icon: Zap,
    title: 'Durable store and forward',
    body: 'Messages survive the peer being gone, the process being killed and the phone being powered off. They go out the moment a link returns.',
    status: 'Implemented',
  },
  {
    icon: Users,
    title: 'Peer verification',
    body: 'Face-to-face code comparison before a device may send anything a user has to trust.',
    status: 'Implemented',
  },
  {
    icon: Brain,
    title: 'Offline advisory triage',
    body: 'A deterministic classifier that runs on the handset with no model download, reports its own uncertainty, and never dispatches anyone.',
    status: 'Implemented',
  },
  {
    icon: ClipboardList,
    title: 'Responder command centre',
    body: 'Incident queue, delivery state, delivery-latency distribution and a live topology view, with simulation data labelled as simulation.',
    status: 'Implemented',
  },
  {
    icon: MapPin,
    title: 'Location sharing',
    body: 'Off by default, per message, coarse to five decimal places, and never transmitted unless the sender chooses to attach it.',
    status: 'Implemented, opt-in',
  },
];

export function Capabilities() {
  return (
    <section className="border-b border-base-600 py-20 sm:py-28">
      <div className="mx-auto w-full max-w-7xl px-5 sm:px-8">
        <SectionHeading
          eyebrow="Capabilities"
          title="What the prototype does today"
          body="Everything listed here is implemented and covered by tests or by a reproducible simulator run. Planned work is listed separately in the repository README, not here."
        />

        <div className="mt-14 grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {CAPABILITIES.map((capability, index) => {
            const Icon = capability.icon;
            return (
              <Reveal
                key={capability.title}
                delay={index * 0.04}
                className="surface group flex flex-col gap-3 p-6 transition-colors duration-300 hover:border-base-500"
              >
                <div className="flex items-start justify-between gap-3">
                  <Icon size={19} className="text-network" aria-hidden="true" />
                  <span className="rounded-full bg-delivered-soft px-2.5 py-1 text-[10px] font-semibold uppercase tracking-widest text-delivered">
                    {capability.status}
                  </span>
                </div>
                <h3 className="text-[15px] font-semibold leading-snug text-ink-primary">
                  {capability.title}
                </h3>
                <p className="text-[13px] leading-relaxed text-ink-secondary">
                  {capability.body}
                </p>
              </Reveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
