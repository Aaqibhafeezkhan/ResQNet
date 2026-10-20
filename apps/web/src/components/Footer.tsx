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
    <footer className="border-t border-base-600 bg-base-950">
      <div className="container-page py-14 sm:py-16">
        <div className="grid gap-12 lg:grid-cols-[1.2fr_1.8fr]">
          <div className="max-w-md">
            <Link
              to="/"
              className="inline-flex items-center gap-3 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              <BrandMark className="h-9 w-9 text-network" />
              <span className="text-[15px] font-semibold tracking-tight text-ink-primary">
                ResQNet
              </span>
            </Link>
            <p className="mt-5 text-sm leading-relaxed text-ink-secondary">
              A local-first emergency messaging research prototype. Nearby
              Android phones store, carry and relay signed SOS messages without
              a tower, a server or an account.
            </p>
            <div className="mt-6 flex flex-wrap gap-2">
              <a
                href={GITHUB_REPO}
                target="_blank"
                rel="noreferrer noopener"
                className="inline-flex items-center gap-2 rounded-full border border-base-600 px-3.5 py-2 text-xs text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                <Github size={14} aria-hidden="true" />
                GitHub
                <ArrowUpRight size={12} aria-hidden="true" className="opacity-50" />
              </a>
              {!PUBLIC_SITE_ONLY ? (
                <Link
                  to="/command"
                  className="inline-flex items-center gap-2 rounded-full border border-base-600 px-3.5 py-2 text-xs text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary"
                >
                  Operations console
                </Link>
              ) : null}
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

        <div className="mt-14 flex flex-col gap-5 border-t border-base-600 pt-7 sm:flex-row sm:items-start sm:justify-between">
          <p className="flex max-w-xl items-start gap-2.5 text-xs leading-relaxed text-ink-tertiary">
            <HeartPulse
              size={15}
              className="mt-0.5 shrink-0 text-emergency"
              aria-hidden="true"
            />
            <span>
              Experimental software. Not certified to any safety standard and not
              a replacement for emergency services. If someone is in immediate
              danger, call your local emergency number.
            </span>
          </p>
          <p className="shrink-0 text-xs text-ink-tertiary">
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
    'inline-flex items-center gap-1.5 text-[13px] text-ink-secondary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network';

  if (external) {
    return (
      <a
        href={href}
        target="_blank"
        rel="noreferrer noopener"
        className={className}
      >
        {children}
        <ArrowUpRight size={11} aria-hidden="true" className="opacity-45" />
      </a>
    );
  }

  return (
    <Link to={href} className={className}>
      {children}
    </Link>
  );
}
