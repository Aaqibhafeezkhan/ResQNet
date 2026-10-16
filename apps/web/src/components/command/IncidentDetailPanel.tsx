import {
  CheckCircle2,
  CircleDot,
  Clock,
  MapPin,
  ShieldAlert,
  UserCheck,
} from 'lucide-react';
import { useState } from 'react';

import { StatusChip } from '@/components/primitives/StatusChip';
import { deliveryStateLabel, relativeTime, severityLabel } from '@/lib/format';
import { ApiError, type IncidentDetail, type IncidentStatus } from '@/lib/api';
import { canMutate } from '@/state/auth';

interface IncidentDetailPanelProps {
  detail: IncidentDetail | null;
  loading: boolean;
  role: string | undefined;
  onStatusChange: (reference: string, status: IncidentStatus, note?: string) => Promise<void>;
}

const NEXT_STATUSES: { status: IncidentStatus; label: string }[] = [
  { status: 'TRIAGED', label: 'Mark triaged' },
  { status: 'IN_PROGRESS', label: 'Take ownership' },
  { status: 'RESOLVED', label: 'Resolve' },
];

/**
 * Incident detail with its full delivery and audit trail.
 *
 * <p>The status actions are gated on the account's role, and the disabled state
 * is explained rather than merely greyed out. A responder who cannot act needs
 * to know why, not to conclude the system is broken.
 */
export function IncidentDetailPanel({
  detail,
  loading,
  role,
  onStatusChange,
}: IncidentDetailPanelProps) {
  const [pending, setPending] = useState<IncidentStatus | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [note, setNote] = useState('');

  if (loading && !detail) {
    return (
      <div className="surface flex h-full flex-col gap-4 p-6" aria-busy="true">
        <div className="h-3 w-24 rounded bg-base-700" />
        <div className="h-5 w-2/3 rounded bg-base-700" />
        <div className="h-3 w-1/2 rounded bg-base-700" />
      </div>
    );
  }

  if (!detail) {
    return (
      <div className="surface flex h-full flex-col items-center justify-center gap-3 p-10 text-center">
        <CircleDot size={20} className="text-ink-tertiary" aria-hidden="true" />
        <p className="text-sm text-ink-secondary">Select an incident to inspect it</p>
      </div>
    );
  }

  const { incident, messages, audit } = detail;
  const allowed = canMutate(role);
  const closed = incident.status === 'RESOLVED' || incident.status === 'DISMISSED';

  const change = async (status: IncidentStatus) => {
    setPending(status);
    setFailure(null);
    try {
      await onStatusChange(incident.reference, status, note.trim() || undefined);
      setNote('');
    } catch (cause) {
      // Previously the promise was voided with no catch, so a rejected status
      // change became an unhandled rejection: the button returned to idle and
      // the responder was told nothing. The most likely failure is another
      // responder having closed the incident first, which is exactly the case
      // where the operator most needs to be told their action did not land.
      setFailure(
        cause instanceof ApiError
          ? cause.message
          : 'The status change could not be saved.',
      );
    } finally {
      setPending(null);
    }
  };

  return (
    <article className="surface flex h-full flex-col overflow-hidden">
      <header className="border-b border-base-700 p-6">
        <p className="flex items-center gap-2">
          <span className="tabular text-xs font-semibold tracking-wider text-ink-tertiary">
            {incident.reference}
          </span>
          {incident.advisory ? (
            <StatusChip tone="pending">Advisory classification</StatusChip>
          ) : (
            <StatusChip tone="delivered">Confirmed by responder</StatusChip>
          )}
        </p>

        <h2 className="mt-2.5 text-xl font-semibold leading-snug tracking-tight text-ink-primary">
          {incident.title}
        </h2>

        <dl className="mt-5 grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3">
          <Fact label="Category" value={incident.categoryLabel} />
          <Fact label="Severity" value={severityLabel(incident.severity)} />
          <Fact label="Status" value={incident.statusLabel} />
          <Fact label="Reported" value={relativeTime(incident.createdAt)} />
          <Fact label="Messages" value={String(incident.messageCount)} />
          <Fact
            label="Location"
            value={
              incident.locationShared &&
              incident.latitude != null &&
              incident.longitude != null
                ? `${incident.latitude.toFixed(4)}, ${incident.longitude.toFixed(4)}`
                : 'Not shared'
            }
            icon={
              incident.locationShared ? <MapPin size={12} aria-hidden="true" /> : undefined
            }
          />
        </dl>

        {incident.advisory && incident.advisoryConfidence != null ? (
          <p className="mt-4 flex items-start gap-2 rounded-lg bg-pending-soft px-3 py-2.5 text-[12px] leading-relaxed text-ink-secondary">
            <ShieldAlert size={13} className="mt-0.5 shrink-0 text-pending" aria-hidden="true" />
            <span>
              This category came from an on-device keyword model at{' '}
              {Math.round(incident.advisoryConfidence * 100)}% confidence. It is a
              suggestion for ordering the queue, not a diagnosis, and it has not
              dispatched anyone.
            </span>
          </p>
        ) : null}
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto p-6">
        <section aria-label="Delivery trail">
          <h3 className="eyebrow">Delivery trail</h3>
          {messages.length === 0 ? (
            <p className="mt-3 text-sm text-ink-tertiary">No delivery metadata reported.</p>
          ) : (
            <ol className="mt-3 space-y-2">
              {messages.map((message) => (
                <li
                  key={message.messageId}
                  className="flex items-start gap-3 rounded-lg border border-base-700 bg-base-850 px-3.5 py-3"
                >
                  <CheckCircle2
                    size={14}
                    className={
                      message.state === 'ACKNOWLEDGED'
                        ? 'mt-0.5 shrink-0 text-delivered'
                        : 'mt-0.5 shrink-0 text-pending'
                    }
                    aria-hidden="true"
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-[13px] font-medium text-ink-primary">
                        {deliveryStateLabel(message.state)}
                      </span>
                      {message.simulated ? (
                        <span className="rounded bg-base-700 px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-widest text-ink-tertiary">
                          Simulated
                        </span>
                      ) : null}
                    </div>
                    <p className="tabular mt-1 text-[11px] text-ink-tertiary">
                      {message.messageId.slice(0, 22)} · {message.priority.toLowerCase()} ·{' '}
                      {message.attempts} attempt{message.attempts === 1 ? '' : 's'}
                      {message.detail ? ` · ${message.detail}` : ''}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          )}
          <p className="mt-3 text-[11px] leading-relaxed text-ink-tertiary">
            Message bodies are never uploaded. The backend records that a message
            exists, where it came from and whether it arrived; the text stays on
            the devices that were already there.
          </p>
        </section>

        <section aria-label="Audit trail" className="mt-7">
          <h3 className="eyebrow">Audit trail</h3>
          <ol className="mt-3 space-y-2">
            {audit.map((entry, index) => (
              <li key={`${entry.occurredAt}-${index}`} className="flex gap-3">
                <Clock size={12} className="mt-1 shrink-0 text-ink-tertiary" aria-hidden="true" />
                <div className="min-w-0">
                  <p className="text-[13px] text-ink-secondary">
                    <span className="font-medium text-ink-primary">{entry.actor}</span>{' '}
                    {entry.action.replace(/_/g, ' ').toLowerCase()}
                  </p>
                  {entry.detail ? (
                    <p className="mt-0.5 text-[11px] text-ink-tertiary">{entry.detail}</p>
                  ) : null}
                  <p className="tabular mt-0.5 text-[11px] text-ink-tertiary">
                    {relativeTime(entry.occurredAt)}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        </section>
      </div>

      <footer className="border-t border-base-700 p-5">
        {closed ? (
          <p className="text-xs text-ink-tertiary">
            This incident is {incident.statusLabel.toLowerCase()} and can no longer be
            changed.
          </p>
        ) : allowed ? (
          <div className="flex flex-col gap-3">
            <label className="sr-only" htmlFor="status-note">
              Note to record with this change
            </label>
            <input
              id="status-note"
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="Optional note for the audit trail"
              className="w-full rounded-lg border border-base-600 bg-base-850 px-3 py-2 text-[13px] text-ink-primary placeholder:text-ink-tertiary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
            />
            {/*
              The error is rendered here rather than only logged, because a
              silently failed status change is worse than no control at all: the
              responder believes the incident was triaged when it was not.
            */}
            {failure ? (
              <p
                role="alert"
                className="mb-2 rounded-lg bg-emergency-soft px-3 py-2 text-[12px] leading-relaxed text-emergency"
              >
                {failure}
              </p>
            ) : null}
            <div className="flex flex-wrap gap-2">
              {NEXT_STATUSES.map((action) => (
                <button
                  key={action.status}
                  type="button"
                  disabled={pending !== null || incident.status === action.status}
                  onClick={() => void change(action.status)}
                  className="inline-flex min-h-[40px] items-center gap-1.5 rounded-lg border border-base-600 bg-base-800 px-3.5 text-[13px] font-medium text-ink-primary transition-colors hover:border-base-500 hover:bg-base-750 disabled:cursor-not-allowed disabled:opacity-40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
                >
                  <UserCheck size={13} aria-hidden="true" />
                  {pending === action.status ? 'Saving…' : action.label}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <p className="text-xs leading-relaxed text-ink-tertiary">
            This account has read-only access. Changing an incident requires a
            responder or administrator role, assigned by a human administrator.
          </p>
        )}
      </footer>
    </article>
  );
}

function Fact({
  label,
  value,
  icon,
}: {
  label: string;
  value: string;
  icon?: React.ReactNode;
}) {
  return (
    <div className="min-w-0">
      <dt className="eyebrow">{label}</dt>
      <dd className="mt-1 flex items-center gap-1.5 truncate text-[13px] text-ink-primary">
        {icon}
        {value}
      </dd>
    </div>
  );
}
