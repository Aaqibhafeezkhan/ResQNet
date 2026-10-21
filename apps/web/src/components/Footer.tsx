import { ArrowUpRight, Github, HeartPulse } from 'lucide-react';
import { Link } from 'react-router-dom';

import { PUBLIC_SITE_ONLY } from '@/app/buildMode';
import { BrandMark } from '@/components/public/BrandMark';
import {
  GITHUB_DEVICE_TESTING,
  GITHUB_ISSUES,
  GITHUB_LICENSE,
  GITHUB_RELEASES,
  GITHUB_REPO,
  GITHUB_SECURITY,
} from '@/lib/links';

const COLUMNS = [
  {
    title: 'Explore',
    links: [
      { label: 'The network', href: '/#network' },
      { label: 'How it works', href: '/#how' },
      { label: 'Field lab', href: '/demo' },
      { label: 'Architecture', href: '/#architecture' },
      { label: 'About the project', href: '/about' },
    ],
  },
  {
    title: 'Build with us',
    links: [
      { label: 'Contribute', href: '/contribute' },
      { label: 'Open issues', href: GITHUB_ISSUES },
      { label: 'Device testing', href: GITHUB_DEVICE_TESTING },
      { label: 'Security policy', href: GITHUB_SECURITY },
    ],
  },
  {
    title: 'Project',
    links: [
      { label: 'Latest release', href: GITHUB_RELEASES },
      { label: 'Source code', href: GITHUB_REPO },
      { label: 'License', href: GITHUB_LICENSE },
      { label: 'Android preview', href: '/download' },
    ],
  },
];

export function Footer() {
  return (
    <footer className="relative overflow-hidden border-t border-white/[0.08] bg-base-950">
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-network/30 to-transparent"
        aria-hidden="true"
      />
      <div className="container-page pb-8 pt-16 sm:pt-20">
        <div className="grid gap-14 lg:grid-cols-[1.1fr_1.4fr] lg:gap-20">
          <div className="max-w-md">
            <Link
              to="/"
              className="inline-flex items-center gap-3 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              <BrandMark className="h-9 w-9 text-network" />
              <span className="text-[15px] font-semibold tracking-[-0.02em] text-ink-primary">
                RESQNET
              </span>
            </Link>
            <p className="mt-6 text-[14px] leading-relaxed text-ink-secondary">
              A local-first emergency messaging research prototype. Nearby
              Android phones store, carry and relay signed SOS messages without
              a tower, a server or an account.
            </p>
            <div className="mt-6 flex flex-wrap gap-2">
              <a
                href={GITHUB_REPO}
                target="_blank"
                rel="noreferrer noopener"
                className="inline-flex items-center gap-2 rounded-full border border-white/10 px-3.5 py-2 text-[11px] text-ink-secondary transition-colors hover:border-network/40 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                <Github size={13} aria-hidden="true" />
                GitHub
                <ArrowUpRight size={11} aria-hidden="true" className="opacity-50" />
              </a>
              {!PUBLIC_SITE_ONLY ? (
                <Link
                  to="/command"
                  className="inline-flex items-center gap-2 rounded-full border border-white/10 px-3.5 py-2 text-[11px] text-ink-secondary transition-colors hover:border-network/40 hover:text-ink-primary"
                >
                  Operations console
                </Link>
              ) : null}
              <span className="inline-flex items-center gap-2 rounded-full border border-white/[0.06] px-3.5 py-2 text-[9px] uppercase tracking-[0.18em] text-ink-tertiary">
                <span className="h-1 w-1 rounded-full bg-delivered" aria-hidden="true" />
                Public build · no operational data
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-8 sm:grid-cols-3">
            {COLUMNS.map((column) => (
              <div key={column.title}>
                <h2 className="eyebrow">{column.title}</h2>
                <ul className="mt-5 space-y-3">
                  {column.links.map((link) => (
                    <li key={link.label}>
                      <FooterLink href={link.href}>{link.label}</FooterLink>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-16 select-none border-t border-white/[0.07] pt-10" aria-hidden="true">
          <p className="text-[clamp(3.4rem,12.5vw,11rem)] font-semibold leading-[0.76] tracking-[-0.06em] text-white/[0.045]">
            RESQNET
          </p>
        </div>

        <div className="mt-10 flex flex-col gap-5 border-t border-white/[0.07] pt-7 sm:flex-row sm:items-start sm:justify-between">
          <p className="flex max-w-xl items-start gap-2.5 text-[11px] leading-relaxed text-ink-tertiary">
            <HeartPulse
              size={14}
              className="mt-0.5 shrink-0 text-emergency"
              aria-hidden="true"
            />
            <span>
              Experimental software. Not certified to any safety standard and not
              a replacement for emergency services. If someone is in immediate
              danger, call your local emergency number.
            </span>
          </p>
          <p className="shrink-0 text-[10px] uppercase tracking-[0.16em] text-ink-tertiary">
            © {new Date().getFullYear()} ResQNet · MIT licensed
          </p>
        </div>
      </div>
    </footer>
  );
}

function FooterLink({ href, children }: { href: string; children: React.ReactNode }) {
  const external = href.startsWith('http');
  const className =
    'group inline-flex items-center gap-1.5 text-[13px] text-ink-secondary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network';

  if (external) {
    return (
      <a
        href={href}
        target="_blank"
        rel="noreferrer noopener"
        className={className}
      >
        {children}
        <ArrowUpRight
          size={11}
          aria-hidden="true"
          className="opacity-40 transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
        />
      </a>
    );
  }

  return (
    <Link to={href} className={className}>
      {children}
    </Link>
  );
}
