import { ArrowUpRight, Github } from 'lucide-react';

import { ButtonAnchor, ButtonLink } from '@/components/primitives/Button';
import { Reveal } from '@/components/primitives/Reveal';
import { GITHUB_REPO } from '@/lib/links';

export function ClosingSection() {
  return (
    <section className="relative overflow-hidden py-24 sm:py-32 lg:py-40">
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(65%_90%_at_50%_110%,rgba(92,225,223,0.1),transparent_64%)]"
        aria-hidden="true"
      />
      <div
        className="pointer-events-none absolute left-1/2 top-1/2 h-[720px] w-[720px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/[0.035]"
        aria-hidden="true"
      />
      <div
        className="pointer-events-none absolute left-1/2 top-1/2 h-[500px] w-[500px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-network/[0.07]"
        aria-hidden="true"
      />
      <div className="container-page relative">
        <Reveal className="flex flex-col items-center text-center">
          <span className="inline-flex items-center gap-2.5 text-[9px] font-semibold uppercase tracking-[0.22em] text-ink-tertiary">
            <span className="h-1.5 w-1.5 rounded-full bg-network" aria-hidden="true" />
            The next message
          </span>
          <h2 className="display-balance mt-8 max-w-[16ch] text-[clamp(2.6rem,7.4vw,7.4rem)] font-medium uppercase leading-[0.85] tracking-[-0.055em] text-ink-primary">
            Built for the moments{' '}
            <span className="block font-display font-normal italic normal-case tracking-[-0.03em] text-ink-secondary">
              when connections fail.
            </span>
          </h2>
          <p className="mt-8 max-w-xl text-[14px] leading-relaxed text-ink-secondary">
            Operate the simulation, read the architecture, or install the Android
            preview. Every claim has a boundary, and every boundary is written
            down.
          </p>
          <div className="mt-10 flex flex-wrap justify-center gap-3">
            <ButtonLink to="/demo" size="large" aria-label="Explore the field lab">
              Explore the field lab
              <ArrowUpRight size={16} aria-hidden="true" />
            </ButtonLink>
            <ButtonLink
              to="/download"
              variant="secondary"
              size="large"
              aria-label="Download the Android beta"
            >
              Download Android beta
            </ButtonLink>
            <ButtonAnchor
              href={GITHUB_REPO}
              variant="ghost"
              size="large"
              target="_blank"
              rel="noreferrer noopener"
              aria-label="View ResQNet on GitHub"
            >
              <Github size={15} aria-hidden="true" />
              View on GitHub
            </ButtonAnchor>
          </div>

          <div className="mt-16 flex w-full max-w-lg items-center gap-3 text-[8px] uppercase tracking-[0.18em] text-ink-tertiary">
            <span className="h-px flex-1 bg-gradient-to-r from-transparent to-white/15" aria-hidden="true" />
            <span>Message end · acknowledgement required</span>
            <span className="h-px flex-1 bg-gradient-to-l from-transparent to-white/15" aria-hidden="true" />
          </div>
        </Reveal>
      </div>
    </section>
  );
}
