import assert from 'node:assert/strict';
import { test } from 'node:test';
import { evaluateChecks } from './checks.mjs';
import {
  checkRun,
  createApi,
  makePull,
  NOW,
} from './test-helpers.mjs';

async function run({ checkRuns = [], statuses = [], errors = {}, commitDate = '2026-10-22T11:55:00Z', now = NOW } = {}) {
  const api = createApi({ checkRuns, statuses, errors, commitDate });
  const result = await evaluateChecks({ api, pull: makePull(), now, noChecksGraceMinutes: 15 });
  return { result, api };
}

test('all successful checks pass', async () => {
  const { result } = await run({ checkRuns: [checkRun('test'), checkRun('lint')] });
  assert.equal(result.status, 'passed');
  assert.equal(result.total, 2);
  assert.ok(result.findings.some((item) => item.id === 'checks.passed'));
});

test('a failed check blocks even when another check is still running', async () => {
  const { result } = await run({
    checkRuns: [
      checkRun('test', { conclusion: 'failure' }),
      checkRun('lint', { status: 'in_progress', conclusion: null }),
    ],
  });
  assert.equal(result.status, 'failed');
  assert.deepEqual(result.failing, ['test']);
  assert.ok(result.findings.some((item) => item.id.startsWith('checks.failed.')));
});

test('queued and in-progress checks are running', async () => {
  const { result } = await run({
    checkRuns: [
      checkRun('test', { status: 'queued', conclusion: null }),
      checkRun('lint', { status: 'in_progress', conclusion: null }),
    ],
  });
  assert.equal(result.status, 'running');
  assert.equal(result.pending.length, 2);
});

test('no checks shortly after the head commit are still running', async () => {
  const { result } = await run({
    checkRuns: [],
    commitDate: '2026-10-22T11:50:00Z',
  });
  assert.equal(result.status, 'running');
  assert.ok(result.findings.some((item) => item.id === 'checks.pending'));
});

test('no checks after the grace window are blocked, never a pass', async () => {
  const { result } = await run({
    checkRuns: [],
    commitDate: '2026-10-22T10:00:00Z',
  });
  assert.equal(result.status, 'blocked');
  assert.ok(result.findings.some((item) => item.id === 'checks.no-data'));
});

test('no checks and unavailable commit data produces unknown', async () => {
  const { result } = await run({
    checkRuns: [],
    errors: { getCommit: { status: 403, message: 'Resource not accessible by integration' } },
  });
  assert.equal(result.status, 'unknown');
  assert.ok(result.findings.some((item) => item.id === 'checks.api-unavailable'));
});

test('a failed check-runs API call produces unknown', async () => {
  const { result } = await run({
    errors: { listCheckRunsForRef: { status: 500, message: 'boom' } },
  });
  assert.equal(result.status, 'unknown');
  assert.ok(result.findings.some((item) => item.id === 'checks.api-unavailable'));
});

test('a failed combined-status API call produces unknown', async () => {
  const { result } = await run({
    errors: { getCombinedStatus: { status: 403, message: 'Resource not accessible by integration' } },
  });
  assert.equal(result.status, 'unknown');
});

test('the latest rerun of the same check supersedes an older failure', async () => {
  const oldFailure = checkRun('test', { conclusion: 'failure' });
  oldFailure.completed_at = '2026-10-22T11:00:00Z';
  const newSuccess = checkRun('test', { conclusion: 'success' });
  newSuccess.completed_at = '2026-10-22T11:30:00Z';
  const { result } = await run({ checkRuns: [oldFailure, newSuccess] });
  assert.equal(result.status, 'passed');
});

test('legacy commit statuses participate in the same decision', async () => {
  const { result } = await run({
    checkRuns: [checkRun('test')],
    statuses: [{ context: 'security/scan', state: 'failure', creator: { login: 'gitguardian' } }],
  });
  assert.equal(result.status, 'failed');
  assert.ok(result.findings.some((item) => item.id.startsWith('checks.failed.')));
});

test('Guardian own advisory check is ignored by external id', async () => {
  const { result } = await run({
    checkRuns: [
      checkRun('test'),
      checkRun('ResQNet Guardian / readiness (advisory)', { conclusion: 'action_required', externalId: 'resqnet-guardian:90' }),
    ],
  });
  assert.equal(result.status, 'passed');
  assert.equal(result.total, 1);
});

test('a check that merely reuses Guardian name is not ignored', async () => {
  const { result } = await run({
    checkRuns: [checkRun('ResQNet Guardian / readiness (advisory)', { conclusion: 'failure' })],
  });
  assert.equal(result.status, 'failed');
});

test('a completed check without a conclusion is unknown and blocking', async () => {
  const { result } = await run({
    checkRuns: [checkRun('test', { conclusion: null })],
  });
  assert.equal(result.status, 'unknown');
  assert.ok(result.findings.some((item) => item.id === 'checks.unknown-conclusion'));
});

test('all skipped checks pass with an explicit warning', async () => {
  const { result } = await run({ checkRuns: [checkRun('test', { conclusion: 'skipped' })] });
  assert.equal(result.status, 'passed');
  assert.ok(result.findings.some((item) => item.id === 'checks.all-skipped'));
});
