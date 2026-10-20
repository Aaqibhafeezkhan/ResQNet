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
    <section id="story" className="section-space scroll-mt-20 border-b border-base-600">
      <div className="container-page">
        <div className="grid gap-14 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] lg:gap-20">
          <SectionIntro
            index="01"
            eyebrow="Why this exists"
            title="The network is not a tower. It is the people around you."
            body="In a blackout or a dead zone, the first infrastructure we reach for is often the first to fail. ResQNet starts from a different assumption: the phones already in the crowd can carry a message farther than any one of them can alone."
          />

          <ol className="lg:pt-2">
            {ACTS.map((act, index) => (
              <Reveal
                as="li"
                key={act.number}
                delay={index * 0.06}
                className="grid grid-cols-[42px_1fr] gap-5 border-t border-base-600 py-6 first:border-t-0 first:pt-0 sm:grid-cols-[64px_1fr] sm:gap-7 sm:py-8"
              >
                <span className="tabular pt-1 text-[11px] font-semibold tracking-widest text-network">
                  {act.number}
                </span>
                <div>
                  <h3 className="text-xl font-semibold tracking-tight text-ink-primary sm:text-2xl">
                    {act.title}
                  </h3>
                  <p className="mt-2.5 max-w-xl text-sm leading-relaxed text-ink-secondary sm:text-[15px]">
                    {act.body}
                  </p>
                </div>
              </Reveal>
            ))}
          </ol>
        </div>

        <div className="mt-16 grid gap-5 lg:grid-cols-[1.25fr_0.75fr]">
          <Reveal className="relative overflow-hidden rounded-2xl border border-base-600 bg-base-800 p-7 sm:p-10">
            <div
              className="pointer-events-none absolute inset-0 bg-[radial-gradient(80%_100%_at_0%_0%,rgba(231,75,75,0.08),transparent_58%)]"
              aria-hidden="true"
            />
            <p className="eyebrow">The design line</p>
            <blockquote className="display-balance mt-4 max-w-3xl font-display text-[2rem] leading-[1.08] text-ink-primary sm:text-[2.7rem]">
              “A radio handoff is not a delivery. ResQNet keeps those states
              separate.”
            </blockquote>
            <p className="mt-5 max-w-2xl text-sm leading-relaxed text-ink-secondary">
              The sender sees a message move from queued to relayed to delivered
              only when the intended recipient returns a signed acknowledgement.
              Optimism is not a status.
            </p>
          </Reveal>

          <Reveal delay={0.06} className="rounded-2xl border border-pending/25 bg-pending-soft p-6 sm:p-7">
            <div className="flex items-center gap-2.5">
              <ShieldAlert size={17} className="text-pending" aria-hidden="true" />
              <h3 className="text-sm font-semibold text-ink-primary">What this is not</h3>
            </div>
            <ul className="mt-4 space-y-3 text-[13px] leading-relaxed text-ink-secondary">
              <li>Not a replacement for emergency services.</li>
              <li>Not encrypted. Payloads are signed, not confidential.</li>
              <li>Not proven for multi-hop delivery on physical hardware.</li>
              <li>Not certified to any safety standard.</li>
            </ul>
            <a
              href={GITHUB_THREAT_MODEL}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-5 inline-flex items-center gap-1.5 text-xs font-medium text-pending transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
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
