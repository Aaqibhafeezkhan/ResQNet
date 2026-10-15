import { Activity, BatteryWarning, Repeat, Split } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionHeading } from '@/components/primitives/SectionHeading';

/**
 * What happens when the network fails, which is the only scenario worth
 * demonstrating.
 *
 * <p>Each row corresponds to a case the deterministic simulator actually runs
 * (`--topology two-cluster --partitioned`, `--churn`, `--drop`). Nothing here is
 * hypothetical.
 */
const FAILURES = [
  {
    icon: Split,
    title: 'The mesh splits in two',
    body: 'Messages between the halves stop and stay visibly queued. The sender is told they are waiting, never that they were sent.',
    outcome: 'Failure is reported, not hidden',
  },
  {
    icon: Repeat,
    title: 'The relay comes back',
    body: 'A new peer connection overrides the retry schedule and the queue drains on its own. Nobody touches the phone.',
    outcome: 'Recovery needs no user action',
  },
  {
    icon: Activity,
    title: 'Frames are lost',
    body: 'A dropped frame is retried with exponential backoff and full jitter, so a hundred devices recovering at once do not synchronise into a thundering herd.',
    outcome: 'Amplification stays bounded',
  },
  {
    icon: BatteryWarning,
    title: 'Batteries run down',
    body: 'Life-safety traffic is exempt from throttling and is never evicted to make room. Everything else yields to it.',
    outcome: 'SOS traffic is protected first',
  },
] as const;

export function Resilience() {
  return (
    <section className="border-b border-base-600 py-20 sm:py-28">
      <div className="mx-auto w-full max-w-7xl px-5 sm:px-8">
        <SectionHeading
          eyebrow="Resilience"
          title="What happens when the network fails"
          body="Failure is the expected case, not the exception. Each of these is a scenario the simulator runs from a fixed seed, so the behaviour is reproducible rather than asserted."
        />

        <div className="mt-14 grid gap-5 md:grid-cols-2">
          {FAILURES.map((failure, index) => {
            const Icon = failure.icon;
            return (
              <Reveal
                key={failure.title}
                delay={index * 0.06}
                className="surface flex flex-col gap-4 p-6 transition-colors duration-300 hover:border-base-500 sm:p-7"
              >
                <div className="flex items-center justify-between">
                  <Icon size={19} className="text-pending" aria-hidden="true" />
                  <span className="rounded-full bg-base-700 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-widest text-ink-tertiary">
                    {failure.outcome}
                  </span>
                </div>
                <h3 className="text-base font-semibold text-ink-primary">{failure.title}</h3>
                <p className="text-sm leading-relaxed text-ink-secondary">{failure.body}</p>
              </Reveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
