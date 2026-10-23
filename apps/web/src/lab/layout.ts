/**
 * Deterministic layouts for recorded topologies.
 *
 * <p>The layout is a reading aid, not evidence. What the simulator recorded is
 * *which* nodes are linked; where they are drawn is a presentation choice. The
 * shapes below are chosen so the recorded topology is easy to read: a line is
 * drawn as a line, a ring as a ring, two clusters as two clusters.
 *
 * <p>Every layout is a pure function of the topology name, the node count and
 * the recorded edges, so the same report always draws the same picture.
 */

export interface LayoutNode {
  index: number;
  /** Position in the drawing viewport, in viewBox units. */
  x: number;
  y: number;
}

export const VIEW_WIDTH = 1000;
export const VIEW_HEIGHT = 620;

const PADDING = 90;

function circleLayout(count: number, radiusX = 380, radiusY = 240): LayoutNode[] {
  const nodes: LayoutNode[] = [];
  const centreX = VIEW_WIDTH / 2;
  const centreY = VIEW_HEIGHT / 2;
  for (let i = 0; i < count; i += 1) {
    // Start at the top and go clockwise, so node 0 is where a reader looks
    // first and the order is stable.
    const angle = (i / Math.max(1, count)) * Math.PI * 2 - Math.PI / 2;
    nodes.push({
      index: i,
      x: centreX + Math.cos(angle) * radiusX,
      y: centreY + Math.sin(angle) * radiusY,
    });
  }
  return nodes;
}

function lineLayout(count: number): LayoutNode[] {
  const nodes: LayoutNode[] = [];
  const span = VIEW_WIDTH - PADDING * 2;
  for (let i = 0; i < count; i += 1) {
    nodes.push({
      index: i,
      x: PADDING + (count === 1 ? span / 2 : (i / (count - 1)) * span),
      y: VIEW_HEIGHT / 2,
    });
  }
  return nodes;
}

function starLayout(count: number): LayoutNode[] {
  const nodes: LayoutNode[] = [];
  if (count === 0) return nodes;
  nodes.push({ index: 0, x: VIEW_WIDTH / 2, y: VIEW_HEIGHT / 2 });
  const ring = circleLayout(count - 1, 360, 230);
  for (let i = 0; i < ring.length; i += 1) {
    const node = ring[i];
    if (!node) continue;
    nodes.push({ index: i + 1, x: node.x, y: node.y });
  }
  return nodes;
}

function twoClusterLayout(count: number, edges: readonly [number, number][]): LayoutNode[] {
  // Use the recorded edges to split the nodes into two groups rather than
  // guessing at nodeCount / 2: the simulator's split is what the report says.
  const parent = new Map<number, number>();
  const find = (value: number): number => {
    let current = value;
    while (parent.get(current) !== current) {
      const next = parent.get(current);
      if (next === undefined) break;
      current = next;
    }
    return current;
  };
  for (let i = 0; i < count; i += 1) parent.set(i, i);
  for (const [from, to] of edges) {
    if (!parent.has(from) || !parent.has(to)) continue;
    parent.set(find(from), find(to));
  }
  const groups = new Map<number, number[]>();
  for (let i = 0; i < count; i += 1) {
    const root = find(i);
    const group = groups.get(root);
    if (group) group.push(i);
    else groups.set(root, [i]);
  }
  const ordered = [...groups.values()].sort((a, b) => (a[0] ?? 0) - (b[0] ?? 0));
  const nodes: LayoutNode[] = [];
  const columnWidth = VIEW_WIDTH / Math.max(1, ordered.length);
  ordered.forEach((group, groupIndex) => {
    const centreX = columnWidth * groupIndex + columnWidth / 2;
    const top = PADDING;
    const bottom = VIEW_HEIGHT - PADDING;
    group.forEach((nodeIndex, position) => {
      const y =
        group.length === 1 ? VIEW_HEIGHT / 2 : top + (position / (group.length - 1)) * (bottom - top);
      nodes.push({ index: nodeIndex, x: centreX, y });
    });
  });
  return nodes.sort((a, b) => a.index - b.index);
}

/**
 * Builds the layout for a report.
 *
 * @param topology the recorded shape name, e.g. `RING`
 * @param nodeCount the number of nodes the simulator used
 * @param edges the recorded topology edges
 */
export function layoutNodes(
  topology: string,
  nodeCount: number,
  edges: readonly [number, number][],
): LayoutNode[] {
  const count = Math.max(0, nodeCount);
  switch (topology.toUpperCase().replace(/[_-]/g, '')) {
    case 'LINE':
    case 'CHAIN':
      return lineLayout(count);
    case 'STAR':
    case 'HUB':
      return starLayout(count);
    case 'TWOCLUSTER':
    case 'PARTITION':
    case 'SPLIT':
      if (edges.length > 0) {
        const clusters = twoClusterLayout(count, edges);
        if (clusters.length === count) return clusters;
      }
      return circleLayout(count);
    default:
      // RING, MESH and RANDOM all read well as a circle; the difference is in
      // the chords, which are drawn from the recorded edges rather than from
      // the shape's name.
      return circleLayout(count);
  }
}
