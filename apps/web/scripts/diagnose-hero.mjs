/**
 * Diagnostic probe for the hero scene.
 *
 * Runs against a served build and reports what the browser actually does:
 * canvas creation, WebGL context, animation frames, and whether the SVG
 * fallback is still painted over the canvas.
 */
import { chromium } from 'playwright';

const url =
  process.argv[2] ??
  `${process.env.RESQNET_BASE_URL ?? 'http://localhost:4173'}/`;
const reduced = process.argv.includes('--reduced');

const browser = await chromium.launch({
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  reducedMotion: reduced ? 'reduce' : 'no-preference',
});
const page = await context.newPage();

const consoleMessages = [];
page.on('console', (message) => {
  consoleMessages.push(`[${message.type()}] ${message.text()}`);
});
page.on('pageerror', (error) => {
  consoleMessages.push(`[pageerror] ${error.message}`);
});

await page.goto(url, { waitUntil: 'networkidle' });
await page.waitForTimeout(2500);

const probe = await page.evaluate(() => {
  const host = document.querySelector('.absolute.inset-0.transition-opacity canvas');
  const canvases = Array.from(document.querySelectorAll('canvas')).map((c) => ({
    width: c.width,
    height: c.height,
    clientWidth: c.clientWidth,
    clientHeight: c.clientHeight,
    style: c.getAttribute('style'),
    parentClass: c.parentElement?.className ?? null,
    parentOpacity: c.parentElement ? getComputedStyle(c.parentElement).opacity : null,
    rect: c.getBoundingClientRect().toJSON(),
  }));
  const svgs = Array.from(document.querySelectorAll('svg')).map((s) => ({
    className: s.getAttribute('class'),
    opacity: getComputedStyle(s).opacity,
    parentOpacity: s.parentElement ? getComputedStyle(s.parentElement).opacity : null,
    parentClass: s.parentElement?.className ?? null,
  }));
  return { canvases, svgs, hasCanvas: Boolean(host) };
});

console.log('--- canvases ---');
console.log(JSON.stringify(probe.canvases, null, 2));
console.log('--- svgs ---');
console.log(JSON.stringify(probe.svgs, null, 2));

// Count animation frames over 2s by patching requestAnimationFrame.
const frames = await page.evaluate(async () => {
  let count = 0;
  const original = window.requestAnimationFrame.bind(window);
  window.requestAnimationFrame = (callback) => {
    count += 1;
    return original(callback);
  };
  await new Promise((resolve) => setTimeout(resolve, 2000));
  window.requestAnimationFrame = original;
  return count;
});
console.log('--- rAF callbacks in 2s:', frames);

// Pixel difference between two frames to detect actual visual change.
const shotA = await page.locator('canvas').first().screenshot().catch(() => null);
await page.waitForTimeout(1200);
const shotB = await page.locator('canvas').first().screenshot().catch(() => null);
if (shotA && shotB) {
  console.log('--- canvas bytes A:', shotA.length, 'B:', shotB.length, 'identical:', Buffer.compare(shotA, shotB) === 0);
}

console.log('--- console ---');
console.log(consoleMessages.join('\n') || '(none)');

await browser.close();
