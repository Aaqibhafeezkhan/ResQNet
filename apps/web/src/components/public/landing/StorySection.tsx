import { ArrowUpRight, ShieldAlert } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';
import { GITHUB_THREAT_MODEL } from '@/lib/links';

const ACTS = [
  {
    number: '01',
    title: 'The tower goes quiet',
    body: 'A flood takes the substation. A fire takes the fibre. A crowd fills the cell. The cloud is still there, but the path to it is not.',
  },
  {
    number: '02',
    title: 'The phones remain',
    body: 'The radios in nearby handsets still work. Bluetooth and Wi-Fi Direct need no infrastructure, no SIM and no account. They only need proximity.',
  },
  {
    number: '03',
    title: 'A route assembles',
    body: 'A signed message is stored locally, carried by one phone, then another. It moves toward a destination even when no single device can reach it alone.',
  },
];

export function StorySection() {
  return (
    <section
      id="story"
      className="relative scroll-mt-20 overflow-hidden border-b border-white/[0.08] py-24 sm:py-32 lg:py-40"
    >
      <span
        className="pointer-events-none absolute -right-8 top-10 select-none text-[18rem] font-semibold leading-none tracking-[-0.08em] text-white/[0.018] sm:text-[26rem]"
        aria-hidden="true"
      >
        01
      </span>

      <div className="container-page relative">
        <div className="grid gap-16 lg:grid-cols-[minmax(0,0.78fr)_minmax(0,1.22fr)] lg:gap-24">
          <div className="lg:sticky lg:top-32 lg:self-start">
            <SectionIntro
              index="01"
              eyebrow="Why this exists"
              title="The network is not a tower. It is the people around you."
              body="In a blackout or a dead zone, the first infrastructure we reach for is often the first to fail. ResQNet starts from a different assumption: the phones already in the crowd can carry a message farther than any one of them can alone."
            />
            <Reveal delay={0.1} className="mt-10 flex items-start gap-3 border-t border-white/[0.08] pt-5">
              <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-network" aria-hidden="true" />
              <p className="max-w-sm text-[11px] leading-relaxed text-ink-tertiary">
                The constraint is not a lack of clever routing. It is physics:
                proximity, power, time and an honest account of all three.
              </p>
            </Reveal>
          </div>

          <ol>
            {ACTS.map((act, index) => (
              <Reveal
                as="li"
                key={act.number}
                delay={index * 0.05}
                className="group grid grid-cols-[42px_1fr] gap-5 border-t border-white/[0.08] py-8 first:border-t-0 first:pt-0 sm:grid-cols-[84px_1fr] sm:gap-8 sm:py-11"
              >
                <span className="tabular pt-1.5 text-[10px] font-semibold tracking-[0.18em] text-network">
                  {act.number}
                </span>
                <div>
                  <h3 className="max-w-lg text-[1.55rem] font-medium leading-[1.02] tracking-[-0.035em] text-ink-primary sm:text-[2.15rem]">
                    {act.title}
                  </h3>
                  <p className="mt-4 max-w-xl text-[14px] leading-relaxed text-ink-secondary sm:text-[15px]">
                    {act.body}
                  </p>
                  <span
                    className="mt-6 block h-px w-10 bg-white/15 transition-all duration-500 group-hover:w-24 group-hover:bg-network/60"
                    aria-hidden="true"
                  />
                </div>
              </Reveal>
            ))}
          </ol>
        </div>

        <div className="mt-20 grid gap-12 border-t border-white/[0.08] pt-12 lg:mt-28 lg:grid-cols-[1.3fr_0.7fr] lg:gap-20 lg:pt-16">
          <Reveal className="relative">
            <p className="eyebrow">The design line</p>
            <blockquote className="display-balance mt-6 max-w-4xl font-display text-[2.4rem] leading-[1.02] tracking-[-0.02em] text-ink-primary sm:text-[3.6rem] lg:text-[4.2rem]">
              “A radio handoff is not a delivery. ResQNet keeps those states
              separate.”
            </blockquote>
            <p className="mt-7 max-w-2xl text-[13px] leading-relaxed text-ink-secondary sm:text-[14px]">
              The sender sees a message move from queued to relayed to delivered
              only when the intended recipient returns a signed acknowledgement.
              Optimism is not a status.
            </p>
          </Reveal>

          <Reveal delay={0.06} className="rounded-2xl border border-pending/20 bg-pending-soft p-6 sm:p-7">
            <div className="flex items-center gap-2.5">
              <ShieldAlert size={16} className="text-pending" aria-hidden="true" />
              <h3 className="text-[13px] font-semibold uppercase tracking-[0.08em] text-ink-primary">
                What this is not
              </h3>
            </div>
            <ul className="mt-5 space-y-3 text-[12px] leading-relaxed text-ink-secondary">
              <li className="flex gap-2.5">
                <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-pending/70" aria-hidden="true" />
                Not a replacement for emergency services.
              </li>
              <li className="flex gap-2.5">
                <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-pending/70" aria-hidden="true" />
                Public SOS broadcasts are not confidential. Direct messages are
                end-to-end encrypted.
              </li>
              <li className="flex gap-2.5">
                <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-pending/70" aria-hidden="true" />
                Not proven for multi-hop delivery on physical hardware.
              </li>
              <li className="flex gap-2.5">
                <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-pending/70" aria-hidden="true" />
                Not certified to any safety standard.
              </li>
            </ul>
            <a
              href={GITHUB_THREAT_MODEL}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-6 inline-flex items-center gap-1.5 text-[11px] font-medium uppercase tracking-[0.1em] text-pending transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              Read the threat model
              <ArrowUpRight size={12} aria-hidden="true" />
            </a>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
