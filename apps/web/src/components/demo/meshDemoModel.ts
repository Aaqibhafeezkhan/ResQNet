export type NodeId = 'amina' | 'jonas' | 'mei' | 'tomas' | 'sana' | 'post';
export type SenderId = 'amina' | 'jonas' | 'mei';
export type DemoPhase = 'ready' | 'queued' | 'in-transit' | 'delivered';
export type DemoEventKind =
  | 'queue'
  | 'route'
  | 'hop'
  | 'delivered'
  | 'lost'
  | 'restored';

export interface DemoNode {
  id: NodeId;
  name: string;
  short: string;
  role: 'sender' | 'relay' | 'destination';
  x: number;
  y: number;
}

export interface DemoEvent {
  id: number;
  kind: DemoEventKind;
  text: string;
}

export interface DemoState {
  sender: SenderId;
  disabled: NodeId[];
  phase: DemoPhase;
  route: NodeId[];
  hopIndex: number;
  heldAt: NodeId | null;
  lastHop: { from: NodeId; to: NodeId } | null;
  events: DemoEvent[];
  eventSeq: number;
}

export const SENDER_IDS: readonly SenderId[] = ['amina', 'jonas', 'mei'];

export const DEMO_NODES: readonly DemoNode[] = [
  { id: 'amina', name: 'Amina', short: 'A', role: 'sender', x: 13, y: 52 },
  { id: 'jonas', name: 'Jonas', short: 'J', role: 'relay', x: 30, y: 25 },
  { id: 'mei', name: 'Mei', short: 'M', role: 'relay', x: 31, y: 78 },
  { id: 'tomas', name: 'Tomas', short: 'T', role: 'relay', x: 55, y: 38 },
  { id: 'sana', name: 'Sana', short: 'S', role: 'relay', x: 57, y: 76 },
  { id: 'post', name: 'Field post', short: 'FP', role: 'destination', x: 85, y: 52 },
];

export const DEMO_LINKS: readonly (readonly [NodeId, NodeId])[] = [
  ['amina', 'jonas'],
  ['amina', 'mei'],
  ['jonas', 'tomas'],
  ['mei', 'tomas'],
  ['mei', 'sana'],
  ['tomas', 'post'],
  ['tomas', 'sana'],
  ['sana', 'post'],
];

const ADJACENCY: Record<NodeId, NodeId[]> = {
  amina: ['jonas', 'mei'],
  jonas: ['amina', 'tomas'],
  mei: ['amina', 'tomas', 'sana'],
  tomas: ['jonas', 'mei', 'sana', 'post'],
  sana: ['mei', 'tomas', 'post'],
  post: ['tomas', 'sana'],
};

const LABELS: Record<NodeId, string> = {
  amina: 'Amina',
  jonas: 'Jonas',
  mei: 'Mei',
  tomas: 'Tomas',
  sana: 'Sana',
  post: 'Field post',
};

export function nodeLabel(id: NodeId): string {
  return LABELS[id];
}

export function nodeById(id: NodeId): DemoNode {
  const node = DEMO_NODES.find((entry) => entry.id === id);
  if (!node) throw new Error(`Unknown demo node: ${id}`);
  return node;
}

export function createInitialState(sender: SenderId = 'amina'): DemoState {
  return {
    sender,
    disabled: [],
    phase: 'ready',
    route: [],
    hopIndex: 0,
    heldAt: null,
    lastHop: null,
    events: [],
    eventSeq: 0,
  };
}

export function findRoute(
  from: NodeId,
  disabled: readonly NodeId[],
): NodeId[] | null {
  if (from === 'post') return ['post'];

  const blocked = new Set(disabled);
  const queue: NodeId[][] = [[from]];
  const visited = new Set<NodeId>([from]);

  while (queue.length > 0) {
    const path = queue.shift();
    if (!path) break;
    const current = path[path.length - 1];
    if (!current) continue;

    for (const neighbour of ADJACENCY[current]) {
      if (blocked.has(neighbour) || visited.has(neighbour)) continue;
      const next = [...path, neighbour];
      if (neighbour === 'post') return next;
      visited.add(neighbour);
      queue.push(next);
    }
  }

  return null;
}

export function currentNode(state: DemoState): NodeId {
  return state.route[state.hopIndex] ?? state.sender;
}

export function routeFromCurrent(state: DemoState): NodeId[] {
  return state.route.slice(state.hopIndex);
}

export function routeLabel(route: readonly NodeId[]): string {
  return route.map(nodeLabel).join(' → ');
}

export function selectSender(state: DemoState, sender: SenderId): DemoState {
  return {
    ...createInitialState(sender),
    disabled: state.disabled.filter((id) => id !== sender),
    eventSeq: state.eventSeq,
  };
}

export function sendSos(state: DemoState): DemoState {
  if (state.phase === 'in-transit' || state.phase === 'queued') return state;

  const fresh = {
    ...createInitialState(state.sender),
    disabled: state.disabled,
    eventSeq: state.eventSeq,
  };
  const route = findRoute(state.sender, state.disabled);
  let next = pushEvent(
    fresh,
    'queue',
    `SOS signed on ${nodeLabel(state.sender)} and written to the local queue.`,
  );

  if (!route) {
    next = pushEvent(
      {
        ...next,
        phase: 'queued',
        route: [state.sender],
        heldAt: state.sender,
      },
      'lost',
      `No route to the field post. The message stays queued on ${nodeLabel(state.sender)}.`,
    );
    return next;
  }

  return pushEvent(
    {
      ...next,
      phase: 'in-transit',
      route,
      hopIndex: 0,
      heldAt: null,
    },
    'route',
    `Route selected: ${routeLabel(route)}.`,
  );
}

export function advanceDemo(state: DemoState): DemoState {
  if (state.phase !== 'in-transit') return state;

  const from = currentNode(state);
  const to = state.route[state.hopIndex + 1];

  if (!to) {
    return pushEvent(
      { ...state, phase: 'delivered', lastHop: null },
      'delivered',
      'The field post accepted the message and returned a signed acknowledgement.',
    );
  }

  const moved: DemoState = {
    ...state,
    hopIndex: state.hopIndex + 1,
    lastHop: { from, to },
  };

  if (to !== 'post') {
    return pushEvent(
      moved,
      'hop',
      `${nodeLabel(from)} → ${nodeLabel(to)} · radio handoff complete.`,
    );
  }

  const delivered = pushEvent(
    { ...moved, phase: 'delivered', heldAt: null },
    'delivered',
    `${nodeLabel(from)} → Field post · delivered and signed acknowledgement verified.`,
  );
  return delivered;
}

export function toggleRelay(state: DemoState, id: NodeId): DemoState {
  if (id === 'post' || id === state.sender) return state;

  const current = state.phase === 'in-transit' ? currentNode(state) : null;
  if (current === id) return state;

  const wasDisabled = state.disabled.includes(id);
  const disabled = wasDisabled
    ? state.disabled.filter((entry) => entry !== id)
    : [...state.disabled, id];

  let next: DemoState = { ...state, disabled };

  if (state.phase !== 'in-transit' && state.phase !== 'queued') {
    return next;
  }

  const from = state.phase === 'queued' ? (state.heldAt ?? state.sender) : currentNode(state);
  const previous = routeFromCurrent(state);
  const route = findRoute(from, disabled);
  const action = wasDisabled ? 'reconnected' : 'disconnected';

  if (!route) {
    return pushEvent(
      {
        ...next,
        phase: 'queued',
        route: [from],
        hopIndex: 0,
        heldAt: from,
        lastHop: null,
      },
      'lost',
      `${nodeLabel(id)} ${action}. No route from ${nodeLabel(from)}; the message is held in the local queue.`,
    );
  }

  next = {
    ...next,
    phase: 'in-transit',
    route,
    hopIndex: 0,
    heldAt: null,
    lastHop: null,
  };

  if (sameRoute(previous, route)) return next;

  return pushEvent(
    next,
    wasDisabled ? 'restored' : 'route',
    `${nodeLabel(id)} ${action}. ${state.phase === 'queued' ? 'Queued message released' : 'Message rerouted'}: ${routeLabel(route)}.`,
  );
}

export function retryQueued(state: DemoState): DemoState {
  if (state.phase !== 'queued') return state;
  const from = state.heldAt ?? state.sender;
  const route = findRoute(from, state.disabled);

  if (!route) {
    return pushEvent(
      state,
      'lost',
      `Still no route from ${nodeLabel(from)}. The message remains queued.`,
    );
  }

  return pushEvent(
    {
      ...state,
      phase: 'in-transit',
      route,
      hopIndex: 0,
      heldAt: null,
      lastHop: null,
    },
    'restored',
    `Queued message released: ${routeLabel(route)}.`,
  );
}

function sameRoute(a: readonly NodeId[], b: readonly NodeId[]): boolean {
  return a.length === b.length && a.every((id, index) => id === b[index]);
}

function pushEvent(
  state: DemoState,
  kind: DemoEventKind,
  text: string,
): DemoState {
  const id = state.eventSeq + 1;
  return {
    ...state,
    eventSeq: id,
    events: [...state.events, { id, kind, text }].slice(-5),
  };
}
