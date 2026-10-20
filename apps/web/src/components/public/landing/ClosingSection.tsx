import { ArrowUpRight, Github } from 'lucide-react';

import { ButtonAnchor, ButtonLink } from '@/components/primitives/Button';
import { Reveal } from '@/components/primitives/Reveal';
import { GITHUB_REPO } from '@/lib/links';

export function ClosingSection() {
  return (
    <section className="relative overflow-hidden py-20 sm:py-28">
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(70%_100%_at_50%_100%,rgba(67,216,220,0.09),transparent_64%)]"
        aria-hidden="true"
      />
      <div className="container-page relative">
        <Reveal className="mx-auto flex max-w-3xl flex-col items-center text-center">
          <span className="eyebrow">The next message</span>
          <h2 className="display-balance mt-5 text-[2.3rem] font-semibold leading-[1.02] tracking-tightest text-ink-primary sm:text-5xl lg:text-6xl">
            The next SOS might not need a tower.
          </h2>
          <p className="mt-5 max-w-xl text-[15px] leading-relaxed text-ink-secondary">
            Operate the simulation, read the architecture, or install the Android
            preview. Every claim has a boundary, and every boundary is written
            down.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <ButtonLink to="/demo" size="large">
              Open the field lab
              <ArrowUpRight size={16} aria-hidden="true" />
            </ButtonLink>
            <ButtonLink to="/download" variant="secondary" size="large">
              Android preview
            </ButtonLink>
            <ButtonAnchor
              href={GITHUB_REPO}
              variant="ghost"
              size="large"
              target="_blank"
              rel="noreferrer noopener"
            >
              <Github size={16} aria-hidden="true" />
              GitHub
            </ButtonAnchor>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
