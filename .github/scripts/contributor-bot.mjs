import { readFile } from 'node:fs/promises';

const LIMIT = 2;
const HELP_URL = 'https://github.com/Gautam-aman/ResQNet/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22';
const GUIDE_URL = 'https://github.com/Gautam-aman/ResQNet/blob/main/CONTRIBUTING.md';
const MARKER = '<!-- resqnet-contributor-bot:interest:';

export function classify(body) {
  const message = body.trim();
  if (/^\/assign$/i.test(message)) return 'assign';
  if (/^\/unassign$/i.test(message)) return 'unassign';
  if (/^(?:i(?: would|’d|'d)? like to (?:work on this|contribute)|can i take this issue\??|please assign this to me)[.!?\s]*$/i.test(message)) return 'interest';
  return null;
}

function sensitive(issue) {
  return issue.labels.some((label) => /^(security|security-sensitive|vulnerability)$/i.test(typeof label === 'string' ? label : label.name))
    || /\b(?:security|vulnerabilit(?:y|ies)|cve-\d{4}|credential(?:s)?|secret(?:s)?)\b/i.test(issue.title);
}

export async function handle(event, api) {
  const { issue, comment } = event;
  if (!issue || !comment || issue.pull_request || issue.state !== 'open'
      || comment.user?.type === 'Bot' || /\[bot\]$/i.test(comment.user?.login ?? '')) return;
  const command = classify(comment.body ?? '');
  if (!command) return;
  const login = comment.user.login;
  const number = issue.number;
  const say = (body) => api.comment(number, body);

  // Re-read mutable issue state after the workflow's issue lock is acquired.
  const current = await api.issue(number);
  if (current.state !== 'open' || current.pull_request) return;

  if (command === 'interest') {
    const marker = `${MARKER}${login.toLowerCase()} -->`;
    const comments = await api.comments(number);
    if (comments.some((item) => item.user?.type === 'Bot' && item.body?.includes(marker))) return;
    await say(`${marker}\nHey @${login}! Thanks for your interest in ResQNet! 🚀\n\nFeel free to take this up. Comment \`/assign\` to claim the issue. Our [setup and contribution guide](${GUIDE_URL}) can help you get started. Looking forward to your PR!`);
    return;
  }

  const assignees = current.assignees.map((user) => user.login);
  if (command === 'unassign') {
    if (!assignees.includes(login)) return;
    await api.unassign(number, login);
    await say(`@${login}, you’ve been unassigned. Thanks for letting us know!`);
    return;
  }

  if (sensitive(current)) {
    await say(`Thanks for offering to help, @${login}. A maintainer handles assignments for this issue. Please ask here if you need guidance.`);
    return;
  }
  if (assignees.includes(login)) {
    await say(`@${login}, you’re already assigned to this issue.`);
    return;
  }
  if (assignees.length) {
    await say(`This issue is currently being worked on by @${assignees[0]}. Feel free to explore our [other open good first issues](${HELP_URL})!`);
    return;
  }
  const active = await api.assignedOpenIssues(login);
  if (active.filter((item) => !item.pull_request).length >= LIMIT) {
    await say(`@${login}, you already have ${LIMIT} open issues assigned. Please finish one or comment \`/unassign\` there before claiming another.`);
    return;
  }
  try {
    await api.assign(number, login);
  } catch (error) {
    if (error.status !== 422) throw error;
    await say(`@${login}, GitHub couldn't assign you automatically. Please ask a maintainer here for help.`);
    return;
  }
  // A maintainer can change assignees outside the workflow's concurrency lock.
  const after = await api.issue(number);
  if (after.assignees.some((user) => user.login !== login)) {
    await api.unassign(number, login);
    await say(`This issue is currently being worked on by @${after.assignees.find((user) => user.login !== login).login}. Feel free to explore our [other open good first issues](${HELP_URL})!`);
    return;
  }
  await say(`Thanks, @${login}! You’re assigned to this issue. Please link it in your PR and ask here if you need help.`);
}

function githubApi(token, owner, repo) {
  const base = `https://api.github.com/repos/${owner}/${repo}`;
  async function request(path, method = 'GET', body) {
    const response = await fetch(`${base}${path}`, {
      method,
      headers: {
        Accept: 'application/vnd.github+json',
        Authorization: `Bearer ${token}`,
        'X-GitHub-Api-Version': '2022-11-28',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    if (!response.ok) {
      const error = new Error(`GitHub API ${method} ${path}: HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    return response.status === 204 ? null : response.json();
  }
  async function pages(path) {
    const result = [];
    for (let page = 1; ; page++) {
      const batch = await request(`${path}${path.includes('?') ? '&' : '?'}per_page=100&page=${page}`);
      result.push(...batch);
      if (batch.length < 100) return result;
    }
  }
  return {
    issue: (number) => request(`/issues/${number}`),
    comments: (number) => pages(`/issues/${number}/comments`),
    assignedOpenIssues: (login) => pages(`/issues?state=open&assignee=${encodeURIComponent(login)}`),
    comment: (number, body) => request(`/issues/${number}/comments`, 'POST', { body }),
    assign: (number, login) => request(`/issues/${number}/assignees`, 'POST', { assignees: [login] }),
    unassign: (number, login) => request(`/issues/${number}/assignees`, 'DELETE', { assignees: [login] }),
  };
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const event = JSON.parse(await readFile(process.env.GITHUB_EVENT_PATH, 'utf8'));
  const [owner, repo] = process.env.GITHUB_REPOSITORY.split('/');
  await handle(event, githubApi(process.env.GH_TOKEN, owner, repo));
}
