import type { ReactNode } from 'react';

import { Reveal } from '@/components/primitives/Reveal';

interface SectionIntroProps {
  eyebrow: string;
  title: ReactNode;
  body?: ReactNode;
  index?: string;
  align?: 'left' | 'center';
  className?: string;
}

export function SectionIntro({
  eyebrow,
  title,
  body,
  index,
  align = 'left',
  className = '',
}: SectionIntroProps) {
  return (
    <div
      className={
        'flex flex-col gap-4 ' +
        (align === 'center' ? 'items-center text-center ' : 'items-start ') +
        className
      }
    >
      <Reveal as="div" className="flex items-center gap-3">
        {index ? (
          <span className="tabular text-[10px] font-semibold tracking-widest text-network">
            {index}
          </span>
        ) : null}
        <span className="eyebrow">{eyebrow}</span>
      </Reveal>
      <Reveal as="h2" delay={0.04}>
        <span className="display-balance block max-w-4xl text-[2rem] font-semibold leading-[1.04] tracking-tightest text-ink-primary sm:text-[2.6rem] lg:text-[3.2rem]">
          {title}
        </span>
      </Reveal>
      {body ? (
        <Reveal as="p" delay={0.08}>
          <span className="prose-measure block text-[15px] leading-relaxed sm:text-base">
            {body}
          </span>
        </Reveal>
      ) : null}
    </div>
  );
}
