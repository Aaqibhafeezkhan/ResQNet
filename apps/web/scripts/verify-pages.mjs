/**
 * Walks every public route and reports console errors, broken internal links
 * and the presence of the primary content on each page.
 */
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';

const base = process.env.RESQNET_BASE_URL ?? 'http://localhost:4173';
const outDir = process.argv[2] ?? '/tmp/resqnet-pages';
const viewportArg = process.argv[3];
const [width, height] = viewportArg
  ? viewportArg.split('x').map(Number)
  : [1440, 900];

await mkdir(outDir, { recursive: true });

const ROUTES = ['/', '/demo', '/download', '/contribute', '/about'];

const browser = await chromium.launch({
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const context = await browser.newContext({
  viewport: { width: width ?? 1440, height: height ?? 900 },
});
const page = await context.newPage();

let failures = 0;
const check = (name, passed, detail = '') => {
  if (!passed) failures += 1;
  console.log(`${passed ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
};

for (const route of ROUTES) {
  const errors = [];
  const onConsole = (m) => {
    if (m.type() === 'error') errors.push(m.text());
  };
  const onError = (e) => errors.push(`pageerror: ${e.message}`);
  page.on('console', onConsole);
  page.on('pageerror', onError);

  await page.goto(`${base}${route}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1400);

  const h1 = await page.locator('h1').first().textContent().catch(() => null);
  check(`${route} renders an h1`, Boolean(h1 && h1.trim().length > 0), h1?.slice(0, 50));

  const title = await page.title();
  check(`${route} has a page title`, title.length > 10, title.slice(0, 50));

  // Every internal link must point at a route that exists.
  const internalLinks = await page.evaluate(() =>
    Array.from(document.querySelectorAll('a[href]'))
      .map((a) => a.getAttribute('href') ?? '')
      .filter((href) => href.startsWith('/') && !href.startsWith('//')),
  );
  const uniqueLinks = Array.from(new Set(internalLinks));
  let broken = 0;
  for (const link of uniqueLinks) {
    const response = await page.request.get(`${base}${link}`);
    if (!response.ok()) {
      broken += 1;
      console.log(`      broken link: ${link} -> ${response.status()}`);
    }
  }
  check(`${route} has no broken internal links`, broken === 0, `${uniqueLinks.length} links checked`);

  // External links must be safe.
  const unsafe = await page.evaluate(() =>
    Array.from(document.querySelectorAll('a[target="_blank"]'))
      .filter((a) => {
        const rel = a.getAttribute('rel') ?? '';
        return !rel.includes('noopener');
      })
      .map((a) => a.getAttribute('href')),
  );
  check(`${route} external links use rel=noopener`, unsafe.length === 0, unsafe.join(', '));

  check(`${route} has no console errors`, errors.length === 0, errors.slice(0, 2).join(' | '));

  const name = route === '/' ? '_home' : route.replace(/\//g, '-').replace(/^-/, '');
  await page.screenshot({ path: `${outDir}/${name}-${width}.png` });

  page.off('console', onConsole);
  page.off('pageerror', onError);
}

console.log(failures === 0 ? '\nAll page checks passed' : `\n${failures} page checks failed`);
await browser.close();
process.exit(failures > 0 ? 1 : 0);
