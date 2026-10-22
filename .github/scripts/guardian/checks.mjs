import {
  CHECK_RUN_EXTERNAL_ID_PREFIX,
  DEFAULT_NO_CHECKS_GRACE_MINUTES,
  SEVERITIES,
} from './constants.mjs';
import { ageInMinutes, codeSpan, escapeInline, slug } from './util.mjs';

const FAILURE_CONCLUSIONS = new Set([
  'failure',
  'timed_out',
  'cancelled',
  'action_required',
  'startup_failure',
  'stale',
]);
const PASSING_CONCLUSIONS = new Set(['success', 'neutral', 'skipped']);
const PENDING_STATUSES = new Set([
  'queued',
  'in_progress',
  'waiting',
  'requested',
  'pending',
]);

function finding(id, severity, title, detail) {
  return { id, severity, title, detail };
}

function timestamp(item) {
  return Date.parse(item?.completed_at ?? item?.started_at ?? item?.updated_at ?? '') || 0;
}

function latestByName(items, keyFor) {
  const latest = new Map();
  for (const item of items) {
    const key = keyFor(item);
    const current = latest.get(key);
    if (!current || timestamp(item) >= timestamp(current)) latest.set(key, item);
  }
  return [...latest.values()];
}

function isGuardianCheck(item) {
  // Only the external_id is attacker-independent. A pull-request workflow can
  // choose its own job names, so filtering by name would let a contributor
  // hide a failing check behind Guardian's advisory name.
  return String(item?.external_id ?? '').startsWith(CHECK_RUN_EXTERNAL_ID_PREFIX);
}

function normalizeCheckRun(run) {
  const status = String(run?.status ?? 'unknown').toLowerCase();
  const conclusion = run?.conclusion == null ? null : String(run.conclusion).toLowerCase();
  return {
    kind: 'check-run',
    name: String(run?.name ?? 'unnamed check'),
    status,
    conclusion,
    app: String(run?.app?.slug ?? run?.app?.name ?? 'unknown'),
  };
}

function normalizeStatus(status) {
  const state = String(status?.state ?? 'unknown').toLowerCase();
  return {
    kind: 'status',
    name: String(status?.context ?? 'unnamed status'),
    status: state === 'pending' ? 'pending' : 'completed',
    conclusion: state === 'success' ? 'success'
      : state === 'pending' ? null
        : state === 'failure' ? 'failure'
          : state === 'error' ? 'failure'
            : 'unknown',
    app: String(status?.creator?.login ?? 'legacy-status'),
  };
}

function classifyItem(item) {
  if (item.kind === 'check-run') {
    if (PENDING_STATUSES.has(item.status) || item.status === 'unknown') return 'pending';
    if (item.status !== 'completed') return 'pending';
    if (item.conclusion == null) return 'unknown';
    if (FAILURE_CONCLUSIONS.has(item.conclusion)) return 'failed';
    if (PASSING_CONCLUSIONS.has(item.conclusion)) return 'passed';
    return 'unknown';
  }
  if (item.status === 'pending') return 'pending';
  if (item.conclusion === 'success') return 'passed';
  if (item.conclusion === 'failure') return 'failed';
  return 'unknown';
}

function describeItems(items) {
  return items
    .map((item) => `${codeSpan(item.name)} (${escapeInline(item.conclusion ?? item.status, { maxLength: 40 })})`)
    .join(', ');
}

/**
 * Normalise check runs and legacy commit statuses into a readiness signal.
 *
 * A missing check set is never a pass. If no checks exist shortly after the
 * head commit, the run is still `running`; after the grace window it becomes
 * `blocked` so a maintainer must explicitly dismiss `checks.no-data`.
 */
export async function evaluateChecks({
  api,
  pull,
  now = Date.now(),
  noChecksGraceMinutes = DEFAULT_NO_CHECKS_GRACE_MINUTES,
}) {
  const sha = pull?.head?.sha;
  if (!sha) {
    return {
      status: 'unknown',
      findings: [
        finding('checks.api-unavailable', SEVERITIES.BLOCKER, 'Head commit is missing',
          'Guardian cannot read checks without a head commit SHA.'),
      ],
      total: 0,
      failing: [],
      pending: [],
    };
  }

  let checkRuns;
  let combinedStatus;
  try {
    [checkRuns, combinedStatus] = await Promise.all([
      api.listCheckRunsForRef(sha),
      api.getCombinedStatus(sha),
    ]);
  } catch (error) {
    return {
      status: 'unknown',
      findings: [
        finding('checks.api-unavailable', SEVERITIES.BLOCKER, 'Check data is unavailable',
          `Guardian could not read checks for ${codeSpan(sha.slice(0, 7))} (HTTP ${error?.status ?? 'unknown'}). Missing data is not treated as a pass.`),
      ],
      total: 0,
      failing: [],
      pending: [],
    };
  }

  const runs = latestByName(
    (Array.isArray(checkRuns) ? checkRuns : []).filter((run) => !isGuardianCheck(run)),
    (run) => `${run?.app?.id ?? 'app'}:${run?.name ?? 'unnamed'}`,
  ).map(normalizeCheckRun);
  const statuses = latestByName(
    Array.isArray(combinedStatus?.statuses) ? combinedStatus.statuses : [],
    (status) => String(status?.context ?? 'unnamed'),
  ).map(normalizeStatus);
  const items = [...runs, ...statuses];

  if (!items.length) {
    let committedAt = null;
    try {
      const commit = await api.getCommit(sha);
      committedAt = commit?.commit?.committer?.date ?? commit?.commit?.author?.date ?? null;
    } catch (error) {
      return {
        status: 'unknown',
        findings: [
          finding('checks.api-unavailable', SEVERITIES.BLOCKER, 'Check and commit data are unavailable',
            `Guardian found no checks and could not read the head commit (HTTP ${error?.status ?? 'unknown'}). Readiness cannot be treated as passing.`),
        ],
        total: 0,
        failing: [],
        pending: [],
      };
    }
    const age = ageInMinutes(committedAt, now);
    if (age != null && age <= noChecksGraceMinutes) {
      return {
        status: 'running',
        findings: [
          finding('checks.pending', SEVERITIES.INFO, 'Checks have not reported yet',
            `No check runs or commit statuses were reported for ${codeSpan(sha.slice(0, 7))} yet. The head commit is ${Math.floor(age)} minute(s) old.`),
        ],
        total: 0,
        failing: [],
        pending: [],
      };
    }
    return {
      status: 'blocked',
      findings: [
        finding('checks.no-data', SEVERITIES.BLOCKER, 'No checks were reported',
          `No check runs or commit statuses were found for ${codeSpan(sha.slice(0, 7))}${age == null ? '' : ` after ${Math.floor(age)} minute(s)`}. Guardian does not treat missing checks as a pass. A maintainer can dismiss this finding only after confirming the absence is expected.`),
      ],
      total: 0,
      failing: [],
      pending: [],
    };
  }

  const classified = items.map((item) => ({ ...item, outcome: classifyItem(item) }));
  const failing = classified.filter((item) => item.outcome === 'failed');
  const pending = classified.filter((item) => item.outcome === 'pending');
  const unknown = classified.filter((item) => item.outcome === 'unknown');
  const passed = classified.filter((item) => item.outcome === 'passed');
  const findings = [];

  if (failing.length) {
    for (const item of failing) {
      findings.push(finding(
        `checks.failed.${slug(`${item.app}-${item.name}`)}`,
        SEVERITIES.BLOCKER,
        `Check failed: ${escapeInline(item.name, { maxLength: 100 })}`,
        `GitHub reported ${codeSpan(item.conclusion ?? 'failure')} for ${codeSpan(item.name)}. Re-run the check after fixing the underlying failure; do not dismiss a real failure.`,
      ));
    }
  }

  if (unknown.length) {
    findings.push(finding(
      'checks.unknown-conclusion',
      SEVERITIES.BLOCKER,
      'Check data has an unknown conclusion',
      `Guardian could not classify ${describeItems(unknown)}. Unknown data is not treated as a pass.`,
    ));
  }

  if (pending.length) {
    findings.push(finding(
      'checks.pending',
      SEVERITIES.INFO,
      'Checks are still running',
      `Waiting for ${describeItems(pending)}.`,
    ));
  }

  if (!failing.length && !unknown.length && !pending.length) {
    findings.push(finding(
      'checks.passed',
      SEVERITIES.INFO,
      'Checks passed',
      `${passed.length} check(s) completed successfully.`,
    ));
    if (passed.length && passed.every((item) => item.conclusion === 'skipped')) {
      findings.push(finding(
        'checks.all-skipped',
        SEVERITIES.WARNING,
        'All reported checks were skipped',
        'GitHub reported only skipped checks for this head commit. Confirm that this is expected for the changed paths.',
      ));
    }
  }

  let status = 'passed';
  if (failing.length) status = 'failed';
  else if (unknown.length) status = 'unknown';
  else if (pending.length) status = 'running';

  return {
    status,
    findings,
    total: items.length,
    failing: failing.map((item) => item.name),
    pending: pending.map((item) => item.name),
  };
}
