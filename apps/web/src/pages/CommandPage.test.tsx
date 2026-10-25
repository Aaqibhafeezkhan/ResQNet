import { fireEvent, render, screen, act } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { CommandPage } from './CommandPage';
import { useAuthStore } from '@/state/auth';

const { mockConnectionChange } = vi.hoisted(() => ({
  mockConnectionChange: vi.fn<(connected: boolean) => void>(),
}));

vi.mock('@/lib/events', () => ({
  EventStream: class {
    constructor(options: {
      onConnectionChange?: (connected: boolean) => void;
    }) {
      mockConnectionChange.mockImplementation(
        (connected: boolean) => options.onConnectionChange?.(connected),
      );
    }

    start() {}

    stop() {}
  },
  shouldRefreshList: () => false,
}));

afterEach(() => {
  useAuthStore.setState({ token: null, identity: null, error: null });
  mockConnectionChange.mockReset();
});

describe('CommandPage after the session ends', () => {
  it('takes the user to the sign-in page and keeps the reason', () => {
    useAuthStore.setState({
      token: null,
      identity: null,
      error: 'Your session expired. Sign in again.',
    });

    render(
      <MemoryRouter initialEntries={['/command']}>
        <Routes>
          <Route path="/command" element={<CommandPage />} />
          <Route path="/signin" element={<p>Sign-in page</p>} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Return to sign in' }));

    expect(screen.getByText('Sign-in page')).toBeDefined();
    expect(useAuthStore.getState().error).toBe('Your session expired. Sign in again.');
  });
});

describe('CommandPage WebSocket status', () => {
  it('shows Live, switches to Polling on disconnect, and returns to Live on reconnect', () => {
    useAuthStore.setState({
      token: 'test-token',
      identity: {
        username: 'test-responder',
        role: 'RESPONDER',
      } as NonNullable<ReturnType<typeof useAuthStore.getState>['identity']>,
      error: null,
    });

    render(
      <MemoryRouter initialEntries={['/command']}>
        <Routes>
          <Route path="/command" element={<CommandPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('Polling')).toBeDefined();
    expect(screen.queryByText('Live')).toBeNull();

    act(() => {
      mockConnectionChange(true);
    });

    expect(screen.getByText('Live')).toBeDefined();
    expect(screen.queryByText('Polling')).toBeNull();

    act(() => {
      mockConnectionChange(false);
    });

    expect(screen.getByText('Polling')).toBeDefined();
    expect(screen.queryByText('Live')).toBeNull();

    act(() => {
      mockConnectionChange(true);
    });

    expect(screen.getByText('Live')).toBeDefined();
    expect(screen.queryByText('Polling')).toBeNull();
  });
});
