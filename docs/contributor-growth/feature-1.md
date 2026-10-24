## Problem, benefit and current behavior

Let visitors bypass the long scroll story and reach the next meaningful content. HeroSection currently renders a 420svh stage, a scroll-to-connection-lost anchor inside the story, and a separate static reduced-motion story. The existing anchor does not skip the entire sequence.

**Evidence:** source-confirmed on published main at [4986e28](https://github.com/Gautam-aman/ResQNet/commit/4986e284f8c4f1add60cb1e2568d0cdeadc29831); proposed enhancement, not a reproduced runtime defect. Start from current GitHub main; no unpublished local commits are required.

## Scope and verified starting points

- [apps/web/src/components/public/landing/HeroSection.tsx](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/components/public/landing/HeroSection.tsx)
- [apps/web/src/components/public/hero/HeroCanvas.tsx](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/components/public/hero/HeroCanvas.tsx)
- [apps/web/src/pages/LandingPage.tsx](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/pages/LandingPage.tsx)
- [apps/web/src/lib/useReducedMotion.ts](https://github.com/Gautam-aman/ResQNet/blob/main/apps/web/src/lib/useReducedMotion.ts)

## Acceptance criteria

- [ ] Add a visible, accessibly named Skip Animation control on the hero.
- [ ] Support keyboard activation and screen readers using a native control.
- [ ] Skip beyond the storytelling sequence to the next meaningful section, verified against LandingPage, with appropriate focus transfer and no trapped focus or broken scroll position.
- [ ] Respect reduced motion, avoiding forced smooth scrolling and preserving the static story.
- [ ] Work on desktop and mobile.
- [ ] Add focused interaction tests for destination, focus and reduced motion.

## Tests and commands

Use React Testing Library and manual keyboard/touch checks at 320px and desktop.

```bash
cd apps/web
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

## Non-goals

No network behavior, routing, Three.js redesign or unrelated page changes.

## Difficulty and contributor guidance

**Beginner** — React, TypeScript, accessibility. Check assignees, comment `/assign` and wait for confirmation before starting. Ask here for setup or test guidance; use `/unassign` to release the issue. Report exact commands, results and anything untested.

Read the [contributor guide](https://github.com/Gautam-aman/ResQNet/blob/main/CONTRIBUTING.md), [PR evidence template](https://github.com/Gautam-aman/ResQNet/blob/main/.github/PULL_REQUEST_TEMPLATE.md) and [security policy](https://github.com/Gautam-aman/ResQNet/blob/main/SECURITY.md). Guardian readiness is advisory; maintainers review and decide.
