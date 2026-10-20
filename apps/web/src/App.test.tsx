import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './app/App';

function renderRoute(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [],
    }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('App', () => {
  it('renders the landing page narrative', async () => {
    renderRoute('/');

    expect(
      await screen.findByRole('heading', { name: /when the signal/i }),
    ).toBeDefined();
    expect(screen.getByText(/a radio handoff is not a delivery/i)).toBeDefined();
    expect(screen.getByText(/latest android build/i)).toBeDefined();
  });

  it('exposes an interactive, labelled mesh map', async () => {
    renderRoute('/demo');

    const map = await screen.findByRole('group', {
      name: /interactive mesh simulation map/i,
    });
    expect(map).toBeDefined();
    expect(screen.getByRole('button', { name: /send sos/i })).toBeDefined();
    expect(screen.getByRole('button', { name: /cut jonas relay/i })).toBeDefined();
  });

  it('queues a message when the origin is isolated', async () => {
    renderRoute('/demo');

    fireEvent.click(await screen.findByRole('button', { name: /cut jonas relay/i }));
    fireEvent.click(screen.getByRole('button', { name: /cut mei relay/i }));
    fireEvent.click(screen.getByRole('button', { name: /send sos/i }));

    expect(
      await screen.findByText(/no route to the field post/i),
    ).toBeDefined();
    expect(screen.getByText(/queued locally/i)).toBeDefined();
  });

  it('offers calls to action that go somewhere real', async () => {
    renderRoute('/');

    const demo = await screen.findByRole('link', { name: /run the mesh lab/i });
    expect(demo.getAttribute('href')).toBe('/demo');

    const download = await screen.findByRole('link', {
      name: /get the android build/i,
    });
    expect(download.getAttribute('href')).toBe('/download');

    const architecture = await screen.findByRole('link', {
      name: /see the architecture/i,
    });
    expect(architecture.getAttribute('href')).toBe('#architecture');
  });

  it('renders the about page with the concept film', async () => {
    renderRoute('/about');

    expect(
      await screen.findByRole('heading', { name: /a small network/i }),
    ).toBeDefined();
    expect(
      screen.getByLabelText(/concept animation showing a two-hop sos relay/i),
    ).toBeDefined();
  });
});
