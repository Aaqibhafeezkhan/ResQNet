/**
 * Deterministic mesh layout.
 *
 * Used by both the landing visualisation and the command centre topology so the
 * two agree on what a mesh looks like. Nodes are placed on a circle with a small
 * deterministic jitter: a perfect circle is obviously artificial, and a random
 * layout is unstable across renders.
 */

export interface MeshNode {
  id: string;
  /** Normalised position in the unit square, 0..1. */
  x: number;
  y: number;
  radius: number;
  role: 'origin' | 'relay' | 'destination';
}

export interface MeshLink {
  from: string;
  to: string;
  /** Visual weight; heavier links carry more traffic. */
  weight: number;
}

/** Small deterministic hash, so no RNG state leaks between renders. */
function hash(value: string): number {
  let result = 2166136261;
  for (let i = 0; i < value.length; i += 1) {
    result ^= value.charCodeAt(i);
    result = Math.imul(result, 16777619);
  }
  return (result >>> 0) / 4294967295;
}

/**
 * Builds a node layout for a count, deterministic for a given seed.
 *
 * Positions are computed once per count and reused, so the animation loop never
 * touches layout maths.
 */
export function buildNodes(count: number, seed = 1): MeshNode[] {
  const nodes: MeshNode[] = [];
  const golden = Math.PI * (3 - Math.sqrt(5));
  for (let i = 0; i < count; i += 1) {
    // Phyllotaxis: evenly spaced without looking like a clock face.
    const angle = i * golden + hash(`${seed}:angle:${i}`) * 0.35;
    const ring = 0.30 + (hash(`${seed}:ring:${i}`) * 0.14);
    const jitter = (hash(`${seed}:jitter:${i}`) - 0.5) * 0.09;
    const role =
      i === 0 ? 'origin' : i === count - 1 ? 'destination' : 'relay';
    nodes.push({
      id: `n${i}`,
      x: 0.5 + (Math.cos(angle) * (ring + jitter) + 0.5) * 0.5,
      y: 0.5 + (Math.sin(angle) * (ring + jitter) + 0.5) * 0.5,
      radius: role === 'origin' || role === 'destination' ? 0.035 : 0.024,
      role,
    });
  }
  return nodes;
}

/** Builds links: each node attaches to its nearest two neighbours. */
export function buildLinks(nodes: MeshNode[], seed = 1): MeshLink[] {
  const links: MeshLink[] = [];
  const seen = new Set<string>();
  for (const node of nodes) {
    const neighbours = nodes
      .filter((other) => other.id !== node.id)
      .map((other) => ({
        id: other.id,
        distance: Math.hypot(other.x - node.x, other.y - node.y),
      }))
      .sort((a, b) => a.distance - b.distance)
      .slice(0, 2);
    for (const neighbour of neighbours) {
      const key = [node.id, neighbour.id].sort().join('|');
      if (seen.has(key)) continue;
      seen.add(key);
      links.push({
        from: node.id,
        to: neighbour.id,
        weight: 0.5 + hash(`${seed}:${key}`) * 0.5,
      });
    }
  }
  return links;
}

/** Resolves a node by id. Returns undefined rather than throwing. */
export function nodeById(nodes: MeshNode[], id: string): MeshNode | undefined {
  return nodes.find((node) => node.id === id);
}

/** Formats a node id as the short label used in dashboard legends. */
export function nodeLabel(id: string): string {
  return id.replace(/^n/, 'node ');
}
