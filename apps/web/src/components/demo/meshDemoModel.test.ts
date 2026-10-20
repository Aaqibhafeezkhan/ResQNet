import { describe, expect, it } from 'vitest';

import {
  advanceDemo,
  createInitialState,
  currentNode,
  findRoute,
  retryQueued,
  routeLabel,
  selectSender,
  sendSos,
  toggleRelay,
} from './meshDemoModel';

describe('meshDemoModel', () => {
  it('finds a deterministic shortest route', () => {
    expect(findRoute('amina', [])).toEqual(['amina', 'jonas', 'tomas', 'post']);
  });

  it('routes around a cut relay', () => {
    expect(findRoute('amina', ['jonas'])).toEqual(['amina', 'mei', 'tomas', 'post']);
  });

  it('queues when every route from the sender is cut', () => {
    expect(findRoute('amina', ['jonas', 'mei'])).toBeNull();

    const queued = sendSos({
      ...createInitialState(),
      disabled: ['jonas', 'mei'],
    });

    expect(queued.phase).toBe('queued');
    expect(queued.heldAt).toBe('amina');
    expect(queued.events.at(-1)?.kind).toBe('lost');
  });

  it('releases a queued message when a relay reconnects', () => {
    let state = sendSos({ ...createInitialState(), disabled: ['jonas', 'mei'] });
    expect(state.phase).toBe('queued');

    state = toggleRelay(state, 'jonas');
    expect(state.phase).toBe('in-transit');
    expect(state.route).toEqual(['amina', 'jonas', 'tomas', 'post']);
    expect(state.events.at(-1)?.kind).toBe('restored');
  });

  it('reroutes an in-flight message without losing it', () => {
    let state = sendSos(createInitialState());
    state = advanceDemo(state);
    expect(currentNode(state)).toBe('jonas');

    state = toggleRelay(state, 'tomas');
    expect(state.phase).toBe('in-transit');
    expect(state.route).toEqual(['jonas', 'amina', 'mei', 'sana', 'post']);
    expect(routeLabel(state.route)).toContain('Mei');
  });

  it('reaches delivery only after every hop and the acknowledgement', () => {
    let state = sendSos(createInitialState());
    const seen: string[] = [];

    for (let index = 0; index < 8 && state.phase !== 'delivered'; index += 1) {
      state = advanceDemo(state);
      seen.push(currentNode(state));
    }

    expect(state.phase).toBe('delivered');
    expect(currentNode(state)).toBe('post');
    expect(seen).toEqual(['jonas', 'tomas', 'post']);
    expect(state.events.at(-1)?.text).toMatch(/acknowledgement/i);
  });

  it('restarts the message when the sender changes', () => {
    let state = sendSos(createInitialState('mei'));
    state = advanceDemo(state);
    state = selectSender(state, 'jonas');

    expect(state.phase).toBe('ready');
    expect(state.sender).toBe('jonas');
    expect(state.route).toEqual([]);
    expect(state.disabled).toEqual([]);
  });

  it('does not allow the field post or current carrier to be cut', () => {
    let state = sendSos(createInitialState());
    state = advanceDemo(state);
    const unchanged = toggleRelay(state, 'jonas');
    expect(unchanged).toBe(state);
    expect(toggleRelay(state, 'post')).toBe(state);
  });

  it('can retry a queue without reconnecting anything', () => {
    const queued = sendSos({ ...createInitialState(), disabled: ['jonas', 'mei'] });
    expect(retryQueued(queued).phase).toBe('queued');
  });
});
