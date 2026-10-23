import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { ArrowUpRight, Github, Menu, X } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { PUBLIC_SITE_ONLY } from '@/app/buildMode';
import { BrandMark } from '@/components/public/BrandMark';
import { GITHUB_REPO } from '@/lib/links';

const NAV = [
  { to: '/#network', label: 'Network', index: '01' },
  { to: '/demo', label: 'Field lab', index: '02' },
  { to: '/lab', label: 'Replay lab', index: '03' },
  { to: '/download', label: 'Android', index: '04' },
  { to: '/contribute', label: 'Contribute', index: '05' },
  { to: '/about', label: 'About', index: '06' },
] as const;

export function SiteHeader() {
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const toggleRef = useRef<HTMLButtonElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const location = useLocation();
  const reduced = useReducedMotion() === true;

  useEffect(() => {
    let frame = 0;
    const onScroll = () => {
      if (frame) return;
      frame = window.requestAnimationFrame(() => {
        setScrolled(window.scrollY > 24);
        frame = 0;
      });
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  const close = useCallback(() => {
    setOpen(false);
  }, []);

  useEffect(() => {
    setOpen(false);
  }, [location.pathname, location.hash]);

  useEffect(() => {
    if (!open) return undefined;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const firstLink = panelRef.current?.querySelector<HTMLElement>('a, button');
    firstLink?.focus({ preventScroll: true });

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        close();
        toggleRef.current?.focus({ preventScroll: true });
        return;
      }
      if (event.key !== 'Tab' || !panelRef.current) return;
      const focusable = panelRef.current.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [close, open]);

  return (
    <header
      className={
        'fixed inset-x-0 top-0 z-50 transition-[background-color,border-color,backdrop-filter] duration-500 ease-cinematic ' +
        (scrolled || open
          ? 'border-b border-white/[0.07] bg-base-950/80 backdrop-blur-xl'
          : 'border-b border-transparent bg-gradient-to-b from-base-950/70 to-transparent')
      }
    >
      <div className="container-page flex h-[76px] items-center justify-between gap-6">
        <Link
          to="/"
          onClick={close}
          className="group flex items-center gap-3 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          aria-label="ResQNet home"
          data-cursor="Home"
        >
          <span className="relative grid h-9 w-9 place-items-center">
            <BrandMark className="h-9 w-9 text-network transition-transform duration-500 ease-cinematic group-hover:rotate-6" />
            <span
              className="absolute right-0.5 top-0.5 h-1 w-1 rounded-full bg-delivered shadow-[0_0_10px_rgba(98,230,181,0.9)]"
              aria-hidden="true"
            />
          </span>
          <span className="flex flex-col">
            <span className="text-[15px] font-semibold leading-none tracking-[-0.02em] text-ink-primary">
              RESQNET
            </span>
            <span className="mt-1 text-[8px] font-semibold uppercase tracking-[0.24em] text-ink-tertiary">
              Local-first mesh
            </span>
          </span>
        </Link>

        <nav className="hidden items-center gap-0.5 lg:flex" aria-label="Primary">
          {NAV.map((item) => (
            <HeaderLink
              key={item.to}
              to={item.to}
              index={item.index}
              active={isActive(location.pathname, item.to)}
            >
              {item.label}
            </HeaderLink>
          ))}
          {!PUBLIC_SITE_ONLY ? (
            <HeaderLink to="/command" index="07" active={location.pathname.startsWith('/command')}>
              Operations
            </HeaderLink>
          ) : null}
        </nav>

        <div className="hidden items-center gap-3 lg:flex">
          <a
            href={GITHUB_REPO}
            target="_blank"
            rel="noreferrer noopener"
            aria-label="ResQNet on GitHub"
            data-cursor="Source"
            className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-transparent text-ink-secondary transition-colors hover:border-white/10 hover:bg-white/[0.03] hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            <Github size={16} aria-hidden="true" />
          </a>
          <Link
            to="/demo"
            data-cursor="Open"
            className="group inline-flex min-h-[42px] items-center gap-2 rounded-full border border-network/35 bg-network/[0.07] px-4 text-[12px] font-semibold uppercase tracking-[0.12em] text-network transition-colors hover:border-network/70 hover:bg-network/15 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            Field lab
            <ArrowUpRight
              size={14}
              aria-hidden="true"
              className="transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
            />
          </Link>
        </div>

        <button
          ref={toggleRef}
          type="button"
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
          aria-controls="mobile-nav"
          aria-label={open ? 'Close navigation' : 'Open navigation'}
          className="relative inline-flex h-11 w-11 items-center justify-center rounded-full border border-white/10 text-ink-secondary transition-colors hover:border-white/20 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network lg:hidden"
        >
          {open ? <X size={18} aria-hidden="true" /> : <Menu size={18} aria-hidden="true" />}
        </button>
      </div>

      <AnimatePresence>
        {open ? (
          <motion.div
            ref={panelRef}
            id="mobile-nav"
            initial={reduced ? false : { opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={reduced ? { opacity: 0 } : { opacity: 0, y: -8 }}
            transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
            className="fixed inset-x-0 top-[76px] z-40 flex h-[calc(100svh-76px)] flex-col overflow-y-auto border-t border-white/[0.07] bg-base-950 px-5 pb-8 pt-6 sm:px-8 lg:hidden"
            aria-label="Mobile"
          >
            <span className="eyebrow">Navigate the mesh</span>
            <nav className="mt-5 flex flex-col" aria-label="Mobile primary">
              {NAV.map((item) => (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={close}
                  className="group flex items-center justify-between border-b border-white/[0.08] py-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
                >
                  <span className="flex items-baseline gap-4">
                    <span className="tabular text-[9px] tracking-widest text-ink-tertiary">
                      {item.index}
                    </span>
                    <span className="text-[26px] font-medium leading-none tracking-[-0.03em] text-ink-primary transition-colors group-hover:text-network">
                      {item.label}
                    </span>
                  </span>
                  <ArrowUpRight
                    size={17}
                    aria-hidden="true"
                    className="text-ink-tertiary transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-network"
                  />
                </Link>
              ))}
              {!PUBLIC_SITE_ONLY ? (
                <Link
                  to="/command"
                  onClick={close}
                  className="flex items-center justify-between border-b border-white/[0.08] py-4 text-[26px] font-medium leading-none tracking-[-0.03em] text-ink-primary"
                >
                  Operations
                  <span className="tabular text-[9px] tracking-widest text-ink-tertiary">07</span>
                </Link>
              ) : null}
            </nav>

            <div className="mt-auto pt-10">
              <p className="max-w-xs text-[13px] leading-relaxed text-ink-secondary">
                Nearby Android phones carrying signed messages when the towers
                do not answer.
              </p>
              <div className="mt-6 flex flex-wrap gap-3">
                <Link
                  to="/demo"
                  onClick={close}
                  className="inline-flex min-h-[46px] flex-1 items-center justify-center gap-2 rounded-full bg-network px-5 text-sm font-semibold text-base-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network focus-visible:ring-offset-2 focus-visible:ring-offset-base-950"
                >
                  Enter the field lab
                  <ArrowUpRight size={15} aria-hidden="true" />
                </Link>
                <a
                  href={GITHUB_REPO}
                  target="_blank"
                  rel="noreferrer noopener"
                  aria-label="ResQNet on GitHub"
                  className="inline-flex h-[46px] w-[46px] items-center justify-center rounded-full border border-white/10 text-ink-secondary"
                >
                  <Github size={17} aria-hidden="true" />
                </a>
              </div>
              <p className="mt-8 text-[9px] uppercase tracking-[0.2em] text-ink-tertiary">
                Experimental software · not a replacement for emergency services
              </p>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </header>
  );
}

function HeaderLink({
  to,
  active,
  index,
  children,
}: {
  to: string;
  active: boolean;
  index: string;
  children: React.ReactNode;
}) {
  return (
    <Link
      to={to}
      data-cursor="Open"
      className={
        'group relative rounded-full px-3 py-2 text-[12px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network ' +
        (active ? 'text-ink-primary' : 'text-ink-secondary hover:text-ink-primary')
      }
    >
      <span className="tabular mr-1.5 align-[1px] text-[8px] text-ink-tertiary">{index}</span>
      {children}
      <span
        className={
          'absolute inset-x-3 -bottom-0.5 h-px origin-left bg-network transition-transform duration-500 ease-cinematic ' +
          (active ? 'scale-x-100' : 'scale-x-0 group-hover:scale-x-100 group-focus-visible:scale-x-100')
        }
        aria-hidden="true"
      />
      {/*
        A soft wash behind the label. It is the same treatment as the hover
        on the hero's buttons, so navigation and actions feel like one
        system rather than two.
      */}
      <span
        className="absolute inset-0 -z-10 rounded-full bg-white/[0.045] opacity-0 transition-opacity duration-300 group-hover:opacity-100 group-focus-visible:opacity-100"
        aria-hidden="true"
      />
    </Link>
  );
}

function isActive(pathname: string, to: string): boolean {
  if (to.startsWith('/#')) return false;
  return to === '/' ? pathname === '/' : pathname.startsWith(to);
}
