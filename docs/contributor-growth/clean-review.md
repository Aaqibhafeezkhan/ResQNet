# Clean contributor-growth review — 2026-10-24

## Result

PR branch: `docs/contributor-growth-clean`, isolated at `/private/tmp/resqnet-contributor-growth-clean`, created from freshly fetched `origin/main` (`4986e284f8c4f1add60cb1e2568d0cdeadc29831`). The original worktree remains on `docs/contributor-growth` at `55a581e`; local `main` remains `2d0f520`. All original branches and the three untracked responsive inspection scripts are preserved.

No new features or additional issues were created. No push, PR, merge, deployment or announcement publication was performed.

## Original ancestry and scope finding

The common ancestor of original campaign, local main and current remote main is `ebbea1a`. Below is the complete divergent ancestry, with each commit's parents; older shared history is in `/private/tmp/resqnet-contributor-clean-review/complete-ancestry.txt` (full hashes and all ancestors to root).

```text
* 55a581e eeac44d docs: record contributor audit and community announcement draft
* eeac44d f36e1c8 feat(web): curate accessible contributor starting points
* f36e1c8 2d0f520 docs: put contributor setup and issue claiming first
*   2d0f520 ebbea1a 3f981c8 merge: integrate mesh replay lab UX overhaul
|\
| * 3f981c8 56c3124 fix(lab): surface topology sooner on small phones
| * 56c3124 ebbea1a feat(lab): open into populated responsive replay dashboard
|/
| *   4986e28 185e246 6c7be7a Merge pull request #81 from komalsaireddy/fix/stop-refresh-spinner
| |\
| | * 6c7be7a 6ac73b0 test(android): isolate message list engine initialization
| | * 6ac73b0 baa8131 test(android): cover message list refresh indicator
| | * baa8131 ebbea1a fix(android): stop spinner after message list refresh
| |/
|/|
| * 185e246 ebbea1a d7764d0 Merge pull request #82 from Sinkleberg/fix/shared-coordinate-guard
|/|
| * d7764d0 ebbea1a fix(web): reject incomplete shared incident locations
|/
o ebbea1a 1137dc0 26f546d Merge pull request #80 from Gautam-aman/feat/mesh-replay-lab
```

Relative to local `main`, the campaign adds only `f36e1c8`, `eeac44d`, `55a581e`. Relative to `origin/main`, it also contains unpublished `56c3124`, `3f981c8` and their merge `2d0f520`. It lacks remote main's map-coordinate and Android refresh fixes. A direct two-tip diff therefore includes apparent reversions of those fixes as well as Lab UX changes; the triple-dot PR diff also includes the unpublished Lab UX work.

Original branch vs local main — complete changed-file list:

```text
M	CONTRIBUTING.md
M	Readme.md
A	apps/web/scripts/verify-contribute.mjs
M	apps/web/src/pages/ContributePage.tsx
A	docs/contributor-growth/announcement.md
A	docs/contributor-growth/audit.md
A	docs/contributor-growth/feature-1.md
A	docs/contributor-growth/feature-2.md
A	docs/contributor-growth/feature-3.md
A	docs/contributor-growth/feature-4.md
A	docs/contributor-growth/feature-5.md
A	docs/contributor-growth/verification.md
```

Original branch vs current origin/main — complete direct changed-file list:

```text
M	CONTRIBUTING.md
M	Readme.md
M	apps/android/app/src/main/java/dev/resqnet/android/ui/MessageListActivity.java
D	apps/android/app/src/test/java/dev/resqnet/android/ui/MessageListActivityTest.java
A	apps/web/scripts/verify-contribute.mjs
M	apps/web/scripts/verify-lab.mjs
D	apps/web/src/components/command/MapPanel.test.tsx
M	apps/web/src/components/command/MapPanel.tsx
M	apps/web/src/components/lab/ScenarioBuilder.tsx
M	apps/web/src/components/lab/SimulatedBanner.tsx
M	apps/web/src/lab/labStore.test.ts
M	apps/web/src/lab/labStore.ts
M	apps/web/src/pages/ContributePage.tsx
M	apps/web/src/pages/LabPage.test.tsx
M	apps/web/src/pages/LabPage.tsx
A	docs/contributor-growth/announcement.md
A	docs/contributor-growth/audit.md
A	docs/contributor-growth/feature-1.md
A	docs/contributor-growth/feature-2.md
A	docs/contributor-growth/feature-3.md
A	docs/contributor-growth/feature-4.md
A	docs/contributor-growth/feature-5.md
A	docs/contributor-growth/verification.md
```

## Commit review and clean extraction

| Original | Clean cherry-pick | Reviewed content |
|---|---|---|
| f36e1c8 | 7c98ac4 | README entry point, CONTRIBUTING quickstart and repository-area descriptions; no simulator quickstart implementation |
| eeac44d | 9b04b90 | Contributor page and browser verification script only; no Lab imports or store/API dependency |
| 55a581e | dd46e10 | Campaign audit, five issue-body archives, verification and unpublished announcement |

All three cherry-picks applied without conflicts. No Lab UX commit was selected. Follow-up review updates evidence references, labels historical verification explicitly, adds exact issue-destination browser assertions and records this review.

The clean branch leaves Lab files, existing Lab smoke script, map validation/regression tests, Android code and CI/automation byte-for-byte identical to `origin/main`. Existing map and Android fixes remain in the baseline. The announcement is unchanged, ready for review, and unpublished.

## Issue review

Issues #83–#87 previously mentioned local-only `2d0f520` (four as their evidence baseline, #84 in its description). Updated those existing bodies to cite published [4986e28](https://github.com/Gautam-aman/ResQNet/commit/4986e284f8c4f1add60cb1e2568d0cdeadc29831). #84 now describes explicit bundled sample loading and existing seek actions, and warns against assuming `loadIfEmpty` exists. Acceptance criteria and feature scope are unchanged. The five local archives match the revised bodies. No discussion, assignment or labels changed.

## Clean-branch verification

Runtime: Node **24.22.1**, Vitest 2.1.9, Vite 6.4.4; dependencies installed with `npm ci --no-audit --no-fund` from the unchanged lockfile.

Commands from `apps/web`:

```bash
npm run lint
npm run typecheck
npm run test
npm run build
npm run build:public
npm run preview -- --host 127.0.0.1 --port 4175 --strictPort
RESQNET_BASE_URL=http://127.0.0.1:4175 node scripts/verify-contribute.mjs /private/tmp/resqnet-contributor-clean-review/screenshots
RESQNET_BASE_URL=http://127.0.0.1:4175 node scripts/verify-lab.mjs /private/tmp/resqnet-contributor-clean-review/lab-screenshots
```

- Lint and typecheck passed; **227 tests in 21 files passed**, including 21 MapPanel coordinate-admission regressions. The original 208 count belongs to the old local-UX baseline.
- Both production builds passed. Public mode was built last for browser checks. `SITE_URL` is unset, so canonical/sitemap generation was skipped; no deployment occurred.
- Contributor browser: **71 checks passed** across 320, 390, 768, 1024 and 1440px, plus ordinary-motion desktop. Exact issue destinations, 44px link targets, focus indication, overflow and runtime errors checked. Five viewport screenshots visually inspected; no cropped cards or unreadable layout found.
- Existing remote-main Lab smoke: **39 checks passed** at 1440, 390 and 320px. Explicit sample loading, next-event/seek, message inspection, comparisons, command generation, overflow and existing demo route all passed. No dependence on auto-loading UX.
- Existing React Router future-flag warnings remain. npm reported existing deprecation/install-script notices; dependency manifests and lockfile were unchanged.
- These results establish no regressions in the exercised web suite and browser flows; they do not establish real-device radio behavior or exhaustive accessibility coverage. No Java/Android suite was needed for unchanged code.

Artifacts are local under `/private/tmp/resqnet-contributor-clean-review`: `contribute-browser.log`, `lab-browser.log`, `link-http.log`, `complete-ancestry.txt`, `screenshots/`, and `lab-screenshots/`.

## Link verification

Every rendered contributor-page link was checked: **23 HTTP 200 responses and two GitHub HTTP 429 rate-limit responses**. The rate-limited hardware guide and security policy were independently confirmed through the authenticated GitHub contents API at their exact expected URLs. This distinguishes transient access limits from missing files; their ordinary HTML response was not confirmed as 200 during this run.

All **125 documentation destinations** checked against local files, published-main Git objects and the current issue/PR inventory resolved. The five revised live issue bodies exactly match their local archives and contain no reference to unpublished `2d0f520`. New documentation anchors and the `main` skip-link target were also checked in the local source/rendered page. No broken link was found within this scope.

## Clean branch changed files

Only these 13 paths differ from `origin/main`:

```text
CONTRIBUTING.md
Readme.md
apps/web/scripts/verify-contribute.mjs
apps/web/src/pages/ContributePage.tsx
docs/contributor-growth/announcement.md
docs/contributor-growth/audit.md
docs/contributor-growth/clean-review.md
docs/contributor-growth/feature-1.md
docs/contributor-growth/feature-2.md
docs/contributor-growth/feature-3.md
docs/contributor-growth/feature-4.md
docs/contributor-growth/feature-5.md
docs/contributor-growth/verification.md
```

Clean ancestry is `4986e28 → 7c98ac4 → 9b04b90 → dd46e10 → review follow-up commit` (the final hash is reported in the handoff). No unpublished Lab commit is an ancestor of this branch. The full original ancestry and changed-file lists above remain available for comparison.

Preview: `http://127.0.0.1:4175/contribute`. All five contributor screenshots and browser logs are in the artifact directory listed above. The original working tree retains exactly its three original untracked scripts.

## Suggested PR

Title: **docs(web): make first contributions easier to discover and verify**

Description:

> Give first-time contributors a clear path from choosing an issue to running checks and opening a focused PR. Add curated contributor-page opportunities and live GitHub search, plus README/CONTRIBUTING setup, assignment and evidence guidance. Include the campaign audit and an unpublished announcement draft.
>
> Based on current remote main and contains only contributor-onboarding changes. Preserves the existing Lab, map validation, Android fixes and CI. Feature proposals remain unimplemented.
>
> Verified on Node 24: lint, typecheck, 227 tests, standard/public production builds, 71 contributor responsive checks and 39 existing Lab smoke checks. GitHub page HTTP rate limits are distinguished from broken links in the review evidence.
