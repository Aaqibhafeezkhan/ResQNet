import { Activity, Split, Unplug, Waypoints } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';

const FAILURES = [
  {
    icon: Unplug,
    title: 'A relay disappears',
    body: 'The route is recalculated at the current carrier. If no path remains, the message is held locally and the sender can see that it is waiting.',
    tag: 'Demonstrated above',
  },
  {
    icon: Split,
    title: 'The mesh partitions',
    body: 'Both halves keep working locally. Messages that need to cross the split wait instead of vanishing or pretending to be sent.',
    tag: 'Protocol behaviour',
  },
  {
    icon: Waypoints,
    title: 'A link returns',
    body: 'A fresh route releases the queue without the sender pressing anything. Recovery is an outcome of the protocol, not a manual retry.',
    tag: 'Demonstrated above',
  },
  {
    icon: Activity,
    title: 'The destination answers',
    body: 'A signed acknowledgement is the only path to delivered. The interface keeps queued, relayed and acknowledged as separate states.',
    tag: 'Protocol behaviour',
  },
];

export function FailureSection() {
  return (
    <section className="relative overflow-hidden border-b border-white/[0.08] bg-base-900 py-24 sm:py-32 lg:py-40">
      <span
        className="pointer-events-none absolute -left-6 bottom-0 select-none text-[16rem] font-semibold leading-[0.7] tracking-[-0.08em] text-white/[0.018] sm:text-[24rem]"
        aria-hidden="true"
      >
        05
      </span>
      <div className="container-page relative">
        <div className="grid gap-16 lg:grid-cols-[minmax(0,0.82fr)_minmax(0,1.18fr)] lg:gap-24">
          <div className="lg:sticky lg:top-32 lg:self-start">
            <SectionIntro
              index="04"
              eyebrow="Resilience"
              title="Failure is a state, not a surprise."
              body="A network that breaks silently is worse than no network at all. ResQNet makes waiting, rerouting and delivery visible, separately."
            />
            <Reveal delay={0.1} className="mt-10 border-l border-network/40 pl-5">
              <p className="eyebrow">The rule</p>
              <p className="mt-4 max-w-md font-display text-[2rem] leading-[1.06] text-ink-primary sm:text-[2.5rem]">
                Never turn an unknown into a success.
              </p>
            </Reveal>
          </div>

          <div className="border-t border-white/[0.08]">
            {FAILURES.map((failure, index) => {
              const Icon = failure.icon;
              return (
                <Reveal
                  key={failure.title}
                  delay={index * 0.03}
                  className="group grid grid-cols-[40px_1fr] gap-4 border-b border-white/[0.08] py-7 sm:grid-cols-[56px_1fr_auto] sm:items-start sm:gap-6 sm:py-9"
                >
                  <span className="grid h-10 w-10 place-items-center rounded-full border border-white/10 bg-white/[0.02] text-ink-secondary transition-colors group-hover:border-network/30 group-hover:text-network">
                    <Icon size={16} aria-hidden="true" />
                  </span>
                  <div>
                    <h3 className="text-[17px] font-medium tracking-[-0.01em] text-ink-primary">
                      {failure.title}
                    </h3>
                    <p className="mt-2 max-w-xl text-[13px] leading-relaxed text-ink-secondary">
                      {failure.body}
                    </p>
                  </div>
                  <span className="col-start-2 mt-2 w-fit rounded-full border border-white/10 px-2.5 py-1 text-[8px] font-semibold uppercase tracking-[0.16em] text-ink-tertiary sm:col-start-3 sm:mt-1">
                    {failure.tag}
                  </span>
                </Reveal>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
