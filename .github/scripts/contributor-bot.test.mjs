import assert from 'node:assert/strict';
import { test } from 'node:test';
import { classify, handle } from './contributor-bot.mjs';

function setup(body, options = {}) {
  const issue = {
    number: 2, title: 'Backend: assign endpoint fails validation', state: 'open',
    assignees: [], labels: [], ...options.issue,
  };
  const event = {
    issue, comment: { body, user: { login: 'alice', type: 'User', id: 1 }, ...options.comment },
  };
  const replies = [];
  const calls = [];
  const api = {
    issue: async () => issue,
    comments: async () => options.comments ?? [],
    assignedOpenIssues: async () => options.active ?? [],
    comment: async (_number, text) => { replies.push(text); },
    assign: async (_number, login) => { calls.push(['assign', login]); issue.assignees.push({ login }); },
    unassign: async (_number, login) => { calls.push(['unassign', login]); issue.assignees = issue.assignees.filter((user) => user.login !== login); },
  };
  return { event, api, issue, replies, calls };
}

test('welcomes interest once per contributor', async () => {
  const state = setup("I'd like to work on this");
  await handle(state.event, state.api);
  assert.match(state.replies[0], /Comment `\/assign`/);
  assert.match(state.replies[0], /CONTRIBUTING\.md/);
  const again = setup('Can I take this issue?', { comments: [{ body: state.replies[0] }] });
  await handle(again.event, again.api);
  assert.equal(again.replies.length, 0);
});

test('assigns an unassigned issue and confirms', async () => {
  const state = setup('/assign');
  await handle(state.event, state.api);
  assert.deepEqual(state.calls, [['assign', 'alice']]);
  assert.match(state.replies[0], /assigned/);
});

test('does not add a second assignee, including issue #2 shape', async () => {
  const state = setup('/assign', { issue: { assignees: [{ login: 'AryanGupta833' }] } });
  await handle(state.event, state.api);
  assert.deepEqual(state.calls, []);
  assert.match(state.replies[0], /@AryanGupta833/);
  assert.match(state.replies[0], /good first issues/);
});

test('repeated assign is idempotent', async () => {
  const state = setup('/assign', { issue: { assignees: [{ login: 'alice' }] } });
  await handle(state.event, state.api);
  assert.deepEqual(state.calls, []);
  assert.match(state.replies[0], /already assigned/);
});

test('assigned user can unassign', async () => {
  const state = setup('/unassign', { issue: { assignees: [{ login: 'alice' }] } });
  await handle(state.event, state.api);
  assert.deepEqual(state.calls, [['unassign', 'alice']]);
  assert.match(state.replies[0], /unassigned/);
});

test('other users cannot unassign', async () => {
  const state = setup('/unassign', { issue: { assignees: [{ login: 'bob' }] } });
  await handle(state.event, state.api);
  assert.deepEqual(state.calls, []);
  assert.deepEqual(state.replies, []);
});

test('two open issue assignments are the limit', async () => {
  const state = setup('/assign', { active: [{ number: 3 }, { number: 4 }] });
  await handle(state.event, state.api);
  assert.deepEqual(state.calls, []);
  assert.match(state.replies[0], /2 open issues/);
});

test('closed issues, PR comments and bot comments are ignored', async () => {
  for (const options of [
    { issue: { state: 'closed' } },
    { issue: { pull_request: { url: 'https://example.test/pr' } } },
    { comment: { user: { login: 'github-actions[bot]', type: 'Bot' } } },
  ]) {
    const state = setup('/assign', options);
    await handle(state.event, state.api);
    assert.deepEqual(state.calls, []);
    assert.deepEqual(state.replies, []);
  }
});

test('security-labeled issues need a maintainer', async () => {
  const state = setup('/assign', { issue: { labels: [{ name: 'security' }] } });
  await handle(state.event, state.api);
  assert.deepEqual(state.calls, []);
  assert.match(state.replies[0], /maintainer/);
});

test('rejects invalid and malicious comment content', async () => {
  for (const body of ['/assign; rm -rf .', '/assign\n/unassign', 'please assign this to me ${github.token}', 'assign', '/assign @bob']) {
    assert.equal(classify(body), null);
    const state = setup(body);
    await handle(state.event, state.api);
    assert.deepEqual(state.calls, []);
    assert.deepEqual(state.replies, []);
  }
});

test('competing attempts under the issue concurrency lock never add a second assignee', async () => {
  const state = setup('/assign');
  let prior = Promise.resolve();
  const locked = (event) => {
    const task = prior.then(() => handle(event, state.api));
    prior = task;
    return task;
  };
  const bob = structuredClone(state.event);
  bob.comment.user = { login: 'bob', type: 'User', id: 2 };
  await Promise.all([locked(state.event), locked(bob)]);
  assert.deepEqual(state.calls, [['assign', 'alice']]);
  assert.match(state.replies[1], /@alice/);
});

test('a maintainer assignment appearing during API assignment is preserved', async () => {
  const state = setup('/assign');
  state.api.assign = async (_number, login) => {
    state.issue.assignees.push({ login: 'maintainer' }, { login });
  };
  await handle(state.event, state.api);
  assert.deepEqual(state.issue.assignees, [{ login: 'maintainer' }]);
  assert.deepEqual(state.calls, [['unassign', 'alice']]);
});

test('GitHub assignment refusal directs the user to a maintainer', async () => {
  const state = setup('/assign');
  state.api.assign = async () => { throw Object.assign(new Error('not assignable'), { status: 422 }); };
  await handle(state.event, state.api);
  assert.match(state.replies[0], /maintainer/);
});

test('bot does not react recursively to its own welcome', async () => {
  const state = setup("I'd like to contribute");
  await handle(state.event, state.api);
  state.event.comment = { body: state.replies[0], user: { login: 'github-actions[bot]', type: 'Bot' } };
  await handle(state.event, state.api);
  assert.equal(state.replies.length, 1);
});
