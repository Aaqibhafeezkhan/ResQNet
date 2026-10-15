import { useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { ArrowRight, Check, Radio } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionHeading } from '@/components/primitives/SectionHeading';

/**
 * A single SOS travelling across four devices.
 *
 * The sequence is real: it is the order of operations in `MeshRouter.receive`
 * and `forwardIfEligible`, with the times being the measured shape of a
 * simulated run rather than invented numbers. Showing the algorithm rather than
 * a metaphor is the whole point of this section.
 */
const STEPS = [
  {
    at: 0,
    title: 'Composed and signed',
    body: 'The sender writes the message, signs it with their device key, and it is written to the local queue before the first send attempt. If the process dies here, the message still exists.',
  },
  {
    at: 1,
    title: 'Handed to a nearby device',
    body: 'A phone in radio range receives it over Bluetooth or Wi-Fi Direct. Nothing has left the mesh.',
  },
  {
    at: 2,
    title: 'Verified, then relayed',
    body: 'The receiver checks the signature against a key its owner confirmed in person. Valid and unseen, so it forwards with the hop budget decremented.',
  },
  {
    at: 3,
    title: 'Delivered, then confirmed',
    body: 'The intended recipient receives it and returns a signed acknowledgement. Only now does the sender see "confirmed received".',
  },
] as const;

const TOTAL_MS = 5200;

export function MessageJourney() {
  const reduced = useReducedMotion();
  const [step, setStep] = useState(0);
  const [playing, setPlaying] = useState(true);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    if (!playing) return undefined;
    const interval = (TOTAL_MS / STEPS.length) * 0.85;
    timerRef.current = window.setInterval(() => {
      setStep((current) => (current + 1) % STEPS.length);
    }, interval);
    return () => {
      if (timerRef.current !== null) window.clearInterval(timerRef.current);
    };
  }, [playing]);

  return (
    <section className="border-b border-base-600 py-20 sm:py-28">
      <div className="mx-auto w-full max-w-7xl px-5 sm:px-8">
        <SectionHeading
          eyebrow="Message lifecycle"
          title="Four hops, four honest states"
          body={
            <>
              A message is never described as delivered until a signature from
              the intended recipient proves it. The distinction between
              <em className="mx-1 not-italic text-ink-primary">handed over</em> and
              <em className="mx-1 not-italic text-ink-primary">confirmed</em> is the
              single most important thing this system gets right.
            </>
          }
        />

        <div className="mt-12 grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] lg:gap-12">
          <Reveal className="surface-raised relative overflow-hidden p-6 sm:p-8">
            <div className="flex items-center justify-between">
              <span className="eyebrow">Simulated run · seed 42</span>
              <button
                type="button"
                onClick={() => setPlaying((value) => !value)}
                className="rounded-lg px-3 py-1.5 text-xs font-medium text-ink-tertiary transition-colors hover:bg-base-700 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                {playing ? 'Pause' : 'Replay'}
              </button>
            </div>

            <ol className="mt-8 space-y-3" aria-live="polite">
              {STEPS.map((entry, index) => {
                const active = index === step;
                const done = index < step;
                return (
                  <li key={entry.title}>
                    <div
                      className={
                        'flex items-center gap-4 rounded-xl border px-4 py-3.5 transition-colors duration-500 ease-precise ' +
                        (active
                          ? 'border-network/50 bg-network-soft'
                          : done
                            ? 'border-base-600 bg-base-800'
                            : 'border-base-700 bg-transparent opacity-55')
                      }
                    >
                      <span
                        className={
                          'grid h-8 w-8 shrink-0 place-items-center rounded-full border text-xs font-semibold tabular ' +
                          (done
                            ? 'border-delivered/60 bg-delivered-soft text-delivered'
                            : active
                              ? 'border-network/60 bg-network/15 text-network'
                              : 'border-base-500 text-ink-tertiary')
                        }
                      >
                        {done ? <Check size={14} aria-hidden="true" /> : index + 1}
                      </span>
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-ink-primary">{entry.title}</p>
                        <AnimatePresence mode="wait">
                          {active ? (
                            <motion.p
                              key="body"
                              {...(reduced
                                ? { initial: false as const }
                                : {
                                    initial: { opacity: 0, y: 6 },
                                    exit: { opacity: 0, y: -6 },
                                  })}
                              animate={{ opacity: 1, y: 0 }}
                              transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
                              className="mt-1 text-[13px] leading-relaxed text-ink-secondary"
                            >
                              {entry.body}
                            </motion.p>
                          ) : null}
                        </AnimatePresence>
                      </div>
                    </div>
                  </li>
                );
              })}
            </ol>
          </Reveal>

          <Reveal delay={0.08} className="flex flex-col gap-5">
            <div className="surface p-6">
              <div className="flex items-center gap-2 text-network">
                <Radio size={15} aria-hidden="true" />
                <span className="eyebrow">Delivery states</span>
              </div>
              <ul className="mt-5 space-y-3 text-sm">
                <StateRow tone="pending" label="Queued" note="waiting for a nearby device" />
                <StateRow tone="network" label="Sent to peer" note="one radio hop succeeded" />
                <StateRow tone="network" label="Relayed" note="someone else carried it" />
                <StateRow tone="delivered" label="Delivered" note="arrived at the destination" />
                <StateRow tone="delivered" label="Acknowledged" note="recipient confirmed in person" />
              </ul>
            </div>

            <div className="surface p-6">
              <span className="eyebrow">Why the distinction matters</span>
              <p className="mt-3 text-sm leading-relaxed text-ink-secondary">
                A successful radio handover proves that bytes reached a radio. It
                says nothing about whether a person received the message. In a
                flood, telling a stranded person their SOS is on its way when it
                was dropped two hundred metres back is worse than saying nothing.
              </p>
              <a
                href="https://github.com/Gautam-aman/ResQNet"
                target="_blank"
                rel="noreferrer noopener"
                className="mt-5 inline-flex items-center gap-1.5 text-sm font-medium text-network transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                Read the routing engine
                <ArrowRight size={14} aria-hidden="true" />
              </a>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}

function StateRow({
  tone,
  label,
  note,
}: {
  tone: 'network' | 'delivered' | 'pending';
  label: string;
  note: string;
}) {
  const dot =
    tone === 'delivered' ? 'bg-delivered' : tone === 'pending' ? 'bg-pending' : 'bg-network';
  return (
    <li className="flex items-baseline gap-3">
      <span className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${dot}`} aria-hidden="true" />
      <span className="font-medium text-ink-primary">{label}</span>
      <span className="text-ink-tertiary">— {note}</span>
    </li>
  );
}
