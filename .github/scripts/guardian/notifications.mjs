import { GUARDIAN_NOTIFICATION_MARKER_PREFIX } from '../lib/markers.mjs';
import { STATES } from './constants.mjs';
import { codeSpan, escapeInline, isSafeLogin, shortSha } from './util.mjs';

function notificationMarker(headSha) {
  return `${GUARDIAN_NOTIFICATION_MARKER_PREFIX}${headSha} -->`;
}

function existingNotification(comments, headSha, botLogin) {
  const marker = notificationMarker(headSha);
  return comments.find((comment) => comment?.user?.login === botLogin
    && comment?.user?.type === 'Bot'
    && typeof comment.body === 'string'
    && comment.body.startsWith(marker));
}

export function buildNotificationBody({ pull, headSha, approvedBy = [], maintainers = [] }) {
  const mentions = maintainers.filter(isSafeLogin).map((login) => `@${login}`);
  const audience = mentions.length ? mentions.join(' ') : 'Maintainers';
  return [
    notificationMarker(headSha),
    `## Guardian: ready for maintainer review`,
    '',
    `${audience}: pull request #${pull.number} has passed its checks and has human approval. It is ready for a final maintainer review.`,
    '',
    `- Head: ${codeSpan(shortSha(headSha))}`,
    ...(approvedBy.length ? [`- Human approval: ${approvedBy.map((login) => codeSpan(login)).join(', ')}`] : []),
    '',
    'Guardian did not approve or merge this pull request. Required checks and branch protection, when configured, remain authoritative.',
  ].join('\n');
}

/**
 * Notify once per genuine transition into READY_FOR_MAINTAINER_REVIEW.
 * A missing previous state is not treated as a transition, so enabling
 * notifications does not announce every already-ready pull request.
 */
export async function maybeNotify({
  api,
  pull,
  outcome,
  previousState,
  comments = [],
  enabled = false,
  dryRun = false,
  botLogin = 'github-actions[bot]',
  maintainers = [],
}) {
  if (!enabled) return { action: 'disabled' };
  if (outcome.state !== STATES.READY_FOR_MAINTAINER_REVIEW) return { action: 'not-ready' };
  if (previousState !== STATES.READY_FOR_MAINTAINER_REVIEW && previousState == null) {
    return { action: 'no-previous-state' };
  }
  if (previousState === STATES.READY_FOR_MAINTAINER_REVIEW) return { action: 'no-transition' };

  const headSha = pull?.head?.sha;
  if (!headSha) return { action: 'missing-head-sha' };
  if (existingNotification(comments, headSha, botLogin)) return { action: 'already-notified' };

  const body = buildNotificationBody({
    pull,
    headSha,
    approvedBy: outcome.approvedBy ?? [],
    maintainers,
  });
  if (dryRun) return { action: 'would-notify', body };
  await api.createPullComment(pull.number, body);
  return { action: 'notified' };
}
