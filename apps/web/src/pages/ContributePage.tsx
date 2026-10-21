import { ArrowUpRight, Bug, FlaskConical, GitPullRequest, ShieldCheck } from 'lucide-react';

import { PageHero } from '@/components/public/PageHero';
import { SafetyNote } from '@/components/public/SafetyNote';
import { Reveal } from '@/components/primitives/Reveal';
import {
  GITHUB_CONTRIBUTING,
  GITHUB_DEVICE_TESTING,
  GITHUB_GOOD_FIRST_ISSUES,
  GITHUB_ISSUES,
  GITHUB_SECURITY,
  GITHUB_THREAT_MODEL,
} from '@/lib/links';
import { usePageMeta } from '@/lib/seo';

const PATHS = [
  {
    icon: FlaskConical,
    title: 'Test the Android build on hardware',
    body: 'The project’s largest evidence gap is multi-hop delivery across three or more physical devices. Follow the procedure, record the devices and say exactly what you observed.',
    href: GITHUB_DEVICE_TESTING,
    label: 'Device testing procedure',
  },
  {
    icon: Bug,
    title: 'Reproduce a failure',
    body: 'A failing test or a minimal reproduction is more useful than a description alone. Include the commit, environment and the smallest sequence that shows the problem.',
    href: GITHUB_ISSUES,
    label: 'Open issues',
  },
  {
    icon: ShieldCheck,
    title: 'Break an assumption',
    body: 'Read the threat model before changing trust, routing or transport. Security reports go through a private channel, not a public issue.',
    href: GITHUB_SECURITY,
    label: 'Security policy',
  },
  {
    icon: GitPullRequest,
    title: 'Improve the protocol or the docs',
    body: 'Routing, validation, transport, tests and documentation all need careful work. Open an issue before starting a substantial change.',
    href: GITHUB_CONTRIBUTING,
    label: 'Contributor guide',
  },
];

export function ContributePage() {
  usePageMeta({
    title: 'Contribute — ResQNet',
    description:
      'Help test, break and improve ResQNet. Real hardware evidence, reproducible failures and careful security review are the most valuable contributions.',
    path: '/contribute',
  });

  return (
    <>
      <PageHero
        eyebrow="Contribute"
        title={
          <>
            Bring evidence, not{' '}
            <span className="font-display font-normal italic">certainty.</span>
          </>
        }
        body="ResQNet is safety-adjacent software. A routing bug can send a rescue request to the wrong place, and a UI bug can make a responder believe the mesh is healthy when it is not."
        aside={
          <div className="rounded-2xl border border-base-600 bg-base-900/70 p-5">
            <p className="eyebrow">Before you start</p>
            <ul className="mt-4 space-y-2.5 text-xs leading-relaxed text-ink-secondary">
              <li>Read the README and threat model.</li>
              <li>Open an issue before substantial work.</li>
              <li>Never disclose a vulnerability publicly.</li>
              <li>Label your evidence honestly.</li>
            </ul>
          </div>
        }
      >
        <a
          href={GITHUB_GOOD_FIRST_ISSUES}
          target="_blank"
          rel="noreferrer noopener"
          className="inline-flex min-h-[44px] items-center gap-2 rounded-full bg-network px-5 text-sm font-medium text-base-950 transition-colors hover:bg-[#66e6e9] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network focus-visible:ring-offset-2 focus-visible:ring-offset-base-900"
        >
          Browse good first issues
          <ArrowUpRight size={15} aria-hidden="true" />
        </a>
      </PageHero>

      <section className="section-space border-b border-base-600">
        <div className="container-page">
          <span className="eyebrow">Where help matters most</span>
          <div className="mt-7 grid gap-px overflow-hidden rounded-2xl border border-base-600 bg-base-600 md:grid-cols-2">
            {PATHS.map((path, index) => {
              const Icon = path.icon;
              return (
                <Reveal
                  key={path.title}
                  delay={index * 0.04}
                  className="group flex flex-col bg-base-850 p-6 transition-colors hover:bg-base-800 sm:p-8"
                >
                  <Icon size={18} className="text-network" aria-hidden="true" />
                  <h2 className="mt-5 text-lg font-semibold tracking-tight text-ink-primary">
                    {path.title}
                  </h2>
                  <p className="mt-2.5 flex-1 text-[13px] leading-relaxed text-ink-secondary">
                    {path.body}
                  </p>
                  <a
                    href={path.href}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="mt-6 inline-flex w-fit items-center gap-1.5 text-xs font-medium text-ink-secondary transition-colors group-hover:text-network focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
                  >
                    {path.label}
                    <ArrowUpRight size={12} aria-hidden="true" />
                  </a>
                </Reveal>
              );
            })}
          </div>
        </div>
      </section>

      <section className="section-space bg-base-900">
        <div className="container-page grid gap-10 lg:grid-cols-[0.9fr_1.1fr] lg:gap-20">
          <div>
            <span className="eyebrow">Evidence protocol</span>
            <h2 className="display-balance mt-4 text-3xl font-semibold leading-[1.04] tracking-tightest text-ink-primary sm:text-4xl">
              Three words that keep a safety project honest.
            </h2>
            <p className="prose-measure mt-5 text-sm leading-relaxed text-ink-secondary">
              Several defects in this codebase were found by reading it rather
              than running it. That is a valid contribution, but it is a
              different kind of evidence.
            </p>
            <SafetyNote className="mt-6" title="Security reports">
              Do not open a public issue, pull request or discussion containing
              exploit details. Use the private channel described in SECURITY.md.
            </SafetyNote>
            <a
              href={GITHUB_THREAT_MODEL}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-5 inline-flex min-h-[28px] items-center gap-1.5 text-xs font-medium text-network transition-colors hover:text-ink-primary"
            >
              Read the threat model
              <ArrowUpRight size={12} aria-hidden="true" />
            </a>
          </div>

          <div className="divide-y divide-base-600 border-y border-base-600">
            {[
              {
                label: 'Reproduced',
                body: 'You ran it and observed the result. Include the environment, commit and steps.',
              },
              {
                label: 'Read from source',
                body: 'You found the issue in code or documentation but did not execute it. Point to the exact lines.',
              },
              {
                label: 'Inferred',
                body: 'You believe a failure is possible from the design. State the assumption and what would confirm it.',
              },
            ].map((item, index) => (
              <Reveal
                key={item.label}
                delay={index * 0.04}
                className="grid grid-cols-[32px_1fr] gap-4 py-6"
              >
                <span className="tabular pt-0.5 text-[11px] text-ink-tertiary">
                  {String(index + 1).padStart(2, '0')}
                </span>
                <div>
                  <h3 className="font-display text-2xl text-ink-primary">{item.label}</h3>
                  <p className="mt-1.5 text-[13px] leading-relaxed text-ink-secondary">
                    {item.body}
                  </p>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
