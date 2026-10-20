import { ArrowRight } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';

const STEPS = [
  {
    number: '01',
    tag: 'SIGN',
    title: 'Compose and sign',
    body: 'The sender writes the SOS, signs it with a device-held key, and writes it to durable storage before the first radio attempt.',
  },
  {
    number: '02',
    tag: 'STORE',
    title: 'Store before sending',
    body: 'No peer nearby? The message waits. A crashed process or a powered-off phone does not erase the request.',
  },
  {
    number: '03',
    tag: 'RELAY',
    title: 'Relay within limits',
    body: 'Phones in range verify the origin signature, decrement a hop budget and pass it on. Duplicate suppression keeps the flood bounded.',
  },
  {
    number: '04',
    tag: 'ACK',
    title: 'Deliver, then acknowledge',
    body: 'Only a signed acknowledgement from the intended recipient moves the state to delivered. A handoff is never called delivery.',
  },
];

export function MechanismSection() {
  return (
    <section id="how" className="section-space scroll-mt-20 border-b border-base-600 bg-base-900">
      <div className="container-page">
        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
          <SectionIntro
            index="02"
            eyebrow="The mechanism"
            title="A message finds a way, one verified handoff at a time."
            body="No cloud, no account, no third party in the emergency path. Four ideas, applied carefully."
          />
          <a
            href="#architecture"
            className="inline-flex shrink-0 items-center gap-2 text-sm font-medium text-network transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            See the architecture
            <ArrowRight size={14} aria-hidden="true" />
          </a>
        </div>

        <ol className="mt-14 grid gap-px overflow-hidden rounded-2xl border border-base-600 bg-base-600 sm:grid-cols-2 xl:grid-cols-4">
          {STEPS.map((step, index) => (
            <Reveal
              as="li"
              key={step.number}
              delay={index * 0.05}
              className="group relative flex flex-col bg-base-850 p-6 transition-colors duration-300 hover:bg-base-800 sm:p-7"
            >
              <div className="flex items-center justify-between">
                <span className="tabular text-[10px] font-semibold tracking-widest text-ink-tertiary">
                  {step.number}
                </span>
                <span className="rounded-full border border-base-600 px-2 py-1 text-[9px] font-semibold uppercase tracking-widest text-network">
                  {step.tag}
                </span>
              </div>
              <h3 className="mt-12 text-lg font-semibold leading-snug tracking-tight text-ink-primary">
                {step.title}
              </h3>
              <p className="mt-3 text-[13px] leading-relaxed text-ink-secondary">
                {step.body}
              </p>
              <span
                className="mt-6 h-px w-8 bg-base-500 transition-all duration-300 group-hover:w-14 group-hover:bg-network"
                aria-hidden="true"
              />
            </Reveal>
          ))}
        </ol>
      </div>
    </section>
  );
}
