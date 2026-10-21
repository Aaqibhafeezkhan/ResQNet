import { ArrowRight, ArrowUpRight } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';

import { RelayStory } from '@/components/public/landing/RelayStory';
import { Reveal } from '@/components/primitives/Reveal';
import { SectionIntro } from '@/components/public/SectionIntro';
import { useReducedMotion } from '@/lib/useReducedMotion';

const STEPS = [
  {
    number: '01',
    tag: 'SIGN',
    title: 'Compose and sign',
    body: 'The sender writes the SOS, signs it with a device-held key, and writes it to durable storage before the first radio attempt.',
  },
  {
    number: '02',
    tag: 'STORE',
    title: 'Store before sending',
    body: 'No peer nearby? The message waits. A crashed process or a powered-off phone does not erase the request.',
  },
  {
    number: '03',
    tag: 'RELAY',
    title: 'Relay within limits',
    body: 'Phones in range verify the origin signature, decrement a hop budget and pass it on. Duplicate suppression keeps the flood bounded.',
  },
  {
    number: '04',
    tag: 'ACK',
    title: 'Deliver, then acknowledge',
    body: 'Only a signed acknowledgement from the intended recipient moves the state to delivered. A handoff is never called delivery.',
  },
];

const RULES = [
  {
    label: 'Peer discovery',
    body: 'Radios announce, then identities are verified. A device you have never met is unverified, not trusted.',
  },
  {
    label: 'Store and forward',
    body: 'A relay persists the envelope before acknowledging it to the previous carrier.',
  },
  {
    label: 'Duplicate suppression',
    body: 'Message identifiers, a byte bound and an age bound stop a long-running relay from growing without limit.',
  },
  {
    label: 'Hop budget',
    body: 'Every relay decrements a bounded budget. A message cannot circulate forever.',
  },
];

const STEP_NAMES = ['Compose and sign', 'Store before sending', 'Relay within limits', 'Deliver, then acknowledge'];

export function MechanismSection() {
  const reduced = useReducedMotion();
  const [activeStep, setActiveStep] = useState(0);
  const stepRefs = useRef<Array<HTMLLIElement | null>>([]);

  useEffect(() => {
    let frame = 0;
    const update = () => {
      frame = 0;
      const centre = window.innerHeight * 0.48;
      let next = 0;
      let best = Number.POSITIVE_INFINITY;
      stepRefs.current.forEach((element, index) => {
        if (!element) return;
        const rect = element.getBoundingClientRect();
        const distance = Math.abs(rect.top + rect.height / 2 - centre);
        if (distance < best) {
          best = distance;
          next = index;
        }
      });
      setActiveStep((current) => (current === next ? current : next));
    };
    const onScroll = () => {
      if (frame) return;
      frame = window.requestAnimationFrame(update);
    };
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  const jumpToStep = useCallback(
    (index: number) => {
      stepRefs.current[index]?.scrollIntoView({
        behavior: reduced ? 'auto' : 'smooth',
        block: 'center',
      });
      setActiveStep(index);
    },
    [reduced],
  );

  return (
    <section
      id="how"
      className="relative scroll-mt-20 overflow-hidden border-b border-white/[0.08] bg-base-900 py-24 sm:py-32 lg:py-40"
    >
      <div
        className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-network/25 to-transparent"
        aria-hidden="true"
      />
      <div className="container-page">
        <div className="flex flex-col gap-8 lg:flex-row lg:items-end lg:justify-between">
          <SectionIntro
            index="02"
            eyebrow="The mechanism"
            title="A message finds a way, one verified handoff at a time."
            body="No cloud, no account, no third party in the emergency path. Four ideas, applied carefully."
          />
          <a
            href="#architecture"
            className="group inline-flex shrink-0 items-center gap-2 text-[12px] font-medium uppercase tracking-[0.12em] text-network transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            See the architecture
            <ArrowRight
              size={14}
              aria-hidden="true"
              className="transition-transform duration-300 group-hover:translate-x-1"
            />
          </a>
        </div>

        <div className="mt-16 grid gap-10 lg:mt-24 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)] lg:gap-16 xl:gap-24">
          <div className="lg:sticky lg:top-28 lg:self-start">
            <Reveal>
              <RelayStory activeStep={activeStep} reduced={reduced} />
            </Reveal>
            <p className="mt-4 max-w-lg text-[10px] leading-relaxed text-ink-tertiary">
              The figure uses the field lab&apos;s routing model. It is a
              protocol illustration, not a radio measurement or evidence of
              physical range.
            </p>
          </div>

          <div>
            <ol className="border-t border-white/[0.08]">
              {STEPS.map((step, index) => {
                const active = activeStep === index;
                return (
                  <li
                    key={step.number}
                    ref={(element) => {
                      stepRefs.current[index] = element;
                    }}
                    className={
                      'group relative border-b border-white/[0.08] transition-colors duration-500 ' +
                      (active ? 'bg-white/[0.025]' : '')
                    }
                  >
                    {/*
                      A marker that slides to the active step. It makes the
                      current position in the sequence legible at a glance
                      instead of leaving the reader to infer it from a
                      background tint.
                    */}
                    <span
                      className={
                        'absolute left-0 top-0 h-full w-px origin-top bg-network transition-transform duration-700 ease-cinematic ' +
                        (active ? 'scale-y-100' : 'scale-y-0')
                      }
                      aria-hidden="true"
                    />
                    <button
                      type="button"
                      onClick={() => jumpToStep(index)}
                      aria-current={active ? 'step' : undefined}
                      className="group grid w-full grid-cols-[38px_1fr_auto] gap-4 px-1 py-7 text-left transition-[transform] duration-500 ease-cinematic hover:translate-x-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network sm:grid-cols-[56px_1fr_auto] sm:gap-6 sm:px-3 sm:py-9"
                    >
                      <span
                        className={
                          'tabular pt-1 text-[10px] font-semibold tracking-[0.16em] transition-colors ' +
                          (active ? 'text-network' : 'text-ink-tertiary')
                        }
                      >
                        {step.number}
                      </span>
                      <span>
                        <span className="flex items-center gap-3">
                          <span
                            className={
                              'rounded-full border px-2 py-0.5 text-[8px] font-semibold uppercase tracking-[0.16em] transition-colors ' +
                              (active
                                ? 'border-network/30 text-network'
                                : 'border-white/10 text-ink-tertiary')
                            }
                          >
                            {step.tag}
                          </span>
                          <span className="text-[15px] font-medium text-ink-primary sm:text-[17px]">
                            {step.title}
                          </span>
                        </span>
                        <span
                          className={
                            'mt-3 block max-w-xl text-[13px] leading-relaxed transition-colors sm:text-[14px] ' +
                            (active ? 'text-ink-secondary' : 'text-ink-tertiary')
                          }
                        >
                          {step.body}
                        </span>
                      </span>
                      <ArrowUpRight
                        size={15}
                        aria-hidden="true"
                        className={
                          'mt-1 transition-all duration-300 ' +
                          (active
                            ? 'text-network'
                            : 'text-ink-tertiary group-hover:text-ink-primary')
                        }
                      />
                    </button>
                  </li>
                );
              })}
            </ol>

            <div className="mt-10">
              <span className="eyebrow">Rules in force</span>
              <div className="mt-5 grid gap-x-8 gap-y-6 sm:grid-cols-2">
                {RULES.map((rule) => (
                  <Reveal key={rule.label}>
                    <h3 className="flex items-center gap-2 text-[12px] font-semibold uppercase tracking-[0.1em] text-ink-primary">
                      <span className="h-px w-4 bg-network/60" aria-hidden="true" />
                      {rule.label}
                    </h3>
                    <p className="mt-2 text-[12px] leading-relaxed text-ink-tertiary">
                      {rule.body}
                    </p>
                  </Reveal>
                ))}
              </div>
            </div>

            <div className="mt-10 flex flex-wrap items-center gap-3 border-t border-white/[0.08] pt-6">
              <span className="text-[10px] uppercase tracking-[0.16em] text-ink-tertiary">
                Current scene
              </span>
              <span className="text-[11px] text-ink-secondary" aria-live="polite">
                {STEP_NAMES[activeStep]}
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
