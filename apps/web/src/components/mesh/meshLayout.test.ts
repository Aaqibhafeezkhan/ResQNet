import { describe, expect, it } from 'vitest';

import { buildLinks, buildNodes, nodeById } from './meshLayout';

/**
 * The layout backs two separate visualisations, so it has to be stable: the
 * same count and seed must always produce the same picture, or a dashboard
 * review would show a different network each time it loaded.
 */
describe('meshLayout', () => {
  it('produces the requested number of nodes', () => {
    expect(buildNodes(9)).toHaveLength(9);
    expect(buildNodes(20)).toHaveLength(20);
  });

  it('is deterministic for a given count and seed', () => {
    expect(buildNodes(12, 7)).toEqual(buildNodes(12, 7));
    expect(buildLinks(buildNodes(12, 7))).toEqual(buildLinks(buildNodes(12, 7)));
  });

  it('changes with the seed', () => {
    expect(buildNodes(12, 1)).not.toEqual(buildNodes(12, 2));
  });

  it('keeps every node inside the unit square', () => {
    for (const node of buildNodes(24)) {
      expect(node.x).toBeGreaterThanOrEqual(0);
      expect(node.x).toBeLessThanOrEqual(1);
      expect(node.y).toBeGreaterThanOrEqual(0);
      expect(node.y).toBeLessThanOrEqual(1);
    }
  });

  it('assigns exactly one origin and one destination', () => {
    const nodes = buildNodes(10);
    expect(nodes.filter((node) => node.role === 'origin')).toHaveLength(1);
    expect(nodes.filter((node) => node.role === 'destination')).toHaveLength(1);
  });

  it('gives every node at least two neighbours so the mesh is connected', () => {
    const nodes = buildNodes(16);
    for (const node of nodes) {
      const degree = linksOf(nodes).filter(
        (link) => link.from === node.id || link.to === node.id,
      ).length;
      expect(degree).toBeGreaterThanOrEqual(2);
    }
  });

  it('never emits a duplicate or self-referential link', () => {
    const nodes = buildNodes(12);
    const keys = new Set<string>();
    for (const link of buildLinks(nodes)) {
      expect(link.from).not.toBe(link.to);
      const key = [link.from, link.to].sort().join('|');
      expect(keys.has(key)).toBe(false);
      keys.add(key);
    }
  });

  it('looks a node up by id and returns undefined rather than throwing', () => {
    const nodes = buildNodes(6);
    expect(nodeById(nodes, 'n0')?.id).toBe('n0');
    expect(nodeById(nodes, 'missing')).toBeUndefined();
  });
});

function linksOf(nodes: ReturnType<typeof buildNodes>) {
  return buildLinks(nodes);
}
