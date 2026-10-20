import type { ReactNode } from 'react';

import { Reveal } from '@/components/primitives/Reveal';

interface PageHeroProps {
  eyebrow: string;
  title: ReactNode;
  body: ReactNode;
  aside?: ReactNode;
  children?: ReactNode;
}

export function PageHero({ eyebrow, title, body, aside, children }: PageHeroProps) {
  return (
    <section className="relative overflow-hidden border-b border-base-600">
      <div className="grid-field pointer-events-none absolute inset-0 opacity-40" aria-hidden="true" />
      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(80%_80%_at_15%_0%,rgba(67,216,220,0.1),transparent_62%)]"
        aria-hidden="true"
      />
      <div className="container-page relative grid gap-12 pb-16 pt-16 sm:pb-20 sm:pt-24 lg:grid-cols-[minmax(0,1.5fr)_minmax(280px,0.5fr)] lg:items-end lg:gap-16 lg:pb-24">
        <div>
          <Reveal as="div" className="flex items-center gap-3">
            <span className="h-px w-8 bg-network" aria-hidden="true" />
            <span className="eyebrow">{eyebrow}</span>
          </Reveal>
          <Reveal as="h1" delay={0.05}>
            <span className="display-balance mt-5 block max-w-5xl text-[2.7rem] font-semibold leading-[0.98] tracking-tightest text-ink-primary sm:text-6xl lg:text-7xl">
              {title}
            </span>
          </Reveal>
          <Reveal as="p" delay={0.1}>
            <span className="prose-measure mt-6 block text-base leading-relaxed text-ink-secondary sm:text-lg">
              {body}
            </span>
          </Reveal>
          {children ? (
            <Reveal as="div" delay={0.14} className="mt-8 flex flex-wrap gap-3">
              {children}
            </Reveal>
          ) : null}
        </div>
        {aside ? (
          <Reveal delay={0.18} className="lg:pb-1">
            {aside}
          </Reveal>
        ) : null}
      </div>
    </section>
  );
}
