# Hero scene diagnosis (Phase 1)

Measured against the production build (`npm run build` → static server) and the
live deployment, using headless Chromium with a real WebGL context
(SwiftShader), pixel-diffing the canvas rather than trusting the source.

## What actually works

- The WebGL canvas **is** created. `canvas count: 1`, `rAF callbacks in 2s: 94`.
- The dynamic `import('three')` succeeds; the `three` chunk is a separate lazy
  chunk and is only fetched when the hero mounts.
- The SVG fallback **is** correctly faded out (`parentOpacity: "0"` when live).
- No console errors, no page errors.
- `prefers-reduced-motion` is **not** the default cause: the canvas is present
  with `reducedMotion: 'no-preference'`.
- The live deployment behaves the same as the local build (same canvas, same
  motion numbers), so this is not a dev/prod divergence.

## Why it still looks static

The scene *renders* but almost nothing in it **moves enough to see**. Measured
pixel deltas over a 5 s idle window:

| Signal | Before |
| --- | --- |
| 5 s idle, mean per-pixel delta | **0.259** (2.08 % of pixels changed) |
| Pointer move | 0.626 (4.50 %) |
| Scroll | 13.904 (26.13 %) |

A mean delta of 0.26/255 is invisible. Scroll is the only strong signal, so
the page reads as a static image with a scroll effect.

### Exact causes

1. **Node motion amplitude is sub-pixel.** The only continuous per-node motion
   is `sin(elapsed * 1.6 + phase) * 0.045` on the halo scale — 4.5 % of a
   sprite that is already a soft glow. At the framing in use (13.2 units of
   camera distance, 42° FOV, 900 px tall viewport ≈ 90 px per world unit) that
   is well under one pixel. Nodes never translate.

2. **The packets crawl.** The story packet traverses a hard-coded route of
   ~11.8 world units at `0.055` units/s → **≈ 215 s for one crossing**, and
   the packet sprite is 0.32 units (≈ 29 px) moving at ≈ 4.9 px/s. Over a
   5 s observation window it moves ~25 px and reads as a still dot.

3. **Camera parallax is ~5 % of the screen and is overwritten by `lookAt`.**
   `pointer.x * 0.55` world units, with `camera.lookAt()` called on a fixed
   target every frame. The pointer therefore shifts the frame rather than
   producing a parallax *between* foreground and background.

4. **No interaction exists at all.** There is no `Raycaster`, no hit testing,
   no hover state and no click handler in `hero/`. The only pointer listener
   is a global `pointermove` that feeds the camera. So "hovering a node" and
   "clicking a node" cannot work — they were never implemented.

5. **There is no story.** Scroll drives a single `failure` scalar that fades
   links and dims nodes. There are no scenes, no failure → emergency →
   reroute → delivery sequence, and the packet is explicitly faded out as
   `failure` rises (`opacity = 0.86 * (1 - failure * 0.92)`) — the message
   disappears exactly when the visitor should be watching it travel.

6. **The route is hard-coded**, not derived from the existing deterministic
   routing model in `components/demo/meshDemoModel.ts` (`findRoute`).

7. **The hero copy sits on top of the canvas.** In `HeroSection` the copy
   block is `relative z-10` over a `sticky` canvas, so the canvas never
   receives element-targeted pointer events; a canvas-level hit test would
   have been impossible without restructuring the stacking.

8. **Reduced motion has no story fallback.** `useReducedMotion` correctly
   reports the preference, but the reduced path renders only the static SVG
   and a `ConnectionLost` block — there is no readable version of the story.

## Scope of the fix

Keep the existing architecture (React + lazy `three` chunk + SVG fallback)
and fix the causes: give the scene real amplitudes, drive the packets from
the existing `findRoute` model, add hover/click picking with an accessible
DOM equivalent, turn the scroll into the five required chapters, and make the
reduced-motion and no-WebGL paths a complete, readable experience.
