import { DEFAULT_AI_REVIEW_LOGIN, SEVERITIES } from './constants.mjs';
import { codeSpan, escapeInline, isBot, isSameLogin } from './util.mjs';

const WRITE_PERMISSIONS = new Set(['admin', 'maintain', 'write']);

function finding(id, severity, title, detail) {
  return { id, severity, title, detail };
}

function reviewTime(review) {
  return Date.parse(review?.submitted_at ?? '') || 0;
}

function latestPerUser(reviews) {
  const latest = new Map();
  for (const review of reviews) {
    const login = review?.user?.login;
    if (!login) continue;
    const state = String(review?.state ?? '').toUpperCase();
    if (state === 'PENDING') continue;
    const key = login.toLowerCase();
    const current = latest.get(key);
    if (!current || reviewTime(review) >= reviewTime(current)) latest.set(key, review);
  }
  return [...latest.values()];
}

function describeLogins(logins) {
  return logins.map((login) => codeSpan(login)).join(', ');
}

/**
 * Reviews from users without repository write access do not affect GitHub's
 * effective review decision. Guardian applies the same rule so a drive-by
 * approval cannot produce a false ready state, and a drive-by change request
 * cannot block an otherwise ready pull request.
 */
async function classifyReviewers(api, reviewers) {
  const trusted = new Map();
  for (const reviewer of reviewers) {
    const login = reviewer.user.login;
    const key = login.toLowerCase();
    if (trusted.has(key)) continue;
    let permission;
    try {
      permission = await api.getCollaboratorPermission(login);
    } catch {
      return { trusted, error: true };
    }
    const level = String(permission?.permission ?? '').toLowerCase();
    trusted.set(key, WRITE_PERMISSIONS.has(level));
  }
  return { trusted, error: false };
}

/**
 * Summarise effective reviews. Only the latest non-pending review per user
 * counts, so an approval supersedes an earlier comment and a later change
 * request supersedes an earlier approval.
 */
export async function evaluateReviews({
  api,
  pull,
  aiReviewEnabled = false,
  aiReviewLogin = DEFAULT_AI_REVIEW_LOGIN,
}) {
  let reviews;
  try {
    reviews = await api.listReviews(pull.number);
  } catch (error) {
    return {
      status: 'unknown',
      approvedBy: [],
      changesRequestedBy: [],
      aiReviewed: false,
      aiChangesRequestedBy: [],
      findings: [
        finding('review.api-unavailable', SEVERITIES.BLOCKER, 'Review data is unavailable',
          `Guardian could not read reviews for this pull request (HTTP ${error?.status ?? 'unknown'}). Readiness cannot be determined from incomplete data.`),
      ],
    };
  }

  const latest = latestPerUser(Array.isArray(reviews) ? reviews : []);
  const author = pull?.user?.login ?? '';
  const decisionStates = new Set(['APPROVED', 'CHANGES_REQUESTED']);
  const humanCandidates = latest.filter((review) => !isBot(review.user)
    && !isSameLogin(review.user?.login, author)
    && decisionStates.has(String(review.state).toUpperCase()));
  const ai = latest.filter((review) => isSameLogin(review.user?.login, aiReviewLogin));

  const reviewerTrust = await classifyReviewers(api, humanCandidates);
  if (reviewerTrust.error) {
    return {
      status: 'unknown',
      approvedBy: [],
      changesRequestedBy: [],
      aiReviewed: false,
      aiChangesRequestedBy: [],
      findings: [
        finding('review.api-unavailable', SEVERITIES.BLOCKER, 'Reviewer permissions are unavailable',
          'Guardian could not verify whether reviewers have repository write access. Readiness cannot be determined from incomplete data.'),
      ],
    };
  }

  const human = humanCandidates.filter((review) => reviewerTrust.trusted.get(review.user.login.toLowerCase()));
  const untrusted = humanCandidates.filter((review) => !reviewerTrust.trusted.get(review.user.login.toLowerCase()));

  const approvedBy = human
    .filter((review) => String(review.state).toUpperCase() === 'APPROVED')
    .map((review) => review.user.login);
  const changesRequestedBy = human
    .filter((review) => String(review.state).toUpperCase() === 'CHANGES_REQUESTED')
    .map((review) => review.user.login);
  const aiChangesRequested = ai
    .filter((review) => String(review.state).toUpperCase() === 'CHANGES_REQUESTED')
    .map((review) => review.user.login);
  const aiReviewed = ai.some((review) => ['APPROVED', 'CHANGES_REQUESTED', 'COMMENTED'].includes(
    String(review.state).toUpperCase(),
  ));

  const findings = [];

  if (untrusted.length) {
    findings.push(finding(
      'review.untrusted-reviewers',
      SEVERITIES.INFO,
      'Reviews without write access were ignored',
      `${untrusted.length} review(s) came from users without repository write access. They do not count toward approval or changes requested.`,
    ));
  }

  if (changesRequestedBy.length) {
    findings.push(finding(
      'review.changes-requested',
      SEVERITIES.BLOCKER,
      'Changes were requested by a human reviewer',
      `${describeLogins(changesRequestedBy)} requested changes. Address the review and ask for a fresh review; Guardian does not dismiss reviews on its own.`,
    ));
  }

  if (aiReviewEnabled) {
    if (aiChangesRequested.length) {
      findings.push(finding(
        'review.ai-changes-requested',
        SEVERITIES.BLOCKER,
        'The configured AI reviewer requested changes',
        `${describeLogins(aiChangesRequested)} requested changes. Guardian does not treat AI output as human approval.`,
      ));
    }
    if (!aiReviewed) {
      findings.push(finding(
        'review.ai-pending',
        SEVERITIES.INFO,
        'AI review has not completed',
        `Waiting for a review from ${codeSpan(aiReviewLogin)}.`,
      ));
    } else {
      findings.push(finding(
        'review.ai-reviewed',
        SEVERITIES.INFO,
        'AI review completed',
        `${codeSpan(aiReviewLogin)} has reviewed this pull request. AI review is advisory and never substitutes for human approval.`,
      ));
    }
  }

  if (approvedBy.length) {
    findings.push(finding(
      'review.approved',
      SEVERITIES.INFO,
      'Human approval present',
      `${describeLogins(approvedBy)} approved this pull request. A maintainer still decides whether to merge.`,
    ));
  } else if (!changesRequestedBy.length) {
    findings.push(finding(
      'review.no-human-approval',
      SEVERITIES.INFO,
      'Waiting for human review',
      'No human reviewer has approved the current pull request. Guardian does not approve or request reviewers automatically.',
    ));
  }

  let status = 'none';
  if (changesRequestedBy.length) status = 'changes-requested';
  else if (approvedBy.length) status = 'approved';

  return {
    status,
    approvedBy,
    changesRequestedBy,
    aiReviewed,
    aiChangesRequestedBy: aiChangesRequested,
    findings,
  };
}

export function formatReviewDetail(reviews) {
  if (!reviews) return 'Review data unavailable.';
  if (reviews.status === 'changes-requested') {
    return `Changes requested by ${escapeInline(reviews.changesRequestedBy.join(', '), { maxLength: 120 })}.`;
  }
  if (reviews.status === 'approved') {
    return `Approved by ${escapeInline(reviews.approvedBy.join(', '), { maxLength: 120 })}.`;
  }
  if (reviews.status === 'unknown') return 'Review data could not be verified.';
  return 'No human approval yet.';
}
