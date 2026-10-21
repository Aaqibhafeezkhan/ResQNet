import { useEffect, useRef } from 'react';

/**
 * A restrained desktop pointer field.
 *
 * The native cursor is never hidden: replacing a platform pointer with a CSS
 * shape is a usability trade a safety-adjacent site should not make. Instead a
 * faint ring tracks behind the pointer and picks up a short contextual label
 * from `[data-cursor]`. Touch devices and reduced-motion visitors see nothing.
 */
export function CursorField() {
  const dotRef = useRef<HTMLDivElement | null>(null);
  const ringRef = useRef<HTMLDivElement | null>(null);
  const labelRef = useRef<HTMLSpanElement | null>(null);

  useEffect(() => {
    const finePointer = window.matchMedia('(pointer: fine)');
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (!finePointer.matches || reduced.matches) return undefined;

    const dot = dotRef.current;
    const ring = ringRef.current;
    const label = labelRef.current;
    if (!dot || !ring || !label) return undefined;

    let targetX = window.innerWidth / 2;
    let targetY = window.innerHeight / 2;
    let ringX = targetX;
    let ringY = targetY;
    let visible = false;
    let active = true;
    let frame = 0;

    const onPointer = (event: PointerEvent) => {
      targetX = event.clientX;
      targetY = event.clientY;
      if (!visible) {
        visible = true;
        dot.style.opacity = '1';
        ring.style.opacity = '1';
      }
    };

    const onEnter = (event: Event) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      const host = target.closest<HTMLElement>('[data-cursor]');
      const text = host?.dataset.cursor ?? '';
      label.textContent = text;
      ring.dataset.active = text ? 'true' : 'false';
    };

    const onLeaveWindow = () => {
      visible = false;
      dot.style.opacity = '0';
      ring.style.opacity = '0';
    };

    const tick = () => {
      if (!active) return;
      ringX += (targetX - ringX) * 0.16;
      ringY += (targetY - ringY) * 0.16;
      dot.style.transform = `translate3d(${targetX}px, ${targetY}px, 0) translate(-50%, -50%)`;
      ring.style.transform = `translate3d(${ringX}px, ${ringY}px, 0) translate(-50%, -50%)`;
      frame = window.requestAnimationFrame(tick);
    };

    window.addEventListener('pointermove', onPointer, { passive: true });
    window.addEventListener('pointerover', onEnter, { passive: true });
    document.documentElement.addEventListener('pointerleave', onLeaveWindow);
    frame = window.requestAnimationFrame(tick);

    return () => {
      active = false;
      window.cancelAnimationFrame(frame);
      window.removeEventListener('pointermove', onPointer);
      window.removeEventListener('pointerover', onEnter);
      document.documentElement.removeEventListener('pointerleave', onLeaveWindow);
    };
  }, []);

  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 z-[80] hidden lg:block">
      <div
        ref={ringRef}
        className="group absolute left-0 top-0 grid h-8 w-8 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border border-network/45 opacity-0 transition-[width,height,background-color,border-color,opacity] duration-300 ease-cinematic data-[active=true]:h-14 data-[active=true]:w-14 data-[active=true]:border-network/70 data-[active=true]:bg-network/10"
        style={{ transform: 'translate3d(-100px,-100px,0)' }}
      >
        <span
          ref={labelRef}
          className="max-w-[64px] text-center text-[8px] font-semibold uppercase leading-tight tracking-[0.16em] text-network opacity-0 transition-opacity duration-200 group-data-[active=true]:opacity-100"
        />
      </div>
      <div
        ref={dotRef}
        className="absolute left-0 top-0 h-1 w-1 -translate-x-1/2 -translate-y-1/2 rounded-full bg-network opacity-0 shadow-[0_0_12px_rgba(92,225,223,0.85)]"
        style={{ transform: 'translate3d(-100px,-100px,0)' }}
      />
    </div>
  );
}
