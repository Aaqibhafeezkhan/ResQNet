/**
 * The live event stream.
 *
 * <p>Polling is still the source of truth. This exists to make the console feel
 * live and to close the fifteen-second window in which a new incident is
 * technically available but invisible.
 *
 * <p>Three rules that come from how this goes wrong in practice:
 *
 * <ul>
 *   <li><b>Every failure is the same failure.</b> An unreachable socket and a
 *       reachable one are both treated as "not connected", and the caller falls
 *       back to polling. There is no state in which the UI stops updating
 *       because a socket died.</li>
 *   <li><b>Reconnect always triggers a refresh.</b> The stream carries no
 *       replay, so anything missed while disconnected is only recoverable by
 *       asking the server again. Reconnecting without refreshing would leave a
 *       gap that never closes.</li>
 *   <li><b>The socket never carries a credential in the URL.</b> Browsers
 *       cannot set headers on a WebSocket, so the token goes in the
 *       subprotocol list; a query parameter would put it in history and in
 *       every proxy log on the way.</li>
 * </ul>
 *
 * <p>Exponential backoff with a ceiling, so a gateway that is down does not
 * get hammered by a room full of open dashboards.
 */

import type { IncidentDetail, IncidentSummary } from './api';

/** Subprotocol the server negotiates. */
export const PROTOCOL = 'resqnet.v1';

/** Prefix for the credential subprotocol. */
const CREDENTIAL_PREFIX = 'bearer.';

/** Backoff schedule, in milliseconds. */
const BACKOFF_MILLIS = [1_000, 2_000, 5_000, 10_000, 30_000];

/** An event frame from the gateway. */
export interface StreamEvent {
  type: string;
  reference: string | null;
  at: string;
  detail: string;
  incident: IncidentSummary | null;
}

export interface EventStreamOptions {
  /** Resolves the current bearer token, or null when signed out. */
  token: () => string | null;
  /** Called for each event. */
  onEvent: (event: StreamEvent) => void;
  /**
   * Called when the stream connects or disconnects. The caller is expected to
   * refresh on every transition, because a reconnect means something was
   * missed.
   */
  onConnectionChange?: (connected: boolean) => void;
  /** Overrides the endpoint, for tests. */
  url?: string;
}

export type EventStreamState = 'idle' | 'connecting' | 'open' | 'retrying';

export class EventStream {
  private socket: WebSocket | null = null;
  private attempt = 0;
  private timer: number | null = null;
  private stopped = false;
  private readonly options: EventStreamOptions;

  state: EventStreamState = 'idle';

  constructor(options: EventStreamOptions) {
    this.options = options;
  }

  /** Opens the stream. Safe to call more than once. */
  start(): void {
    this.stopped = false;
    this.open();
  }

  /**
   * Closes the stream and stops reconnecting.
   *
   * <p>Explicit rather than relying on the component unmounting, because a
   * tab that was backgrounded may never fire the same teardown path.
   */
  stop(): void {
    this.stopped = true;
    this.clearTimer();
    if (this.socket) {
      this.socket.onclose = null;
      this.socket.onerror = null;
      this.socket.onmessage = null;
      this.socket.onopen = null;
      if (this.socket.readyState === WebSocket.OPEN
        || this.socket.readyState === WebSocket.CONNECTING) {
        this.socket.close();
      }
      this.socket = null;
    }
    this.setState('idle', false);
  }

  private endpoint(): string {
    if (this.options.url) {
      return this.options.url;
    }
    const configured = import.meta.env.VITE_GATEWAY_WS as string | undefined;
    if (configured) {
      return configured;
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws/incidents`;
  }

  private open(): void {
    if (this.stopped) return;
    const token = this.options.token();
    if (!token) {
      // Signed out. Nothing to reconnect to, and retrying would be noise.
      this.setState('idle', false);
      return;
    }

    this.setState(this.attempt === 0 ? 'connecting' : 'retrying', false);

    let socket: WebSocket;
    try {
      socket = new WebSocket(this.endpoint(), [PROTOCOL, `${CREDENTIAL_PREFIX}${token}`]);
    } catch {
      this.scheduleRetry();
      return;
    }
    this.socket = socket;

    socket.onopen = () => {
      this.attempt = 0;
      this.setState('open', true);
    };

    socket.onmessage = (event: MessageEvent<string>) => {
      let parsed: StreamEvent;
      try {
        parsed = JSON.parse(event.data) as StreamEvent;
      } catch {
        // A frame we cannot parse is not worth tearing the socket down for.
        return;
      }
      if (parsed.type === 'pong' || parsed.type === 'connected') {
        return;
      }
      this.options.onEvent(parsed);
    };

    socket.onerror = () => {
      // onclose always follows, so retrying happens there.
    };

    socket.onclose = () => {
      this.socket = null;
      this.setState('retrying', false);
      this.scheduleRetry();
    };
  }

  private scheduleRetry(): void {
    if (this.stopped) return;
    this.clearTimer();
    const delay = BACKOFF_MILLIS[Math.min(this.attempt, BACKOFF_MILLIS.length - 1)];
    this.attempt += 1;
    this.timer = window.setTimeout(() => this.open(), delay);
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      window.clearTimeout(this.timer);
      this.timer = null;
    }
  }

  private setState(state: EventStreamState, connected: boolean): void {
    const changed = this.state !== state;
    this.state = state;
    if (changed) {
      this.options.onConnectionChange?.(connected);
    }
  }
}

/** True when an event should cause the incident list to be refreshed. */
export function shouldRefreshList(event: StreamEvent): boolean {
  return event.type.startsWith('incident.');
}

/** The incident carried by an event, when there is one. */
export function incidentFrom(event: StreamEvent): IncidentDetail['incident'] | null {
  return event.incident;
}