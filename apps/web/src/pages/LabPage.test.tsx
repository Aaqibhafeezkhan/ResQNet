import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '@/app/App';
import { useLabStore } from '@/lab/labStore';
import { LabPage } from './LabPage';

const initialState = useLabStore.getInitialState();

function renderLab() {
  return render(
    <MemoryRouter initialEntries={['/lab']}>
      <LabPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  useLabStore.setState(initialState, true);
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: true, json: async () => [] }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('LabPage', () => {
  it('states the simulation boundary before anything is loaded', () => {
    renderLab();

    expect(screen.getAllByText(/simulated — not real radio telemetry/i).length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', { name: /read the simulation/i })).toBeDefined();
    expect(screen.getByRole('button', { name: /open the ring sample/i })).toBeDefined();
  });

  it('loads a committed report and shows the map, timeline and metrics', async () => {
    renderLab();
    fireEvent.click(screen.getByRole('button', { name: /open the ring sample/i }));

    expect(await screen.findByRole('img', { name: /recorded RING topology/i })).toBeDefined();
    expect(screen.getByText('0 / 558 events')).toBeDefined();
    expect(screen.getByText('ring · 6 nodes · 6 recorded links')).toBeDefined();
    expect(screen.getByRole('heading', { name: 'Reported metrics' })).toBeDefined();
    expect(screen.getByText('Acknowledgement rate')).toBeDefined();
    expect(screen.getAllByText(/simulated — not real radio telemetry/i).length).toBeGreaterThan(0);
  });

  it('steps the replay and toggles playback from the transport', async () => {
    renderLab();
    fireEvent.click(screen.getByRole('button', { name: /open the ring sample/i }));
    await screen.findByRole('img', { name: /recorded RING topology/i });

    fireEvent.click(screen.getByRole('button', { name: /next event/i }));
    expect(screen.getByText('1 / 558 events')).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: /play the replay/i }));
    expect(screen.getByRole('button', { name: /pause the replay/i })).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: /pause the replay/i }));
    expect(screen.getByRole('button', { name: /play the replay/i })).toBeDefined();
  });

  it('inspects a recorded message at the end of the replay', async () => {
    renderLab();
    fireEvent.click(screen.getByRole('button', { name: /open the ring sample/i }));
    await screen.findByRole('img', { name: /recorded RING topology/i });

    fireEvent.change(screen.getByLabelText('Replay position'), { target: { value: '558' } });
    const select = screen.getByLabelText('Message to inspect') as HTMLSelectElement;
    const firstMessage = select.options[1]?.value;
    if (!firstMessage) throw new Error('expected a message option');
    fireEvent.change(select, { target: { value: firstMessage } });

    expect(screen.getByText('Acknowledged end to end')).toBeDefined();
    expect(screen.getByText(/a mesh floods/i)).toBeDefined();
    expect(screen.getAllByText(/accepted the message from/i).length).toBeGreaterThan(0);
  });

  it('selects a node and shows its recorded counters', async () => {
    renderLab();
    fireEvent.click(screen.getByRole('button', { name: /open the ring sample/i }));
    await screen.findByRole('img', { name: /recorded RING topology/i });

    fireEvent.click(screen.getByRole('button', { name: /^n0/ }));
    expect(screen.getByRole('heading', { name: /node-0/ })).toBeDefined();
    expect(screen.getByText(/counters cover the whole run/i)).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: /clear the selected node/i }));
    expect(screen.queryByText(/counters cover the whole run/i)).toBeNull();
  });

  it('validates the scenario builder and never claims to run Java', () => {
    renderLab();

    fireEvent.change(screen.getByLabelText('Nodes'), { target: { value: '1' } });
    expect(screen.getByText(/a mesh needs at least 2 nodes/i)).toBeDefined();
    expect(
      (screen.getByRole('button', { name: /^copy$/i }) as HTMLButtonElement).disabled,
    ).toBe(true);

    fireEvent.change(screen.getByLabelText('Nodes'), { target: { value: '7' } });
    expect(screen.queryByText(/a mesh needs at least 2 nodes/i)).toBeNull();
    expect(screen.getByText(/--nodes 7/)).toBeDefined();
    expect(screen.getByText(/does not run the simulation in this browser/i)).toBeDefined();
  });

  it('rejects a malformed import with an actionable error', async () => {
    const { container } = renderLab();
    const section = screen.getByRole('heading', { name: 'Load a report' }).closest('section');
    if (!section) throw new Error('expected the loader section');
    const input = section.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['{"simulation":'], 'broken.json', { type: 'application/json' });

    fireEvent.change(input, { target: { files: [file] } });

    expect(await screen.findByText(/not valid JSON/i)).toBeDefined();
    expect(container.querySelector('[role="alert"]')).not.toBeNull();
    expect(useLabStore.getState().report).toBeNull();
  });

  it('compares the two committed samples and marks the comparison uncontrolled', async () => {
    renderLab();
    fireEvent.click(screen.getByRole('tab', { name: /compare/i }));

    fireEvent.change(screen.getByLabelText('Report A'), {
      target: { value: 'ring-6-seed-42' },
    });
    fireEvent.change(screen.getByLabelText('Report B'), {
      target: { value: 'two-cluster-6-seed-42' },
    });

    expect(await screen.findByText('Not a controlled comparison')).toBeDefined();
    expect(screen.getByText('Configuration differences')).toBeDefined();
    expect(screen.getByText('Starts partitioned')).toBeDefined();
    expect(screen.getByRole('button', { name: /export \.md/i })).toBeDefined();
  });

  it('says when two slots hold the same configuration', async () => {
    renderLab();
    fireEvent.click(screen.getByRole('tab', { name: /compare/i }));

    fireEvent.change(screen.getByLabelText('Report A'), {
      target: { value: 'ring-6-seed-42' },
    });
    fireEvent.change(screen.getByLabelText('Report B'), {
      target: { value: 'ring-6-seed-42' },
    });

    expect(await screen.findByText('Same configuration')).toBeDefined();
  });
});

describe('/lab route', () => {
  it('is mounted on the public site without authentication', async () => {
    render(
      <MemoryRouter initialEntries={['/lab']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: /read the simulation/i })).toBeDefined();
    expect(screen.getByRole('link', { name: /replay lab/i })).toBeDefined();
  });
});
