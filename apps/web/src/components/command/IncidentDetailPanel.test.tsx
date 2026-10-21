import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
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

  // A status change for A that finishes after the switch to B (#37 review).
  function pendingChange() {
    const calls: { resolve: () => void; reject: (cause: Error) => void }[] = [];
    const onStatusChange = vi.fn(
      () => new Promise<void>((resolve, reject) => calls.push({ resolve, reject })),
    );
    const props = { loading: false, role: 'RESPONDER', onStatusChange };
    const view = render(<IncidentDetailPanel {...props} detail={detail('INC-A')} />);
    fireEvent.change(noteInput(), { target: { value: 'checked with the shelter team' } });
    fireEvent.click(screen.getByRole('button', { name: 'Mark triaged' }));
    view.rerender(<IncidentDetailPanel {...props} detail={detail('INC-B')} />);
    fireEvent.change(noteInput(), { target: { value: 'note for B' } });
    const call = calls[0];
    if (!call) throw new Error('the status change for INC-A was not started');
    return call;
  }

  it('keeps the new incident\'s draft when the previous incident\'s change succeeds', async () => {
    const call = pendingChange();
    await act(async () => call.resolve());
    expect(noteInput().value).toBe('note for B');
  });

  it('does not show the previous incident\'s error on the new one', async () => {
    const call = pendingChange();
    await act(async () => call.reject(new Error('Incident INC-A is already closed')));
    expect(screen.queryByRole('alert')).toBeNull();
    expect(noteInput().value).toBe('note for B');
  });
});
