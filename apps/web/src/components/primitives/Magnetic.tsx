import { useRef, type CSSProperties, type PointerEvent, type ReactNode } from 'react';

import { useReducedMotion } from '@/lib/useReducedMotion';

interface MagneticProps {
  children: ReactNode;
  /** How far the element may move towards the pointer, in pixels. */
  strength?: number;
  className?: string;
}

/**
 * A magnetic hover response.
 *
 * The element leans towards the pointer and settles back when the pointer
 * leaves. The movement is small — a few pixels — because the goal is to make
 * a control feel alive under the cursor, not to make it hard to click.
 *
 * The transform is written straight to the element's style rather than
 * through state, so moving the pointer never triggers a React render. That
 * matters: a hover effect that re-renders the tree on every pointer move is
 * how a page ends up feeling slow exactly when the visitor is exploring it.
 *
 * Touch devices get the effect through focus and press instead, since there
 * is no hover state to speak of.
 */
export function Magnetic({ children, strength = 6, className = '' }: MagneticProps) {
  const ref = useRef<HTMLSpanElement | null>(null);
  const reduced = useReducedMotion();
  const frame = useRef(0);
  const target = useRef({ x: 0, y: 0 });
  const current = useRef({ x: 0, y: 0 });

  const tick = () => {
    frame.current = 0;
    const element = ref.current;
    if (!element) return;
    current.current.x += (target.current.x - current.current.x) * 0.18;
    current.current.y += (target.current.y - current.current.y) * 0.18;
    element.style.transform = `translate3d(${current.current.x.toFixed(2)}px, ${current.current.y.toFixed(2)}px, 0)`;
    if (
      Math.abs(target.current.x - current.current.x) > 0.05 ||
      Math.abs(target.current.y - current.current.y) > 0.05
    ) {
      frame.current = window.requestAnimationFrame(tick);
    }
  };

  const schedule = () => {
    if (reduced) return;
    if (!frame.current) frame.current = window.requestAnimationFrame(tick);
  };

  const onPointerMove = (event: PointerEvent<HTMLSpanElement>) => {
    if (reduced) return;
    const element = ref.current;
    if (!element) return;
    const rect = element.getBoundingClientRect();
    const x = (event.clientX - rect.left - rect.width / 2) / (rect.width / 2 || 1);
    const y = (event.clientY - rect.top - rect.height / 2) / (rect.height / 2 || 1);
    target.current.x = x * strength;
    target.current.y = y * strength;
    schedule();
  };

  const reset = () => {
    target.current.x = 0;
    target.current.y = 0;
    schedule();
  };

  return (
    <span
      ref={ref}
      className={'inline-flex will-change-transform ' + className}
      onPointerMove={onPointerMove}
      onPointerLeave={reset}
      onBlur={reset}
      style={{ transform: 'translate3d(0,0,0)' }}
    >
      {children}
    </span>
  );
}

/**
 * A hairline that draws itself when the control is hovered or focused.
 *
 * Used under links and list rows so that the "where does this go" affordance
 * is a movement rather than a colour change.
 */
export function Underline({ className = '' }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={
        'pointer-events-none absolute inset-x-0 bottom-0 h-px origin-left scale-x-0 bg-current ' +
        'transition-transform duration-500 ease-cinematic group-hover:scale-x-100 ' +
        'group-focus-visible:scale-x-100 ' +
        className
      }
    />
  );
}

/**
 * A surface that lifts and catches a light edge when hovered.
 *
 * The lift is 2px and the border brightens. Small enough to read as
 * material responding, large enough to be noticed.
 */
export function LiftSurface({
  children,
  className = '',
  style,
}: {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
}) {
  return (
    <div
      className={
        'transition-[transform,border-color,background-color] duration-500 ease-cinematic ' +
        'hover:-translate-y-0.5 hover:border-white/20 focus-within:-translate-y-0.5 ' +
        'focus-within:border-white/20 ' +
        className
      }
      style={style}
    >
      {children}
    </div>
  );
}
