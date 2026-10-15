import { FileKey, Fingerprint, ScanFace, ShieldAlert, ShieldCheck } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionHeading } from '@/components/primitives/SectionHeading';

/**
 * Security and privacy.
 *
 * <p>The honesty matters more than the reassurance here. A page that lists only
 * what the system does is easy to write and useless to evaluate; a page that
 * names its residual risks is the one a responder can actually decide on.
 */
const CONTROLS = [
  {
    icon: Fingerprint,
    title: 'Key-derived identity',
    body: 'Device identity is the SHA-256 of a public key, not a radio handle. A Nearby endpoint id rotates on reboot; a key-derived identity survives it.',
  },
  {
    icon: ScanFace,
    title: 'Verification you can perform',
    body: 'Eight characters compared on two screens, in person. There is no trust-on-first-use shortcut, and no path that skips looking at the other device.',
  },
  {
    icon: FileKey,
    title: 'Signed, not just delivered',
    body: 'Every envelope carries a signature binding the origin id, the destination, the timestamps and the payload. Editing the origin field invalidates it.',
  },
  {
    icon: ShieldAlert,
    title: 'Bounded on purpose',
    body: 'Envelope size, hop budget, lifetime, queue capacity, fan-out and per-origin forwarding rate are all capped. An unbounded queue is a memory exhaustion target.',
  },
] as const;

const LIMITS = [
  'Keys can be extracted from a rooted or unlocked device',
  'Payloads are signed but not encrypted, and no claim is made otherwise',
  'A radio-level attacker could jam discovery entirely',
  'Devices that have never met cannot authenticate each other',
];

export function Security() {
  return (
    <section className="border-b border-base-600 py-20 sm:py-28">
      <div className="mx-auto w-full max-w-7xl px-5 sm:px-8">
        <SectionHeading
          eyebrow="Security and privacy"
          title="What is actually defended"
          body="Every control below exists because a specific attack against a mesh was considered and written down in the threat model."
        />

        <div className="mt-14 grid gap-5 sm:grid-cols-2">
          {CONTROLS.map((control, index) => {
            const Icon = control.icon;
            return (
              <Reveal key={control.title} delay={index * 0.05} className="surface p-6">
                <Icon size={19} className="text-network" aria-hidden="true" />
                <h3 className="mt-4 text-base font-semibold text-ink-primary">{control.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-ink-secondary">{control.body}</p>
              </Reveal>
            );
          })}
        </div>

        <Reveal delay={0.1} className="mt-8">
          <div className="rounded-2xl border border-pending/25 bg-pending-soft p-6 sm:p-7">
            <div className="flex items-center gap-2.5">
              <ShieldAlert size={18} className="text-pending" aria-hidden="true" />
              <h3 className="text-sm font-semibold text-ink-primary">
                Residual risks, stated plainly
              </h3>
            </div>
            <ul className="mt-4 grid gap-2.5 sm:grid-cols-2">
              {LIMITS.map((limit) => (
                <li key={limit} className="flex gap-2.5 text-[13px] leading-relaxed text-ink-secondary">
                  <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-pending" aria-hidden="true" />
                  {limit}
                </li>
              ))}
            </ul>
            <p className="mt-5 flex items-start gap-2.5 text-[13px] leading-relaxed text-ink-tertiary">
              <ShieldCheck size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
              <span>
                ResQNet is experimental software. It is not certified to any safety
                standard and it is not a replacement for emergency services.
              </span>
            </p>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
