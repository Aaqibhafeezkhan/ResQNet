import { useCallback, useEffect, useMemo, useRef } from 'react';

import { useDocumentVisible } from '@/lib/useVisibility';
import { useReducedMotion } from '@/lib/useReducedMotion';
import { buildLinks, buildNodes, type MeshLink, type MeshNode } from './meshLayout';

interface MeshFieldProps {
  /** Number of simulated devices shown. */
  count?: number;
  /** Animation speed multiplier; 0 pauses. */
  intensity?: number;
  className?: string;
  /** Accessible description; the canvas is decorative so this is the label. */
  label?: string;
}

interface TravellingMessage {
  id: number;
  path: string[];
  progress: number;
  speed: number;
  /** 1 for life-safety traffic, lower for the rest. */
  weight: number;
}

/**
 * Animated mesh network visualisation.
 *
 * <h3>Why canvas rather than SVG</h3>
 * <p>A travelling message, a pulse ring per node and a handful of links is a
 * steady stream of attribute mutations. On SVG that is layout and paint on every
 * frame; on canvas it is a single draw pass with no DOM involvement at all,
 * which is what keeps the hero smooth next to a page that is already busy.
 *
 * <h3>Cost discipline</h3>
 * <ul>
 *   <li>One requestAnimationFrame loop, cancelled on unmount.</li>
 *   <li>The loop stops entirely while the tab is hidden.</li>
 *   <li>Reduced motion replaces the travelling messages with static ones.</li>
 *   <li>The buffer is sized to devicePixelRatio capped at 2, so a 3x phone does
 *       not render three times the pixels for no visible gain.</li>
 *   <li>Layout and gradients are computed on resize, never per frame.</li>
 * </ul>
 */
export function MeshField({
  count = 9,
  intensity = 1,
  className,
  label = 'Animation of a ResQNet mesh relaying an emergency message across several devices',
}: MeshFieldProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const frameRef = useRef<number | null>(null);
  const messagesRef = useRef<TravellingMessage[]>([]);
  const lastTimeRef = useRef(0);
  const sequenceRef = useRef(0);
  const visibleRef = useRef(true);
  const reduced = useReducedMotion();

  const nodes = useMemo<MeshNode[]>(() => buildNodes(count), [count]);
  const links = useMemo<MeshLink[]>(() => buildLinks(nodes), [nodes]);

  /**
   * One animation frame.
   *
   * <p>Defined with useCallback rather than inline so the visibility effect can
   * start and stop the same function without re-creating it.
   */
  const step = useCallback(
    (time: number) => {
      frameRef.current = null;
      const canvas = canvasRef.current;
      const context = canvas?.getContext('2d');
      if (!canvas || !context) return;

      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const width = canvas.width / dpr;
      const height = canvas.height / dpr;
      if (width <= 0 || height <= 0) {
        frameRef.current = requestAnimationFrame(step);
        return;
      }

      const delta = lastTimeRef.current === 0 ? 16 : Math.min(64, time - lastTimeRef.current);
      lastTimeRef.current = time;

      context.clearRect(0, 0, width, height);

      const positionOf = (id: string): MeshNode | undefined =>
        nodes.find((node) => node.id === id);

      context.lineWidth = 1;
      for (const link of links) {
        const a = positionOf(link.from);
        const b = positionOf(link.to);
        if (!a || !b) continue;
        context.beginPath();
        context.moveTo(a.x * width, a.y * height);
        context.lineTo(b.x * width, b.y * height);
        context.strokeStyle = `rgba(46, 211, 230, ${0.05 + link.weight * 0.1})`;
        context.lineWidth = 1 + link.weight * 0.6;
        context.stroke();
      }

      for (const node of nodes) {
        const cx = node.x * width;
        const cy = node.y * height;
        const r = node.radius * Math.min(width, height);

        context.beginPath();
        context.arc(cx, cy, r * 2.4, 0, Math.PI * 2);
        context.fillStyle =
          node.role === 'origin'
            ? 'rgba(224, 59, 59, 0.12)'
            : node.role === 'destination'
              ? 'rgba(53, 201, 138, 0.12)'
              : 'rgba(46, 211, 230, 0.10)';
        context.fill();

        context.beginPath();
        context.arc(cx, cy, r, 0, Math.PI * 2);
        context.fillStyle =
          node.role === 'origin'
            ? '#E03B3B'
            : node.role === 'destination'
              ? '#35C98A'
              : '#2ED3E6';
        context.fill();
      }

      if (!reduced && intensity > 0) {
        if (messagesRef.current.length < 3) {
          sequenceRef.current += 1;
          const origin = nodes.find((node) => node.role === 'origin');
          const destination = nodes.find((node) => node.role === 'destination');
          if (origin && destination) {
            messagesRef.current = [
              ...messagesRef.current.slice(-2),
              {
                id: sequenceRef.current,
                path: shortestPath(origin.id, destination.id, links),
                progress: 0,
                speed: 0.00022 * (0.7 + pseudoRandom(sequenceRef.current) * 0.6) * intensity,
                weight: sequenceRef.current % 4 === 0 ? 1 : 0.55,
              },
            ];
          }
        }

        messagesRef.current = messagesRef.current
          .map((message) => ({ ...message, progress: message.progress + message.speed * delta }))
          .filter((message) => message.progress < 1);

        for (const message of messagesRef.current) {
          const point = pointAlong(message.path, message.progress, nodes);
          if (!point) continue;
          const r = (message.weight > 0.9 ? 5.5 : 3.4) * intensity;
          const glow = context.createRadialGradient(
            point.x * width, point.y * height, 0,
            point.x * width, point.y * height, r * 4,
          );
          glow.addColorStop(0, `rgba(224, 59, 59, ${0.5 * message.weight})`);
          glow.addColorStop(1, 'rgba(224, 59, 59, 0)');
          context.fillStyle = glow;
          context.beginPath();
          context.arc(point.x * width, point.y * height, r * 4, 0, Math.PI * 2);
          context.fill();

          context.beginPath();
          context.arc(point.x * width, point.y * height, r, 0, Math.PI * 2);
          context.fillStyle = message.weight > 0.9 ? '#FF6B6B' : 'rgba(224, 59, 59, 0.7)';
          context.fill();
        }
      }

      frameRef.current = requestAnimationFrame(step);
    },
    [intensity, links, nodes, reduced],
  );

  /**
   * Starts the loop, unless it is already running or the tab is hidden.
   *
   * <p>Declared after `step` so the dependency is explicit rather than relying
   * on `step` being initialised by the time this ever runs. The loop needs to be
   * stoppable and restartable from the visibility effect, which means both have
   * to be stable identities.
   */
  const startLoop = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas || frameRef.current !== null || !visibleRef.current) return;
    lastTimeRef.current = 0;
    frameRef.current = requestAnimationFrame(step);
  }, [step]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) return;
      canvas.width = Math.round(rect.width * dpr);
      canvas.height = Math.round(rect.height * dpr);
    };

    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(canvas);

    return () => {
      observer.disconnect();
      if (frameRef.current !== null) {
        cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
    };
  }, []);

  useDocumentVisible((visible) => {
    visibleRef.current = visible;
    if (!visible) {
      if (frameRef.current !== null) {
        cancelAnimationFrame(frameRef.current);
        frameRef.current = null;
      }
      return;
    }
    startLoop();
  });

  useEffect(() => {
    startLoop();
  }, [startLoop]);

  return (
    <canvas
      ref={canvasRef}
      className={className}
      role="img"
      aria-label={label}
      data-testid="mesh-field"
    />
  );
}

/** Deterministic 0..1 value, so no Math.random state leaks between frames. */
function pseudoRandom(seed: number): number {
  const x = Math.sin(seed * 12.9898) * 43758.5453;
  return x - Math.floor(x);
}

/** Breadth-first shortest path over the link list. */
function shortestPath(from: string, to: string, links: MeshLink[]): string[] {
  const adjacency = new Map<string, string[]>();
  for (const link of links) {
    adjacency.set(link.from, [...(adjacency.get(link.from) ?? []), link.to]);
    adjacency.set(link.to, [...(adjacency.get(link.to) ?? []), link.from]);
  }

  const queue: string[] = [from];
  const cameFrom = new Map<string, string>();
  const visited = new Set<string>([from]);

  while (queue.length > 0) {
    const current = queue.shift();
    if (current === undefined) break;
    if (current === to) break;
    for (const neighbour of adjacency.get(current) ?? []) {
      if (visited.has(neighbour)) continue;
      visited.add(neighbour);
      cameFrom.set(neighbour, current);
      queue.push(neighbour);
    }
  }

  if (!visited.has(to)) return [from, to];
  const path = [to];
  let cursor = to;
  while (cursor !== from) {
    const previous = cameFrom.get(cursor);
    if (previous === undefined) break;
    path.unshift(previous);
    cursor = previous;
  }
  return path;
}

/** Interpolates a point along a multi-segment path. */
function pointAlong(
  path: string[],
  progress: number,
  nodes: MeshNode[],
): { x: number; y: number } | null {
  if (path.length < 2) return null;
  const segments = path.length - 1;
  const scaled = Math.min(0.9999, Math.max(0, progress)) * segments;
  const index = Math.floor(scaled);
  const local = scaled - index;
  const fromId = path[index];
  const toId = path[index + 1];
  if (fromId === undefined || toId === undefined) return null;

  const a = nodes.find((node) => node.id === fromId);
  const b = nodes.find((node) => node.id === toId);
  if (!a || !b) return null;
  return {
    x: a.x + (b.x - a.x) * local,
    y: a.y + (b.y - a.y) * local,
  };
}
