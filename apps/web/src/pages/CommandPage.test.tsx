import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';

import { CommandPage } from './CommandPage';
import { useAuthStore } from '@/state/auth';

afterEach(() => {
  useAuthStore.setState({ token: null, identity: null, error: null });
});

describe('CommandPage after the session ends', () => {
  it('takes the user to the sign-in page and keeps the reason', () => {
    useAuthStore.setState({ token: null, identity: null, error: 'Your session expired. Sign in again.' });
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
