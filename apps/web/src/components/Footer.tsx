import { Github, HeartPulse } from 'lucide-react';
import { Link } from 'react-router-dom';

const COLUMNS = [
  {
    title: 'Product',
    links: [
      { label: 'Overview', href: '/' },
      { label: 'How it works', href: '/#how' },
      { label: 'Command centre', href: '/command' },
    ],
  },
  {
    title: 'Engineering',
    links: [
      { label: 'Architecture', href: '/#architecture' },
      { label: 'Run it locally', href: '/#run-it-locally' },
      { label: 'Source', href: 'https://github.com/Gautam-aman/ResQNet' },
    ],
  },
];

export function Footer() {
  return (
    <footer className="border-t border-base-600 py-14">
      <div className="mx-auto w-full max-w-7xl px-5 sm:px-8">
        <div className="flex flex-col gap-10 lg:flex-row lg:justify-between">
          <div className="max-w-sm">
            <div className="flex items-center gap-2.5">
              <HeartPulse size={18} className="text-emergency" aria-hidden="true" />
              <span className="text-sm font-semibold tracking-tight text-ink-primary">
                ResQNet
              </span>
            </div>
            <p className="mt-3 text-[13px] leading-relaxed text-ink-tertiary">
              Experimental decentralised emergency messaging. Not certified to
              any safety standard, and not a replacement for emergency services.
              If someone is in immediate danger, call your local emergency
              number.
            </p>
          </div>

          <nav className="flex gap-12 sm:gap-20" aria-label="Footer">
            {COLUMNS.map((column) => (
              <div key={column.title}>
                <h2 className="eyebrow">{column.title}</h2>
                <ul className="mt-4 space-y-2.5">
                  {column.links.map((link) => (
                    <li key={link.label}>
                      {link.href.startsWith('http') ? (
                        <a
                          href={link.href}
                          target="_blank"
                          rel="noreferrer noopener"
                          className="inline-flex items-center gap-1.5 text-[13px] text-ink-secondary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
                        >
                          {link.label}
                          <Github size={12} aria-hidden="true" className="opacity-60" />
                        </a>
                      ) : (
                        <Link
                          to={link.href}
                          className="text-[13px] text-ink-secondary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
                        >
                          {link.label}
                        </Link>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </nav>
        </div>

        <p className="mt-12 border-t border-base-700 pt-6 text-xs text-ink-tertiary">
          Built as a prototype. Verified behaviour is recorded in the repository
          README; everything else is labelled as such.
        </p>
      </div>
    </footer>
  );
}
