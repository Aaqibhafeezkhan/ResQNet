import { ArrowUpRight, Github, ShieldAlert } from 'lucide-react';

import { PageHero } from '@/components/public/PageHero';
import { ButtonAnchor, ButtonLink } from '@/components/primitives/Button';
import { Reveal } from '@/components/primitives/Reveal';
import {
  GITHUB_LICENSE,
  GITHUB_REPO,
  GITHUB_SECURITY,
  GITHUB_THREAT_MODEL,
} from '@/lib/links';
import { usePageMeta } from '@/lib/seo';

const PRINCIPLES = [
  {
    title: 'Local-first is a constraint',
    body: 'The emergency path cannot depend on a service that may be unavailable. No account, gateway or internet connection is required to sign, store or relay an SOS.',
  },
  {
    title: 'Delivery requires acknowledgement',
    body: 'A radio handoff proves bytes moved. Only a signed acknowledgement from the intended recipient proves a person received the message.',
  },
  {
    title: 'Failure must be legible',
    body: 'Queued, relayed, delivered and acknowledged are separate states. The interface never collapses uncertainty into a green checkmark.',
  },
  {
    title: 'Trust is a human act',
    body: 'A device identity becomes trusted after two people compare a short code in person. There is no trust-on-first-use shortcut.',
  },
  {
    title: 'Simulation is labelled',
    body: 'The simulator uses the same routing engine, but a modelled radio is not a physical one. Simulated results are never presented as field evidence.',
  },
];

export function AboutPage() {
  usePageMeta({
    title: 'About — ResQNet',
    description:
      'Why ResQNet exists, the principles behind its local-first mesh, and the limits it refuses to hide. An open-source emergency messaging research prototype.',
    path: '/about',
  });

  return (
    <>
      <PageHero
        eyebrow="About the project"
        title={
          <>
            A small network with a{' '}
            <span className="font-display font-normal italic">narrow promise.</span>
          </>
        }
        body="ResQNet does not promise to replace emergency services. It asks a smaller, more useful question: if the infrastructure disappears, can the phones already in the crowd carry a message a little farther?"
        aside={
          <div className="rounded-2xl border border-base-600 bg-base-900/70 p-5">
            <p className="eyebrow">Project shape</p>
            <dl className="mt-4 space-y-3 text-xs">
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">License</dt>
                <dd className="text-ink-secondary">MIT</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Platform</dt>
                <dd className="text-ink-secondary">Android · Java</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Simulator</dt>
                <dd className="text-ink-secondary">Deterministic</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-ink-tertiary">Status</dt>
                <dd className="text-ink-secondary">Experimental preview</dd>
              </div>
            </dl>
          </div>
        }
      >
        <ButtonLink to="/demo">
          Explore the field lab
          <ArrowUpRight size={15} aria-hidden="true" />
        </ButtonLink>
        <ButtonAnchor
          href={GITHUB_REPO}
          variant="secondary"
          target="_blank"
          rel="noreferrer noopener"
        >
          <Github size={15} aria-hidden="true" />
          Source code
        </ButtonAnchor>
      </PageHero>

      <section className="section-space border-b border-base-600">
        <div className="container-page grid gap-12 lg:grid-cols-[1.1fr_0.9fr] lg:gap-20">
          <div>
            <span className="eyebrow">The premise</span>
            <h2 className="display-balance mt-4 text-3xl font-semibold leading-[1.04] tracking-tightest text-ink-primary sm:text-4xl">
              Infrastructure fails first. The people around it do not.
            </h2>
            <div className="mt-6 space-y-4 text-sm leading-relaxed text-ink-secondary sm:text-[15px]">
              <p>
                A cell tower needs power, backhaul and a working core network. A
                cloud account needs a route to the cloud. In the first hours of a
                disaster, those dependencies are exactly what go missing.
              </p>
              <p>
                ResQNet explores a local alternative: a signed message held on a
                phone, handed to another phone, then another, until it reaches a
                field post. The radios are ordinary. The protocol is the work.
              </p>
              <p>
                The project is deliberately narrow. It is a research prototype,
                not a certified safety system. Its value is in making its
                assumptions, failure states and evidence gaps inspectable.
              </p>
            </div>
          </div>

          <Reveal delay={0.06} className="flex flex-col justify-between rounded-2xl border border-base-600 bg-base-850 p-6 sm:p-8">
            <div>
              <ShieldAlert size={19} className="text-pending" aria-hidden="true" />
              <h2 className="mt-5 font-display text-3xl leading-tight text-ink-primary">
                What it refuses to claim
              </h2>
            </div>
            <ul className="mt-8 space-y-3 text-[13px] leading-relaxed text-ink-secondary">
              <li>That simulation proves physical radio behaviour.</li>
              <li>That a relayed frame proves a person received it.</li>
              <li>That public SOS broadcasts are confidential. They are not encrypted; directed messages use HPKE.</li>
              <li>That a two-phone demonstration proves multi-hop routing.</li>
              <li>That this software is ready to be relied on in an emergency.</li>
            </ul>
            <a
              href={GITHUB_THREAT_MODEL}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-7 inline-flex min-h-[28px] items-center gap-1.5 text-xs font-medium text-pending transition-colors hover:text-ink-primary"
            >
              Read the full threat model
              <ArrowUpRight size={12} aria-hidden="true" />
            </a>
          </Reveal>
        </div>
      </section>

      <section className="section-space border-b border-base-600 bg-base-900">
        <div className="container-page">
          <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <span className="eyebrow">Concept film</span>
              <h2 className="display-balance mt-4 max-w-2xl text-3xl font-semibold leading-[1.04] tracking-tightest text-ink-primary sm:text-4xl">
                A 38-second visual explanation of the relay idea.
              </h2>
            </div>
            <span className="text-[10px] uppercase tracking-widest text-ink-tertiary">
              Concept animation · not device footage
            </span>
          </div>

          <Reveal delay={0.06} className="mt-8 overflow-hidden rounded-2xl border border-base-600 bg-base-950">
            <video
              className="aspect-video w-full"
              controls
              playsInline
              preload="none"
              poster="/media/resqnet-explainer-poster.png"
              aria-label="Concept animation showing a two-hop SOS relay between three phones"
            >
              <source src="/media/resqnet-explainer.mp4" type="video/mp4" />
              Your browser cannot play this concept animation. The relay idea is
              described in text above.
            </video>
            <div className="flex flex-col gap-2 border-t border-base-600 px-4 py-3 text-[11px] text-ink-tertiary sm:flex-row sm:items-center sm:justify-between">
              <span>Amina → relay → recipient. Modelled transport, not measured radio range.</span>
              <a
                href="/media/resqnet-explainer.mp4"
                className="inline-flex items-center gap-1.5 transition-colors hover:text-ink-primary"
              >
                Open source file
                <ArrowUpRight size={11} aria-hidden="true" />
              </a>
            </div>
          </Reveal>
        </div>
      </section>

      <section className="section-space border-b border-base-600">
        <div className="container-page">
          <div className="grid gap-12 lg:grid-cols-[0.75fr_1.25fr] lg:gap-20">
            <div>
              <span className="eyebrow">Principles</span>
              <h2 className="mt-4 max-w-md text-3xl font-semibold leading-[1.04] tracking-tightest text-ink-primary sm:text-4xl">
                The constraints are the design.
              </h2>
            </div>

            <ol className="divide-y divide-base-600 border-y border-base-600">
              {PRINCIPLES.map((principle, index) => (
                <Reveal
                  key={principle.title}
                  delay={index * 0.03}
                  className="grid grid-cols-[38px_1fr] gap-4 py-6 sm:grid-cols-[56px_1fr] sm:gap-6"
                >
                  <span className="tabular pt-1 text-[10px] font-semibold tracking-widest text-network">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <div>
                    <h3 className="text-base font-semibold text-ink-primary">{principle.title}</h3>
                    <p className="mt-2 max-w-2xl text-[13px] leading-relaxed text-ink-secondary">
                      {principle.body}
                    </p>
                  </div>
                </Reveal>
              ))}
            </ol>
          </div>
        </div>
      </section>

      <section className="section-space">
        <div className="container-page">
          <div className="mx-auto max-w-3xl text-center">
            <span className="eyebrow">Open by default</span>
            <h2 className="display-balance mt-4 text-3xl font-semibold leading-[1.04] tracking-tightest text-ink-primary sm:text-4xl">
              Read it, run it, break it.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-ink-secondary">
              The source, threat model, device testing procedure and release
              artifacts are public. The project is MIT licensed and has no
              bug bounty.
            </p>
            <div className="mt-7 flex flex-wrap justify-center gap-3">
              <ButtonAnchor
                href={GITHUB_REPO}
                target="_blank"
                rel="noreferrer noopener"
              >
                <Github size={15} aria-hidden="true" />
                Repository
              </ButtonAnchor>
              <ButtonAnchor
                href={GITHUB_LICENSE}
                variant="secondary"
                target="_blank"
                rel="noreferrer noopener"
              >
                MIT license
                <ArrowUpRight size={14} aria-hidden="true" />
              </ButtonAnchor>
              <ButtonAnchor
                href={GITHUB_SECURITY}
                variant="ghost"
                target="_blank"
                rel="noreferrer noopener"
              >
                Security policy
              </ButtonAnchor>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
