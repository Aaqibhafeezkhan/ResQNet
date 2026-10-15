import type { ReactNode } from 'react';

import { Reveal } from './Reveal';

interface SectionHeadingProps {
  eyebrow: string;
  title: string;
  body?: ReactNode;
  align?: 'left' | 'center';
}

/**
 * Section heading.
 *
 * One heading component rather than five variations, so vertical rhythm stays
 * consistent down the page. The title uses a negative tracking value at large
 * sizes, which is what stops a big sans-serif headline from looking loosely set.
 */
export function SectionHeading({
  eyebrow,
  title,
  body,
  align = 'left',
}: SectionHeadingProps) {
  const alignment = align === 'center' ? 'items-center text-center' : 'items-start text-left';
  return (
    <div className={`flex flex-col gap-3 ${alignment}`}>
      <Reveal as="div">
        <span className="eyebrow">{eyebrow}</span>
      </Reveal>
      <Reveal as="h2" delay={0.04}>
        <span className="block max-w-3xl text-3xl font-semibold leading-[1.1] tracking-tightest text-ink-primary sm:text-4xl md:text-[2.75rem]">
          {title}
        </span>
      </Reveal>
      {body ? (
        <Reveal as="p" delay={0.08}>
          <span className="prose-measure text-[15px] sm:text-base">{body}</span>
        </Reveal>
      ) : null}
    </div>
  );
}
