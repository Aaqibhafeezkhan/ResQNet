import assert from 'node:assert/strict';
import { test } from 'node:test';
import { evaluateReviews } from './reviews.mjs';
import { createApi, makePull, review } from './test-helpers.mjs';

function permissions(overrides = {}) {
  return {
    maintainer: { permission: 'write' },
    admin: { permission: 'admin' },
    driveby: { permission: 'read' },
    ...overrides,
  };
}

test('only reviewers with repository write access count toward approval', async () => {
  const api = createApi({
    reviews: [
      review('maintainer', 'APPROVED', '2026-10-21T10:00:00Z'),
      review('driveby', 'APPROVED', '2026-10-21T11:00:00Z'),
    ],
    permissions: permissions(),
  });
  const result = await evaluateReviews({ api, pull: makePull() });
  assert.equal(result.status, 'approved');
  assert.deepEqual(result.approvedBy, ['maintainer']);
  assert.ok(result.findings.some((finding) => finding.id === 'review.untrusted-reviewers'));
  assert.ok(!result.findings.some((finding) => finding.id === 'review.approved' && /driveby/.test(finding.detail)));
});

test('a drive-by change request cannot block an otherwise ready pull request', async () => {
  const api = createApi({
    reviews: [review('driveby', 'CHANGES_REQUESTED')],
    permissions: permissions(),
  });
  const result = await evaluateReviews({ api, pull: makePull() });
  assert.equal(result.status, 'none');
  assert.deepEqual(result.changesRequestedBy, []);
});

test('a write-access change request still blocks', async () => {
  const api = createApi({
    reviews: [review('maintainer', 'CHANGES_REQUESTED')],
    permissions: permissions(),
  });
  const result = await evaluateReviews({ api, pull: makePull() });
  assert.equal(result.status, 'changes-requested');
  assert.deepEqual(result.changesRequestedBy, ['maintainer']);
});

test('the latest trusted review per user wins', async () => {
  const api = createApi({
    reviews: [
      review('maintainer', 'CHANGES_REQUESTED', '2026-10-21T10:00:00Z'),
      review('maintainer', 'APPROVED', '2026-10-21T11:00:00Z'),
    ],
    permissions: permissions(),
  });
  const result = await evaluateReviews({ api, pull: makePull() });
  assert.equal(result.status, 'approved');
  assert.deepEqual(result.approvedBy, ['maintainer']);
  assert.deepEqual(result.changesRequestedBy, []);
});

test('an administrator approval counts', async () => {
  const api = createApi({
    reviews: [review('admin', 'APPROVED')],
    permissions: permissions(),
  });
  const result = await evaluateReviews({ api, pull: makePull() });
  assert.equal(result.status, 'approved');
  assert.deepEqual(result.approvedBy, ['admin']);
});

test('bots and the pull request author cannot approve', async () => {
  const api = createApi({
    reviews: [
      review('dependabot[bot]', 'APPROVED', '2026-10-21T10:00:00Z', 'Bot'),
      review('alice', 'APPROVED', '2026-10-21T11:00:00Z'),
    ],
    permissions: permissions({ alice: { permission: 'admin' } }),
  });
  const result = await evaluateReviews({ api, pull: makePull() });
  assert.equal(result.status, 'none');
  assert.deepEqual(result.approvedBy, []);
});

test('a permission lookup failure blocks rather than guessing', async () => {
  const api = createApi({
    reviews: [review('maintainer', 'APPROVED')],
    permissions: permissions(),
    errors: { getCollaboratorPermission: { status: 500, message: 'server error' } },
  });
  const result = await evaluateReviews({ api, pull: makePull() });
  assert.equal(result.status, 'unknown');
  assert.equal(result.approvedBy.length, 0);
  assert.ok(result.findings.some((finding) => finding.id === 'review.api-unavailable'));
});

test('commented reviews from read-access users do not create a finding', async () => {
  const api = createApi({
    reviews: [review('driveby', 'COMMENTED')],
    permissions: permissions(),
  });
  const result = await evaluateReviews({ api, pull: makePull() });
  assert.equal(result.status, 'none');
  assert.ok(!result.findings.some((finding) => finding.id === 'review.untrusted-reviewers'));
});

test('AI review is advisory and does not need a collaborator permission lookup', async () => {
  const api = createApi({
    reviews: [review('coderabbitai[bot]', 'COMMENTED', '2026-10-21T10:00:00Z', 'Bot')],
    permissions: permissions(),
  });
  const result = await evaluateReviews({
    api,
    pull: makePull(),
    aiReviewEnabled: true,
    aiReviewLogin: 'coderabbitai[bot]',
  });
  assert.equal(result.aiReviewed, true);
  assert.equal(result.status, 'none');
  assert.deepEqual(result.approvedBy, []);
  assert.ok(result.findings.some((finding) => finding.id === 'review.ai-reviewed'));
  assert.ok(!api.calls.some(([method]) => method === 'getCollaboratorPermission'));
});
