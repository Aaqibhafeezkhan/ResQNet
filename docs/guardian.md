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
| `CHECKS_FAILED` | At least one required check or a security check did not succeed. Optional provider failures remain visible warnings. | Fix the failure and push a new commit. Guardian does not re-run CI by itself. |
| `INFRASTRUCTURE_BLOCKED` | Guardian cannot obtain trustworthy check, review, mergeability, or policy data. Also used when expected checks are missing, or when no checks were reported after the grace window. | A maintainer runs `/guardian recheck` after the data source recovers. Only verified `checks.no-data` false positives can be dismissed. |
| `AWAITING_AI_REVIEW` | AI review is enabled and the configured reviewer has not reviewed the current head commit. | Wait for the configured reviewer, or ask a maintainer whether the integration is expected. Without the GitHub App, no independent AI review is happening. |
| `AWAITING_HUMAN_REVIEW` | Legacy state retained for compatibility. Guardian no longer emits it. | No action; use `READY_FOR_MAINTAINER_REVIEW` for the first human review. |
| `READY_FOR_MAINTAINER_REVIEW` | Assignment, relevant CI, security checks, mergeability, and configured AI-review conditions are satisfied; there are no known blocking findings. A human approval is **not** required for this state. | A maintainer performs the first review. Approval and merge eligibility are separate. |
| `STALE` | An otherwise awaiting or ready PR has had no activity beyond `GUARDIAN_STALE_DAYS`. | Confirm whether the work is still active. Guardian does not close stale PRs. |
| `MERGED` | The PR was merged. | No action. Guardian did not merge it. |
| `CLOSED` | The PR was closed without merging. | No action. Guardian did not close it. |
| `UNKNOWN` | Guardian could not determine a trustworthy state from an unexpected shape. | Inspect the workflow log and run `/guardian recheck`. |

### Precedence

Guardian reports the first actionable condition in this order:

1. merged or closed
2. assignment policy unknown or blocked
3. draft, merge conflict, human change request, or enabled-AI change request
4. failed required checks or a failed security check
5. missing or unknown check data, including missing expected checks
6. unknown review data or unknown mergeability
7. checks still running
8. AI review pending
9. ready for the maintainer's first review
10. `STALE` overrides an awaiting or ready state when the inactivity window has
    passed

Human approval is not a readiness gate. Guardian reports approvals and stale
approvals separately, and never treats an approval as merge authorization.

A dismissed finding stops blocking, except for unknown-data findings, missing
expected checks, and security failures, which are never dismissible. A dismissal
of a check or review finding is scoped to the
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

**Expected checks are path-aware.** `.github/scripts/guardian/ci-policy.mjs` maps
changed paths to the GitHub Actions jobs that must report:

| Changed paths | Expected checks |
|---|---|
| `apps/web/**`, `web.yml` | `typecheck + test + build` |
| `apps/android/**`, `packages/mesh-core/**`, `android.yml` | `assemble + unit tests` |
| same, `android-instrumented.yml` | `emulator instrumentation` |
| `services/backend/**`, `packages/mesh-core/**`, `pom.xml`, `backend.yml` | backend unit, PostgreSQL, and boot jobs |
| `packages/mesh-core/**`, `packages/mesh-simulator/**`, `scripts/**`, `pom.xml`, `java.yml` | Java mesh and benchmark jobs |

Branch-protection required status checks and repository ruleset
`required_status_checks` are added to that set when the workflow token can read
them. The static policy is the floor; it does not depend on settings access. If
the complete changed-file list cannot be read (including a truncated 3,000-file
response), Guardian reports `INFRASTRUCTURE_BLOCKED` rather than applying a
partial policy.

- A required `failure`, `timed_out`, `cancelled`, `action_required`,
  `startup_failure`, or `stale` result blocks as `CHECKS_FAILED`.
- `success`, `neutral`, and `skipped` pass. If every observed check was skipped,
  Guardian adds a warning.
- Queued or in-progress expected checks block as `CHECKS_RUNNING`.
- A completed expected check with no conclusion is unknown and blocks.
- **A passing check is not proof that all required CI ran.** A missing expected
  check is `CHECKS_RUNNING` shortly after the head commit and
  `INFRASTRUCTURE_BLOCKED` after `GUARDIAN_NO_CHECKS_GRACE_MINUTES`. The
  `checks.expected-missing` finding cannot be dismissed; fix the workflow,
  trigger, or policy rather than bypassing it.
- **No checks found is never a pass.** Without an expected-check policy, shortly
  after the head commit it is `CHECKS_RUNNING`; after the grace window it becomes
  `INFRASTRUCTURE_BLOCKED` and `checks.no-data` may be dismissed only after a
  maintainer confirms the absence is expected.
- **Optional provider failures stay visible.** A Vercel failure whose target is
  an authorization redirect is reported as a warning, not a required test
  failure. The same applies to repository automation checks such as the PR
  assignment advisory. A real failure in an expected job still sets
  `CHECKS_FAILED`. A GitGuardian failure is always blocking when reported.

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
permissions cannot be read, readiness is `INFRASTRUCTURE_BLOCKED`.

**Approvals are head-scoped.** A review must carry the current head SHA. An
approval of commit A is not an approval of commit B, and a review without a
commit SHA is not counted as current-head approval. The only exception is an
explicitly verified repository review policy: if branch protection or a ruleset
reports `dismiss_stale_reviews: false` (or
`dismiss_stale_reviews_on_push: false`), Guardian records that policy and may
treat an earlier approval as effective. If the policy is unavailable or says
stale approvals are dismissed, Guardian stays strict. A stale change request is
reported as a warning and never counts against the new head. AI review is always
head-scoped, even when the repository permits stale human approvals.

A trusted human `CHANGES_REQUESTED` review on the current head moves the PR to
`NEEDS_CHANGES`. An approval is informational for readiness and is shown in the
summary; `READY_FOR_MAINTAINER_REVIEW` means the PR is ready for the **first**
human review, not that it has been approved or is mergeable.

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

If `GUARDIAN_NOTIFY=true`, Guardian posts one notification comment when a
contributor PR first becomes ready for maintainer review. The default recipient
is `@Gautam-aman`; set `GUARDIAN_MAINTAINERS` to a comma-separated list to
override it. Notification is deduplicated per ready head commit, so reruns and
later approvals on the same head do not repeat it. The notification explicitly
says when no human approval exists yet and never describes readiness as merge
eligibility.

Commands are accepted from pull-request comments:

| Command | Who can use it | Effect |
|---|---|---|
| `/guardian status` | Anyone except bots | Replies with the last recorded state. Does not write the summary. |
| `/guardian explain` | Anyone except bots | Evaluates live and explains current findings without persisting. |
| `/guardian recheck` | Repository `write`, `maintain`, or `admin` | Re-evaluates and updates the summary. |
| `/guardian dismiss <finding-id> <reason>` | Repository `write`, `maintain`, or `admin` | Dismisses one verified non-required check or review false positive. Reason required, max 500 characters. |
| `/guardian help` | Anyone except bots | Shows command help. |

`/guardian merge` does not exist. Guardian refuses it explicitly.

Policy findings cannot be dismissed; use the issue-assignment flow or the
maintainer-approved `no-issue-required` label. Unknown-data findings
(`checks.api-unavailable`, `checks.files-unavailable`, `review.api-unavailable`,
`pr.mergeability-unknown`, and similar), missing expected checks
(`checks.expected-missing`), and security-check failures
(`checks.security-failed`) cannot be dismissed; restore trustworthy data, fix
the workflow, or address the security failure and recheck.

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
| `GUARDIAN_MAINTAINERS` | `Gautam-aman` | Comma-separated logins mentioned in ready notifications. |
| `GUARDIAN_BOT_LOGIN` | `github-actions[bot]` | Login that owns the summary comment. |
| `GUARDIAN_MAX_PULLS` | `25` | Maximum PRs evaluated by a schedule or manual run. |
| `GUARDIAN_MERGEABLE_RETRY_MS` | `800` | One retry when GitHub has not calculated mergeability yet. |

## AI review (CodeRabbit is not installed)

CodeRabbit is **not installed** in this repository. Guardian does not install
apps, assume CodeRabbit is present, or treat its absence as a failure. With the
default `GUARDIAN_AI_REVIEW=false`, Guardian provides deterministic checks,
assignment policy, contributor feedback, and readiness reporting. It does
**not** perform independent AI code review, and its summary says so explicitly.

If AI review is enabled without the GitHub App, Guardian stays in
`AWAITING_AI_REVIEW`; it never marks the AI-review condition complete just
because no review arrived. Absence of an AI review is not a completed review.

To enable CodeRabbit manually, a maintainer must:

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
arrive. AI review is advisory even when present and never substitutes for human
approval.

## Security model

- The workflow runs on `pull_request_target`, `pull_request_review`, `issues`,
  `issue_comment`, `check_suite` (non-GitHub Actions apps), `workflow_run`
  (named CI workflows), a daily schedule, and manual dispatch.
- It checks out only the trusted default branch and never fetches or executes
  pull-request head code. The comment body is data passed to the script, never a
  shell command.
- The token is the short-lived repository `GITHUB_TOKEN` with `contents: read`,
  `issues: write`, `pull-requests: write`, `checks: write`, and `statuses:
  read`. No PAT or long-lived secret is used. Branch protection and ruleset
  reads are best-effort; when the token cannot read them, the static
  path-aware policy still applies and is never replaced by an assumption that
  no protection exists.
- The issue-comment and label REST endpoints accept either `issues: write` or
  `pull-requests: write`. The assignment bot retains `issues: write` for label
  creation and has added `pull-requests: write` as a candidate remediation; the
  accepted-permissions header does not prove that this explains the historical
  403s. See the diagnosis section below.
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

## Assignment-bot 403s from PRs #58, #60, and #61

These are the observed failures, not a confirmed root-cause diagnosis:

- PR #58: `POST /repos/{owner}/{repo}/issues/58/comments` → HTTP 403
- PR #60 and #61: `POST /repos/{owner}/{repo}/issues/{n}/labels` → HTTP 403

The workflow token reported `Issues: write` and `PullRequests: read` at the
time. GitHub documents that the [issue-comment][issue-comment-permissions] and
[label][label-permissions] endpoints accept **either** `issues: write` **or**
`pull-requests: write`; the `x-accepted-github-permissions` response header
lists alternatives, not a diff of missing scopes. Adding `pull-requests: write`
is therefore a candidate remediation, not proof that it fixes the 403s.

[issue-comment-permissions]: https://docs.github.com/en/rest/issues/comments#create-an-issue-comment
[label-permissions]: https://docs.github.com/en/rest/issues/labels#add-labels-to-an-issue

`pr-assignment-bot.yml` keeps `issues: write` for label creation and adds
`pull-requests: write` as a candidate remediation. No PAT or long-lived secret
is used. Do not treat this as verified until a live run of the updated workflow
on the default branch succeeds for a contributor PR.

The shared transport improves the diagnosis without changing the token: it
records the HTTP status, classification, request id, GitHub message,
documentation URL, accepted-permission header, bounded API error details, and
the next check (`effective-workflow-token-and-event-context`). This is enough to
distinguish a permission failure from rate limiting or authorization policy, but
it cannot prove which permission the effective token actually carried. The
remaining checks are the effective token and event context, including any
repository or organization Actions permission restriction; adding a scope is
not a substitute for observing a successful live write.

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
`.github/scripts/guardian/fixtures/`. They cover the lifecycle, first-ready
notifications with no approvals, path-aware expected checks, missing Web and
Android checks with only GitGuardian passing, Vercel authorization warnings,
branch-protection and ruleset requirements, stale approvals, AI-review
staleness, fork PR resolution, policy bypass attempts, untrusted markers,
unknown-data blocking, dismissal scoping, check reruns, and the 403 shapes
observed in PRs #58, #60, and #61. These are deterministic tests, not live
GitHub integration tests.

A read-only live dry-run can be performed locally with an authenticated `gh`
token and `GUARDIAN_DRY_RUN=true`; Guardian will not write. Live workflow
verification for fork PRs requires the workflow to exist on the default branch.

## Limitations

- Guardian is advisory; it cannot enforce a merge without branch protection,
  and this repository currently has no branch protection or rulesets.
- Missing or unreadable data blocks rather than passing. This can require an
  operator to restore API access; missing expected checks and security failures
  cannot be dismissed.
- Docs-only and path-filtered PRs may legitimately have no expected code CI. If
  no checks at all are reported, Guardian remains `INFRASTRUCTURE_BLOCKED` until
  a maintainer dismisses `checks.no-data`; a reported security check such as
  GitGuardian is still evaluated.
- Branch protection and ruleset checks are best-effort. If the token cannot read
  those settings, the static path policy still applies, but a required check
  configured only in inaccessible settings may not be known to Guardian. Check
  the workflow log for policy-source diagnostics during rollout.
- External check apps such as GitGuardian may report after the initial event.
  Guardian refreshes on non-GitHub-Actions `check_suite` events and on the daily
  schedule; use `/guardian recheck` for an immediate refresh.
- A GitGuardian failure blocks when reported, but Guardian does not require
  GitGuardian to be installed. Absence of a security app is not evidence that a
  security review happened.
- The summary is a comment, not a database. If the comment is deleted, Guardian
  recreates it and loses dismissal history.
- If the issue timeline API fails during an assignment event, Guardian falls
  back to GitHub text search. That search can over-select PRs, but each PR is
  still evaluated against its own body and no false readiness is produced.
- Guardian does not inspect PR diffs for security-sensitive files. It flags
  security-sensitive linked issues and leaves the review decision to humans.
- The daily schedule and manual run evaluate only open PRs, capped by
  `GUARDIAN_MAX_PULLS`.
