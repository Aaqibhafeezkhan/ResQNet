/** Start npm run preview first. Screenshots stay outside the repository. */
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';
const out = process.argv[2] ?? '/tmp/resqnet-contributor-review';
await mkdir(out, { recursive: true });
const browser = await chromium.launch();
let failures = 0;
const check = (name, ok) => { console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`); if (!ok) failures++; };
try {
  for (const width of [320, 390, 768, 1024, 1440]) {
    const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    const response = await page.goto(`${process.env.RESQNET_BASE_URL ?? 'http://127.0.0.1:4173'}/contribute`);
    await page.getByRole('heading', { name: 'Your first contribution starts here.' }).waitFor();
    check(`${width}: page renders`, response.ok());
    check(`${width}: six cards`, await page.locator('article').count() === 6);
    const issueLinks = await page.getByRole('link', { name: /^Explore issue #/ }).evaluateAll(links => links.map(link => link.href));
    check(`${width}: exact issue destinations`, JSON.stringify(issueLinks) === JSON.stringify([75, 83, 78, 68, 56, 84].map(issue => `https://github.com/Gautam-aman/ResQNet/issues/${issue}`)));
    check(`${width}: no horizontal cropping`, await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    check(`${width}: status guidance`, await page.getByText(/Check GitHub for current scope, status and assignees/).isVisible());
    check(`${width}: live search`, (await page.getByRole('link', { name: 'See all current good first issues' }).getAttribute('href')).includes('is%3Aopen'));
    for (const link of await page.getByRole('link', { name: /^Explore issue #/ }).all()) {
      await link.scrollIntoViewIfNeeded();
      const box = await link.boundingBox();
      check(`${width}: ${await link.getAttribute('aria-label')} fits with 44px target`, box && box.x >= 0 && box.x + box.width <= width && box.height >= 44);
    }
    await page.reload();
    let found = false;
    for (let i = 0; i < 80 && !found; i++) {
      await page.keyboard.press('Tab');
      found = await page.evaluate(() => document.activeElement?.getAttribute('aria-label')?.startsWith('Explore issue #') ?? false);
    }
    check(`${width}: keyboard focus indicator`, found && await page.evaluate(() => getComputedStyle(document.activeElement).boxShadow !== 'none' || getComputedStyle(document.activeElement).outlineStyle !== 'none'));
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: `${out}/contribute-${width}.png`, fullPage: true });
    check(`${width}: no runtime errors`, !errors.length);
    await context.close();
  }
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, reducedMotion: 'no-preference' });
  await page.goto(`${process.env.RESQNET_BASE_URL ?? 'http://127.0.0.1:4173'}/contribute`);
  for (const heading of await page.getByRole('heading').all()) await heading.scrollIntoViewIfNeeded();
  await page.screenshot({ path: `${out}/contribute-motion-desktop.png`, fullPage: true });
  check('ordinary motion: cards visible', await page.locator('article').first().isVisible());
} finally { await browser.close(); }
process.exitCode = failures ? 1 : 0;
