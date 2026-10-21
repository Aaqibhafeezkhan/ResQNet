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
        'flex flex-col gap-5 ' +
        (align === 'center' ? 'items-center text-center ' : 'items-start ') +
        className
      }
    >
      <Reveal as="div" className="flex items-center gap-3.5">
        {index ? (
          <>
            <span className="tabular text-[9px] font-semibold tracking-[0.2em] text-network">
              {index}
            </span>
            <span className="h-px w-7 bg-white/15" aria-hidden="true" />
          </>
        ) : null}
        <span className="eyebrow">{eyebrow}</span>
      </Reveal>
      <Reveal as="h2" delay={0.04}>
        <span className="display-balance block max-w-[19ch] text-[2.15rem] font-medium leading-[0.98] tracking-tightest text-ink-primary sm:text-[3rem] lg:text-[3.9rem]">
          {title}
        </span>
      </Reveal>
      {body ? (
        <Reveal as="p" delay={0.08}>
          <span className="prose-measure block text-[14px] leading-relaxed sm:text-[15px]">
            {body}
          </span>
        </Reveal>
      ) : null}
    </div>
  );
}
