import { DEMO_LINKS, findRoute, type NodeId } from '@/components/demo/meshDemoModel';

/**
 * The hero scene model.
 *
 * Pure data and pure functions only. The WebGL runtime, the SVG fallback and
 * the accessible DOM roster all read this module, so the animated scene, the
 * static fallback and the screen-reader text cannot drift apart.
 *
 * Routing is not re-implemented here: it comes from `meshDemoModel`, the same
 * deterministic engine the field lab uses, so the hero cannot show a route the
 * rest of the product would not compute.
 */

export type HeroNodeId = NodeId;

export type HeroPhase =
  | 'ambient'
  | 'interrupted'
  | 'dispatched'
  | 'rerouting'
  | 'in-transit'
  | 'received'
  | 'acknowledged';

export interface HeroNodeSpec {
  id: HeroNodeId;
  name: string;
  role: 'sender' | 'relay' | 'recipient';
  /** Position in world units. The scene is authored around these numbers. */
  position: readonly [number, number, number];
  /** Relative visual weight. */
  scale: number;
  color: number;
  /** What this device does, in protocol terms. Not a radio measurement. */
  duty: string;
}

export interface HeroLinkSpec {
  id: string;
  from: HeroNodeId;
  to: HeroNodeId;
}

export interface HeroChapterSpec {
  id: string;
  index: string;
  label: string;
  title: string;
  body: string;
  /** Short line shown in the on-screen HUD. */
  hud: string;
}

/** A stable id for a link, independent of the direction it is stored in. */
export function heroLinkId(a: HeroNodeId, b: HeroNodeId): string {
  return [a, b].sort().join('|');
}

export const HERO_NODES: readonly HeroNodeSpec[] = [
  {
    id: 'amina',
    name: 'Amina',
    role: 'sender',
    position: [-4.9, -0.85, 0.9],
    scale: 1,
    color: 0xff5c35,
    duty:
      'Signs the SOS with a device-held key and writes it to durable local storage before the first radio attempt.',
  },
  {
    id: 'jonas',
    name: 'Jonas',
    role: 'relay',
    position: [-2.95, 1.75, -0.55],
    scale: 0.72,
    color: 0x5ce1df,
    duty:
      'Verifies the origin signature, decrements the hop budget and hands the message to the next device.',
  },
  {
    id: 'mei',
    name: 'Mei',
    role: 'relay',
    position: [-2.45, -2.05, 0.5],
    scale: 0.7,
    color: 0x5ce1df,
    duty:
      'Stores the envelope before acknowledging the previous carrier, so a crash does not lose the message.',
  },
  {
    id: 'tomas',
    name: 'Tomas',
    role: 'relay',
    position: [1.15, 1.15, -0.8],
    scale: 0.74,
    color: 0x5ce1df,
    duty:
      'Announces itself to peers, suppresses duplicates and forwards the message within its budget.',
  },
  {
    id: 'sana',
    name: 'Sana',
    role: 'relay',
    position: [1.7, -1.85, 0.6],
    scale: 0.7,
    color: 0x5ce1df,
    duty:
      'Holds the message when no onward route exists and releases it the moment a path reappears.',
  },
  {
    id: 'post',
    name: 'Field post',
    role: 'recipient',
    position: [4.9, 0.2, 0.7],
    scale: 0.92,
    color: 0x62e6b5,
    duty:
      'Accepts the message, then returns a signed acknowledgement. Only that acknowledgement means delivered.',
  },
];

export const HERO_LINKS: readonly HeroLinkSpec[] = DEMO_LINKS.map(([from, to]) => ({
  id: heroLinkId(from, to),
  from,
  to,
}));

/** The five chapters of the scroll story. */
export const HERO_CHAPTERS: readonly HeroChapterSpec[] = [
  {
    id: 'connected',
    index: '01',
    label: 'Connected',
    title: 'Six devices. One message.',
    body:
      'Nearby phones form a mesh over local radios. Messages move hop by hop between handsets that can see each other — no tower, no server, no account in the path.',
    hud: 'Mesh healthy · 6 devices · 8 links',
  },
  {
    id: 'interrupted',
    index: '02',
    label: 'Interrupted',
    title: 'A link goes quiet.',
    body:
      'One relay drops out of the mesh. The routes that depended on it break visibly instead of failing silently, and the remaining devices keep their own local state.',
    hud: 'Jonas offline · 2 links down',
  },
  {
    id: 'dispatched',
    index: '03',
    label: 'Dispatched',
    title: 'An SOS is signed and stored.',
    body:
      'The sender signs the message and writes it to durable storage before the first radio attempt. The packet then follows a visible path through the relay devices.',
    hud: 'SOS signed · hop 1 of 3 · Amina → Mei',
  },
  {
    id: 'rerouting',
    index: '04',
    label: 'Rerouted',
    title: 'The route is recalculated.',
    body:
      'Another relay becomes unavailable. The mesh computes an alternate path from the device currently holding the message. When no path remains, the message waits in a local queue — it is never silently dropped.',
    hud: 'Tomas offline · rerouted via Sana',
  },
  {
    id: 'acknowledged',
    index: '05',
    label: 'Acknowledged',
    title: 'Received is not acknowledged.',
    body:
      'The field post accepts the message, then returns a signed acknowledgement. Reception and acknowledgement are shown as two separate events, because only the second one means the message was received.',
    hud: 'Received at field post · acknowledgement returning',
  },
];

/**
 * Scroll-progress boundaries between chapters.
 *
 * The first chapter covers exactly the run-up to the end of the hero copy, so
 * chapter two begins at the moment the copy has scrolled away.
 */
export const HERO_CHAPTER_BOUNDS: readonly number[] = [0, 0.33, 0.52, 0.68, 0.8, 1];

/**
 * The physical path the packet has travelled, per chapter.
 *
 * This is not the route the mesh believes in: after the reroute the packet
 * takes a different physical path than the one computed at dispatch time.
 */
const JOURNEY_BY_CHAPTER: readonly (readonly HeroNodeId[])[] = [
  ['amina', 'jonas', 'tomas'],
  ['amina', 'jonas', 'tomas'],
  ['amina', 'mei'],
  ['amina', 'mei', 'sana'],
  ['amina', 'mei', 'sana', 'post'],
];

/** Devices that have dropped out by the start of each chapter. */
const OFFLINE_BY_CHAPTER: readonly (readonly HeroNodeId[])[] = [
  [],
  ['jonas'],
  ['jonas'],
  ['jonas', 'tomas'],
  ['jonas', 'tomas'],
];

/** The device each chapter points the camera at. */
const FOCUS_BY_CHAPTER: readonly (readonly HeroNodeId[])[] = [
  [],
  ['jonas'],
  ['amina'],
  ['mei', 'sana'],
  ['post'],
];

export interface HeroState {
  /** Index into `HERO_CHAPTERS`. */
  chapterIndex: number;
  /** Progress inside the current chapter, 0..1. */
  local: number;
  /** Devices that have dropped out. */
  offline: readonly HeroNodeId[];
  /** Links broken by the devices above. */
  brokenLinks: readonly string[];
  /** The route the mesh currently believes in, from the current carrier. */
  route: readonly HeroNodeId[];
  /** The device currently holding the message. */
  carrier: HeroNodeId;
  /** The physical path the packet has travelled. */
  journey: readonly HeroNodeId[];
  /** Packet position along the journey, measured in legs. */
  packetProgress: number;
  /** Acknowledgement position travelling back along the journey, 0..1. */
  ackProgress: number;
  phase: HeroPhase;
  /** The device the camera is drawn towards. */
  focus: HeroNodeId;
}

export function clamp01(value: number): number {
  if (Number.isNaN(value)) return 0;
  return Math.min(1, Math.max(0, value));
}

export function chapterIndexFor(progress: number): number {
  const p = clamp01(progress);
  let index = 0;
  for (let i = 0; i < HERO_CHAPTER_BOUNDS.length - 1; i += 1) {
    const start = HERO_CHAPTER_BOUNDS[i];
    if (start === undefined) continue;
    if (p >= start) index = i;
  }
  return Math.min(index, HERO_CHAPTERS.length - 1);
}

export function chapterLocalFor(progress: number, chapterIndex: number): number {
  const start = HERO_CHAPTER_BOUNDS[chapterIndex] ?? 0;
  const end = HERO_CHAPTER_BOUNDS[chapterIndex + 1] ?? 1;
  return clamp01((clamp01(progress) - start) / Math.max(0.0001, end - start));
}

/**
 * Derives the full scene state from scroll progress.
 *
 * Every chapter is a pure function of progress, which means scrubbing the
 * scrollbar backwards produces exactly the same scene as scrolling forwards.
 */
export function deriveHeroState(progress: number): HeroState {
  const chapterIndex = chapterIndexFor(progress);
  const local = chapterLocalFor(progress, chapterIndex);
  const offline = OFFLINE_BY_CHAPTER[chapterIndex] ?? [];
  const brokenLinks = HERO_LINKS.filter(
    (link) => offline.includes(link.from) || offline.includes(link.to),
  ).map((link) => link.id);

  const { route, carrier } = routeForChapter(chapterIndex, offline);
  const journey = JOURNEY_BY_CHAPTER[chapterIndex] ?? [];

  let packetProgress = 0;
  let ackProgress = 0;
  let phase: HeroPhase = 'ambient';

  if (chapterIndex === 0) {
    phase = 'ambient';
    // The mesh is already carrying traffic before the story starts, so the
    // first chapter shows a message moving through healthy links.
    packetProgress = 1.35;
  } else if (chapterIndex === 1) {
    phase = 'interrupted';
    packetProgress = 1.35;
  } else if (chapterIndex === 2) {
    phase = 'dispatched';
    packetProgress = local;
  } else if (chapterIndex === 3) {
    phase = 'rerouting';
    packetProgress = 1 + local;
  } else {
    const travel = Math.min(1, local / 0.5);
    packetProgress = 2 + travel;
    ackProgress = clamp01((local - 0.78) / 0.22);
    phase = local < 0.45 ? 'in-transit' : local < 0.78 ? 'received' : 'acknowledged';
  }

  const focus = focusForChapter(chapterIndex, local);

  return {
    chapterIndex,
    local,
    offline,
    brokenLinks,
    route,
    carrier,
    journey,
    packetProgress,
    ackProgress,
    phase,
    focus,
  };
}

function routeForChapter(
  chapterIndex: number,
  offline: readonly HeroNodeId[],
): { route: readonly HeroNodeId[]; carrier: HeroNodeId } {
  switch (chapterIndex) {
    case 0:
      return { route: findRoute('amina', []) ?? [], carrier: 'amina' };
    case 1:
      // Jonas has dropped out, so the mesh has already routed around it.
      return { route: findRoute('amina', offline) ?? [], carrier: 'amina' };
    case 2:
      return { route: findRoute('amina', offline) ?? [], carrier: 'amina' };
    case 3:
      return { route: findRoute('mei', offline) ?? [], carrier: 'mei' };
    default:
      return { route: findRoute('sana', offline) ?? [], carrier: 'sana' };
  }
}

function focusForChapter(
  chapterIndex: number,
  local: number,
): HeroNodeId {
  const focus = FOCUS_BY_CHAPTER[chapterIndex] ?? [];
  const first = focus[0];
  if (!first) return 'amina';
  const second = focus[1];
  if (!second) return first;
  // Chapter 4 moves from the device holding the message to the one that
  // takes over, so the camera follows the reroute.
  return local < 0.5 ? first : second;
}

/** Nodes that are neighbours of `id` in the modelled topology. */
export function neighboursOf(id: HeroNodeId): HeroNodeId[] {
  const result: HeroNodeId[] = [];
  for (const link of HERO_LINKS) {
    if (link.from === id && !result.includes(link.to)) result.push(link.to);
    if (link.to === id && !result.includes(link.from)) result.push(link.from);
  }
  return result;
}

export function nodeSpec(id: HeroNodeId): HeroNodeSpec {
  const spec = HERO_NODES.find((node) => node.id === id);
  if (!spec) throw new Error(`Unknown hero node: ${id}`);
  return spec;
}

export function linkSpec(id: string): HeroLinkSpec | undefined {
  return HERO_LINKS.find((link) => link.id === id);
}
