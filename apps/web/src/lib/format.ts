/** Presentation helpers shared by both experiences. */

/** Compact relative time. Deliberately vague past a minute: "2h ago" is more
 *  useful in an incident queue than a precise timestamp nobody reads. */
export function relativeTime(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return '—';
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return '—';
  const deltaSeconds = Math.round((now - then) / 1000);
  const future = deltaSeconds < 0;
  const seconds = Math.abs(deltaSeconds);
  const text =
    seconds < 10
      ? 'just now'
      : seconds < 60
        ? `${seconds}s`
        : seconds < 3600
          ? `${Math.floor(seconds / 60)}m`
          : seconds < 86400
            ? `${Math.floor(seconds / 3600)}h`
            : `${Math.floor(seconds / 86400)}d`;
  if (text === 'just now') return text;
  return future ? `in ${text}` : `${text} ago`;
}

/** Absolute local time, to the minute. */
export function clockTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const value = new Date(iso);
  if (Number.isNaN(value.getTime())) return '—';
  return value.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/** Percentage with a sensible representation of an unmeasured value. */
export function percent(value: number | null | undefined, digits = 1): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return `${(value * 100).toFixed(digits)}%`;
}

/**
 * Milliseconds, or an explicit dash when nothing was measured.
 *
 * A fabricated zero is worse than an acknowledged gap: it reads as "instant",
 * which is a claim about a system that cannot be instant.
 */
export function millis(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value) || value < 0) {
    return '—';
  }
  if (value < 1000) return `${value} ms`;
  return `${(value / 1000).toFixed(2)} s`;
}

/** Truncates a device id for display while keeping it recognisable. */
export function shortId(value: string | null | undefined, head = 10): string {
  if (!value) return '—';
  return value.length <= head ? value : `${value.slice(0, head)}…`;
}

/** Human label for a delivery state, distinct from a hop count. */
export function deliveryStateLabel(state: string): string {
  switch (state) {
    case 'ACKNOWLEDGED':
      return 'Confirmed received';
    case 'DELIVERED':
      return 'Received';
    case 'SENT_TO_PEER':
      return 'Handed to a nearby device';
    case 'QUEUED':
      return 'Waiting for a nearby device';
    case 'RELAYED':
      return 'Relayed for someone else';
    case 'CREATED':
      return 'Composed';
    case 'EXPIRED':
      return 'Too old to send';
    case 'FAILED':
      return 'Could not be sent';
    default:
      return state.replace(/_/g, ' ').toLowerCase();
  }
}

/** Colour role for a delivery state. Colour is paired with a label always. */
export function deliveryStateTone(
  state: string,
): 'delivered' | 'pending' | 'emergency' | 'neutral' {
  if (state === 'ACKNOWLEDGED' || state === 'DELIVERED') return 'delivered';
  if (state === 'EXPIRED' || state === 'FAILED') return 'emergency';
  if (state === 'QUEUED' || state === 'SENT_TO_PEER' || state === 'CREATED') return 'pending';
  return 'neutral';
}

/** Severity label. Numeric severity is meaningless without words. */
export function severityLabel(severity: number): string {
  switch (severity) {
    case 3:
      return 'Critical';
    case 2:
      return 'Elevated';
    case 1:
      return 'Routine';
    default:
      return 'Unclassified';
  }
}
