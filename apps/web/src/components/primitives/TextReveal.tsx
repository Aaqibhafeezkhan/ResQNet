import { motion, useReducedMotion } from 'framer-motion';
import { Children, isValidElement, useMemo, type ReactNode } from 'react';

interface TextRevealProps {
  children: ReactNode;
  className?: string;
  /** Stagger between words, in seconds. */
  stagger?: number;
  /** Delay before the reveal starts, in seconds. */
  delay?: number;
  as?: 'h1' | 'h2' | 'h3' | 'p' | 'span';
}

/**
 * A run of words that share a style.
 */
interface Segment {
  words: string[];
  className: string;
}

/**
 * A typographic reveal.
 *
 * Each word rises out of its own mask, the way a line of type resolves when
 * it comes into focus. This is deliberately not the same treatment as
 * `Reveal`: a heading that fades up as a block reads as a loading state, while
 * a heading that resolves word by word reads as language being set.
 *
 * Inline markup inside the heading is preserved. A heading that emphasises a
 * word with a different face or colour keeps that emphasis after the
 * reveal, because the words are grouped by the style of the element they came
 * from rather than being flattened into one string.
 *
 * The words are real text nodes in the accessibility tree, so a screen reader
 * still reads the heading as one sentence.
 */
export function TextReveal({
  children,
  className = '',
  stagger = 0.035,
  delay = 0,
  as = 'span',
}: TextRevealProps) {
  const reduced = useReducedMotion();
  const segments = useMemo(() => collectSegments(children), [children]);

  if (reduced) {
    const Static = as;
    return (
      <Static className={className}>
        {segments.map((segment) => (
          <span key={segment.words.join('-')} className={segment.className}>
            {segment.words.join(' ')}
            {' '}
          </span>
        ))}
      </Static>
    );
  }

  const Component = motion[as] as typeof motion.span;

  let index = 0;

  return (
    <Component
      className={className}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, margin: '-10% 0px -6% 0px' }}
      transition={{ staggerChildren: stagger, delayChildren: delay }}
    >
      {segments.map((segment) => (
        <span key={segment.words.join('-')} className={segment.className}>
          {segment.words.map((word) => {
            index += 1;
            const key = `${word}-${index}`;
            return (
              <span
                key={key}
                className="inline-block overflow-hidden align-bottom"
                style={{ paddingBottom: '0.12em', marginBottom: '-0.12em' }}
              >
                <motion.span
                  className="inline-block"
                  variants={{
                    hidden: { y: '110%' },
                    visible: { y: '0%' },
                  }}
                  transition={{ duration: 0.72, ease: [0.16, 1, 0.3, 1] }}
                >
                  {word}
                  {'\u00A0'}
                </motion.span>
              </span>
            );
          })}
        </span>
      ))}
    </Component>
  );
}

/**
 * Flattens a heading into styled runs of words.
 *
 * A node's own class is merged with the classes of its ancestors, so an
 * emphasised word keeps its emphasis even when it sits inside a styled
 * span.
 */
function collectSegments(node: ReactNode, inherited = ''): Segment[] {
  const segments: Segment[] = [];

  const visit = (current: ReactNode, className: string) => {
    Children.forEach(current, (child) => {
      if (typeof child === 'string' || typeof child === 'number') {
        const words = String(child).split(/\s+/).filter((word) => word.length > 0);
        if (words.length === 0) return;
        const last = segments[segments.length - 1];
        if (last && last.className === className) {
          last.words.push(...words);
        } else {
          segments.push({ words: [...words], className });
        }
        return;
      }

      if (isValidElement(child)) {
        const props = child.props as { className?: string; children?: ReactNode };
        const merged = [className, props.className].filter(Boolean).join(' ');
        visit(props.children, merged);
      }
    });
  };

  visit(node, inherited);
  return segments;
}
