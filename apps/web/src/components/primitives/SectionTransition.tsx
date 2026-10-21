import { motion, useReducedMotion } from 'framer-motion';
import type { ReactNode } from 'react';

interface SectionTransitionProps {
  children: ReactNode;
  className?: string;
  /** Which side the section enters from. */
  from?: 'left' | 'right' | 'bottom';
}

/**
 * A section that arrives as a connected chapter.
 *
 * The landing page reads as one continuous document rather than as a stack of
 * independent blocks, so each section enters with a small directional push
 * that matches the way the content is laid out. The distance is deliberately
 * short: the visitor should feel the page continuing, not watch a slideshow.
 *
 * The wrapper is a plain element so it can sit around an existing `<section>`
 * without changing the document outline or the section's own ids.
 *
 * Under reduced motion the section simply appears.
 */
export function SectionTransition({
  children,
  className = '',
  from = 'bottom',
}: SectionTransitionProps) {
  const reduced = useReducedMotion();

  if (reduced) {
    return <div className={'overflow-x-clip ' + className}>{children}</div>;
  }

  const offset =
    from === 'left'
      ? { x: -30, y: 0 }
      : from === 'right'
        ? { x: 30, y: 0 }
        : { x: 0, y: 30 };

  return (
    /*
      The clipping wrapper is not cosmetic.

      A horizontal entrance transform on a full-width element pushes it past
      the right edge of the viewport, which grows the document's scroll width
      and produces a horizontal scrollbar on every page. The static wrapper
      clips the transformed child so the entrance can move without changing
      the layout. `clip` rather than `hidden` because `hidden` would create a
      scroll container and break the sticky column inside some sections.
    */
    <div className={'overflow-x-clip ' + className}>
      <motion.div
        initial={{ opacity: 0, ...offset }}
        whileInView={{ opacity: 1, x: 0, y: 0 }}
        viewport={{ once: true, margin: '-6% 0px -4% 0px' }}
        transition={{ duration: 0.75, ease: [0.16, 1, 0.3, 1] }}
      >
        {children}
      </motion.div>
    </div>
  );
}
