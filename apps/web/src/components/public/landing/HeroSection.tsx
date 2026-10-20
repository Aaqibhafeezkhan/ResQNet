import { ArrowDownRight, ArrowUpRight, Github, RadioTower, ShieldCheck, WifiOff } from 'lucide-react';

import { HeroNetwork } from '@/components/public/HeroNetwork';
import { ButtonLink } from '@/components/primitives/Button';
import { Reveal } from '@/components/primitives/Reveal';
import { GITHUB_REPO } from '@/lib/links';

const PROOF = [
  { icon: WifiOff, label: 'No internet required' },
  { icon: RadioTower, label: 'Local radios only' },
  { icon: ShieldCheck, label: 'No account in the SOS path' },
];

export function HeroSection() {
  return (
    <section className="relative isolate overflow-hidden">
      <div className="grid-field pointer-events-none absolute inset-0 opacity-35" aria-hidden="true" />
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(80%_70%_at_30%_0%,rgba(67,216,220,0.1),transparent_62%)]"
        aria-hidden="true"
      />

      <div className="container-page relative grid gap-14 pb-20 pt-14 sm:pb-24 sm:pt-20 lg:grid-cols-[minmax(0,1.08fr)_minmax(420px,0.92fr)] lg:items-center lg:gap-12 lg:pb-28 lg:pt-24">
        <div>
          <Reveal as="div" className="flex flex-wrap items-center gap-3">
            <span className="inline-flex items-center gap-2 rounded-full border border-base-600 bg-base-900/70 px-3 py-1.5 text-[10px] font-semibold uppercase tracking-widest text-ink-secondary">
              <span className="relative flex h-1.5 w-1.5" aria-hidden="true">
                <span className="absolute inline-flex h-full w-full animate-signal-pulse rounded-full bg-delivered" />
                <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-delivered" />
              </span>
              Android preview · v0.2
            </span>
            <span className="text-[10px] font-medium uppercase tracking-widest text-ink-tertiary">
              Open-source research prototype
            </span>
          </Reveal>

          <Reveal as="h1" delay={0.06}>
            <span className="display-balance mt-7 block max-w-[13ch] text-[3.15rem] font-semibold leading-[0.93] tracking-tightest text-ink-primary sm:text-[4.5rem] lg:text-[5.1rem]">
              When the signal{' '}
              <span className="block font-display font-normal italic leading-[0.98] text-ink-primary">
                disappears,
              </span>{' '}
              the message keeps moving.
            </span>
          </Reveal>

          <Reveal as="p" delay={0.12}>
            <span className="prose-measure mt-7 block text-base leading-relaxed text-ink-secondary sm:text-lg">
              ResQNet turns nearby Android phones into a store-and-forward
              emergency network. A signed SOS can cross a relay, change
              direction and reach a field post without a tower, a server or an
              account.
            </span>
          </Reveal>

          <Reveal as="div" delay={0.18} className="mt-8 flex flex-wrap items-center gap-3">
            <ButtonLink to="/demo" size="large">
              Run the mesh lab
              <ArrowUpRight size={17} aria-hidden="true" />
            </ButtonLink>
            <ButtonLink to="/download" variant="secondary" size="large">
              Get the Android build
              <ArrowDownRight size={17} aria-hidden="true" />
            </ButtonLink>
            <a
              href={GITHUB_REPO}
              target="_blank"
              rel="noreferrer noopener"
              className="inline-flex min-h-[44px] items-center gap-2 rounded-full px-3 text-sm text-ink-secondary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              <Github size={16} aria-hidden="true" />
              Source
            </a>
          </Reveal>

          <Reveal as="ul" delay={0.23} className="mt-10 flex flex-wrap gap-x-5 gap-y-3">
            {PROOF.map((item) => {
              const Icon = item.icon;
              return (
                <li
                  key={item.label}
                  className="inline-flex items-center gap-2 text-[11px] font-medium text-ink-tertiary"
                >
                  <Icon size={13} aria-hidden="true" className="text-ink-tertiary" />
                  {item.label}
                </li>
              );
            })}
          </Reveal>
        </div>

        <Reveal delay={0.12} className="lg:pl-3">
          <HeroNetwork />
        </Reveal>
      </div>

      <div className="container-page relative">
        <div className="flex flex-col gap-3 border-t border-base-600 py-5 text-[10px] uppercase tracking-widest text-ink-tertiary sm:flex-row sm:items-center sm:justify-between">
          <span>Field note 001 · the network is already around you</span>
          <a
            href="#story"
            className="inline-flex items-center gap-2 transition-colors hover:text-ink-primary"
          >
            Read the premise
            <ArrowDownRight size={12} aria-hidden="true" />
          </a>
        </div>
      </div>
    </section>
  );
}
