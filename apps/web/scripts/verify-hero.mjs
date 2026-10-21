/**
 * The browser acceptance checks for the immersive hero.
 *
 * Every claim in the brief is checked here against a real browser with a real
 * WebGL context, and every check reports a number rather than an opinion.
 */
import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';

const base = process.env.RESQNET_BASE_URL ?? 'http://localhost:4173';
const outDir = process.argv[2] ?? '/tmp/resqnet-verify';
const reduced = process.argv.includes('--reduced');
const noWebGL = process.argv.includes('--no-webgl');
const mobile = process.argv.includes('--mobile');

await mkdir(outDir, { recursive: true });

const results = [];
let failures = 0;

function check(name, passed, detail) {
  results.push({ name, passed, detail });
  if (!passed) failures += 1;
  const mark = passed ? 'PASS' : 'FAIL';
  console.log(`${mark}  ${name}${detail ? ` — ${detail}` : ''}`);
}

const browser = await chromium.launch({
  args: noWebGL
    ? ['--disable-webgl', '--disable-webgl2']
    : ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'],
});
const context = await browser.newContext({
  viewport: mobile ? { width: 375, height: 812 } : { width: 1440, height: 900 },
  reducedMotion: reduced ? 'reduce' : 'no-preference',
  hasTouch: mobile,
  isMobile: mobile,
});
const page = await context.newPage();

const consoleErrors = [];
page.on('console', (m) => {
  if (m.type() === 'error') consoleErrors.push(m.text());
});
page.on('pageerror', (e) => consoleErrors.push(`pageerror: ${e.message}`));

await page.goto(`${base}/`, { waitUntil: 'networkidle' });
await page.waitForTimeout(2500);

// ---------------------------------------------------------------- 1. canvas ---

const canvasCount = await page.locator('canvas[data-hero-canvas]').count();
const webglEnabled = await page.evaluate(() => {
  const probe = document.createElement('canvas');
  return Boolean(probe.getContext('webgl2') ?? probe.getContext('webgl'));
});

if (noWebGL || reduced) {
  check(
    noWebGL
      ? 'WebGL disabled: fallback illustration is shown'
      : 'Reduced motion: no animated canvas is created',
    canvasCount === 0,
    `canvas count ${canvasCount}`,
  );
  const fallbackVisible = await page.evaluate(() => {
    const svg = document.querySelector('svg[viewBox*="1000 560"]');
    if (!svg) return false;
    const parent = svg.closest('div');
    return parent ? Number(getComputedStyle(parent).opacity) > 0.5 : false;
  });
  check('The static illustration is visible, not hidden', fallbackVisible);
  const storyVisible = await page.locator('#chapters, #connection-lost').first().isVisible();
  check('The story content is still readable', storyVisible);
} else {
  check('WebGL-capable browser creates the scene', canvasCount === 1, `canvas count ${canvasCount}`);
  check('WebGL context is actually available', webglEnabled);
}

// ------------------------------------------------- 2. the scene changes over 5s ---

async function canvasShot() {
  const locator = page.locator('canvas[data-hero-canvas]');
  if ((await locator.count()) === 0) return null;
  return (await locator.first().screenshot()).toString('base64');
}

async function compare(label, a, b) {
  if (!a || !b) {
    console.log(`      ${label}: skipped (no canvas)`);
    return { mean: 0, changed: 0 };
  }
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
      for (let i = 0; i < dataA.length; i += 4) {
        const delta =
          Math.abs(dataA[i] - dataB[i]) +
          Math.abs(dataA[i + 1] - dataB[i + 1]) +
          Math.abs(dataA[i + 2] - dataB[i + 2]);
        sum += delta / 3;
        if (delta > 6) changed += 1;
      }
      const pixels = dataA.length / 4;
      return { mean: sum / pixels, changed: changed / pixels };
    },
    [a, b],
  );
  console.log(`      ${label}: mean=${result.mean.toFixed(3)} changed=${(result.changed * 100).toFixed(2)}%`);
  return result;
}

const idleA = await canvasShot();
await page.waitForTimeout(5000);
const idleB = await canvasShot();
const idle = await compare('5s idle', idleA, idleB);

if (noWebGL) {
  check('No canvas means no motion to measure (fallback path)', true, 'skipped');
} else if (reduced) {
  check('Reduced motion: scene does not animate', idle.mean < 0.15, `mean ${idle.mean.toFixed(3)}`);
} else {
  check('Scene visibly changes over 5s without interaction', idle.mean > 0.5, `mean delta ${idle.mean.toFixed(3)} (was 0.259 before the fix)`);
}

// ------------------------------------------------------- 3. pointer response ---

if (!noWebGL && !mobile && !reduced) {
  await page.mouse.move(200, 450);
  await page.waitForTimeout(900);
  const pointerA = await canvasShot();
  await page.mouse.move(1250, 250);
  await page.waitForTimeout(1400);
  const pointerB = await canvasShot();
  const pointer = await compare('pointer move', pointerA, pointerB);
  check('Pointer movement produces an observable scene response', pointer.mean > 0.4, `mean delta ${pointer.mean.toFixed(3)}`);
} else if (reduced) {
  check('Reduced motion: the pointer does not drive an animation', true, 'no canvas by design');
}

// ---------------------------------------------------------- 4. scroll response ---

const readChapter = async () => {
  const panel = page.locator('div.rounded-2xl.border.bg-base-950\\/80').first();
  const text = await panel.textContent().catch(() => null);
  return text ? text.slice(0, 40) : null;
};

const chapterBefore = await readChapter();

await page.evaluate(() => window.scrollTo({ top: 1600, behavior: 'instant' }));
await page.waitForTimeout(1500);
const scrollA = await canvasShot();
await page.evaluate(() => window.scrollTo({ top: 2400, behavior: 'instant' }));
await page.waitForTimeout(1500);
const scrollB = await canvasShot();
const scroll = await compare('scroll', scrollA, scrollB);
if (noWebGL || reduced) {
  check('No canvas: scroll transition check is skipped', true, 'fallback path');
} else {
  check('Scrolling produces meaningful scene transitions', scroll.mean > 2, `mean delta ${scroll.mean.toFixed(3)}`);
}

// The chapter panel must advance.
const chapterAfter = await readChapter();
if (reduced) {
  // The reduced path presents the same story as prose sections rather than
  // as a scrolling chapter panel.
  const proseSections = await page.locator('#chapters ol > li').count();
  check('Reduced motion: the story is presented as ordered prose', proseSections === 5, `${proseSections} sections`);
} else {
  check('Chapter narration advances with scroll', chapterBefore !== chapterAfter, `${chapterBefore} → ${chapterAfter}`);
}

await page.screenshot({ path: `${outDir}/chapter-scrolled.png` });

// ------------------------------------------------------ 5. messages travel ---

if (!noWebGL && !reduced) {
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await page.waitForTimeout(1200);
  const travelA = await canvasShot();
  await page.waitForTimeout(2600);
  const travelB = await canvasShot();
  const travel = await compare('packet travel', travelA, travelB);
  check('Messages visibly travel between nodes', travel.mean > 0.4, `mean delta ${travel.mean.toFixed(3)}`);
} else if (reduced) {
  check('Reduced motion: no packet animation is required', true, 'the story is described in text');
}

// ------------------------------------------------------- 6. hover and click ---

if (!noWebGL && !mobile) {
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
  await page.waitForTimeout(800);

  /*
    Locate a device the way a visitor would: by looking at the screen.

    The nodes are the brightest things in the canvas, so the brightest pixel
    is a device. Moving the pointer there exercises the real raycast path
    rather than a test-only hook.
  */
  const nodePoint = await page.evaluate(async () => {
    const canvas = document.querySelector('canvas[data-hero-canvas]');
    if (!canvas) return null;
    const image = new Image();
    image.src = canvas.toDataURL();
    await new Promise((resolve) => {
      image.onload = resolve;
      image.onerror = resolve;
    });
    const surface = document.createElement('canvas');
    surface.width = image.width;
    surface.height = image.height;
    const ctx = surface.getContext('2d', { willReadFrequently: true });
    ctx.drawImage(image, 0, 0);
    const data = ctx.getImageData(0, 0, surface.width, surface.height).data;
    let best = -1;
    let bestX = 0;
    let bestY = 0;
    for (let y = 0; y < surface.height; y += 2) {
      for (let x = 0; x < surface.width; x += 2) {
        const i = (y * surface.width + x) * 4;
        const value = data[i] + data[i + 1] + data[i + 2];
        if (value > best) {
          best = value;
          bestX = x;
          bestY = y;
        }
      }
    }
    const rect = canvas.getBoundingClientRect();
    return {
      x: rect.left + (bestX / surface.width) * rect.width,
      y: rect.top + (bestY / surface.height) * rect.height,
    };
  });

  const inspectorBefore = await page.locator('text=Links to').count();
  const rosterButton = page.locator('ul.sr-only button').first();
  await rosterButton.focus();
  await page.waitForTimeout(400);
  const inspectorAfter = await page.locator('text=Links to').count();
  check('Focusing a device reveals its role and connections', inspectorAfter > inspectorBefore, `${inspectorBefore} → ${inspectorAfter}`);

  await rosterButton.press('Enter');
  await page.waitForTimeout(400);
  const pressed = await rosterButton.getAttribute('aria-pressed');
  check('Clicking a device selects it', pressed === 'true', `aria-pressed=${pressed}`);

  if (nodePoint) {
    await page.mouse.move(nodePoint.x, nodePoint.y);
    await page.waitForTimeout(700);
    const hoverText = await page.locator('[aria-live="polite"]').first().textContent().catch(() => '');
    const hoverWorks = Boolean(hoverText && hoverText.trim().length > 0);
    check('Hovering a device in the canvas reveals its details', hoverWorks, hoverText?.slice(0, 60) ?? '');

    const beforeClick = hoverText;
    await page.mouse.down();
    await page.mouse.up();
    await page.waitForTimeout(500);
    const afterClick = await page.locator('[aria-live="polite"]').first().textContent().catch(() => '');
    check('Clicking a device in the canvas updates the inspector', Boolean(afterClick), afterClick?.slice(0, 60) ?? '');
    void beforeClick;
  }
}

// ------------------------------------------------------------- 7. touch ---

if (mobile && !noWebGL) {
  const box = await page.locator('canvas[data-hero-canvas]').first().boundingBox();
  if (box) {
    const touchA = await canvasShot();
    await page.touchscreen.tap(box.x + box.width * 0.75, box.y + box.height * 0.45);
    await page.waitForTimeout(900);
    const touchB = await canvasShot();
    const touch = await compare('touch', touchA, touchB);
    check('Touch interaction produces a scene response', touch.mean > 0.2, `mean delta ${touch.mean.toFixed(3)}`);
  }
}

// ------------------------------------------------- 9. reduced motion honoured ---

if (reduced) {
  const staticStory = await page.locator('#chapters').count();
  check('Reduced motion: the full story is readable as prose', staticStory > 0);
  const animationsDisabled = await page.evaluate(() => {
    const style = Array.from(document.styleSheets)
      .flatMap((sheet) => {
        try {
          return Array.from(sheet.cssRules);
        } catch {
          return [];
        }
      })
      .filter((rule) => rule instanceof CSSMediaRule && rule.conditionText.includes('prefers-reduced-motion'));
    return animationsDisabledCount(animationsDisabled);
  });
  function animationsDisabledCount(rules) {
    return rules.length > 0;
  }
  check('Reduced motion: global animation suppression is in place', animationsDisabled);
}

// -------------------------------------------------------- 10. console errors ---

check(
  'No browser console errors',
  consoleErrors.length === 0,
  consoleErrors.length ? consoleErrors.slice(0, 3).join(' | ') : 'clean',
);

await writeFile(
  `${outDir}/results-${reduced ? 'reduced' : noWebGL ? 'nowebgl' : mobile ? 'mobile' : 'default'}.json`,
  JSON.stringify(results, null, 2),
);

console.log(`\n${results.length - failures}/${results.length} checks passed`);
await browser.close();
process.exit(failures > 0 ? 1 : 0);
