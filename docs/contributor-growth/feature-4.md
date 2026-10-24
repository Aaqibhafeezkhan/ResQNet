## Problem, benefit and current behavior

Make discussions reproducible with a link to a bundled sample and replay position. On published main, users explicitly load bundled samples. `SAMPLE_REPORTS` includes ring and two-cluster fixtures, and the store supports cursor seeking. No query-string sharing exists. `scenario.ts` generates Java commands, not replay links.

**Evidence:** source-confirmed on published main at [4986e28](https://github.com/Gautam-aman/ResQNet/commit/4986e284f8c4f1add60cb1e2568d0cdeadc29831); proposed enhancement, not a reproduced runtime defect. Start from current GitHub main. No unpublished Lab UX work is required: use the existing sample-loading and seek actions rather than assuming a `loadIfEmpty` API or automatic sample loading exists.

## Scope and verified starting points

- [apps/web/src/pages/LabPage.tsx](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/pages/LabPage.tsx)
- [apps/web/src/pages/LabPage.test.tsx](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/pages/LabPage.test.tsx)
- [apps/web/src/lab/sampleReport.ts](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/lab/sampleReport.ts)
- [apps/web/src/lab/labStore.ts](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/lab/labStore.ts)
- [apps/web/src/lab/labStore.test.ts](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/lab/labStore.test.ts)
- [apps/web/src/lab/replay.ts](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/lab/replay.ts)
- [docs/mesh-replay-lab/README.md](https://github.com/Gautam-aman/ResQNet/blob/main/docs/mesh-replay-lab/README.md)

## Acceptance criteria

- [ ] Document an allowlisted bundled sample ID and optional integer event cursor URL format.
- [ ] Default missing cursor to zero; safely handle malformed, negative, fractional, duplicate and out-of-range parameters and unknown samples with a documented fallback.
- [ ] Load the bundled sample before seeking; refresh or a fresh context reproduces the same sample/cursor, paused.
- [ ] Never serialize imported JSON, message contents, device IDs, file names, search text or private metadata into URLs.
- [ ] Only enable sharing for a positively identified bundled sample; importing JSON clears any previous sample share identity.
- [ ] Add an accessible copy-link control with success and clipboard-failure feedback.
- [ ] Add encoding/decoding, invalid-input, initial-load/StrictMode, refresh and imported-report privacy tests.
- [ ] Preserve deterministic replay, imports and comparisons.

## Tests and commands

Use pure codec tests and LabPage/store tests. Open copied links in fresh browser contexts; import synthetic private JSON and verify sharing is unavailable.

```bash
cd apps/web
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

## Non-goals

No uploads, server storage, URL payloads, arbitrary remote report fetches or replay-engine replacement.

## Difficulty and contributor guidance

**Intermediate** — React, TypeScript, URL validation, deterministic state. Check assignees, comment `/assign` and wait for confirmation before starting. Ask here for setup or test guidance; use `/unassign` to release the issue. Report exact commands, results and anything untested.

Read the [contributor guide](https://github.com/Gautam-aman/ResQNet/blob/main/CONTRIBUTING.md), [PR evidence template](https://github.com/Gautam-aman/ResQNet/blob/main/.github/PULL_REQUEST_TEMPLATE.md) and [security policy](https://github.com/Gautam-aman/ResQNet/blob/main/SECURITY.md). Guardian readiness is advisory; maintainers review and decide.
