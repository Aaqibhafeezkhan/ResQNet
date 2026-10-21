/**
 * Measures how much the hero canvas actually changes over time.
 *
 * The comparison is done on decoded pixels inside the browser: two
 * screenshots are loaded as images, drawn to an offscreen 2D canvas and
 * compared sample by sample. The result is the mean absolute per-channel
 * difference (0..255) over the region that changed, plus the fraction of
 * pixels that changed at all.
 */
import { chromium } from 'playwright';

const base = process.env.RESQNET_BASE_URL ?? 'http://localhost:4173';
const target = process.argv[2] ?? base;
const reduced = process.argv.includes('--reduced');

const browser = await chromium.launch({
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  reducedMotion: reduced ? 'reduce' : 'no-preference',
});
const page = await context.newPage();
const errors = [];
page.on('console', (m) => {
  if (m.type() === 'error') errors.push(m.text());
});
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));

await page.goto(target, { waitUntil: 'networkidle' });
await page.waitForTimeout(2500);

const count = await page.locator('canvas').count();
console.log('canvas count:', count);
if (count === 0) {
  console.log('NO CANVAS — fallback path active');
  await browser.close();
  process.exit(0);
}

const canvas = page.locator('canvas').first();

async function shot() {
  return (await canvas.screenshot()).toString('base64');
}

async function compare(label, a, b) {
  const result = await page.evaluate(
    async ([aData, bData]) => {
      const load = (data) =>
        new Promise((resolve, reject) => {
          const image = new Image();
          image.onload = () => resolve(image);
          image.onerror = reject;
          image.src = `data:image/png;base64,${data}`;
        });
      const [imageA, imageB] = await Promise.all([load(aData), load(bData)]);
      const width = imageA.width;
      const height = imageA.height;
      const read = (image) => {
        const surface = document.createElement('canvas');
        surface.width = width;
        surface.height = height;
        const ctx = surface.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(image, 0, 0);
        return ctx.getImageData(0, 0, width, height).data;
      };
      const dataA = read(imageA);
      const dataB = read(imageB);
      let sum = 0;
      let changed = 0;
      let max = 0;
      for (let i = 0; i < dataA.length; i += 4) {
        const delta =
          Math.abs(dataA[i] - dataB[i]) +
          Math.abs(dataA[i + 1] - dataB[i + 1]) +
          Math.abs(dataA[i + 2] - dataB[i + 2]);
        sum += delta / 3;
        if (delta > 6) changed += 1;
        if (delta / 3 > max) max = delta / 3;
      }
      const pixels = dataA.length / 4;
      return {
        meanDelta: sum / pixels,
        changedFraction: changed / pixels,
        maxDelta: max,
      };
    },
    [a, b],
  );
  console.log(
    `${label}: mean=${result.meanDelta.toFixed(3)} changed=${(result.changedFraction * 100).toFixed(2)}% max=${result.maxDelta.toFixed(1)}`,
  );
  return result;
}

const a = await shot();
await page.waitForTimeout(5000);
const b = await shot();
await compare('5s idle', a, b);

await page.mouse.move(200, 400);
await page.waitForTimeout(700);
const c = await shot();
await page.mouse.move(1250, 300);
await page.waitForTimeout(1200);
const d = await shot();
await compare('pointer move', c, d);

await page.evaluate(() => window.scrollTo({ top: 700, behavior: 'instant' }));
await page.waitForTimeout(1400);
const e = await shot();
await compare('scroll', d, e);

console.log('console errors:', errors.length ? errors : '(none)');
await browser.close();
