import { useCallback, useEffect, useMemo, useRef, useState, type RefObject } from 'react';

import { StaticNetwork } from './StaticNetwork';
import {
  HERO_LINKS,
  HERO_NODES,
  deriveHeroState,
  neighboursOf,
  type HeroNodeId,
  type HeroState,
} from './heroScene';

type Three = typeof import('three');

interface HeroCanvasProps {
  stageRef: RefObject<HTMLDivElement | null>;
  reduced: boolean;
}

type SceneStatus = 'pending' | 'live' | 'fallback';

export function HeroCanvas({ stageRef, reduced }: HeroCanvasProps) {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const sceneRef = useRef<import('./heroSceneRuntime').HeroSceneHandle | null>(null);
  const [status, setStatus] = useState<SceneStatus>('pending');
  const [state, setState] = useState<HeroState>(() => deriveHeroState(0));
  const [hoveredNode, setHoveredNode] = useState<HeroNodeId | null>(null);
  const [focusedNode, setFocusedNode] = useState<HeroNodeId | null>(null);
  const [hoveredLink, setHoveredLink] = useState<string | null>(null);
  const [selectedNode, setSelectedNode] = useState<HeroNodeId | null>(null);

  // Keep the latest state in a ref so the scroll handler never closes over a
  // stale value and never re-subscribes on every render.
  const stateRef = useRef(state);
  stateRef.current = state;

  useEffect(() => {
    if (reduced) {
      setStatus('fallback');
      return undefined;
    }

    const host = hostRef.current;
    if (!host) return undefined;

    // Probe before importing Three: on a browser with WebGL disabled the
    // static composition is the finished experience, not a failed canvas.
    const probe = document.createElement('canvas');
    const context = probe.getContext('webgl2') ?? probe.getContext('webgl');
    if (!context) {
      setStatus('fallback');
      return undefined;
    }

    let cancelled = false;
    let cleanup: (() => void) | undefined;

    void import('three')
      .then(async (THREE) => {
        if (cancelled) return;
        const handle = await createScene(THREE, host, {
          reduced,
          onHoverNode: setHoveredNode,
          onHoverLink: setHoveredLink,
          onSelectNode: setSelectedNode,
        });
        if (cancelled) {
          handle?.dispose();
          return;
        }
        if (!handle) {
          setStatus('fallback');
          return;
        }
        sceneRef.current = handle;
        cleanup = () => {
          handle.dispose();
          sceneRef.current = null;
        };
        setStatus('live');
      })
      .catch(() => {
        if (!cancelled) setStatus('fallback');
      });

    return () => {
      cancelled = true;
      cleanup?.();
      setStatus('pending');
    };
  }, [reduced]);

  // Feed scroll progress into the scene. The listener is passive and the
  // scene is only updated when the derived chapter actually changes, so a
  // long scroll does not cause a React render per frame.
  useEffect(() => {
    if (status !== 'live') return undefined;
    const stage = stageRef.current;
    if (!stage) return undefined;

    let frame = 0;
    let lastChapter = -1;

    const measure = () => {
      frame = 0;
      const rect = stage.getBoundingClientRect();
      const viewport = window.innerHeight;
      const scrollable = Math.max(1, rect.height - viewport);
      const travelled = Math.min(
        scrollable,
        Math.max(0, -rect.top),
      );
      const progress = Math.min(1, Math.max(0, travelled / scrollable));
      const next = deriveHeroState(progress);
      sceneRef.current?.setProgress(progress);
      if (next.chapterIndex !== lastChapter) {
        lastChapter = next.chapterIndex;
        setState(next);
      } else {
        stateRef.current = next;
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
  }, [stageRef, status]);

  const onSelectNode = useCallback((id: HeroNodeId | null) => {
    setSelectedNode((current) => (id === null ? null : current === id ? null : id));
  }, []);

  const nodeUnderPointer = hoveredNode;

  return (
    <div className="absolute inset-0 overflow-hidden">
      <StaticNetwork
        state={state}
        className={
          status === 'live'
            ? 'opacity-0 transition-opacity duration-1000'
            : 'opacity-100 transition-opacity duration-700'
        }
      />

      <div
        ref={hostRef}
        aria-hidden="true"
        data-hero-canvas={status === 'live' ? 'live' : 'idle'}
        className={
          'absolute inset-0 transition-opacity duration-[1400ms] ease-cinematic ' +
          (status === 'live' ? 'opacity-100' : 'opacity-0')
        }
      />

      {/*
        The accessible roster.

        Every device in the scene is also a real button. The canvas is
        decorative for assistive technology; this list carries the same
        information and the same interactions, so nothing in the scene is
        reachable only by seeing it.
      */}
      <div className="pointer-events-none absolute inset-0">
        <ul className="sr-only">
          {HERO_NODES.map((spec) => (
            <li key={spec.id}>
              <button
                type="button"
                onFocus={() => {
                  setFocusedNode(spec.id);
                  sceneRef.current?.setActiveNode(spec.id);
                }}
                onBlur={() => {
                  setFocusedNode(null);
                  sceneRef.current?.setActiveNode(null);
                }}
                onClick={() => onSelectNode(spec.id)}
                aria-pressed={selectedNode === spec.id}
              >
                {spec.name} — {spec.role}
              </button>
            </li>
          ))}
        </ul>
      </div>

      <div
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(80%_75%_at_50%_45%,transparent_0%,rgba(4,6,7,0.16)_62%,rgba(4,6,7,0.78)_100%)]"
        aria-hidden="true"
      />

      <HeroInspector
        nodeId={nodeUnderPointer ?? (hoveredLink ? null : focusedNode ?? selectedNode)}
        linkId={hoveredLink}
      />
    </div>
  );
}

/**
 * Creates the WebGL scene.
 *
 * The scene module is imported lazily inside the effect so that Three.js only
 * reaches the browser when the hero actually mounts and a WebGL context is
 * available.
 */
async function createScene(
  THREE: Three,
  host: HTMLDivElement,
  handlers: {
    reduced: boolean;
    onHoverNode: (id: HeroNodeId | null) => void;
    onHoverLink: (id: string | null) => void;
    onSelectNode: (id: HeroNodeId | null) => void;
  },
) {
  const runtime = await import('./heroSceneRuntime');
  return runtime.createHeroScene(THREE, host, {
    nodes: HERO_NODES,
    links: HERO_LINKS,
    derive: deriveHeroState,
    reduced: handlers.reduced,
    quality: runtime.resolveSceneQuality(),
    callbacks: {
      onHoverNode: handlers.onHoverNode,
      onHoverLink: handlers.onHoverLink,
      onSelectNode: handlers.onSelectNode,
    },
  });
}

/**
 * The inspector panel.
 *
 * Hovering a node shows what that device does; hovering a link shows the
 * route it belongs to. It is a real DOM panel, so it is readable by a screen
 * reader and it works on a touch screen where hover does not exist.
 */
function HeroInspector({
  nodeId,
  linkId,
}: {
  nodeId: HeroNodeId | null;
  linkId: string | null;
}) {
  const spec = useMemo(
    () => HERO_NODES.find((node) => node.id === nodeId) ?? null,
    [nodeId],
  );
  const link = useMemo(
    () => HERO_LINKS.find((entry) => entry.id === linkId) ?? null,
    [linkId],
  );

  if (!spec && !link) return null;

  const title = spec ? spec.name : link ? linkName(link.from, link.to) : '';
  const role = spec
    ? spec.role === 'sender'
      ? 'Sender'
      : spec.role === 'recipient'
        ? 'Recipient'
        : 'Relay'
    : 'Link';
  const body = spec
    ? spec.duty
    : link
      ? `${nameOf(link.from)} ↔ ${nameOf(link.to)}. A modelled hop between two devices. Link availability is a protocol state, not a measured radio range.`
      : '';

  return (
    <div
      className={
        'absolute bottom-5 left-5 z-20 w-[min(21rem,calc(100%-2.5rem))] ' +
        'rounded-2xl border border-white/10 bg-base-950/85 p-4 backdrop-blur-md ' +
        'shadow-lift transition-[opacity,transform] duration-300 ease-cinematic ' +
        'animate-[chapter-rise_0.5s_cubic-bezier(0.16,1,0.3,1)_both]'
      }
      aria-live="polite"
    >
      <div className="flex items-center gap-2.5">
        <span className="h-1.5 w-1.5 rounded-full bg-network" aria-hidden="true" />
        <span className="text-[9px] font-semibold uppercase tracking-[0.2em] text-ink-tertiary">
          {role}
        </span>
      </div>
      <p className="mt-2 text-[13px] font-medium text-ink-primary">{title}</p>
      <p className="mt-1.5 text-[11px] leading-relaxed text-ink-secondary">{body}</p>
      {spec ? (
        <p className="mt-3 text-[9px] uppercase tracking-[0.14em] text-ink-tertiary">
          Links to {neighboursOf(spec.id).map(nameOf).join(', ') || 'no peers'}
        </p>
      ) : null}
    </div>
  );
}

function nameOf(id: HeroNodeId): string {
  return HERO_NODES.find((node) => node.id === id)?.name ?? id;
}

function linkName(from: HeroNodeId, to: HeroNodeId): string {
  return `${nameOf(from)} ↔ ${nameOf(to)}`;
}
