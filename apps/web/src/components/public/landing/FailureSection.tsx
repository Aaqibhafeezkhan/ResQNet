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
    <section className="section-space border-b border-base-600 bg-base-900">
      <div className="container-page">
        <div className="grid gap-12 lg:grid-cols-[0.8fr_1.2fr] lg:gap-20">
          <div>
            <SectionIntro
              index="04"
              eyebrow="Resilience"
              title="Failure is a state, not a surprise."
              body="A network that breaks silently is worse than no network at all. ResQNet makes waiting, rerouting and delivery visible, separately."
            />
            <Reveal delay={0.1} className="mt-8 rounded-xl border border-base-600 bg-base-850 p-5">
              <p className="eyebrow">The rule</p>
              <p className="mt-3 font-display text-2xl leading-snug text-ink-primary">
                Never turn an unknown into a success.
              </p>
            </Reveal>
          </div>

          <div className="divide-y divide-base-600 border-y border-base-600">
            {FAILURES.map((failure, index) => {
              const Icon = failure.icon;
              return (
                <Reveal
                  key={failure.title}
                  delay={index * 0.04}
                  className="grid grid-cols-[42px_1fr] gap-4 py-6 sm:grid-cols-[54px_1fr_auto] sm:items-start sm:gap-6"
                >
                  <span className="grid h-10 w-10 place-items-center rounded-full border border-base-600 bg-base-850 text-ink-secondary">
                    <Icon size={16} aria-hidden="true" />
                  </span>
                  <div>
                    <h3 className="text-base font-semibold text-ink-primary">{failure.title}</h3>
                    <p className="mt-1.5 max-w-xl text-[13px] leading-relaxed text-ink-secondary">
                      {failure.body}
                    </p>
                  </div>
                  <span className="col-start-2 mt-1 w-fit rounded-full border border-base-600 px-2.5 py-1 text-[9px] font-semibold uppercase tracking-widest text-ink-tertiary sm:col-start-3 sm:mt-0">
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
