import { ArrowRight, Github, RadioTower, TerminalSquare } from 'lucide-react';
import { Link } from 'react-router-dom';

import { Button } from '@/components/primitives/Button';
import { Reveal } from '@/components/primitives/Reveal';

/**
 * Demo entry point.
 *
 * The dashboard runs against real gateway data when one is reachable and falls
 * back to a clearly-labelled deterministic simulation when it is not. Both paths
 * are offered here so neither is presented as the other.
 */
export function DemoCta() {
  return (
    <section className="py-20 sm:py-28">
      <div className="mx-auto w-full max-w-7xl px-5 sm:px-8">
        <Reveal className="surface-raised relative overflow-hidden p-8 sm:p-12">
          <div
            className="pointer-events-none absolute inset-0 -z-10 opacity-40"
            style={{
              background:
                'radial-gradient(70% 100% at 15% 0%, rgba(46,211,230,0.16), transparent 62%)',
            }}
            aria-hidden="true"
          />

          <div className="max-w-2xl">
            <span className="eyebrow">Try it</span>
            <h2 className="mt-3 text-3xl font-semibold leading-[1.08] tracking-tightest text-ink-primary sm:text-4xl">
              Watch a mesh route a message across devices that never touch the
              internet
            </h2>
            <p className="prose-measure mt-4 text-[15px] leading-relaxed text-ink-secondary">
              The command centre connects to the coordination gateway when one is
              running. When it is not, it renders a deterministic simulation run
              with every node and event labelled as simulated, so the two are
              never confused.
            </p>

            <div className="mt-8 flex flex-wrap gap-3">
              <Link to="/command" className="inline-flex">
                <Button variant="primary">
                  <RadioTower size={17} aria-hidden="true" />
                  Open the command centre
                </Button>
              </Link>
              <a href="#run-it-locally">
                <Button variant="secondary">
                  <TerminalSquare size={17} aria-hidden="true" />
                  Run it locally
                </Button>
              </a>
              <a
                href="https://github.com/Gautam-aman/ResQNet"
                target="_blank"
                rel="noreferrer noopener"
              >
                <Button variant="ghost">
                  <Github size={17} aria-hidden="true" />
                  Source
                  <ArrowRight size={14} aria-hidden="true" />
                </Button>
              </a>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
