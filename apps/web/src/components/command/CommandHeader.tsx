import { LogOut, Plus, ShieldAlert, Wifi, WifiOff } from 'lucide-react';

import { StatusChip } from '@/components/primitives/StatusChip';
import { useAuthStore } from '@/state/auth';

interface CommandHeaderProps {
  /** True when the gateway answered the last request. */
  connected: boolean;
  /** Set when the gateway could not be reached at all. */
  unreachable: boolean;
  /**
   * True when the live stream is open.
   *
   * <p>Shown separately from `connected` because the two mean different things
   * and conflating them is misleading. The dashboard keeps working without the
   * socket; it just refreshes on a timer instead of the instant something
   * happens. Saying "Live" while polling quietly would teach an operator that
   * the indicator is decorative.
   */
  live?: boolean;
  /** Set when the last refresh failed although the gateway answered. */
  stale?: boolean;
  /** Opens the manual incident reporter. */
  onCreateIncident?: (() => void) | undefined;
}

/**
 * Command centre header.
 *
 * <p>The connection state is stated in words. A small dot that happens to be
 * red is not a sufficient signal that the whole data source is unavailable,
 * which is the one thing an operator most needs to notice immediately.
 */
export function CommandHeader({
  connected,
  unreachable,
  live,
  stale = false,
  onCreateIncident,
}: CommandHeaderProps) {
  const identity = useAuthStore((state) => state.identity);
  const signOut = useAuthStore((state) => state.signOut);

  return (
    <header className="sticky top-0 z-30 border-b border-base-700 bg-base-900/90 backdrop-blur-md">
      <div className="mx-auto flex w-full max-w-[1600px] flex-wrap items-center gap-x-6 gap-y-3 px-5 py-3.5 sm:px-8">
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <span className="text-sm font-semibold tracking-tight text-ink-primary">
            ResQNet
          </span>
          <span className="text-xs text-ink-tertiary">Command centre</span>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {unreachable ? (
            <StatusChip tone="emergency" pulse>
              <WifiOff size={12} aria-hidden="true" />
              Gateway unreachable
            </StatusChip>
          ) : stale ? (
            <StatusChip tone="emergency">
              <ShieldAlert size={12} aria-hidden="true" />
              Data out of date
            </StatusChip>
          ) : connected ? (
            <StatusChip tone="delivered" pulse>
              <Wifi size={12} aria-hidden="true" />
              {live ? 'Live' : 'Polling'}
            </StatusChip>
          ) : (
            <StatusChip tone="pending">
              <ShieldAlert size={12} aria-hidden="true" />
              Connecting
            </StatusChip>
          )}

          {/*
            Reachable but not live is its own state. Silently dropping to a
            slower refresh is the kind of thing nobody notices until the moment
            it matters, so it is stated rather than hidden.
          */}
          {connected && !live && !unreachable && !stale ? (
            <span className="text-[11px] text-ink-tertiary">
              live stream unavailable; refreshing every 15s
            </span>
          ) : null}

          {identity ? (
            <span className="inline-flex items-center gap-2 rounded-full border border-base-600 px-2.5 py-1 text-[11px] font-medium text-ink-secondary">
              {identity.username}
              <span className="text-ink-tertiary">{identity.role.toLowerCase()}</span>
            </span>
          ) : null}

          {onCreateIncident ? (
            <button
              type="button"
              onClick={onCreateIncident}
              className="inline-flex items-center gap-1.5 rounded-lg border border-base-600 px-2.5 py-1.5 text-xs font-medium text-ink-secondary transition-colors hover:border-base-500 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              <Plus size={13} aria-hidden="true" />
              Report incident
            </button>
          ) : null}

          <button
            type="button"
            onClick={signOut}
            className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium text-ink-tertiary transition-colors hover:bg-base-800 hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
          >
            <LogOut size={13} aria-hidden="true" />
            Sign out
          </button>
        </div>
      </div>
    </header>
  );
}
