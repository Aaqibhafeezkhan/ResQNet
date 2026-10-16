/**
 * Typed client for the coordination gateway.
 *
 * Deliberately hand-written rather than generated. The surface is small, the
 * types are the documentation, and a generated client would hide the two
 * behaviours that matter most here: every response is checked for a structured
 * error, and a gateway failure is never allowed to look like an empty
 * incident list.
 */

export type IncidentStatus = 'NEW' | 'TRIAGED' | 'IN_PROGRESS' | 'RESOLVED' | 'DISMISSED';

export type Category =
  | 'MEDICAL'
  | 'FLOODING'
  | 'FIRE'
  | 'TRAPPED'
  | 'MISSING_PERSON'
  | 'SUPPLIES'
  | 'GENERAL'
  | 'UNKNOWN';

/**
 * Nullable fields are absent, not null.
 *
 * <p>Spring is configured with `default-property-inclusion: non_null`, so a
 * null field is omitted from the JSON entirely and arrives here as
 * `undefined`. Declaring these `| null` and guarding with `!== null` compiles
 * cleanly and is wrong at runtime, because `undefined !== null` is true. That
 * is how the dashboard came to render "NaN% confidence": the handset never
 * sends `advisoryConfidence`, so every device-created incident hit the guard
 * and then multiplied `undefined`.
 *
 * <p>Declared `?:` with a union so both readings are caught, and so the type
 * cannot drift away from what the server actually sends.
 */
export interface IncidentSummary {
  id: number;
  reference: string;
  title: string;
  category: Category;
  categoryLabel: string;
  severity: number;
  status: IncidentStatus;
  statusLabel: string;
  /** True when the category came from the on-device classifier. */
  advisory: boolean;
  advisoryConfidence?: number | null;
  /** Distinguishes "no location was sent" from "a location was sent". */
  locationShared: boolean;
  latitude?: number | null;
  longitude?: number | null;
  sourceDeviceId?: string | null;
  gatewayDeviceId?: string | null;
  createdAt: string;
  updatedAt: string;
  acknowledgedAt?: string | null;
  resolvedAt?: string | null;
  assignedTo?: number | null;
  messageCount: number;
}

export interface MessageDelivery {
  messageId: string;
  originDeviceId: string;
  destinationDeviceId?: string | null;
  state: string;
  priority: string;
  createdAt: number;
  expiresAt: number;
  attempts: number;
  detail?: string | null;
  simulated: boolean;
}

export interface AuditRow {
  occurredAt: string;
  actor: string;
  actorRole: string;
  action: string;
  targetType?: string | null;
  targetId?: string | null;
  detail?: string | null;
}

export interface IncidentDetail {
  incident: IncidentSummary;
  messages: MessageDelivery[];
  audit: AuditRow[];
}

export interface Overview {
  open: number;
  critical: number;
  total: number;
  newIncidents: number;
  resolved: number;
}

export interface SimulationRun {
  runId: string;
  topology: string;
  nodeCount: number;
  seed: number;
  startedAt: string;
  originated: number;
  acknowledged: number;
  deliveryRate: number;
  medianLatencyMillis?: number | null;
  p95LatencyMillis?: number | null;
  amplification?: number | null;
  partitions: number;
  /**
   * Always true. Sent explicitly rather than implied by the endpoint so that a
   * dashboard merging generated runs with field incidents cannot lose the
   * distinction further downstream.
   */
  simulated: true;
}

export interface SimulationEvent {
  atEpochMillis: number;
  round: number;
  node: string;
  kind: string;
  messageId?: string | null;
  hopCount: number;
  reason?: string | null;
  detail: string;
  simulated: true;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface LoginResponse {
  token: string;
  username: string;
  role: 'OBSERVER' | 'RESPONDER' | 'ADMIN';
}

/**
 * A responder account that work can be assigned to.
 *
 * <p>Kept separate from {@link Identity}, which describes who is signed in.
 */
export interface ResponderAccount {
  id: number;
  username: string;
  role: string;
}

export interface Identity {
  name: string;
  role: string;
  canMutateIncidents: boolean;
  canReadAudit: boolean;
}

/** Thrown for any non-2xx response. Carries the server's stable error code. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }

  /** True when re-authenticating could plausibly fix it. */
  get isAuthFailure(): boolean {
    return this.status === 401;
  }
}

const TOKEN_KEY = 'resqnet.token';
const IDENTITY_KEY = 'resqnet.identity';

export const authStorage = {
  token(): string | null {
    try {
      return window.localStorage.getItem(TOKEN_KEY);
    } catch {
      // Private browsing modes throw on storage access. An in-memory session
      // is degraded but functional, which beats a blank screen.
      return null;
    }
  },
  save(token: string, identity: LoginResponse): void {
    try {
      window.localStorage.setItem(TOKEN_KEY, token);
      window.localStorage.setItem(IDENTITY_KEY, JSON.stringify(identity));
    } catch {
      /* ignored: see above */
    }
  },
  identity(): LoginResponse | null {
    try {
      const raw = window.localStorage.getItem(IDENTITY_KEY);
      return raw ? (JSON.parse(raw) as LoginResponse) : null;
    } catch {
      return null;
    }
  },
  clear(): void {
    try {
      window.localStorage.removeItem(TOKEN_KEY);
      window.localStorage.removeItem(IDENTITY_KEY);
    } catch {
      /* ignored */
    }
  },
};

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = authStorage.token();
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(path, { ...init, headers });
  } catch {
    // A gateway that cannot be reached is not an empty queue. Saying so
    // matters more than the retry, because an operator acting on an empty
    // incident list during an outage is the dangerous outcome.
    throw new ApiError(0, 'GATEWAY_UNREACHABLE',
      'The coordination gateway could not be reached.');
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as
      | { code?: string; message?: string }
      | null;
    throw new ApiError(
      response.status,
      problem?.code ?? 'UNKNOWN',
      problem?.message ?? `Request failed with status ${response.status}`,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  login(username: string, password: string): Promise<LoginResponse> {
    return request<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
  },

  whoami(): Promise<Identity> {
    return request<Identity>('/api/v1/auth/whoami', { method: 'POST' });
  },

  incidents(status?: IncidentStatus, page = 0, size = 25): Promise<Page<IncidentSummary>> {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) query.set('status', status);
    return request<Page<IncidentSummary>>(`/api/v1/incidents?${query.toString()}`);
  },

  search(query: string, page = 0, size = 25): Promise<Page<IncidentSummary>> {
    const params = new URLSearchParams({ q: query, page: String(page), size: String(size) });
    return request<Page<IncidentSummary>>(`/api/v1/incidents/search?${params.toString()}`);
  },

  overview(): Promise<Overview> {
    return request<Overview>('/api/v1/incidents/overview');
  },

  incident(reference: string): Promise<IncidentDetail> {
    return request<IncidentDetail>(
      `/api/v1/incidents/${encodeURIComponent(reference)}`,
    );
  },

  updateStatus(
    reference: string,
    status: IncidentStatus,
    note?: string,
  ): Promise<IncidentSummary> {
    return request<IncidentSummary>(
      `/api/v1/incidents/${encodeURIComponent(reference)}/status`,
      {
        method: 'PATCH',
        body: JSON.stringify({ status, note: note ?? null, severity: null }),
      },
    );
  },

  /**
   * Creates an incident by hand.
   *
   * <p>Distinct from a device report throughout the system: no advisory flag,
   * no confidence, and the audit row records a person rather than a gateway.
   */
  createIncident(input: {
    title: string;
    category: Category;
    severity?: number;
    latitude?: number;
    longitude?: number;
  }): Promise<IncidentSummary> {
    return request<IncidentSummary>('/api/v1/incidents', {
      method: 'POST',
      body: JSON.stringify(input),
    });
  },

  assign(reference: string, accountId: number): Promise<IncidentSummary> {
    return request<IncidentSummary>(
      `/api/v1/incidents/${encodeURIComponent(reference)}/assign`,
      { method: 'POST', body: JSON.stringify({ accountId }) },
    );
  },

  /**
   * Accounts available for assignment.
   *
   * <p>Admin only on the server, so the caller has to handle a 403 rather than
   * assuming the list exists.
   */
  accounts(): Promise<ResponderAccount[]> {
    return request<ResponderAccount[]>('/api/v1/admin/accounts');
  },

  simulationRuns(limit = 10): Promise<SimulationRun[]> {
    return request<SimulationRun[]>(`/api/v1/simulations?limit=${limit}`);
  },

  simulationEvents(runId: string, limit = 500): Promise<SimulationEvent[]> {
    return request<SimulationEvent[]>(
      `/api/v1/simulations/${encodeURIComponent(runId)}/events?limit=${limit}`,
    );
  },
};
