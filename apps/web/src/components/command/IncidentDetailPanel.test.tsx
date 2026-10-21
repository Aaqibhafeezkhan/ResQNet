import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import type { IncidentDetail } from '@/lib/api';

import { IncidentDetailPanel } from './IncidentDetailPanel';

function detail(reference: string): IncidentDetail {
  return {
    incident: {
      id: 1,
      reference,
      title: `Incident ${reference}`,
      category: 'MEDICAL',
      categoryLabel: 'Medical',
      severity: 3,
      status: 'NEW',
      statusLabel: 'New',
      advisory: false,
      locationShared: false,
      createdAt: '2026-10-20T10:00:00Z',
      updatedAt: '2026-10-20T10:00:00Z',
      messageCount: 0,
    },
    messages: [],
    audit: [],
  };
}

const noteInput = () => screen.getByLabelText(/note to record/i) as HTMLInputElement;

describe('IncidentDetailPanel', () => {
  it('clears the note draft when another incident is selected', async () => {
    const onStatusChange = vi.fn().mockResolvedValue(undefined);
    const props = { loading: false, role: 'RESPONDER', onStatusChange };
    const { rerender } = render(<IncidentDetailPanel {...props} detail={detail('INC-A')} />);

    fireEvent.change(noteInput(), { target: { value: 'checked with the shelter team' } });
    rerender(<IncidentDetailPanel {...props} detail={detail('INC-B')} />);

    expect(noteInput().value).toBe('');
    fireEvent.click(screen.getByRole('button', { name: 'Mark triaged' }));
    await waitFor(() => expect(onStatusChange).toHaveBeenCalledWith('INC-B', 'TRIAGED', undefined));
  });

  it('keeps the draft when the same incident is refreshed', () => {
    const props = { loading: false, role: 'RESPONDER', onStatusChange: vi.fn() };
    const { rerender } = render(<IncidentDetailPanel {...props} detail={detail('INC-A')} />);

    fireEvent.change(noteInput(), { target: { value: 'checked with the shelter team' } });
    rerender(<IncidentDetailPanel {...props} detail={detail('INC-A')} />);

    expect(noteInput().value).toBe('checked with the shelter team');
  });
});
