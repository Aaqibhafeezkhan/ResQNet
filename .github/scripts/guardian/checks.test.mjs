import assert from 'node:assert/strict';
import { test } from 'node:test';
import { evaluateChecks } from './checks.mjs';
import { loadChangedFiles, loadRepositoryPolicy } from './repository-policy.mjs';
import {
  checkRun,
  createApi,
  makePull,
  NOW,
} from './test-helpers.mjs';

const WEB_FILES = [{ filename: 'apps/web/src/components/App.tsx' }];
const ANDROID_FILES = [{ filename: 'apps/android/app/src/main/AndroidManifest.xml' }];
const GUARDIAN_FILES = [{ filename: '.github/scripts/guardian/index.mjs' }];
const WEB_CHECK = 'typecheck + test + build';
const ANDROID_CHECK = 'assemble + unit tests';
const GUARDIAN_CHECK = 'guardian + bot tests';

async function run({
  checkRuns = [],
  statuses = [],
  files = [],
  branchProtection = null,
  rulesets = [],
  rulesetDetails = {},
  errors = {},
  commitDate = '2026-10-22T11:55:00Z',
  now = NOW,
} = {}) {
  const pull = makePull();
  const api = createApi({
    checkRuns,
    statuses,
    errors,
    commitDate,
    files,
    branchProtection,
    rulesets,
    rulesetDetails,
  });
  const [changed, repositoryPolicy] = await Promise.all([
    loadChangedFiles({ api, pull }),
    loadRepositoryPolicy({ api, pull }),
  ]);
  const result = await evaluateChecks({
    api,
    pull,
    now,
    noChecksGraceMinutes: 15,
    changedFiles: changed.files,
    repositoryPolicy,
  });
  return { result, api };
}

test('all expected checks passing produces a pass', async () => {
  const { result } = await run({
    files: WEB_FILES,
    checkRuns: [
      checkRun(WEB_CHECK),
      checkRun('GitGuardian Security Checks', { app: 'gitguardian' }),
    ],
  });
  assert.equal(result.status, 'passed');
  assert.deepEqual(result.expected, [WEB_CHECK]);
  assert.deepEqual(result.missing, []);
  assert.ok(result.findings.some((item) => item.id === 'checks.passed'));
});

test('a required failed check blocks even when another required check is still running', async () => {
  const { result } = await run({
    files: [{ filename: 'packages/mesh-core/src/main/java/App.java' }],
    checkRuns: [
      checkRun('mesh core + simulator', { conclusion: 'failure' }),
      checkRun('benchmark smoke', { status: 'in_progress', conclusion: null }),
    ],
  });
  assert.equal(result.status, 'failed');
  assert.deepEqual(result.failing, ['mesh core + simulator']);
  assert.ok(result.findings.some((item) => item.id.startsWith('checks.failed.')));
});

test('queued and in-progress expected checks are running', async () => {
  const { result } = await run({
    files: WEB_FILES,
    checkRuns: [
      checkRun(WEB_CHECK, { status: 'queued', conclusion: null }),
      checkRun('GitGuardian Security Checks', { app: 'gitguardian' }),
    ],
  });
  assert.equal(result.status, 'running');
  assert.deepEqual(result.pending, [WEB_CHECK]);
});

test('a single passing GitGuardian check is not enough when Web CI is expected and missing', async () => {
  const { result } = await run({
    files: WEB_FILES,
    commitDate: '2026-10-22T10:00:00Z',
    checkRuns: [checkRun('GitGuardian Security Checks', { app: 'gitguardian' })],
  });
  assert.equal(result.status, 'blocked');
  assert.deepEqual(result.expected, [WEB_CHECK]);
  assert.deepEqual(result.missing, [WEB_CHECK]);
  assert.ok(result.findings.some((item) => item.id === 'checks.expected-missing'));
});

test('a single passing GitGuardian check is not enough when Android CI is missing', async () => {
  const { result } = await run({
    files: ANDROID_FILES,
    commitDate: '2026-10-22T10:00:00Z',
    checkRuns: [checkRun('GitGuardian Security Checks', { app: 'gitguardian' })],
  });
  assert.equal(result.status, 'blocked');
  assert.deepEqual(result.missing, [ANDROID_CHECK, 'emulator instrumentation']);
});

test('a passing GitGuardian check is not enough when the Guardian test job is missing', async () => {
  const { result } = await run({
    files: GUARDIAN_FILES,
    commitDate: '2026-10-22T10:00:00Z',
    checkRuns: [checkRun('GitGuardian Security Checks', { app: 'gitguardian' })],
  });
  assert.equal(result.status, 'blocked');
  assert.deepEqual(result.expected, [GUARDIAN_CHECK]);
  assert.deepEqual(result.missing, [GUARDIAN_CHECK]);
  assert.ok(result.findings.some((item) => item.id === 'checks.expected-missing'));
});

test('a failing Guardian test job blocks Guardian and bot changes', async () => {
  const { result } = await run({
    files: [{ filename: '.github/workflows/contributor-bot.yml' }],
    checkRuns: [checkRun(GUARDIAN_CHECK, { conclusion: 'failure' })],
  });
  assert.equal(result.status, 'failed');
  assert.deepEqual(result.expected, [GUARDIAN_CHECK]);
  assert.deepEqual(result.failing, [GUARDIAN_CHECK]);
  assert.ok(result.findings.some((item) => item.id.startsWith('checks.failed.')));
});

test('missing expected checks shortly after the head commit are pending, never ready', async () => {
  const { result } = await run({
    files: WEB_FILES,
    commitDate: '2026-10-22T11:50:00Z',
    checkRuns: [checkRun('GitGuardian Security Checks', { app: 'gitguardian' })],
  });
  assert.equal(result.status, 'running');
  assert.deepEqual(result.pending, [WEB_CHECK]);
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

test('an unavailable changed-file list produces unknown rather than guessing the policy', async () => {
  const { result } = await run({
    errors: { listPullFiles: { status: 403, message: 'Resource not accessible by integration' } },
  });
  assert.equal(result.status, 'unknown');
  assert.ok(result.findings.some((item) => item.id === 'checks.files-unavailable'));
});

test('the latest rerun of the same expected check supersedes an older failure', async () => {
  const oldFailure = checkRun(WEB_CHECK, { conclusion: 'failure' });
  oldFailure.completed_at = '2026-10-22T11:00:00Z';
  const newSuccess = checkRun(WEB_CHECK, { conclusion: 'success' });
  newSuccess.completed_at = '2026-10-22T11:30:00Z';
  const { result } = await run({ files: WEB_FILES, checkRuns: [oldFailure, newSuccess] });
  assert.equal(result.status, 'passed');
});

test('legacy commit statuses can satisfy an expected check', async () => {
  const { result } = await run({
    files: WEB_FILES,
    statuses: [{ context: WEB_CHECK, state: 'failure', creator: { login: 'legacy-ci' } }],
  });
  assert.equal(result.status, 'failed');
  assert.ok(result.findings.some((item) => item.id.startsWith('checks.failed.')));
});

test('a required failure blocks even when another check is still running', async () => {
  const { result } = await run({
    files: WEB_FILES,
    checkRuns: [
      checkRun(WEB_CHECK, { conclusion: 'failure' }),
      checkRun('GitGuardian Security Checks', { app: 'gitguardian', status: 'in_progress', conclusion: null }),
    ],
  });
  assert.equal(result.status, 'failed');
});

test('Guardian own advisory check is ignored by external id', async () => {
  const { result } = await run({
    files: WEB_FILES,
    checkRuns: [
      checkRun(WEB_CHECK),
      checkRun('ResQNet Guardian / readiness (advisory)', { conclusion: 'action_required', externalId: 'resqnet-guardian:90' }),
    ],
  });
  assert.equal(result.status, 'passed');
  assert.equal(result.total, 1);
});

test('a check that merely reuses Guardian name is optional and visible, not silently ignored', async () => {
  const { result } = await run({
    checkRuns: [checkRun('ResQNet Guardian / readiness (advisory)', { conclusion: 'failure' })],
  });
  assert.equal(result.status, 'passed');
  assert.ok(result.findings.some((item) => item.id.startsWith('checks.optional-failed.')));
});

test('a completed expected check without a conclusion is unknown and blocking', async () => {
  const { result } = await run({
    files: WEB_FILES,
    checkRuns: [checkRun(WEB_CHECK, { conclusion: null })],
  });
  assert.equal(result.status, 'unknown');
  assert.ok(result.findings.some((item) => item.id === 'checks.unknown-conclusion'));
});

test('an expected GitGuardian failure blocks as a security failure', async () => {
  const { result } = await run({
    files: WEB_FILES,
    checkRuns: [
      checkRun(WEB_CHECK),
      checkRun('GitGuardian Security Checks', { app: 'gitguardian', conclusion: 'failure' }),
    ],
  });
  assert.equal(result.status, 'failed');
  assert.ok(result.findings.some((item) => item.id === 'checks.security-failed'));
});

test('all expected checks skipped pass with an explicit warning', async () => {
  const { result } = await run({
    files: WEB_FILES,
    checkRuns: [checkRun(WEB_CHECK, { conclusion: 'skipped' })],
  });
  assert.equal(result.status, 'passed');
  assert.ok(result.findings.some((item) => item.id === 'checks.all-skipped'));
});

test('a Vercel authorization failure is an optional warning, not a required test failure', async () => {
  const { result } = await run({
    files: WEB_FILES,
    checkRuns: [
      checkRun(WEB_CHECK),
      checkRun('GitGuardian Security Checks', { app: 'gitguardian' }),
    ],
    statuses: [{
      context: 'Vercel',
      state: 'failure',
      target_url: 'https://vercel.com/git/authorize?team=x&type=github',
      creator: { login: 'vercel[bot]' },
    }],
  });
  assert.equal(result.status, 'passed');
  assert.deepEqual(result.failing, []);
  assert.ok(result.findings.some((item) => item.id.startsWith('checks.optional-failed.')));
  assert.ok(result.findings.some((item) => /Vercel authorization/.test(item.detail)));
});

test('a required Web failure is not hidden by an optional Vercel failure', async () => {
  const { result } = await run({
    files: WEB_FILES,
    checkRuns: [checkRun(WEB_CHECK, { conclusion: 'failure' })],
    statuses: [{
      context: 'Vercel',
      state: 'failure',
      target_url: 'https://vercel.com/git/authorize?team=x&type=github',
      creator: { login: 'vercel[bot]' },
    }],
  });
  assert.equal(result.status, 'failed');
  assert.deepEqual(result.failing, [WEB_CHECK]);
  assert.ok(result.findings.some((item) => item.id.startsWith('checks.failed.')));
  assert.ok(result.findings.some((item) => item.id.startsWith('checks.optional-failed.')));
});

test('branch protection required checks are added to the expected set', async () => {
  const { result } = await run({
    files: WEB_FILES,
    branchProtection: {
      required_status_checks: {
        contexts: ['protected/e2e'],
        checks: [],
      },
    },
    checkRuns: [checkRun(WEB_CHECK)],
    commitDate: '2026-10-22T10:00:00Z',
  });
  assert.equal(result.status, 'blocked');
  assert.ok(result.expected.includes('protected/e2e'));
  assert.ok(result.missing.includes('protected/e2e'));
});

test('ruleset required checks are added to the expected set', async () => {
  const { result } = await run({
    files: WEB_FILES,
    rulesets: [{
      id: 7,
      target: 'branch',
      enforcement: 'active',
      conditions: { ref_name: { include: ['refs/heads/main'] } },
    }],
    rulesetDetails: {
      7: {
        id: 7,
        rules: [{
          type: 'required_status_checks',
          parameters: {
            required_status_checks: [{ context: 'ruleset/e2e', integration_id: 42 }],
          },
        }],
      },
    },
    checkRuns: [checkRun(WEB_CHECK)],
    commitDate: '2026-10-22T10:00:00Z',
  });
  assert.equal(result.status, 'blocked');
  assert.ok(result.expected.includes('ruleset/e2e'));
});
