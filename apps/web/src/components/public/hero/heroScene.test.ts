import { describe, expect, it } from 'vitest';

import {
  HERO_CHAPTERS,
  HERO_LINKS,
  HERO_NODES,
  chapterIndexFor,
  deriveHeroState,
  heroLinkId,
  neighboursOf,
} from './heroScene';

describe('heroScene', () => {
  it('maps scroll progress onto the five chapters', () => {
    expect(chapterIndexFor(0)).toBe(0);
    expect(chapterIndexFor(0.4)).toBe(1);
    expect(chapterIndexFor(0.6)).toBe(2);
    expect(chapterIndexFor(0.75)).toBe(3);
    expect(chapterIndexFor(0.95)).toBe(4);
  });

  it('clamps progress outside the scroll range', () => {
    expect(chapterIndexFor(-4)).toBe(0);
    expect(chapterIndexFor(12)).toBe(4);
  });

  it('starts with a healthy mesh and no broken links', () => {
    const state = deriveHeroState(0);

    expect(state.chapterIndex).toBe(0);
    expect(state.offline).toEqual([]);
    expect(state.brokenLinks).toEqual([]);
    expect(state.phase).toBe('ambient');
  });

  it('takes one relay offline in the interruption chapter', () => {
    const state = deriveHeroState(0.4);

    expect(state.chapterIndex).toBe(1);
    expect(state.offline).toContain('jonas');
    expect(state.brokenLinks).toContain(heroLinkId('jonas', 'amina'));
    expect(state.phase).toBe('interrupted');
  });

  it('routes around the offline relay using the shared routing model', () => {
    const state = deriveHeroState(0.4);

    expect(state.route).toEqual(['amina', 'mei', 'tomas', 'post']);
    expect(state.route).not.toContain('jonas');
  });

  it('dispatches the message from the sender in the emergency chapter', () => {
    const state = deriveHeroState(0.55);

    expect(state.chapterIndex).toBe(2);
    expect(state.phase).toBe('dispatched');
    expect(state.carrier).toBe('amina');
    expect(state.packetProgress).toBeGreaterThan(0);
  });

  it('shows a broken link for every offline device in the reroute chapter', () => {
    const state = deriveHeroState(0.72);

    expect(state.chapterIndex).toBe(3);
    expect(state.offline).toEqual(['jonas', 'tomas']);
    expect(state.brokenLinks).toContain(heroLinkId('tomas', 'mei'));
    expect(state.phase).toBe('rerouting');
  });

  it('separates reception from acknowledgement in the final chapter', () => {
    const inTransit = deriveHeroState(0.85);
    const received = deriveHeroState(0.9);
    const acknowledged = deriveHeroState(0.99);

    expect(inTransit.phase).toBe('in-transit');

    expect(received.phase).toBe('received');
    expect(received.ackProgress).toBe(0);

    expect(acknowledged.phase).toBe('acknowledged');
    expect(acknowledged.ackProgress).toBeGreaterThan(0);
  });

  it('is a pure function of progress', () => {
    expect(deriveHeroState(0.42)).toEqual(deriveHeroState(0.42));
  });

  it('keeps the topology consistent with the demo model', () => {
    expect(HERO_NODES).toHaveLength(6);
    expect(HERO_LINKS).toHaveLength(8);

    for (const link of HERO_LINKS) {
      expect(HERO_NODES.some((node) => node.id === link.from)).toBe(true);
      expect(HERO_NODES.some((node) => node.id === link.to)).toBe(true);
    }
  });

  it('lists the modelled neighbours of a device', () => {
    expect(neighboursOf('jonas')).toEqual(['amina', 'tomas']);
    expect(neighboursOf('post')).toEqual(['tomas', 'sana']);
  });

  it('describes every chapter with copy', () => {
    expect(HERO_CHAPTERS).toHaveLength(5);

    for (const chapter of HERO_CHAPTERS) {
      expect(chapter.title.length).toBeGreaterThan(0);
      expect(chapter.body.length).toBeGreaterThan(0);
      expect(chapter.hud.length).toBeGreaterThan(0);
    }
  });
});
