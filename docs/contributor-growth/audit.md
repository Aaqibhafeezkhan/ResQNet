# Contributor opportunity audit — 2026-10-24

Historical campaign snapshot. The original branch included unpublished Lab UX ancestry; [clean-review.md](clean-review.md) records the isolated PR branch and current verification. Issue archives now use published-main evidence references.

Initial checkout: local `main` at `2d0f520`, three pre-existing untracked web inspection scripts preserved. Work branch: `docs/contributor-growth`. GitHub main was separately checked at `4986e284f8c4f1add60cb1e2568d0cdeadc29831`; local main includes unpublished Lab UX changes. No unrelated history changed.

## Inventory

GitHub API returned **32 open issues and 13 open PRs** before this campaign. Every open issue title, labels, assignees and body was reviewed for scope overlap. The table records that snapshot, not a live availability promise.

| Issue | Area / title | Assignment at audit |
|---|---|---|
| [#78](https://github.com/Gautam-aman/ResQNet/issues/78) | Android: add copy fingerprint action on the identity settings screen | Unassigned |
| [#77](https://github.com/Gautam-aman/ResQNet/issues/77) | Docs: clarify how to verify GitHub release APK checksum on macOS, Linux and Windows | Unassigned |
| [#76](https://github.com/Gautam-aman/ResQNet/issues/76) | Web: document and test keyboard controls for the interactive mesh demo | Unassigned |
| [#75](https://github.com/Gautam-aman/ResQNet/issues/75) | Docs: add a beginner-friendly 60-second simulator quickstart | Unassigned |
| [#74](https://github.com/Gautam-aman/ResQNet/issues/74) | Android: add a copy-diagnostics action for reproducible issue reports | Unassigned |
| [#73](https://github.com/Gautam-aman/ResQNet/issues/73) | Simulator CLI: support printing JSON reports to stdout for piping | Unassigned |
| [#72](https://github.com/Gautam-aman/ResQNet/issues/72) | Simulator CLI: make --help behavior explicit when other options are invalid | Unassigned |
| [#71](https://github.com/Gautam-aman/ResQNet/issues/71) | Docs: provide accessible static fallbacks for README Mermaid diagrams | Unassigned |
| [#69](https://github.com/Gautam-aman/ResQNet/issues/69) | Web: make canvas incident pins selectable by mouse or touch | Unassigned |
| [#68](https://github.com/Gautam-aman/ResQNet/issues/68) | Web: fix incident map longitude projection so pins stay within the canvas | Unassigned |
| [#56](https://github.com/Gautam-aman/ResQNet/issues/56) | [Testing] Help validate ResQNet on real Android devices | Unassigned |
| [#50](https://github.com/Gautam-aman/ResQNet/issues/50) | Web: restore focus after the create-incident dialog closes | nishu91020 |
| [#49](https://github.com/Gautam-aman/ResQNet/issues/49) | Simulator CLI: reject integer arguments that overflow int | samjeslin46 |
| [#48](https://github.com/Gautam-aman/ResQNet/issues/48) | Simulator: reject non-finite probabilities and nonpositive message lifetimes | Unassigned |
| [#47](https://github.com/Gautam-aman/ResQNet/issues/47) | Android: show when a message row includes a shared location | Unassigned |
| [#44](https://github.com/Gautam-aman/ResQNet/issues/44) | mesh-core: preserve row IDs when upserting an existing message | samjeslin46 |
| [#38](https://github.com/Gautam-aman/ResQNet/issues/38) | Web: assignment selector calls an admin accounts endpoint that does not exist | Unassigned |
| [#35](https://github.com/Gautam-aman/ResQNet/issues/35) | Web: live event stream never negotiates its subprotocol, and the header reports Live after close | Unassigned |
| [#34](https://github.com/Gautam-aman/ResQNet/issues/34) | Simulator: recovery benchmark never heals the partition it measures | Unassigned |
| [#33](https://github.com/Gautam-aman/ResQNet/issues/33) | Simulator: benchmark table undercounts forwards and reports the wrong delivery rate | Unassigned |
| [#32](https://github.com/Gautam-aman/ResQNet/issues/32) | mesh-core: maxRelayMessages is configurable but never enforced | Unassigned |
| [#31](https://github.com/Gautam-aman/ResQNet/issues/31) | Backend: nested gateway reports bypass validation and can fail a whole batch with HTTP 500 | Unassigned |
| [#30](https://github.com/Gautam-aman/ResQNet/issues/30) | Backend: gateway sync never updates delivery state on repeat reports | Unassigned |
| [#12](https://github.com/Gautam-aman/ResQNet/issues/12) | Web: add automated accessibility checks to the test suite | Unassigned |
| [#11](https://github.com/Gautam-aman/ResQNet/issues/11) | Simulator: partitions field contradicts its experienced contract | nehavaid02 |
| [#10](https://github.com/Gautam-aman/ResQNet/issues/10) | Android: foreground notification never updates peer and queue counts | Unassigned |
| [#9](https://github.com/Gautam-aman/ResQNet/issues/9) | Web: incident queue is truncated at 25 results with no pagination | Pranita1846 |
| [#8](https://github.com/Gautam-aman/ResQNet/issues/8) | Web: filter changes are delayed up to 15 seconds and search drops status | Unassigned |
| [#7](https://github.com/Gautam-aman/ResQNet/issues/7) | Android: directed SOS is sent to an arbitrary peer with no recipient picker | Unassigned |
| [#6](https://github.com/Gautam-aman/ResQNet/issues/6) | Backend: gateway sync attaches reports to an arbitrary open incident | Unassigned |
| [#5](https://github.com/Gautam-aman/ResQNet/issues/5) | Backend: incident search skips pages and reports a window-local total | Unassigned |
| [#4](https://github.com/Gautam-aman/ResQNet/issues/4) | Android: diagnostics always reports zero discovered endpoints | Unassigned |

## Duplicates and scope boundaries

- Existing #75, #78, #68, #56, #73 and #76 remain the original opportunities. Refreshed their bodies with setup/testing guidance, without changing discussions or assignment. Fixed the malformed hardware-guide link in #56 and distinguished beginner one-phone testing from advanced multi-hop isolation.
- #74 diagnostics copying and #78 fingerprint copying do not copy message IDs; #86 is a separate, limited message-detail action.
- #72 CLI help precedence and #73 JSON stdout remain separate from #83 topology discovery; #83 explicitly preserves help precedence and calls out parser coordination.
- #76 keyboard instructions remain separate from #87 named failure presets. Lab scenario.ts is a Java-command builder, not guided browser-demo scenarios.
- #70 is already closed, with PR #82 addressing coordinate admission. It does not fix #68 longitude projection or #69 canvas hit geometry. Source confirms the projection expression remains in MapPanel.
- Assigned #49 has PR #67; assigned #9 has PR #60; assigned #11 has PR #54. Do not promote them as unclaimed. Assigned #44 and #50 likewise stay outside the shortlist.
- #12 still describes a 46-test suite; this checkout now runs 208 tests. Its accessibility-testing scope remains useful, but the count is stale.
- #33 needs revalidation: SimulationReport now uses directed-message delivery accounting and aggregate forward counts, while BenchmarkSuite still counts forwards from report.events(). Do not close it without reproducing current benchmark output and maintainer approval.
- Old issue line numbers have drifted (for example #4 diagnostics references); source paths are more reliable than those historical line numbers. No issue was closed.

## New issues

| Issue | Feature | Difficulty |
|---|---|---|
| [#85](https://github.com/Gautam-aman/ResQNet/issues/85) | Skip hero animation | Beginner |
| [#86](https://github.com/Gautam-aman/ResQNet/issues/86) | Copy Message ID | Beginner |
| [#83](https://github.com/Gautam-aman/ResQNet/issues/83) | List topologies | Beginner |
| [#84](https://github.com/Gautam-aman/ResQNet/issues/84) | Bundled replay sharing | Intermediate |
| [#87](https://github.com/Gautam-aman/ResQNet/issues/87) | Guided failure scenarios | Intermediate |

All five use existing enhancement/help-wanted/area labels. Only the beginner three use good first issue. Source paths were checked locally and against the GitHub tree; features are proposals, not claims of runtime defects. Full issue bodies are archived in feature-1.md through feature-5.md. No feature was implemented.

## Curated existing opportunities

At the final API check, #75 (docs), #73 (Java), #78 (Android), #68 and #76 (frontend/accessibility), and #56 (hardware testing) were open and unassigned. The website selects #75, #78, #68, #56 plus new #83 and #84 for a six-card mix, and links to live GitHub search rather than claiming lasting availability.

## Policies and automation reviewed

Reviewed Readme.md, CONTRIBUTING.md, CODE_OF_CONDUCT.md, SECURITY.md, all four issue-template/config files, PR template, contributor-bot.mjs and assignment workflow, PR assignment advisory, Guardian documentation/workflow and Web CI. Existing assignment policy is one contributor per issue, at most two active issues per contributor, /unassign for release and maintainer assignment for security issues. PR assignment and Guardian are advisory; neither promises approval or merge. No automation, permissions or CI files changed. Existing documented assignment-bot 403 remediation is not independently live-verified here.

## Remote/local source verification

GitHub tree hashes match every feature starting point except the four Lab page/store files changed by the unpublished local UX work. Remote LabPage was separately inspected: no query decoding or share control exists; remote main requires manual sample loading while local main auto-loads through loadIfEmpty. #84 was subsequently simplified during clean-branch review to describe published main only and explicitly avoid depending on the local-only API.

## Scope and limits

Only contributor-page content, onboarding docs and review tooling changed. No live feed/API keys were introduced. Safety, private reporting, evidence wording, existing typography and reduced-motion behavior were preserved. No Java, Android or hardware test run is claimed for this documentation/web task. No code was pushed, no PR opened, no deployment performed, no announcement published and no contributor assigned.

## Open pull requests reviewed

- [#67](https://github.com/Gautam-aman/ResQNet/pull/67): fix(simulator): reject overflowing integer arguments
- [#60](https://github.com/Gautam-aman/ResQNet/pull/60): fix(web): paginate incident queue results
- [#54](https://github.com/Gautam-aman/ResQNet/pull/54): Clarify final partition count in simulation report
- [#26](https://github.com/Gautam-aman/ResQNet/pull/26): build(deps): bump the actions group across 1 directory with 7 updates
- [#25](https://github.com/Gautam-aman/ResQNet/pull/25): build(deps-dev): bump vitest from 2.1.9 to 5.0.5 in /apps/web
- [#24](https://github.com/Gautam-aman/ResQNet/pull/24): build(deps): bump com.android.application from 8.13.2 to 9.4.1 in /apps/android
- [#23](https://github.com/Gautam-aman/ResQNet/pull/23): build(deps-dev): bump @eslint/js from 9.39.5 to 10.0.1 in /apps/web
- [#22](https://github.com/Gautam-aman/ResQNet/pull/22): build(deps): bump gradle-wrapper from 8.14.3 to 9.8.1 in /apps/android
- [#21](https://github.com/Gautam-aman/ResQNet/pull/21): build(deps-dev): bump @vitejs/plugin-react from 4.7.0 to 6.1.2 in /apps/web
- [#20](https://github.com/Gautam-aman/ResQNet/pull/20): build(deps-dev): bump eslint from 9.39.5 to 10.13.0 in /apps/web
- [#19](https://github.com/Gautam-aman/ResQNet/pull/19): build(deps): bump spring-boot.version from 3.5.9 to 4.1.1
- [#18](https://github.com/Gautam-aman/ResQNet/pull/18): build(deps): bump the android-minor-patch group across 1 directory with 11 updates
- [#15](https://github.com/Gautam-aman/ResQNet/pull/15): build(deps): bump org.springdoc:springdoc-openapi-starter-webmvc-ui from 2.8.9 to 3.1.1
