## Problem, benefit and current behavior

Help beginners explore failure/recovery through reproducible named presets. MeshDemo uses meshDemoModel for sender selection, relay toggling, send, retry, advance and reset, but has no guided presets. The separate Replay Lab command builder is not this browser model.

**Evidence:** source-confirmed implementation at `2d0f520`; proposed enhancement, not a reproduced runtime defect.

## Scope and verified starting points

- [apps/web/src/components/demo/MeshDemo.tsx](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/components/demo/MeshDemo.tsx)
- [apps/web/src/components/demo/meshDemoModel.ts](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/components/demo/meshDemoModel.ts)
- [apps/web/src/components/demo/meshDemoModel.test.ts](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/components/demo/meshDemoModel.test.ts)
- [apps/web/src/pages/DemoPage.tsx](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/pages/DemoPage.tsx)

## Acceptance criteria

- [ ] Provide healthy network, single relay failure, route unavailable/message queued, and alternative route restored presets.
- [ ] Define explicit model state/actions and relay choices: repeated selection yields the same topology, sender, phase and event sequence.
- [ ] Reuse the current demo model and describe simulated behavior accessibly without fabricating real connectivity or measured delivery.
- [ ] Safely reset pending timers on preset switches so previous progression cannot advance a new scenario.
- [ ] Reset restores the documented baseline; manual sender, relay, send and retry controls continue to work.
- [ ] Add deterministic model and component tests with controlled timers for repeated selection, queued/recovery transitions, reset and manual controls.
- [ ] Preserve keyboard access and reduced motion; coordinate keyboard instructions with #76.

## Tests and commands

Extend meshDemoModel.test.ts and add a MeshDemo component test with fake timers.

```bash
cd apps/web
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

## Non-goals

No replacement simulator, Replay Lab/Java CLI changes, production routing changes or claims of physical radio performance.

## Difficulty and contributor guidance

**Intermediate** — React, TypeScript, state transitions, accessibility. Check assignees, comment `/assign` and wait for confirmation before starting. Ask here for setup or test guidance; use `/unassign` to release the issue. Report exact commands, results and anything untested.

Read the [contributor guide](https://github.com/Gautam-aman/ResQNet/blob/main/CONTRIBUTING.md), [PR evidence template](https://github.com/Gautam-aman/ResQNet/blob/main/.github/PULL_REQUEST_TEMPLATE.md) and [security policy](https://github.com/Gautam-aman/ResQNet/blob/main/SECURITY.md). Guardian readiness is advisory; maintainers review and decide.
