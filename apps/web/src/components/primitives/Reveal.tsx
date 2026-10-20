import { motion, useReducedMotion } from 'framer-motion';
import type { ReactNode } from 'react';

interface RevealProps {
  children: ReactNode;
  /** Stagger in milliseconds. */
  delay?: number;
  className?: string;
  /** Travel distance in pixels. Lower is calmer. */
  distance?: number;
  as?: 'div' | 'section' | 'li' | 'article' | 'h1' | 'h2' | 'p' | 'span' | 'ul';
}

/**
 * Progressive content reveal.
 *
 * Transform and opacity only. Both are compositor-friendly; animating height or
 * top would trigger layout on every frame, which is exactly the kind of cost
 * that turns a page that feels solid into one that stutters on a phone.
 *
 * Under reduced motion the element simply appears. No shorter duration, no
 * smaller travel: a person who asked for less motion gets none.
 */
export function Reveal({
  children,
  delay = 0,
  className,
  distance = 14,
  as = 'div',
}: RevealProps) {
  const reduced = useReducedMotion();

  if (reduced) {
    const Static = as;
    return <Static className={className}>{children}</Static>;
  }

  // `motion[as]` cannot be typed against an open string union without losing
  // every prop, so the element name is narrowed once and the shared props are
  // applied through a plain wrapper. Framer Motion's own generic handles the
  // result correctly at runtime.
  const Component = motion[as] as typeof motion.div;

  return (
    <Component
      className={className}
      initial={{ opacity: 0, y: distance }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-12% 0px -8% 0px' }}
      transition={{ duration: 0.55, delay, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </Component>
  );
}
