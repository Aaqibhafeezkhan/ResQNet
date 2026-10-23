/**
 * Smoke-test the public Mesh Replay Lab in a real Chromium browser.
 *
 * Start the public preview first (`npm run build:public && npm run preview`).
 * Screenshots are written outside the repository by default.
 */
import { chromium } from 'playwright';
import { mkdir } from 'node:fs/promises';

const base = process.env.RESQNET_BASE_URL ?? 'http://127.0.0.1:4173';
const outDir = process.argv[2] ?? '/tmp/resqnet-lab-review';
await mkdir(outDir, { recursive: true });

const browser = await chromium.launch({
  args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
let failures = 0;
const check = (name, passed, detail = '') => {
  if (!passed) failures += 1;
  console.log(`${passed ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
};

for (const viewport of [
  { width: 1440, height: 1000, name: 'desktop' },
  { width: 390, height: 844, name: 'mobile' },
  { width: 320, height: 700, name: 'small-mobile' },
]) {
  const context = await browser.newContext({ viewport });
  const page = await context.newPage();
  const errors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });
  page.on('pageerror', (error) => errors.push(error.message));

  await page.goto(`${base}/lab`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('heading', { name: /read the simulation/i }).waitFor();
  check(`${viewport.name}: /lab resolves`, (await page.title()).includes('Mesh replay lab'));
  check(
    `${viewport.name}: simulated-data disclosure is visible`,
    await page.getByText('Not real radio telemetry', { exact: true }).first().isVisible(),
  );

  await page.getByRole('button', { name: 'Open the ring sample' }).click();
  await page.getByRole('heading', { name: 'Replay', exact: true }).waitFor();
  check(
    `${viewport.name}: sample report loads`,
    await page.getByText('Ring · 6 nodes · seed 42', { exact: true }).first().isVisible(),
  );
  check(`${viewport.name}: recorded topology renders`, (await page.locator('svg').count()) > 0);

  const position = page.getByRole('slider', { name: 'Replay position' });
  const total = Number(await position.getAttribute('max'));
  await page.getByRole('button', { name: 'Next event' }).click();
  check(`${viewport.name}: next-event control advances`, (await position.inputValue()) === '1');
  await position.fill(String(total));
  check(`${viewport.name}: replay can seek to the end`, (await position.inputValue()) === String(total));
  await page.getByLabel('Message to inspect').selectOption({ index: 1 });
  check(
    `${viewport.name}: message journey is evidence-backed`,
    await page.getByText('Acknowledged end to end', { exact: true }).isVisible(),
  );
  const command = await page.locator('pre code').textContent();
  check(
    `${viewport.name}: scenario command is generated`,
    Boolean(command?.includes('mvn -q -pl packages/mesh-simulator exec:java') && command.includes('--topology')),
  );

  await page.getByRole('tab', { name: 'Compare' }).click();
  await page.getByLabel('Report A', { exact: true }).selectOption('ring-6-seed-42');
  await page.getByLabel('Report B', { exact: true }).selectOption('two-cluster-6-seed-42');
  check(
    `${viewport.name}: two real samples compare`,
    await page.getByText('Not a controlled comparison', { exact: true }).isVisible(),
  );
  check(`${viewport.name}: comparison metrics render`, await page.getByText('Recorded metrics').isVisible());
  await page.screenshot({ path: `${outDir}/lab-${viewport.name}.png`, fullPage: true });

  const dimensions = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }));
  check(
    `${viewport.name}: no horizontal overflow`,
    dimensions.scrollWidth <= dimensions.clientWidth + 1,
    `${dimensions.scrollWidth} / ${dimensions.clientWidth}px`,
  );
  check(`${viewport.name}: no browser errors`, errors.length === 0, errors.slice(0, 2).join(' | '));

  await page.goto(`${base}/demo`, { waitUntil: 'domcontentloaded' });
  await page.locator('h1').first().waitFor();
  check(`${viewport.name}: existing /demo route still resolves`, (await page.title()).length > 10);
  await context.close();
}

await browser.close();
console.log(failures === 0 ? '\nAll lab browser checks passed' : `\n${failures} lab browser checks failed`);
process.exit(failures > 0 ? 1 : 0);
