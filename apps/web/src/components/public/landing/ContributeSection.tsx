import { ArrowUpRight, Bug, FlaskConical, GitPullRequest, Github, ShieldCheck } from 'lucide-react';

import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';
import {
  GITHUB_CONTRIBUTING,
  GITHUB_DEVICE_TESTING,
  GITHUB_GOOD_FIRST_ISSUES,
  GITHUB_REPO,
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
    <section
      id="contribute"
      className="relative scroll-mt-20 overflow-hidden border-b border-white/[0.08] bg-base-900 py-24 sm:py-32 lg:py-40"
    >
      <div className="container-page">
        <div className="grid gap-16 lg:grid-cols-[minmax(0,0.86fr)_minmax(0,1.14fr)] lg:gap-24">
          <div className="min-w-0 lg:sticky lg:top-32 lg:self-start">
            <SectionIntro
              index="06"
              eyebrow="Open-source community"
              title="Bring evidence, not certainty."
              body="ResQNet is safety-adjacent software. Correctness, reproducibility and honest evidence matter more than speed."
            />

            <div className="mt-10 flex flex-wrap gap-2">
              {['Reproduced', 'Read from source', 'Inferred'].map((label) => (
                <span
                  key={label}
                  className="rounded-full border border-white/10 px-3 py-1.5 text-[10px] uppercase tracking-[0.1em] text-ink-secondary"
                >
                  {label}
                </span>
              ))}
            </div>

            <a
              href={GITHUB_THREAT_MODEL}
              target="_blank"
              rel="noreferrer noopener"
              className="group mt-7 inline-flex items-center gap-2 text-[11px] font-medium uppercase tracking-[0.1em] text-network transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              Start with the threat model
              <ArrowUpRight
                size={12}
                aria-hidden="true"
                className="transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
              />
            </a>

            <div className="mt-12 overflow-hidden rounded-xl border border-white/[0.08] bg-base-950">
              <div className="flex items-center justify-between border-b border-white/[0.07] px-4 py-2.5">
                <span className="text-[8px] uppercase tracking-[0.18em] text-ink-tertiary">
                  Clone the source
                </span>
                <span className="flex gap-1" aria-hidden="true">
                  <span className="h-1.5 w-1.5 rounded-full bg-white/10" />
                  <span className="h-1.5 w-1.5 rounded-full bg-white/10" />
                  <span className="h-1.5 w-1.5 rounded-full bg-white/10" />
                </span>
              </div>
              <div className="flex items-center gap-2.5 px-4 py-4 font-mono text-[11px] text-ink-secondary">
                <span className="text-network" aria-hidden="true">
                  $
                </span>
                <span className="min-w-0 truncate">git clone {GITHUB_REPO}.git</span>
              </div>
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              <a
                href={GITHUB_REPO}
                target="_blank"
                rel="noreferrer noopener"
                className="inline-flex min-h-[44px] items-center gap-2 rounded-full border border-white/15 px-4 text-[11px] font-medium text-ink-primary transition-colors hover:border-network/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                <Github size={14} aria-hidden="true" />
                GitHub repository
              </a>
              <a
                href={GITHUB_CONTRIBUTING}
                target="_blank"
                rel="noreferrer noopener"
                className="inline-flex min-h-[44px] items-center gap-2 rounded-full px-3 text-[11px] text-ink-secondary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
              >
                Contributing guidelines
                <ArrowUpRight size={12} aria-hidden="true" />
              </a>
            </div>
          </div>

          <div className="min-w-0">
            <div className="border-t border-white/[0.08]">
              {PATHS.map((path, index) => {
                const Icon = path.icon;
                return (
                  <Reveal
                    key={path.title}
                    delay={index * 0.03}
                    className="group border-b border-white/[0.08]"
                  >
                    <a
                      href={path.href}
                      target="_blank"
                      rel="noreferrer noopener"
                      className="grid grid-cols-[38px_1fr_auto] gap-4 py-7 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network sm:grid-cols-[52px_1fr_auto] sm:gap-6 sm:py-9"
                    >
                      <span className="grid h-9 w-9 place-items-center rounded-full border border-white/10 text-ink-secondary transition-colors group-hover:border-network/40 group-hover:text-network">
                        <Icon size={15} aria-hidden="true" />
                      </span>
                      <span>
                        <span className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
                          <span className="text-[19px] font-medium tracking-[-0.02em] text-ink-primary sm:text-[23px]">
                            {path.title}
                          </span>
                          <span className="text-[8px] font-semibold uppercase tracking-[0.18em] text-ink-tertiary">
                            0{index + 1}
                          </span>
                        </span>
                        <span className="mt-2 block max-w-xl text-[13px] leading-relaxed text-ink-secondary">
                          {path.body}
                        </span>
                        <span className="mt-4 inline-flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-[0.12em] text-network">
                          {path.cta}
                          <ArrowUpRight
                            size={11}
                            aria-hidden="true"
                            className="transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5"
                          />
                        </span>
                      </span>
                      <ArrowUpRight
                        size={18}
                        aria-hidden="true"
                        className="mt-1 text-ink-tertiary transition-all duration-300 group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-network"
                      />
                    </a>
                  </Reveal>
                );
              })}
            </div>

            <Reveal delay={0.05} className="mt-10 flex items-center gap-3 text-[10px] uppercase tracking-[0.16em] text-ink-tertiary">
              <span className="h-px w-8 bg-white/15" aria-hidden="true" />
              No fabricated contributor counts. The repository is the record.
            </Reveal>
          </div>
        </div>
      </div>
    </section>
  );
}
