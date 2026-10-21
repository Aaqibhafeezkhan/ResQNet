import { readFile } from 'node:fs/promises';

export const COMMENT_MARKER = '<!-- resqnet-pr-assignment-bot -->';
export const RESOLUTION_MARKER = '<!-- resqnet-pr-assignment-bot:resolved -->';
const ACTIONS_BOT_LOGIN = 'github-actions[bot]';
export const LABELS = Object.freeze({
  awaiting: 'awaiting-issue-assignment',
  ok: 'contribution-policy-ok',
  exception: 'no-issue-required',
});

const MAX_ISSUE_REFERENCES = 10;

function isBot(user) {
  return user?.type === 'Bot' || /\[bot\]$/i.test(user?.login ?? '');
}

function isOwnComment(comment) {
  return comment?.user?.login?.toLowerCase() === ACTIONS_BOT_LOGIN && comment.body?.includes(COMMENT_MARKER);
}

function stripCode(text) {
  return text
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/~~~[\s\S]*?~~~/g, ' ')
    .replace(/`[^`\n]*`/g, ' ');
}

/** Extract deliberate same-repository issue references, excluding external forms. */
export function extractIssueNumbers(body = '') {
  const text = stripCode(String(body));
  const found = new Set();
  const patterns = [
    /\b(?:close[sd]?|fix(?:es|ed)?|resolve[sd]?)\s+(?:#(\d+)|([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)#\d+)\b/gi,
    /\brelated\s+issue\s*:\s*#(\d+)\b/gi,
    /\bissue\s+#(\d+)\b/gi,
  ];
  for (const pattern of patterns) {
    for (const match of text.matchAll(pattern)) {
      // External owner/repository#number references are intentionally ignored.
      if (match[1] && !match[2]) found.add(Number(match[1]));
    }
  }
  return [...found].filter((number) => Number.isSafeInteger(number) && number > 0).slice(0, MAX_ISSUE_REFERENCES);
}

function labelNames(labels = []) {
  return labels.map((label) => typeof label === 'string' ? label : label?.name).filter(Boolean);
}

function hasLabel(labels, name) {
  return labels.some((label) => label.toLowerCase() === name.toLowerCase());
}

function policyResult({ pull, issues, exception, bypass }) {
  if (bypass) return { state: 'bypass', issues, body: null, labels: [] };
  if (exception) return { state: 'exception', issues, body: null, labels: [LABELS.exception, LABELS.ok] };
  if (!issues.length) {
    return {
      state: 'missing', issues,
      body: `${COMMENT_MARKER}\n👋 Thanks for opening this pull request, @${pull.user.login}!\n\nCould you link the issue this PR addresses using \`Closes #123\` or explain why an issue is not needed? A maintainer can add the \`${LABELS.exception}\` label for documentation or maintenance work.\n\nThis is an advisory reminder and does not block review.\n\n— ResQNet Contributor Bot`,
      labels: [LABELS.awaiting],
    };
  }
  const assignedElsewhere = issues.filter((item) => item.valid && item.assignedTo.length && !item.assigned);
  const missing = issues.filter((item) => !item.valid || !item.assigned);
  if (!missing.length) {
    return {
      state: 'ok', issues,
      body: `${COMMENT_MARKER}\n${RESOLUTION_MARKER}\n✅ Assignment verified! Thanks @${pull.user.login}; you’re assigned to ${issues.map((item) => `issue #${item.number}`).join(', ')}. The assignment reminder is resolved. Your PR can continue through normal review.\n\n— ResQNet Contributor Bot`,
      labels: [LABELS.ok],
    };
  }
  const lines = missing.map((item) => {
    if (!item.valid) return `- Issue #${item.number} could not be verified in this repository. Please check the reference.`;
    if (item.assignedTo.length) return `- Issue #${item.number} is assigned to ${item.assignedTo.map((name) => `@${name}`).join(', ')}. Please coordinate with them or a maintainer.`;
    return `- Issue #${item.number} is not assigned to you. Open it and comment \`/assign\`, then wait for confirmation from the Contributor Bot.`;
  });
  return {
    state: assignedElsewhere.length && assignedElsewhere.length === missing.length ? 'coordination' : 'warning', issues,
    body: `${COMMENT_MARKER}\n👋 Hey @${pull.user.login}! Thanks for contributing to ResQNet.\n\nI checked the issue references in this PR:\n${lines.join('\n')}\n\nThis is an advisory reminder and does not block review. Please coordinate with a maintainer if the assignment is incorrect.\n\n— ResQNet Contributor Bot`,
    labels: [LABELS.awaiting],
  };
}

function isMaintainer(pull) {
  return ['OWNER', 'MEMBER', 'COLLABORATOR'].includes(pull.author_association);
}

function safePull(pull, expectedRepo) {
  return Boolean(pull && Number.isSafeInteger(pull.number) && pull.number > 0
    && pull.user?.login && pull.base?.repo?.full_name?.toLowerCase() === expectedRepo.toLowerCase());
}

/** Validate and reconcile a single PR. API methods are injected for unit testing. */
export async function validatePull(pull, api, options = {}) {
  const repository = options.repository ?? pull?.base?.repo?.full_name;
  if (!safePull(pull, repository)) throw new Error('Invalid pull request or repository identity');
  if (pull.state !== 'open' || (pull.draft && options.allowDraft !== true)) return { state: 'skipped' };
  if (isBot(pull.user)) return { state: 'skipped' };

  const numbers = extractIssueNumbers(pull.body ?? '');
  const labels = labelNames(pull.labels);
  // A label name alone is not authorization. Verify the latest label event and
  // its actor's repository permission on every run, including workflow reruns.
  const exception = hasLabel(labels, LABELS.exception)
    && await api.exceptionAuthorized(pull.number, LABELS.exception);
  const bypass = isMaintainer(pull) || options.trustedAutomation === true;
  const issues = [];
  for (const number of numbers) {
    let issue;
    try {
      issue = await api.issue(number);
    } catch (error) {
      if (error.status === 404) {
        issues.push({ number, valid: false, assignedTo: [], assigned: false });
        continue;
      }
      throw error;
    }
    const expected = repository.toLowerCase();
    const actual = issue?.repository?.full_name ?? expected;
    const valid = issue && issue.number === number && actual.toLowerCase() === expected && !issue.pull_request;
    const assignedTo = valid ? (issue.assignees ?? []).map((user) => user.login).filter(Boolean) : [];
    issues.push({ number, valid: Boolean(valid), assignedTo, assigned: assignedTo.some((login) => login.toLowerCase() === pull.user.login.toLowerCase()) });
  }

  const result = policyResult({ pull, issues, exception, bypass });
  const existing = await api.comments(pull.number);
  const hasWarning = existing.some(isOwnComment);
  const desiredBody = result.state === 'ok' && !hasWarning ? null : result.body;
  await reconcileComment(pull.number, desiredBody, api, existing);
  await reconcileLabels(pull.number, labels, result.labels, api);
  return result;
}

export async function handleIssueEvent(event, api, options = {}) {
  if (!event?.issue || !Number.isSafeInteger(event.issue.number)) throw new Error('Invalid issue event');
  if (event.issue.pull_request && ['assigned', 'unassigned'].includes(event.action)) {
    return revalidateIssue(event.issue.number, api, options);
  }
  if (event.issue.pull_request && ['labeled', 'unlabeled'].includes(event.action)) {
    if (event.label?.name === LABELS.exception && !await actorCanManageException(api, event.sender?.login)) {
      return { state: 'ignored', reason: 'unauthorized-exception-label-change' };
    }
    const pull = await api.pull(event.issue.number);
    return validatePull(pull, api, { ...options, exceptionApproved: event.label?.name === LABELS.exception && event.action === 'labeled' });
  }
  if (!event.issue.pull_request && ['assigned', 'unassigned'].includes(event.action)) {
    return revalidateIssue(event.issue.number, api, options);
  }
  return { state: 'ignored' };
}

export async function handlePullEvent(event, api, options = {}) {
  if (!event?.pull_request?.number) throw new Error('Invalid pull request event');
  const pull = await api.pull(event.pull_request.number);
  return validatePull(pull, api, {
    ...options,
    allowDraft: pull.draft === true && ['opened', 'reopened'].includes(event.action),
    trustedAutomation: isBot(pull.user),
  });
}

async function reconcileComment(number, desiredBody, api, comments) {
  const own = comments.filter(isOwnComment);
  if (!desiredBody) {
    for (const comment of own) {
      if (!comment.body.includes(RESOLUTION_MARKER)) {
        await api.updateComment(number, comment.id, `${COMMENT_MARKER}\n${RESOLUTION_MARKER}\n✅ This assignment reminder is no longer needed. The PR is covered by a maintainer-approved policy exception or bypass.\n\n— ResQNet Contributor Bot`);
      }
    }
    return;
  }
  if (own.length) {
    const [first, ...duplicates] = own;
    if (first.body !== desiredBody) await api.updateComment(number, first.id, desiredBody);
    // Clean up only duplicates carrying this bot's unique marker.
    for (const duplicate of duplicates) await api.updateComment(number, duplicate.id, `${COMMENT_MARKER}\nSuperseded duplicate reminder.`);
    return;
  }
  await api.comment(number, desiredBody);
}

async function reconcileLabels(number, current, desired, api) {
  const managed = [LABELS.awaiting, LABELS.ok];
  for (const label of managed) {
    const present = hasLabel(current, label);
    const wanted = desired.some((item) => item.toLowerCase() === label.toLowerCase());
    if (present && !wanted) await api.removeLabel(number, label);
    if (wanted && !present) {
      await api.ensureLabel(label);
      await api.addLabel(number, label);
    }
  }
}

/** Revalidate open PRs referencing an issue after assignment changes. */
export async function revalidateIssue(issueNumber, api, options = {}) {
  if (!Number.isSafeInteger(issueNumber) || issueNumber < 1) throw new Error('Invalid issue number');
  const prs = await api.searchPulls(issueNumber);
  const outcomes = [];
  for (const candidate of prs.slice(0, 100)) {
    const pull = await api.pull(candidate.number);
    if (pull?.state === 'open' && extractIssueNumbers(pull.body ?? '').includes(issueNumber)) {
      let allowDraft = false;
      if (pull.draft) {
        const comments = await api.comments(pull.number);
        allowDraft = comments.some(isOwnComment);
        if (!allowDraft) {
          outcomes.push({ state: 'skipped' });
          continue;
        }
      }
      outcomes.push(await validatePull(pull, api, { ...options, allowDraft }));
    }
  }
  return outcomes;
}

export async function actorCanManageException(api, actor) {
  if (!actor || isBot({ login: actor, type: 'User' })) return false;
  const permission = await api.permission(actor);
  return ['admin', 'maintain', 'write'].includes((permission?.permission ?? '').toLowerCase());
}

function githubApi(token, owner, repo) {
  const base = `https://api.github.com/repos/${owner}/${repo}`;
  async function request(path, method = 'GET', body) {
    const response = await fetch(`${base}${path}`, {
      method,
      headers: { Accept: 'application/vnd.github+json', Authorization: `Bearer ${token}`, 'X-GitHub-Api-Version': '2022-11-28', ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    if (!response.ok) {
      const error = new Error(`GitHub API ${method} ${path}: HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    return response.status === 204 ? null : response.json();
  }
  async function pages(path, maxPages = 10) {
    const results = [];
    for (let page = 1; page <= maxPages; page++) {
      const batch = await request(`${path}${path.includes('?') ? '&' : '?'}per_page=100&page=${page}`);
      results.push(...batch);
      if (batch.length < 100) break;
    }
    return results;
  }
  async function actorPermission(login) {
    const response = await fetch(`https://api.github.com/repos/${owner}/${repo}/collaborators/${encodeURIComponent(login)}/permission`, { headers: { Accept: 'application/vnd.github+json', Authorization: `Bearer ${token}`, 'X-GitHub-Api-Version': '2022-11-28' } });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`GitHub API collaborator permission: HTTP ${response.status}`);
    return response.json();
  }
  async function exceptionLabelActor(pullNumber, labelName) {
    const events = [];
    for (let page = 1; page <= 10; page++) {
      const batch = await request(`/issues/${pullNumber}/events?per_page=100&page=${page}`);
      events.push(...batch);
      if (batch.length < 100) {
        const latest = events
          .filter((item) => ['labeled', 'unlabeled'].includes(item.event)
            && item.label?.name?.toLowerCase() === labelName.toLowerCase())
          .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at))[0];
        return latest?.event === 'labeled' ? latest.actor : null;
      }
    }
    // A full bounded scan may have omitted a newer authorization or revocation.
    return null;
  }
  const pull = async (number) => {
    const pr = await request(`/pulls/${number}`);
    return { ...pr, base: { ...pr.base, repo: pr.base.repo } };
  };
  return {
    issue: (number) => request(`/issues/${number}`),
    pull,
    comments: (number) => pages(`/issues/${number}/comments`),
    comment: (number, body) => request(`/issues/${number}/comments`, 'POST', { body }),
    updateComment: (_number, id, body) => request(`/issues/comments/${id}`, 'PATCH', { body }),
    ensureLabel: async (name) => {
      try { await request(`/labels/${encodeURIComponent(name)}`); }
      catch (error) {
        if (error.status !== 404) throw error;
        try { await request('/labels', 'POST', { name, color: name === LABELS.awaiting ? 'FBCA04' : '0E8A16', description: 'Managed by ResQNet PR assignment validation' }); }
        catch (createError) { if (createError.status !== 422) throw createError; }
      }
    },
    addLabel: (number, name) => request(`/issues/${number}/labels`, 'POST', { labels: [name] }),
    removeLabel: async (number, name) => {
      try { await request(`/issues/${number}/labels/${encodeURIComponent(name)}`, 'DELETE'); }
      catch (error) { if (error.status !== 404) throw error; }
    },
    searchPulls: async (issueNumber) => {
      const query = encodeURIComponent(`repo:${owner}/${repo} is:pr is:open #${issueNumber}`);
      const response = await fetch(`https://api.github.com/search/issues?q=${query}&per_page=100`, { headers: { Accept: 'application/vnd.github+json', Authorization: `Bearer ${token}`, 'X-GitHub-Api-Version': '2022-11-28' } });
      if (!response.ok) throw new Error(`GitHub API search: HTTP ${response.status}`);
      return (await response.json()).items.map((item) => ({ number: item.number }));
    },
    exceptionAuthorized: async (pullNumber, labelName) => {
      const actor = await exceptionLabelActor(pullNumber, labelName);
      if (!actor?.login || isBot(actor)) return false;
      const permission = await actorPermission(actor.login);
      return ['admin', 'maintain', 'write'].includes((permission?.permission ?? '').toLowerCase());
    },
    permission: actorPermission,
  };
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const event = JSON.parse(await readFile(process.env.GITHUB_EVENT_PATH, 'utf8'));
  const [owner, repo] = process.env.GITHUB_REPOSITORY.split('/');
  const api = githubApi(process.env.GH_TOKEN, owner, repo);
  const options = { repository: `${owner}/${repo}` };
  if (event.pull_request) {
    await handlePullEvent(event, api, options);
  } else if (event.issue) {
    await handleIssueEvent(event, api, options);
  }
}
