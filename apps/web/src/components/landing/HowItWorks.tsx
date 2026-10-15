import { Bluetooth, Fingerprint, RadioTower, Route, ShieldCheck } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionHeading } from '@/components/primitives/SectionHeading';

/**
 * How offline communication actually works.
 *
 * The order is deliberate: radio, then routing, then forwarding for people who
 * are not in range. Most descriptions start with relaying because it sounds
 * impressive; starting with the radio makes the whole thing legible.
 */
const STEPS = [
  {
    icon: Bluetooth,
    index: '01',
    title: 'A radio link, not a network',
    body: 'Bluetooth and Wi-Fi Direct need no infrastructure at all. The radios are in the phones and the batteries are in the phones. That is the entire reason this works when cell towers do not.',
  },
  {
    icon: Route,
    index: '02',
    title: 'Messages are validated before they are trusted',
    body: 'Every frame is decoded, bounds-checked, expiry-checked and signature-verified against a key the device owner confirmed in person. Only then does it enter the relay queue.',
  },
  {
    index: '03',
    icon: RadioTower,
    title: 'Range is extended by relaying',
    body: 'A message addressed to someone out of range is carried by whoever is in between. Controlled flooding, a hop budget, and a deduplication cache keep that from becoming a storm.',
  },
  {
    index: '04',
    icon: Fingerprint,
    title: 'A relayed copy is still verified',
    body: 'Being relayed through does not make a message more trusted. Each device checks the origin signature for itself, against a key its own owner verified face to face.',
  },
] as const;

export function HowItWorks() {
  return (
    <section className="border-b border-base-600 py-20 sm:py-28">
      <div className="mx-auto w-full max-w-7xl px-5 sm:px-8">
        <SectionHeading
          eyebrow="How it works"
          title="Four things, in the order they matter"
          body="No cloud, no account, no third party between the sender and the recipient. The whole system is four ideas applied carefully."
        />

        <ol className="mt-14 grid gap-px overflow-hidden rounded-2xl border border-base-600 bg-base-600 sm:grid-cols-2">
          {STEPS.map((step, index) => {
            const Icon = step.icon;
            return (
              <Reveal
                as="li"
                key={step.title}
                delay={index * 0.06}
                className="group relative bg-base-850 p-7 transition-colors duration-300 hover:bg-base-800 sm:p-9"
              >
                <div className="flex items-start gap-5">
                  <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl border border-base-600 bg-base-800 text-network transition-colors group-hover:border-network/40">
                    <Icon size={19} aria-hidden="true" />
                  </span>
                  <div>
                    <p className="tabular text-xs font-semibold tracking-widest text-ink-tertiary">
                      {step.index}
                    </p>
                    <h3 className="mt-2 text-lg font-semibold leading-snug text-ink-primary">
                      {step.title}
                    </h3>
                    <p className="mt-2.5 text-sm leading-relaxed text-ink-secondary">
                      {step.body}
                    </p>
                  </div>
                </div>
              </Reveal>
            );
          })}
        </ol>

        <Reveal delay={0.1} className="mt-8">
          <div className="flex flex-col gap-4 rounded-2xl border border-delivered/20 bg-delivered-soft p-6 sm:flex-row sm:items-center sm:gap-6">
            <ShieldCheck size={20} className="shrink-0 text-delivered" aria-hidden="true" />
            <p className="text-sm leading-relaxed text-ink-secondary">
              <span className="font-medium text-ink-primary">No trust on first use.</span>{' '}
              Devices you have never met can still tell you a flood is coming, but
              they cannot impersonate the responder you have already verified.
              Verification is a person comparing eight characters on two screens,
              because that is the only authentication a disaster actually affords.
            </p>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
