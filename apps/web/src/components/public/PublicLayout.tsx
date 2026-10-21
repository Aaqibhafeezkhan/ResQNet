import { motion, useReducedMotion } from 'framer-motion';
import { useEffect, useRef } from 'react';
import { useLocation, useOutlet } from 'react-router-dom';

import { Footer } from '@/components/Footer';
import { CursorField } from '@/components/public/CursorField';
import { SiteHeader } from '@/components/SiteHeader';

/**
 * The persistent public shell.
 *
 * Header and footer live outside the route outlet so navigation does not
 * remount the chrome. Each route enters with a short transform/opacity
 * transition; the previous page is replaced rather than faded out, which keeps
 * focus and scroll behaviour straightforward.
 */
export function PublicLayout() {
  const location = useLocation();
  const outlet = useOutlet();
  const reduced = useReducedMotion() === true;
  const mainRef = useRef<HTMLElement | null>(null);
  const previousPath = useRef(location.pathname);

  useScrollReset();
  useRouteFocus(previousPath, mainRef, location.pathname);

  return (
    <div className="min-h-svh bg-base-950">
      <a
        href="#main"
        className="fixed left-4 top-4 z-[90] -translate-y-24 rounded-full bg-network px-5 py-3 text-sm font-semibold text-base-950 transition-transform focus:translate-y-0"
      >
        Skip to content
      </a>
      <ScrollProgress disabled={reduced} />
      <SiteHeader />
      <main id="main" ref={mainRef} tabIndex={-1} className="relative">
        <motion.div
          key={`${location.pathname}${location.hash}`}
          initial={reduced ? false : { opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, ease: [0.16, 1, 0.3, 1] }}
        >
          {outlet}
        </motion.div>
      </main>
      <Footer />
      <CursorField />
    </div>
  );
}

function useRouteFocus(
  previousPath: React.MutableRefObject<string>,
  mainRef: React.RefObject<HTMLElement | null>,
  pathname: string,
): void {
  useEffect(() => {
    if (previousPath.current === pathname) return;
    previousPath.current = pathname;
    const frame = window.requestAnimationFrame(() => {
      mainRef.current?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [mainRef, pathname, previousPath]);
}

function useScrollReset(): void {
  const { pathname, hash } = useLocation();

  useEffect(() => {
    if (hash) {
      const id = decodeURIComponent(hash.slice(1));
      const frame = window.requestAnimationFrame(() => {
        document.getElementById(id)?.scrollIntoView({
          behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
            ? 'auto'
            : 'smooth',
          block: 'start',
        });
      });
      return () => window.cancelAnimationFrame(frame);
    }

    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    return undefined;
  }, [pathname, hash]);
}

function ScrollProgress({ disabled }: { disabled: boolean }) {
  const barRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (disabled) {
      if (barRef.current) barRef.current.style.transform = 'scaleX(0)';
      return undefined;
    }
    let frame = 0;
    const update = () => {
      frame = 0;
      const doc = document.documentElement;
      const max = doc.scrollHeight - window.innerHeight;
      const progress = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0;
      if (barRef.current) barRef.current.style.transform = `scaleX(${progress})`;
    };
    const onScroll = () => {
      if (frame) return;
      frame = window.requestAnimationFrame(update);
    };
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, [disabled]);

  return (
    <div
      ref={barRef}
      aria-hidden="true"
      className="fixed inset-x-0 top-0 z-[60] h-[2px] origin-left scale-x-0 bg-gradient-to-r from-network/80 via-network to-delivered"
    />
  );
}
