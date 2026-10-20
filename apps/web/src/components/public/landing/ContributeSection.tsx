import { ArrowUpRight, Bug, FlaskConical, GitPullRequest, ShieldCheck } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';
import {
  GITHUB_CONTRIBUTING,
  GITHUB_DEVICE_TESTING,
  GITHUB_GOOD_FIRST_ISSUES,
  GITHUB_SECURITY,
  GITHUB_THREAT_MODEL,
} from '@/lib/links';

const PATHS = [
  {
    icon: FlaskConical,
    title: 'Test on real hardware',
    body: 'The largest evidence gap is multi-hop delivery across three or more physical phones. A careful device test is worth more than another mock.',
    href: GITHUB_DEVICE_TESTING,
    cta: 'Device testing procedure',
  },
  {
    icon: Bug,
    title: 'Reproduce a bug',
    body: 'A failing test or a minimal reproduction is the most useful bug report. Say whether you reproduced, read or inferred the failure.',
    href: GITHUB_GOOD_FIRST_ISSUES,
    cta: 'Good first issues',
  },
  {
    icon: ShieldCheck,
    title: 'Attack the model',
    body: 'Read the threat model, then try to break a specific assumption. Report vulnerabilities privately, never in a public issue.',
    href: GITHUB_SECURITY,
    cta: 'Security policy',
  },
  {
    icon: GitPullRequest,
    title: 'Change the protocol',
    body: 'Routing, validation, transport and documentation all need careful contributors. Open an issue before starting substantial work.',
    href: GITHUB_CONTRIBUTING,
    cta: 'Contributor guide',
  },
];

export function ContributeSection() {
  return (
    <section className="section-space border-b border-base-600 bg-base-900">
      <div className="container-page">
        <div className="grid gap-12 lg:grid-cols-[0.8fr_1.2fr] lg:gap-20">
          <div>
            <SectionIntro
              index="06"
              eyebrow="Contribute"
              title="Bring evidence, not certainty."
              body="ResQNet is safety-adjacent software. Correctness, reproducibility and honest evidence matter more than speed."
            />
            <Reveal delay={0.1} className="mt-8">
              <p className="eyebrow">Evidence labels used by this project</p>
              <div className="mt-3 flex flex-wrap gap-2">
                {['Reproduced', 'Read from source', 'Inferred'].map((label) => (
                  <span
                    key={label}
                    className="rounded-full border border-base-600 px-3 py-1.5 text-[11px] text-ink-secondary"
                  >
                    {label}
                  </span>
                ))}
              </div>
              <a
                href={GITHUB_THREAT_MODEL}
                target="_blank"
                rel="noreferrer noopener"
                className="mt-5 inline-flex items-center gap-1.5 text-xs font-medium text-network transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                Start with the threat model
                <ArrowUpRight size={12} aria-hidden="true" />
              </a>
            </Reveal>
          </div>

          <div className="grid gap-px overflow-hidden rounded-2xl border border-base-600 bg-base-600 sm:grid-cols-2">
            {PATHS.map((path, index) => {
              const Icon = path.icon;
              return (
                <Reveal
                  key={path.title}
                  delay={index * 0.04}
                  className="group flex flex-col bg-base-850 p-6 transition-colors hover:bg-base-800"
                >
                  <Icon size={17} className="text-network" aria-hidden="true" />
                  <h3 className="mt-4 text-base font-semibold text-ink-primary">{path.title}</h3>
                  <p className="mt-2 flex-1 text-[13px] leading-relaxed text-ink-secondary">
                    {path.body}
                  </p>
                  <a
                    href={path.href}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="mt-5 inline-flex items-center gap-1.5 text-xs font-medium text-ink-secondary transition-colors group-hover:text-network focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
                  >
                    {path.cta}
                    <ArrowUpRight size={12} aria-hidden="true" />
                  </a>
                </Reveal>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
