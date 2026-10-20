import { ArrowUpRight, Github, Menu, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { PUBLIC_SITE_ONLY } from '@/app/buildMode';
import { BrandMark } from '@/components/public/BrandMark';
import { ButtonLink } from '@/components/primitives/Button';
import { GITHUB_REPO } from '@/lib/links';

const NAV = [
  { to: '/#how', label: 'How it works' },
  { to: '/demo', label: 'Field lab' },
  { to: '/download', label: 'Android' },
  { to: '/contribute', label: 'Contribute' },
  { to: '/about', label: 'About' },
];

export function SiteHeader() {
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const location = useLocation();

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    setOpen(false);
  }, [location.pathname, location.hash]);

  return (
    <header
      className={
        'sticky top-0 z-50 border-b transition-colors duration-300 ' +
        (scrolled || open
          ? 'border-base-600/80 bg-base-950/88 backdrop-blur-xl'
          : 'border-transparent bg-transparent')
      }
    >
      <div className="container-page flex h-[72px] items-center justify-between gap-5">
        <Link
          to="/"
          className="group flex items-center gap-3 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          aria-label="ResQNet home"
        >
          <BrandMark className="h-9 w-9 text-network transition-transform duration-300 group-hover:-rotate-3" />
          <span className="flex flex-col">
            <span className="text-[15px] font-semibold leading-none tracking-tight text-ink-primary">
              ResQNet
            </span>
            <span className="mt-1 text-[10px] font-medium uppercase tracking-[0.2em] text-ink-tertiary">
              Local-first SOS
            </span>
          </span>
        </Link>

        <nav className="hidden items-center gap-0.5 lg:flex" aria-label="Primary">
          {NAV.map((item) => (
            <HeaderLink key={item.to} to={item.to} active={isActive(location.pathname, item.to)}>
              {item.label}
            </HeaderLink>
          ))}
          {!PUBLIC_SITE_ONLY ? (
            <HeaderLink to="/command" active={location.pathname.startsWith('/command')}>
              Operations
            </HeaderLink>
          ) : null}
        </nav>

        <div className="hidden items-center gap-2 lg:flex">
          <a
            href={GITHUB_REPO}
            target="_blank"
            rel="noreferrer noopener"
            aria-label="ResQNet on GitHub"
            className="inline-flex h-10 w-10 items-center justify-center rounded-full text-ink-secondary transition-colors hover:bg-base-800 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            <Github size={17} aria-hidden="true" />
          </a>
          <ButtonLink to="/demo" size="default">
            Enter the field lab
            <ArrowUpRight size={15} aria-hidden="true" />
          </ButtonLink>
        </div>

        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
          aria-controls="mobile-nav"
          className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-base-600 text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network lg:hidden"
        >
          {open ? <X size={18} aria-hidden="true" /> : <Menu size={18} aria-hidden="true" />}
          <span className="sr-only">{open ? 'Close navigation' : 'Open navigation'}</span>
        </button>
      </div>

      {open ? (
        <nav
          id="mobile-nav"
          className="border-t border-base-600 bg-base-950 px-5 pb-6 pt-4 lg:hidden"
          aria-label="Mobile"
        >
          <div className="flex flex-col">
            {NAV.map((item, index) => (
              <Link
                key={item.to}
                to={item.to}
                onClick={() => setOpen(false)}
                className="flex items-center justify-between border-b border-base-700 py-4 text-lg tracking-tight text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                <span>{item.label}</span>
                <span className="tabular text-[10px] text-ink-tertiary">
                  0{index + 1}
                </span>
              </Link>
            ))}
            {!PUBLIC_SITE_ONLY ? (
              <Link
                to="/command"
                onClick={() => setOpen(false)}
                className="flex items-center justify-between border-b border-base-700 py-4 text-lg tracking-tight text-ink-primary"
              >
                Operations
              </Link>
            ) : null}
          </div>
          <div className="mt-5 flex items-center gap-3">
            <ButtonLink to="/demo" className="flex-1" onClick={() => setOpen(false)}>
              Enter the field lab
              <ArrowUpRight size={15} aria-hidden="true" />
            </ButtonLink>
            <a
              href={GITHUB_REPO}
              target="_blank"
              rel="noreferrer noopener"
              className="inline-flex h-11 w-11 items-center justify-center rounded-full border border-base-600 text-ink-secondary"
              aria-label="ResQNet on GitHub"
            >
              <Github size={17} aria-hidden="true" />
            </a>
          </div>
        </nav>
      ) : null}
    </header>
  );
}

function HeaderLink({
  to,
  active,
  children,
}: {
  to: string;
  active: boolean;
  children: React.ReactNode;
}) {
  return (
    <Link
      to={to}
      className={
        'relative rounded-full px-3.5 py-2 text-[13px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network ' +
        (active ? 'text-ink-primary' : 'text-ink-secondary hover:text-ink-primary')
      }
    >
      {children}
      {active ? (
        <span
          className="absolute inset-x-3.5 -bottom-px h-px bg-network"
          aria-hidden="true"
        />
      ) : null}
    </Link>
  );
}

function isActive(pathname: string, to: string): boolean {
  if (to.startsWith('/#')) return false;
  return to === '/' ? pathname === '/' : pathname.startsWith(to);
}
