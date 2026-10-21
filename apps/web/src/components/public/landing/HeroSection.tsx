import { ArrowDown, ArrowUpRight, Github, RadioTower, ShieldCheck, WifiOff } from 'lucide-react';
import { useEffect, useRef, useState, type CSSProperties } from 'react';

import { HeroCanvas } from '@/components/public/hero/HeroCanvas';
import {
  HERO_CHAPTERS,
  deriveHeroState,
  type HeroState,
} from '@/components/public/hero/heroScene';
import { ButtonLink } from '@/components/primitives/Button';
import { GITHUB_REPO } from '@/lib/links';
import { useReducedMotion } from '@/lib/useReducedMotion';

const PROOF = [
  { icon: WifiOff, label: 'No internet required' },
  { icon: RadioTower, label: 'Local radios only' },
  { icon: ShieldCheck, label: 'No account in the SOS path' },
];

const DEPENDENCIES = [
  { label: 'Cell tower power', state: 'unavailable' },
  { label: 'Backhaul to the core network', state: 'unavailable' },
  { label: 'Cloud account', state: 'unreachable' },
  { label: 'Nearby radios', state: 'still here', live: true },
];

export function HeroSection() {
  const stageRef = useRef<HTMLDivElement | null>(null);
  const reduced = useReducedMotion();

  if (reduced) {
    return (
      <div id="network">
        <section className="relative isolate min-h-svh overflow-hidden pt-28">
          <HeroCanvas stageRef={stageRef} reduced />
          <HeroCopy reduced />
        </section>
        <StaticChapterStory />
        <ConnectionLost static />
      </div>
    );
  }

  return (
    <div id="network" ref={stageRef} className="relative isolate h-[420svh]">
      <div className="sticky top-0 h-svh overflow-hidden">
        <HeroCanvas stageRef={stageRef} reduced={false} />
        <div
          className="pointer-events-none absolute inset-0 bg-[linear-gradient(90deg,rgba(4,6,7,0.92)_0%,rgba(4,6,7,0.58)_32%,rgba(4,6,7,0.06)_66%,transparent_100%)]"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute inset-x-0 bottom-0 h-[42%] bg-gradient-to-t from-base-950 via-base-950/55 to-transparent"
          aria-hidden="true"
        />
        <ChapterOverlay />
      </div>

      <div className="pointer-events-none relative z-10 -mt-[100svh]">
        <section className="pointer-events-none relative flex min-h-svh flex-col">
          <HeroCopy reduced={false} />
        </section>
        <ChapterSpacer />
        <ConnectionLost />
      </div>
    </div>
  );
}

/**
 * The scroll-driven chapter overlay.
 *
 * It sits on top of the canvas and follows the same progress value, so the
 * prose and the 3D scene always describe the same moment.
 */
function ChapterOverlay() {
  const [state, setState] = useState<HeroState>(() => deriveHeroState(0));

  useEffect(() => {
    const stage = document.getElementById('network');
    if (!stage) return undefined;

    let frame = 0;
    let lastChapter = -1;

    const measure = () => {
      frame = 0;
      const rect = stage.getBoundingClientRect();
      const viewport = window.innerHeight;
      const scrollable = Math.max(1, rect.height - viewport);
      const progress = Math.min(
        1,
        Math.max(0, Math.min(scrollable, Math.max(0, -rect.top)) / scrollable),
      );
      const next = deriveHeroState(progress);
      if (next.chapterIndex !== lastChapter) {
        lastChapter = next.chapterIndex;
        setState(next);
      }
    };

    const onScroll = () => {
      if (frame) return;
      frame = window.requestAnimationFrame(measure);
    };

    measure();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);

  const chapter = HERO_CHAPTERS[state.chapterIndex] ?? HERO_CHAPTERS[0];
  if (!chapter) return null;

  /*
    The panel belongs to chapters two onwards. During chapter one the hero
    copy occupies the same corner, and showing both would read as a collision
    rather than as a chapter change, so the panel fades in as the copy leaves.
  */
  const visible = state.chapterIndex > 0;

  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-0 z-20">
      <div className="container-page pb-10 sm:pb-14">
        {/*
          The panel is anchored bottom-left. The hero copy owns the left side
          of the screen, so the panel only becomes visible once that copy has
          scrolled away — and the right side stays clear for the scene.
        */}
        <div className="flex justify-start">
          <div
            className="transition-opacity duration-500 ease-cinematic"
            style={{ opacity: visible ? 1 : 0 }}
          >
            <div
              key={chapter.id}
              className="w-full max-w-[27rem] rounded-2xl border border-white/10 bg-base-950/80 p-5 backdrop-blur-md sm:p-6"
              style={{ animation: 'chapter-rise 0.7s cubic-bezier(0.16, 1, 0.3, 1) both' }}
            >
              <div className="flex items-center gap-3">
                <span className="tabular text-[9px] font-semibold tracking-[0.2em] text-network">
                  {chapter.index}
                </span>
                <span className="h-px w-8 bg-network/40" aria-hidden="true" />
                <span className="eyebrow">{chapter.label}</span>
              </div>
              <h2 className="mt-4 text-[1.35rem] font-medium leading-[1.12] tracking-[-0.03em] text-ink-primary sm:text-[1.6rem]">
                {chapter.title}
              </h2>
              <p className="mt-3 text-[12px] leading-relaxed text-ink-secondary sm:text-[13px]">
                {chapter.body}
              </p>
              <div className="mt-5 flex items-center gap-2 border-t border-white/10 pt-3.5">
                <span className="h-1.5 w-1.5 rounded-full bg-network" aria-hidden="true" />
                <span className="tabular text-[9px] uppercase tracking-[0.14em] text-ink-tertiary">
                  {chapter.hud}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div className="mt-6 flex items-center gap-2">
          {HERO_CHAPTERS.map((entry, index) => (
            <span
              key={entry.id}
              className={
                'h-px flex-1 transition-colors duration-500 ' +
                (index <= state.chapterIndex ? 'bg-network/70' : 'bg-white/12')
              }
              aria-hidden="true"
            />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * The scroll runway.
 *
 * Empty space whose only job is to give the sticky canvas room to travel
 * through the five chapters.
 */
function ChapterSpacer() {
  return (
    <div
      aria-hidden="true"
      style={{ height: 'calc(320svh)' }}
      className="pointer-events-none"
    />
  );
}

/**
 * The complete story as static prose.
 *
 * Used when reduced motion is requested: the visitor still gets the whole
 * narrative, in reading order, with no scroll-jacking and no canvas.
 */
function StaticChapterStory() {
  return (
    <section
      id="chapters"
      className="relative border-y border-white/[0.08] bg-base-900 py-20 sm:py-28"
    >
      <div className="container-page">
        <span className="eyebrow">The relay, in five steps</span>
        <h2 className="display-balance mt-5 max-w-[18ch] text-[2.1rem] font-medium leading-[1] tracking-tightest text-ink-primary sm:text-[2.9rem]">
          What happens to a message when the network breaks.
        </h2>
        <ol className="mt-12 border-t border-white/[0.08]">
          {HERO_CHAPTERS.map((chapter) => (
            <li key={chapter.id} className="grid gap-4 border-b border-white/[0.08] py-8 sm:grid-cols-[84px_1fr] sm:gap-8">
              <div className="flex items-center gap-3 sm:block">
                <span className="tabular text-[10px] font-semibold tracking-[0.18em] text-network">
                  {chapter.index}
                </span>
                <span className="eyebrow sm:mt-2 sm:block">{chapter.label}</span>
              </div>
              <div>
                <h3 className="text-[1.35rem] font-medium leading-tight tracking-[-0.03em] text-ink-primary sm:text-[1.6rem]">
                  {chapter.title}
                </h3>
                <p className="mt-3 max-w-2xl text-[13px] leading-relaxed text-ink-secondary sm:text-[14px]">
                  {chapter.body}
                </p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

function HeroCopy({ reduced }: { reduced: boolean }) {
  return (
    /*
      `pointer-events-none` on the wrapper is deliberate.

      The wrapper is a full-viewport block that sits above the canvas, so
      without this it swallows every pointer event and the scene can never be
      hovered or clicked. The interactive content re-enables pointer events
      for itself.
    */
    <div className="container-page pointer-events-none relative flex min-h-svh flex-col pb-8 pt-28 sm:pt-32">
      <div
        className="pointer-events-none flex flex-wrap items-center justify-between gap-4"
        style={entrance(0.05, reduced)}
      >
        <span className="inline-flex items-center gap-2.5 rounded-full border border-white/10 bg-base-950/40 px-3 py-1.5 text-[9px] font-semibold uppercase tracking-[0.2em] text-ink-secondary backdrop-blur-sm">
          <span className="relative flex h-1.5 w-1.5" aria-hidden="true">
            <span className="absolute inline-flex h-full w-full animate-signal-pulse rounded-full bg-delivered" />
            <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-delivered" />
          </span>
          Open source · Android preview
        </span>
        <span className="hidden text-[9px] uppercase tracking-[0.22em] text-ink-tertiary md:block">
          Field note 001 / the network is already around you
        </span>
      </div>

      <div className="mt-auto grid gap-10 pt-20 lg:grid-cols-[minmax(0,1.2fr)_minmax(260px,0.8fr)] lg:items-end lg:pb-2">
        <div className="pointer-events-none max-w-[54rem]">
          <h1 className="text-shadow-deep text-[clamp(2.9rem,7.6vw,7.6rem)] font-medium uppercase leading-[0.82] tracking-[-0.055em] text-ink-primary">
            <span className="block overflow-hidden">
              <span className="block" style={entrance(0.12, reduced)}>
                When the signal
              </span>
            </span>
            <span className="block overflow-hidden pb-[0.06em]">
              <span
                className="block font-display font-normal italic normal-case tracking-[-0.035em] text-ink-primary"
                style={entrance(0.2, reduced)}
              >
                disappears.
              </span>
            </span>
          </h1>

          <p
            className="mt-7 flex max-w-2xl flex-wrap items-baseline gap-x-4 gap-y-2 text-[clamp(1rem,1.45vw,1.35rem)] font-medium uppercase tracking-[0.04em] text-ink-secondary"
            style={entrance(0.28, reduced)}
          >
            <span className="h-1.5 w-1.5 shrink-0 translate-y-[-0.2em] rounded-full bg-network" aria-hidden="true" />
            The message keeps moving.
          </p>

          <p
            className="prose-measure mt-6 max-w-xl text-[14px] leading-relaxed text-ink-secondary sm:text-[15px]"
            style={entrance(0.34, reduced)}
          >
            ResQNet turns nearby Android phones into a store-and-forward
            emergency network. A signed SOS can cross a relay, change direction
            and reach a field post without a tower, a server or an account.
          </p>

          <div
            className="pointer-events-auto mt-8 flex flex-wrap items-center gap-x-3 gap-y-3"
            style={entrance(0.4, reduced)}
          >
            <ButtonLink
              to="/demo"
              size="large"
              aria-label="Explore the demo: run the mesh lab"
              data-cursor="Open"
            >
              Explore the demo
              <ArrowUpRight
                size={16}
                aria-hidden="true"
                className="transition-transform duration-300 group-hover/button:-translate-y-0.5 group-hover/button:translate-x-0.5"
              />
            </ButtonLink>
            <ButtonLink
              to="/download"
              variant="secondary"
              size="large"
              aria-label="Download Android beta: get the Android build"
              data-cursor="Download"
            >
              Download Android beta
            </ButtonLink>
            <a
              href={GITHUB_REPO}
              target="_blank"
              rel="noreferrer noopener"
              aria-label="View ResQNet on GitHub"
              data-cursor="Source"
              className="inline-flex min-h-[48px] items-center gap-2 rounded-full px-3 text-[13px] text-ink-secondary transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              <Github size={15} aria-hidden="true" />
              View on GitHub
            </a>
          </div>
        </div>

        <div className="hidden lg:block">
          <div
            className="ml-auto max-w-[17rem] border-l border-white/10 pl-5"
            style={entrance(0.5, reduced)}
          >
            <p className="text-[9px] uppercase tracking-[0.22em] text-ink-tertiary">
              Scene 01 / the network
            </p>
            <p className="mt-3 text-[12px] leading-relaxed text-ink-secondary">
              Six nearby devices. One signed message. Signal paths change as
              devices move — the message waits when a route disappears.
            </p>
            <div className="mt-5 flex items-center gap-2 text-[9px] uppercase tracking-[0.2em] text-ink-tertiary">
              <span className="h-px w-8 bg-network/50" aria-hidden="true" />
              Illustrative topology
            </div>
          </div>
        </div>
      </div>

      <div
        className="pointer-events-none mt-12 flex flex-col gap-5 border-t border-white/10 pt-5 sm:flex-row sm:items-center sm:justify-between"
        style={entrance(0.55, reduced)}
      >
        <ul className="flex flex-wrap gap-x-5 gap-y-2">
          {PROOF.map((item) => {
            const Icon = item.icon;
            return (
              <li
                key={item.label}
                className="inline-flex items-center gap-2 text-[10px] font-medium uppercase tracking-[0.12em] text-ink-tertiary"
              >
                <Icon size={12} aria-hidden="true" />
                {item.label}
              </li>
            );
          })}
        </ul>
        <a
          href="#connection-lost"
          className="pointer-events-auto group inline-flex min-h-[32px] items-center gap-2 text-[9px] uppercase tracking-[0.22em] text-ink-tertiary transition-colors hover:text-ink-primary"
        >
          Scroll to lose the signal
          <ArrowDown
            size={12}
            aria-hidden="true"
            className="transition-transform duration-300 group-hover:translate-y-1"
          />
        </a>
      </div>
    </div>
  );
}

function ConnectionLost({ static: isStatic = false }: { static?: boolean }) {
  return (
    <section
      id="connection-lost"
      className={
        'relative flex min-h-svh scroll-mt-20 items-center overflow-hidden ' +
        (isStatic ? 'border-t border-white/[0.07]' : '')
      }
    >
      <div
        className="pointer-events-none absolute inset-0 bg-[linear-gradient(90deg,transparent_0%,rgba(4,6,7,0.55)_46%,rgba(4,6,7,0.96)_78%)]"
        aria-hidden="true"
      />
      <div className="container-page relative w-full py-24 sm:py-28">
        <div className="ml-auto max-w-[36rem] lg:mr-[4vw]">
          <div className="flex items-center gap-3.5">
            <span className="tabular text-[9px] font-semibold tracking-[0.2em] text-emergency">
              02
            </span>
            <span className="h-px w-8 bg-emergency/40" aria-hidden="true" />
            <span className="eyebrow">Connection lost</span>
          </div>

          <h2 className="display-balance mt-6 text-[2.4rem] font-medium leading-[0.96] tracking-tightest text-ink-primary sm:text-[3.4rem]">
            The tower goes quiet.{' '}
            <span className="font-display font-normal italic text-ink-secondary">
              The phones do not.
            </span>
          </h2>

          <p className="mt-6 max-w-lg text-[14px] leading-relaxed text-ink-secondary sm:text-[15px]">
            A cell tower needs power, backhaul and a working core network. A
            cloud account needs a route to the cloud. In the first hours of a
            disaster, those are exactly the dependencies that disappear.
          </p>

          <div className="mt-9 border-y border-white/10">
            {DEPENDENCIES.map((dependency) => (
              <div
                key={dependency.label}
                className="flex items-center justify-between gap-4 border-b border-white/[0.06] py-3.5 last:border-b-0"
              >
                <span className="text-[12px] text-ink-secondary">{dependency.label}</span>
                <span
                  className={
                    'inline-flex items-center gap-2 text-[9px] font-semibold uppercase tracking-[0.16em] ' +
                    (dependency.live ? 'text-delivered' : 'text-ink-tertiary')
                  }
                >
                  <span
                    className={
                      'h-1.5 w-1.5 rounded-full ' +
                      (dependency.live ? 'bg-delivered' : 'bg-ink-disabled')
                    }
                    aria-hidden="true"
                  />
                  {dependency.state}
                </span>
              </div>
            ))}
          </div>

          <p className="mt-4 text-[10px] uppercase tracking-[0.16em] text-ink-tertiary">
            Illustrative failure path · not live infrastructure monitoring
          </p>

          <p className="mt-8 max-w-md font-display text-[1.65rem] leading-[1.16] text-ink-primary sm:text-[2rem]">
            Infrastructure has a single point of failure. A mesh has many.
          </p>
        </div>
      </div>
    </section>
  );
}

function entrance(delay: number, reduced: boolean): CSSProperties | undefined {
  if (reduced) return undefined;
  return {
    animation: `rise-in 0.9s cubic-bezier(0.16, 1, 0.3, 1) ${delay}s both`,
  };
}
