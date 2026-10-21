/**
 * Captures screenshots of the hero across scroll positions and records
 * per-frame pixel deltas so "is it moving?" is answered with evidence.
 */
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';

const base = process.env.RESQNET_BASE_URL ?? 'http://localhost:4173';
const outDir = process.argv[2] ?? '/tmp/resqnet-shots';
const target = process.argv[3] ?? base;
await mkdir(outDir, { recursive: true });

const browser = await chromium.launch({
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
const errors = [];
page.on('console', (m) => {
  if (m.type() === 'error') errors.push(m.text());
});
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));

await page.goto(target, { waitUntil: 'networkidle' });
await page.waitForTimeout(2500);

const canvas = page.locator('canvas').first();

for (const [index, delay] of [0, 900, 900, 900].entries()) {
  if (delay) await page.waitForTimeout(delay);
  await canvas.screenshot({ path: `${outDir}/hero-${index}.png` });
}

// Scroll positions across the hero stage.
const stage = page.locator('#network');
const box = await stage.boundingBox();
console.log('stage box:', JSON.stringify(box));

for (const [index, fraction] of [0, 0.25, 0.5, 0.75, 1].entries()) {
  const y = Math.round((box?.y ?? 0) + (box?.height ?? 900) * fraction);
  await page.evaluate((top) => window.scrollTo({ top, behavior: 'instant' }), y);
  await page.waitForTimeout(1400);
  await page.screenshot({ path: `${outDir}/scroll-${index}.png` });
}

console.log('errors:', errors.length ? errors : '(none)');
await browser.close();
