import { motion, useReducedMotion } from 'framer-motion';
import { ArrowRight, Github, RadioTower } from 'lucide-react';
import { Link } from 'react-router-dom';

import { Button } from '@/components/primitives/Button';
import { StatusChip } from '@/components/primitives/StatusChip';
import { MeshField } from './MeshField';

/**
 * Opening hero.
 *
 * <h3>Design intent</h3>
 * <p>Headline in all caps and letter-spaced because the situation it describes
 * is not conversational. The mesh visualisation sits behind the text rather
 * than beside it, so the claim and the mechanism occupy the same space and the
 * eye reads them together.
 *
 * <p>Every call to action goes somewhere real: the demo route renders a live
 * simulation run, the architecture anchor is a real section, and the repository
 * link is the actual repository. A landing page whose buttons do nothing is
 * worse than one with no buttons, because it spends the visitor's trust.
 */
export function Hero() {
  const reduced = useReducedMotion();

  const fade = reduced
    ? {}
    : {
        initial: { opacity: 0, y: 18 },
        animate: { opacity: 1, y: 0 },
        transition: { duration: 0.7, ease: [0.22, 1, 0.36, 1] as const },
      };

  return (
    <section className="relative isolate overflow-hidden border-b border-base-600">
      {/* The visualisation layer. A radial mask fades it at the edges so it reads
          as depth rather than as a diagram pasted behind the copy. */}
      <div className="pointer-events-none absolute inset-0 -z-10">
        <div
          className="absolute inset-0 opacity-[0.55]"
          style={{
            maskImage:
              'radial-gradient(120% 90% at 50% 20%, black 30%, transparent 78%)',
            WebkitMaskImage:
              'radial-gradient(120% 90% at 50% 20%, black 30%, transparent 78%)',
          }}
        >
          <MeshField count={11} intensity={1} className="h-full w-full" />
        </div>
        <div
          className="absolute inset-0"
          style={{
            background:
              'radial-gradient(90% 60% at 50% 0%, rgba(46,211,230,0.10), transparent 60%)',
          }}
        />
      </div>

      <div className="mx-auto w-full max-w-7xl px-5 pb-20 pt-16 sm:px-8 sm:pb-24 sm:pt-24 lg:pb-28 lg:pt-28">
        <motion.div {...fade} className="flex flex-col items-start gap-7">
          <StatusChip tone="emergency" pulse>
            Offline-first emergency network
          </StatusChip>

          <h1 className="max-w-[18ch] text-[2.5rem] font-bold leading-[0.95] tracking-tightest text-ink-primary sm:text-6xl lg:text-7xl">
            <span className="block">WHEN EVERYTHING</span>
            <span className="block">GOES OFFLINE,</span>
            <span className="block text-emergency">WE STAY CONNECTED.</span>
          </h1>

          <p className="prose-measure max-w-[54ch] text-base leading-relaxed text-ink-secondary sm:text-lg">
            A decentralised emergency communication network that helps nearby
            devices relay critical messages without relying on the internet. No
            cell tower, no account, no server. If someone is out of range, their
            phone carries the message for them.
          </p>

          <div className="flex flex-wrap items-center gap-3">
            <Link to="/command" className="inline-flex">
              <Button variant="primary">
                <RadioTower size={17} aria-hidden="true" />
                Explore live demo
              </Button>
            </Link>
            <a href="#architecture">
              <Button variant="secondary">
                View architecture
                <ArrowRight size={16} aria-hidden="true" />
              </Button>
            </a>
            <a
              href="https://github.com/Gautam-aman/ResQNet"
              target="_blank"
              rel="noreferrer noopener"
            >
              <Button variant="ghost">
                <Github size={17} aria-hidden="true" />
                GitHub repository
              </Button>
            </a>
          </div>

          <dl className="grid w-full max-w-2xl grid-cols-2 gap-x-6 gap-y-5 pt-6 sm:grid-cols-4">
            <HeroStat label="Hop budget" value="6" hint="messages travel this far" />
            <HeroStat label="Envelope size" value="16 KiB" hint="radio payload ceiling" />
            <HeroStat label="Cloud dependency" value="None" hint="mesh works standalone" />
            <HeroStat label="Duplicate alerts" value="0" hint="suppressed by message id" />
          </dl>
        </motion.div>
      </div>

      <div
        className="pointer-events-none absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-base-900 to-transparent"
        aria-hidden="true"
      />
    </section>
  );
}

function HeroStat({
  label,
  value,
  hint,
}: {
  label: string;
  value: string;
  hint: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="eyebrow">{label}</dt>
      <dd className="tabular text-2xl font-semibold text-ink-primary">{value}</dd>
      <dd className="text-xs leading-snug text-ink-tertiary">{hint}</dd>
    </div>
  );
}
