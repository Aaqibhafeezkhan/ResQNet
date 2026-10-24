# Local verification — 2026-10-24

## Results

- `cd apps/web && npm run lint`: passed, zero warnings.
- `npm run typecheck`: passed.
- `npm run test`: passed, **208 tests in 20 files**.
- `npm run build`: passed, Vite production bundle generated.
- These four checks passed first under the installed Node 26.9.0 and were repeated successfully under supported **Node 24.22.1**. Existing React Router future-flag warnings remain; Node 26 also emitted localStorage experimental warnings, absent under Node 24.
- `npm run build:public`: passed under Node 24.22.1. `SITE_URL` was unset, so canonical URLs and sitemap generation were skipped as documented by the generator.
- `git diff --check`: passed.
- GitHub GET validated every created issue link and the six existing issue statuses. All were open/unassigned at that check. Markdown local links and GitHub source paths were checked against filesystem and remote tree: none missing.
- `node scripts/verify-contribute.mjs`: passed **66 checks** across 320, 390, 768, 1024 and 1440px plus ordinary-motion desktop. Confirmed six cards, status guidance, live search target, card-link bounds and 44px touch targets, keyboard reachability/focus indication, no horizontal overflow and no runtime page errors.
- All five full-page screenshots were visually inspected. One additional desktop screenshot was generated with ordinary motion. Browser checks used local Chromium; this is not an exhaustive screen-reader audit or physical-device test.
- Public `/contribute` HTTP check returned 200; the deployed UI was not visually checked or changed. Screenshots and browser results refer to the local production preview only.

## Screenshots

Outside the repository, under `/tmp/resqnet-contributor-review/`:

- `contribute-320.png`
- `contribute-390.png`
- `contribute-768.png`
- `contribute-1024.png`
- `contribute-1440.png`
- `contribute-motion-desktop.png`

Browser log: `/tmp/resqnet-contributor-browser.log`.

## Reproduce the preview

Use Node 24 LTS. From the repository root:

```bash
cd apps/web
npm ci
npm run build
npm run preview -- --host 127.0.0.1
```

Open `http://127.0.0.1:4173/contribute`. In a second terminal:

```bash
cd apps/web
node scripts/verify-contribute.mjs
```

If Playwright Chromium is not installed, run `npx playwright install chromium` first. Override `RESQNET_BASE_URL` if the preview uses another port. Screenshots default to `/tmp/resqnet-contributor-review`; pass another output directory as the first script argument.

## Local milestones

- `f36e1c8`: README and CONTRIBUTING first-contribution quickstart.
- `eeac44d`: curated contributor page and browser verification script.
- Audit, issue-body archive, verification and unpublished announcement are committed as the final documentation milestone; its hash is reported in the maintainer handoff.

No features were implemented. No code was pushed, no PR opened and nothing deployed. No Java/Android test suite or physical radios were exercised.
