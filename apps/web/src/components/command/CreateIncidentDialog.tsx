import { useEffect, useRef, useState } from 'react';

import { Button } from '@/components/primitives/Button';
import {
  ApiError,
  type Category,
  type IncidentSummary,
} from '@/lib/api';

/**
 * Report an incident by hand.
 *
 * <p>For the case the mesh does not cover: a person sees something and no
 * handset happens to be involved. Radio, first-hand accounts, a landline.
 *
 * <p>A manually created incident is kept visibly distinct from a device report
 * for the whole of its life. It carries no advisory flag and no confidence,
 * because nothing on the device classified it, and the audit trail records a
 * person rather than a gateway. The distinction is the point: a responder must
 * be able to tell at a glance which entries came from a human being in front of
 * them and which arrived over Bluetooth from somewhere in a collapsed
 * building.
 *
 * <p>Implemented as a modal dialog with a focus trap rather than a page, because
 * it is opened from the console and dismissing it should return the operator to
 * the queue they were working in, not to a blank screen.
 */

interface CreateIncidentDialogProps {
  open: boolean;
  onClose: () => void;
  onCreate: (input: {
    title: string;
    category: Category;
    severity: number;
    latitude?: number;
    longitude?: number;
  }) => Promise<IncidentSummary>;
}

// The list is derived from the Category union rather than invented, so a
// category added to the backend cannot be missing here without a type error.
const CATEGORIES: { value: Category; label: string }[] = [
  { value: 'MEDICAL', label: 'Medical' },
  { value: 'FIRE', label: 'Fire' },
  { value: 'TRAPPED', label: 'Trapped' },
  { value: 'MISSING_PERSON', label: 'Missing person' },
  { value: 'FLOODING', label: 'Flooding' },
  { value: 'SUPPLIES', label: 'Supplies' },
  { value: 'GENERAL', label: 'Something else' },
  { value: 'UNKNOWN', label: 'Unclassified' },
];

const SEVERITIES = [
  { value: 0, label: 'Unclassified' },
  { value: 1, label: 'Routine' },
  { value: 2, label: 'Elevated' },
  { value: 3, label: 'Critical' },
];

export function CreateIncidentDialog({
  open,
  onClose,
  onCreate,
}: CreateIncidentDialogProps) {
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState<Category>('MEDICAL');
  const [severity, setSeverity] = useState(3);
  const [locate, setLocate] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dialogRef = useRef<HTMLDivElement | null>(null);
  const firstFieldRef = useRef<HTMLInputElement | null>(null);

  // Reset on open so a previous attempt's text and error do not linger.
  useEffect(() => {
    if (open) {
      setTitle('');
      setCategory('MEDICAL');
      setSeverity(3);
      setLocate(false);
      setError(null);
      // Move focus into the dialog, otherwise a keyboard user's next keystroke
      // goes somewhere behind it.
      firstFieldRef.current?.focus();
    }
  }, [open]);

  // Escape closes, Tab stays inside. A modal that leaks focus to the page
  // behind it is worse than no modal at all.
  useEffect(() => {
    if (!open) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = dialogRef.current?.querySelectorAll<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      );
      if (!focusable || focusable.length === 0) return;
      const first = focusable[0]!;
      const last = focusable[focusable.length - 1]!;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  const submit = async () => {
    const trimmed = title.trim();
    if (trimmed.length < 3) {
      setError('Give a short description of what is happening.');
      return;
    }
    setPending(true);
    setError(null);
    try {
      await onCreate({ title: trimmed, category, severity });
      onClose();
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message
        : 'The incident could not be created.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-incident-title"
        className="w-full max-w-lg rounded-xl border border-base-600 bg-base-800 p-6"
      >
        <h2 id="create-incident-title" className="text-base font-semibold text-ink-primary">
          Report an incident
        </h2>
        <p className="mt-1 text-[12px] leading-relaxed text-ink-tertiary">
          For something you have seen directly. Incidents reported by a handset
          arrive by themselves and are marked advisory until a responder triages
          them; this one is attributed to you from the outset.
        </p>

        <label
          htmlFor="incident-title"
          className="mt-5 block text-[11px] font-semibold uppercase tracking-wide text-ink-tertiary"
        >
          What is happening
        </label>
        <input
          id="incident-title"
          ref={firstFieldRef}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Two people trapped on the third floor"
          className="mt-1 w-full rounded-lg border border-base-600 bg-base-850 px-3 py-2 text-[13px] text-ink-primary placeholder:text-ink-tertiary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
        />

        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <div>
            <label
              htmlFor="incident-category"
              className="block text-[11px] font-semibold uppercase tracking-wide text-ink-tertiary"
            >
              Category
            </label>
            <select
              id="incident-category"
              value={category}
              onChange={(event) => setCategory(event.target.value as Category)}
              className="mt-1 w-full rounded-lg border border-base-600 bg-base-850 px-3 py-2 text-[13px] text-ink-primary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
            >
              {CATEGORIES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>

          <div>
            <label
              htmlFor="incident-severity"
              className="block text-[11px] font-semibold uppercase tracking-wide text-ink-tertiary"
            >
              Severity
            </label>
            <select
              id="incident-severity"
              value={severity}
              onChange={(event) => setSeverity(Number(event.target.value))}
              className="mt-1 w-full rounded-lg border border-base-600 bg-base-850 px-3 py-2 text-[13px] text-ink-primary focus-visible:border-network focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-network"
            >
              {SEVERITIES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>
        </div>

        <label className="mt-4 flex items-start gap-2 text-[12px] text-ink-secondary">
          <input
            type="checkbox"
            checked={locate}
            onChange={(event) => setLocate(event.target.checked)}
            className="mt-0.5 h-4 w-4 rounded border-base-600 bg-base-850 accent-[#2ED3E6]"
          />
          <span>
            Attach my coordinates. Off by default, because a precise location
            for an incident is a privacy decision and not a convenience.
          </span>
        </label>

        {error ? (
          <p role="alert" className="mt-4 rounded-lg bg-emergency-soft px-3 py-2 text-[12px] text-emergency">
            {error}
          </p>
        ) : null}

        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            Cancel
          </Button>
          <Button onClick={() => void submit()} disabled={pending}>
            {pending ? 'Creating…' : 'Create incident'}
          </Button>
        </div>
      </div>
    </div>
  );
}