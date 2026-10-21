import type { ReactNode } from 'react';

import { Reveal } from '@/components/primitives/Reveal';
import { TextReveal } from '@/components/primitives/TextReveal';

interface PageHeroProps {
  eyebrow: string;
  title: ReactNode;
  body: ReactNode;
  aside?: ReactNode;
  children?: ReactNode;
}

export function PageHero({ eyebrow, title, body, aside, children }: PageHeroProps) {
  return (
    <section className="relative isolate overflow-hidden border-b border-white/[0.08] pb-16 pt-36 sm:pb-20 sm:pt-44 lg:pb-24 lg:pt-48">
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.55]"
        aria-hidden="true"
        style={{
          backgroundImage:
            'linear-gradient(to right, rgba(244,241,232,0.035) 1px, transparent 1px), linear-gradient(to bottom, rgba(244,241,232,0.035) 1px, transparent 1px)',
          backgroundSize: '88px 88px',
          maskImage: 'linear-gradient(to bottom, black, transparent 82%)',
        }}
      />
      <div
        className="pointer-events-none absolute -top-40 left-[8%] h-[420px] w-[420px] rounded-full bg-network/[0.06] blur-[100px]"
        aria-hidden="true"
      />
      <div className="container-page relative grid gap-12 lg:grid-cols-[minmax(0,1.45fr)_minmax(280px,0.55fr)] lg:items-end lg:gap-16">
        <div>
          <Reveal as="div" className="flex items-center gap-4">
            <span className="tabular text-[9px] font-semibold tracking-[0.24em] text-network">
              RSQ / {String(new Date().getFullYear()).slice(2)}
            </span>
            <span className="h-px w-10 bg-network/45" aria-hidden="true" />
            <span className="eyebrow">{eyebrow}</span>
          </Reveal>
          <Reveal as="h1" delay={0.04}>
            {/*
              The heading resolves word by word rather than fading in as one
              block. A whole-block fade reads as a loading state; a heading
              that sets itself reads as a page opening.
            */}
            <TextReveal
              as="span"
              className="display-balance mt-6 block max-w-[15ch] text-[2.9rem] font-medium leading-[0.92] tracking-tightest text-ink-primary sm:text-[4.4rem] lg:text-[5.6rem]"
            >
              {title}
            </TextReveal>
          </Reveal>
          <Reveal as="p" delay={0.08}>
            <span className="prose-measure mt-7 block text-[15px] leading-relaxed text-ink-secondary sm:text-lg">
              {body}
            </span>
          </Reveal>
          {children ? (
            <Reveal as="div" delay={0.12} className="mt-9 flex flex-wrap gap-3">
              {children}
            </Reveal>
          ) : null}
        </div>
        {aside ? (
          <Reveal delay={0.14} className="lg:pb-1">
            {aside}
          </Reveal>
        ) : null}
      </div>
    </section>
  );
}
