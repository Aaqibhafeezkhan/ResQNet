import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';

import { App } from './app/App';

/**
 * A smoke test that the whole route tree renders without throwing.
 *
 * <p>The canvas visualisations cannot be asserted visually in jsdom, so what
 * this covers is what actually breaks in practice: a bad import, a hook used
 * outside its rules, a provider missing.
 */
describe('App', () => {
  it('renders the landing page', async () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );

    expect(
      await screen.findByRole('heading', { name: /when everything/i }),
    ).toBeDefined();
  });

  it('exposes the mesh visualisation with a label', async () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );

    const canvas = await screen.findByTestId('mesh-field');
    expect(canvas.getAttribute('role')).toBe('img');
    expect(canvas.getAttribute('aria-label')).toMatch(/mesh relaying/i);
  });

  it('offers only calls to action that go somewhere', async () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );

    const demo = await screen.findByRole('link', { name: /explore live demo/i });
    expect(demo.getAttribute('href')).toBe('/command');

    const architecture = await screen.findByRole('link', { name: /view architecture/i });
    expect(architecture.getAttribute('href')).toBe('#architecture');
  });
});
