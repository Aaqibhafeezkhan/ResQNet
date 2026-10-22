import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { CommandHeader } from './CommandHeader';

describe('CommandHeader', () => {
  it('says the data is out of date instead of Live after a failed refresh', () => {
    render(<CommandHeader connected unreachable={false} live={false} stale />);
    expect(screen.getByText('Data out of date')).toBeDefined();
    expect(screen.queryByText('Live')).toBeNull();
    expect(screen.queryByText('Polling')).toBeNull();
    expect(screen.queryByText(/refreshing every 15s/)).toBeNull();
  });

  it('shows Live when the last refresh worked and the stream is open', () => {
    render(<CommandHeader connected unreachable={false} live />);
    expect(screen.getByText('Live')).toBeDefined();
    expect(screen.queryByText('Data out of date')).toBeNull();
  });
});
