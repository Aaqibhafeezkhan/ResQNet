# ResQNet Guardian

Guardian is the repository's readiness and contribution-policy reporter. It
turns pull-request state into one deterministic readiness state, keeps one
summary comment up to date, and gives maintainers a small set of auditable
commands.

Guardian is advisory. It **never** merges, approves, closes, deploys, or opens a
pull request. It does not change repository settings, branch protection,
secrets, or labels; label management remains with the existing assignment bot.
A human maintainer always makes the final decision.

## Readiness states

| State | Meaning | What happens next |
|---|---|---|
| `AWAITING_ASSIGNMENT` | The PR has no linked issue, a linked issue is unverified, or the author is not assigned to every linked issue. | Link the issue with `Closes #123` and claim it with `/assign`. For work that genuinely needs no issue, a maintainer applies `no-issue-required`. |
| `NEEDS_CHANGES` | The PR is a draft, has a merge conflict, or a write-access human reviewer (or the enabled AI reviewer) requested changes. | Mark it ready, resolve the conflict, or address the review. |
| `CHECKS_RUNNING` | At least one check is queued or in progress, or checks have not reported yet shortly after the head commit. | Wait. Guardian re-evaluates when checks complete. |
| `CHECKS_FAILED` | At least one check run or legacy commit status did not succeed. | Fix the failure and push a new commit. Guardian does not re-run CI by itself. |
| `INFRASTRUCTURE_BLOCKED` | Guardian cannot obtain trustworthy check, review, mergeability, or policy data. Also used when no checks were reported after the grace window. | A maintainer runs `/guardian recheck` after the data source recovers. Verified `checks.no-data` false positives can be dismissed. |
| `AWAITING_AI_REVIEW` | AI review is enabled and the configured reviewer has not reviewed the current PR. | Wait for the configured reviewer, or ask a maintainer whether the integration is expected. |
| `AWAITING_HUMAN_REVIEW` | Checks pass and no write-access human reviewer has approved. | Wait for a human reviewer. Guardian does not request reviewers or approve. |
| `READY_FOR_MAINTAINER_REVIEW` | Checks pass and at least one write-access human reviewer approved. | A maintainer makes the final review and merge decision. |
| `STALE` | An otherwise awaiting or ready PR has had no activity beyond `GUARDIAN_STALE_DAYS`. | Confirm whether the work is still active. Guardian does not close stale PRs. |
| `MERGED` | The PR was merged. | No action. Guardian did not merge it. |
| `CLOSED` | The PR was closed without merging. | No action. Guardian did not close it. |
| `UNKNOWN` | Guardian could not determine a trustworthy state from an unexpected shape. | Inspect the workflow log and run `/guardian recheck`. |

### Precedence

Guardian reports the first actionable condition in this order:

1. merged or closed
2. assignment policy unknown or blocked
3. draft, merge conflict, human change request, or enabled-AI change request
4. failed checks
5. missing or unknown check data
6. unknown review data or unknown mergeability
7. checks still running
8. AI review pending
9. human approval (`READY_FOR_MAINTAINER_REVIEW`)
10. otherwise `AWAITING_HUMAN_REVIEW`
11. `STALE` overrides an awaiting or ready state when the inactivity window has
    passed

A dismissed finding stops blocking, except for unknown-data findings, which are
never dismissible. A dismissal of a check or review finding is scoped to the
head commit; a new push requires a new decision.

## What Guardian checks

### Linked-issue policy

Guardian reuses the repository's deliberate-reference parser, so `Closes #123`,
`Fixes #123`, `Resolves #123`, `Related issue: #123`, and `Issue #123` count.
Code fences, inline code, external `owner/repo#123` references, and references
to pull requests do not count.

Every linked issue must exist in this repository and be assigned to the PR
author. Guardian does not assign issues. The existing Contributor Bot still
owns `/assign`, `/unassign`, the two-open-issue limit, and the
security-sensitive assignment restriction.

When an issue is assigned or unassigned, Guardian finds affected PRs from the
issue timeline's `cross-referenced` events and re-checks only PRs whose bodies
contain a deliberate reference to that issue. This avoids a text search that
would match unrelated numbers such as `#9` inside `#90`.

Exceptions:

- Maintainer-authored PRs (`OWNER`, `MEMBER`, `COLLABORATOR`) are compliant.
- Trusted automation (a GitHub App bot) is compliant.
- `no-issue-required` is accepted only when the most recent label event for that
  label was a `labeled` event by a non-bot actor with repository `write`,
  `maintain`, or `admin` permission. A later unlabel revokes it. If the event
  history is too long to scan safely, Guardian treats the exception as
  unverified rather than guessing.

### Checks

Guardian reads the latest check runs and legacy commit statuses for the PR head
commit. Reruns supersede older results with the same name and app.

- `failure`, `timed_out`, `cancelled`, `action_required`, `startup_failure`, and
  `stale` block as `CHECKS_FAILED`.
- `success`, `neutral`, and `skipped` pass. If every reported check was skipped,
  Guardian adds an informational warning.
- Queued or in-progress checks block as `CHECKS_RUNNING`.
- A completed check with no conclusion is unknown and blocks.
- **No checks found is never a pass.** Shortly after the head commit it is
  `CHECKS_RUNNING`; after `GUARDIAN_NO_CHECKS_GRACE_MINUTES` it becomes
  `INFRASTRUCTURE_BLOCKED`. This is deliberate: a path-filtered or missing
  workflow must be confirmed by a maintainer, not silently treated as green.

### Reviews

Guardian uses the latest non-pending review per user. A later change request
supersedes an earlier approval. Bot reviews are ignored unless AI review is
explicitly enabled. AI review is advisory and never substitutes for human
approval.

Only reviews from users with repository `write`, `maintain`, or `admin`
permission affect readiness. GitHub lets users with read access submit reviews,
but those reviews do not count toward the effective review decision; Guardian
applies the same rule so a drive-by approval cannot produce a false ready state
and a drive-by change request cannot block a pull request. Reviews from
untrusted users are reported as informational findings. If reviewer
permissions cannot be read, readiness is `INFRASTRUCTURE_BLOCKED`. A trusted
human `APPROVED` review moves the PR to `READY_FOR_MAINTAINER_REVIEW`; a trusted
human `CHANGES_REQUESTED` review moves it to `NEEDS_CHANGES`.

### Mergeability and staleness

A known merge conflict is `NEEDS_CHANGES`. Unknown mergeability is
`INFRASTRUCTURE_BLOCKED`, not ready. Staleness is based on `updated_at` and
`GUARDIAN_STALE_DAYS`; it never masks assignment, check, or infrastructure
blockers.

## Comments, notifications, and commands

Guardian keeps exactly one summary comment per PR. It is identified by a hidden
marker and is edited in place. Duplicate Guardian summaries are marked
superseded, not deleted. The summary carries a compact, base64-encoded state
record so reruns do not need a database.

If `GUARDIAN_NOTIFY=true`, Guardian posts one notification comment when a PR
genuinely transitions into `READY_FOR_MAINTAINER_REVIEW`. It does not announce
PRs that were already ready when notifications were enabled, and it deduplicates
per head commit. Set `GUARDIAN_MAINTAINERS` to a comma-separated list of logins
if the notification should mention people; otherwise it says "Maintainers".

Commands are accepted from pull-request comments:

| Command | Who can use it | Effect |
|---|---|---|
| `/guardian status` | Anyone except bots | Replies with the last recorded state. Does not write the summary. |
| `/guardian explain` | Anyone except bots | Evaluates live and explains current findings without persisting. |
| `/guardian recheck` | Repository `write`, `maintain`, or `admin` | Re-evaluates and updates the summary. |
| `/guardian dismiss <finding-id> <reason>` | Repository `write`, `maintain`, or `admin` | Dismisses one verified check or review false positive. Reason required, max 500 characters. |
| `/guardian help` | Anyone except bots | Shows command help. |

`/guardian merge` does not exist. Guardian refuses it explicitly.

Policy findings cannot be dismissed; use the issue-assignment flow or the
maintainer-approved `no-issue-required` label. Unknown-data findings
(`checks.api-unavailable`, `review.api-unavailable`, `pr.mergeability-unknown`,
and similar) cannot be dismissed; restore trustworthy data and recheck.

## Dry-run and rollout

Guardian is safe to merge before it is enabled. The workflow defaults to:

```text
GUARDIAN_DRY_RUN=true
GUARDIAN_NOTIFY=false
GUARDIAN_CHECK_RUN=true
```

In dry-run, Guardian reads GitHub data and logs the state it would publish, but
does not create or edit comments, labels, notifications, or check runs. The
workflow uses the repository-scoped `GITHUB_TOKEN`; no personal access token is
required.

Recommended rollout:

1. Merge the workflow and scripts to the default branch.
2. Leave `GUARDIAN_DRY_RUN=true`. Inspect several workflow logs, including a
   fork PR and a docs-only PR.
3. Set the repository variable `GUARDIAN_DRY_RUN=false`.
4. Inspect the first live summary comment and advisory check run.
5. Enable `GUARDIAN_NOTIFY=true` only after the summary is trusted.
6. If the advisory check run is not wanted, set `GUARDIAN_CHECK_RUN=false`.
   Guardian's summary still works.

Guardian does not add required checks or branch protection. Do not make the
advisory Guardian check a required check without a separate maintainer decision.

### Repository variables

| Variable | Default | Purpose |
|---|---|---|
| `GUARDIAN_DRY_RUN` | `true` | `false` enables summary, notification, and check-run writes. |
| `GUARDIAN_NOTIFY` | `false` | `true` posts one ready notification per ready head commit. |
| `GUARDIAN_CHECK_RUN` | `true` | Publish the advisory check run. |
| `GUARDIAN_AI_REVIEW` | `false` | Track the configured AI reviewer and wait for a completed review before the ready state. |
| `GUARDIAN_AI_REVIEW_LOGIN` | `coderabbitai[bot]` | Login used to recognise AI reviews. |
| `GUARDIAN_STALE_DAYS` | `30` | Inactivity window for `STALE`. |
| `GUARDIAN_NO_CHECKS_GRACE_MINUTES` | `15` | How long missing checks remain `CHECKS_RUNNING`. |
| `GUARDIAN_MAINTAINERS` | empty | Comma-separated logins mentioned in ready notifications. |
| `GUARDIAN_BOT_LOGIN` | `github-actions[bot]` | Login that owns the summary comment. |
| `GUARDIAN_MAX_PULLS` | `25` | Maximum PRs evaluated by a schedule or manual run. |
| `GUARDIAN_MERGEABLE_RETRY_MS` | `800` | One retry when GitHub has not calculated mergeability yet. |

## CodeRabbit (not installed)

CodeRabbit is **not installed** in this repository. Guardian does not install
apps, assume CodeRabbit is present, or treat its absence as a failure. AI review
is disabled by default, and even when enabled it is advisory.

To enable it manually, a maintainer must:

1. Install the CodeRabbit GitHub App on the repository through CodeRabbit's own
   onboarding flow.
2. Confirm it reviews pull requests from forks as well as branches in this
   repository, subject to CodeRabbit's plan and configuration.
3. Optionally add a `.coderabbit.yaml` in a separate, reviewed PR. Guardian does
   not create or edit that file.
4. Set `GUARDIAN_AI_REVIEW=true` and, if the login differs from
   `coderabbitai[bot]`, set `GUARDIAN_AI_REVIEW_LOGIN`.
5. Inspect a few reviews before relying on the `AWAITING_AI_REVIEW` state.

If CodeRabbit is uninstalled later, turn `GUARDIAN_AI_REVIEW` off. Otherwise
Guardian will correctly remain in `AWAITING_AI_REVIEW` because no AI review will
arrive.

## Security model

- The workflow runs on `pull_request_target`, `pull_request_review`, `issues`,
  `issue_comment`, `check_suite` (non-GitHub Actions apps), `workflow_run`
  (named CI workflows), a daily schedule, and manual dispatch.
- It checks out only the trusted default branch and never fetches or executes
  pull-request head code. The comment body is data passed to the script, never a
  shell command.
- The token is the short-lived repository `GITHUB_TOKEN` with `contents: read`,
  `issues: write`, `pull-requests: write`, `checks: write`, and `statuses:
  read`. No PAT or long-lived secret is used.
- Check names, reviewer logins, and dismissal reasons are escaped before being
  rendered. Mentioning in notifications is limited to validated logins from
  `GUARDIAN_MAINTAINERS`.
- Review approvals and change requests count only after Guardian confirms the
  reviewer has repository write access. A permission lookup failure blocks
  readiness instead of assuming trust.
- Commands that change state verify the actor's repository permission through
  the collaborator-permission endpoint. Bots are ignored.
- The summary marker is trusted only when the comment is authored by the
  configured bot login with type `Bot`. A user cannot forge a summary.

### Public-repository `pull_request_target` policy

GitHub is tightening `pull_request_target` for public repositories. The existing
repository bots already use it with a default-branch-only checkout and no PR
code execution. If GitHub's policy requires an explicit repository opt-in,
a maintainer must approve that setting. Do not "fix" a blocked event by checking
out PR code or by adding an unrestricted PAT.

## Root cause of the PR #58, #60, and #61 failures

The logs for those PRs showed the workflow token with `Issues: write` and
`PullRequests: read`. Ordinary issue comments succeeded with `issues: write`,
but PR-object writes failed:

- PR #58: `POST /repos/{owner}/{repo}/issues/58/comments` → HTTP 403
- PR #60 and #61: `POST /repos/{owner}/{repo}/issues/{n}/labels` → HTTP 403

The fix is `pull-requests: write` on `pr-assignment-bot.yml`, while retaining
`issues: write`. A personal access token is not required and is not used. The
shared transport now classifies 403 responses as `permission`, `rate_limit`, or
`authorization` and logs the request id and accepted permissions without logging
the token.

## Testing

Run the deterministic suites locally:

```bash
node --test \
  .github/scripts/contributor-bot.test.mjs \
  .github/scripts/pr-assignment-bot.test.mjs \
  .github/scripts/lib/*.test.mjs \
  .github/scripts/guardian/*.test.mjs
```

The tests use mocked GitHub responses and replay fixtures in
`.github/scripts/guardian/fixtures/`. They cover the lifecycle, fork PR
resolution, policy bypass attempts, untrusted markers, unknown-data blocking,
dismissal scoping, check reruns, and the 403 shapes observed in PRs #58, #60, and
#61. These are deterministic tests, not live GitHub integration tests.

A read-only live dry-run can be performed locally with an authenticated `gh`
token and `GUARDIAN_DRY_RUN=true`; Guardian will not write. Live workflow
verification for fork PRs requires the workflow to exist on the default branch.

## Limitations

- Guardian is advisory; it cannot enforce a merge without branch protection,
  and this repository currently has no branch protection or rulesets.
- Missing or unreadable data blocks rather than passing. This can require a
  maintainer dismissal while an infrastructure problem is fixed.
- Docs-only and path-filtered PRs may legitimately have no checks. They remain
  `INFRASTRUCTURE_BLOCKED` until a maintainer dismisses `checks.no-data`.
- External check apps such as GitGuardian may report after the initial event.
  Guardian refreshes on non-GitHub-Actions `check_suite` events and on the daily
  schedule; use `/guardian recheck` for an immediate refresh.
- The summary is a comment, not a database. If the comment is deleted, Guardian
  recreates it and loses dismissal history.
- If the issue timeline API fails during an assignment event, Guardian falls
  back to GitHub text search. That search can over-select PRs, but each PR is
  still evaluated against its own body and no false readiness is produced.
- Guardian does not inspect PR diffs for security-sensitive files. It flags
  security-sensitive linked issues and leaves the review decision to humans.
- The daily schedule and manual run evaluate only open PRs, capped by
  `GUARDIAN_MAX_PULLS`.
