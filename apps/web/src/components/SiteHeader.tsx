import { Command, Github, HeartPulse, Menu, X } from 'lucide-react';
import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { Button } from '@/components/primitives/Button';

/**
 * Site header.
 *
 * Sticky rather than fixed, with a backdrop blur applied only after the page
 * has scrolled. A permanently blurred bar costs a compositing pass on every
 * frame of an otherwise static scroll.
 */
export function SiteHeader() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const onCommand = location.pathname.startsWith('/command');

  return (
    <header className="sticky top-0 z-40 border-b border-base-700/70 bg-base-900/85 backdrop-blur-md">
      <div className="mx-auto flex w-full max-w-7xl items-center justify-between gap-4 px-5 py-3.5 sm:px-8">
        <Link
          to="/"
          className="flex items-center gap-2.5 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
        >
          <HeartPulse size={18} className="text-emergency" aria-hidden="true" />
          <span className="text-sm font-semibold tracking-tight text-ink-primary">ResQNet</span>
          <span className="hidden text-xs text-ink-tertiary sm:inline">
            offline emergency messaging
          </span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex" aria-label="Primary">
          <HeaderLink href="/#how">How it works</HeaderLink>
          <HeaderLink href="/#architecture">Architecture</HeaderLink>
          <HeaderLink href="/#run-it-locally">Run it</HeaderLink>
          <a
            href="https://github.com/Gautam-aman/ResQNet"
            target="_blank"
            rel="noreferrer noopener"
            className="rounded-lg px-3 py-2 text-sm text-ink-secondary transition-colors hover:bg-base-800 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            <span className="inline-flex items-center gap-1.5">
              <Github size={15} aria-hidden="true" />
              GitHub
            </span>
          </a>
          <Link to="/command" className="ml-2 inline-flex">
            <Button variant={onCommand ? 'secondary' : 'primary'}>
              <Command size={15} aria-hidden="true" />
              Command centre
            </Button>
          </Link>
        </nav>

        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
          aria-controls="mobile-nav"
          className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-base-600 text-ink-secondary transition-colors hover:text-ink-primary md:hidden"
        >
          {open ? <X size={18} aria-hidden="true" /> : <Menu size={18} aria-hidden="true" />}
          <span className="sr-only">{open ? 'Close navigation' : 'Open navigation'}</span>
        </button>
      </div>

      {open ? (
        <nav
          id="mobile-nav"
          className="border-t border-base-700 bg-base-850 px-5 py-4 md:hidden"
          aria-label="Mobile"
        >
          <div className="flex flex-col gap-1">
            <HeaderLink href="/#how" onNavigate={() => setOpen(false)}>
              How it works
            </HeaderLink>
            <HeaderLink href="/#architecture" onNavigate={() => setOpen(false)}>
              Architecture
            </HeaderLink>
            <HeaderLink href="/#run-it-locally" onNavigate={() => setOpen(false)}>
              Run it locally
            </HeaderLink>
            <Link
              to="/command"
              onClick={() => setOpen(false)}
              className="mt-2 inline-flex items-center justify-center gap-2 rounded-xl bg-network px-4 py-3 text-sm font-medium text-base-900"
            >
              <Command size={15} aria-hidden="true" />
              Command centre
            </Link>
          </div>
        </nav>
      ) : null}
    </header>
  );
}

function HeaderLink({
  href,
  children,
  onNavigate,
}: {
  href: string;
  children: React.ReactNode;
  onNavigate?: () => void;
}) {
  return (
    <a
      href={href}
      onClick={onNavigate}
      className="rounded-lg px-3 py-2 text-sm text-ink-secondary transition-colors hover:bg-base-800 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
    >
      {children}
    </a>
  );
}
