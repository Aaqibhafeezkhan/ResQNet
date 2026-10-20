import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const dist = resolve(process.cwd(), 'dist');
const rawUrl =
  process.env.SITE_URL ??
  process.env.VERCEL_PROJECT_PRODUCTION_URL ??
  process.env.VERCEL_URL ??
  '';
const siteUrl = normaliseUrl(rawUrl);
const routes = ['/', '/demo', '/download', '/contribute', '/about'];

const index = await readFile(resolve(dist, 'index.html'), 'utf8');
if (siteUrl) {
  const tags = [
    `<link rel="canonical" href="${siteUrl}/" />`,
    `<meta property="og:url" content="${siteUrl}/" />`,
    `<meta property="og:image" content="${siteUrl}/og-resqnet.png" />`,
    '<meta property="og:image:width" content="1200" />',
    '<meta property="og:image:height" content="630" />',
    '<meta property="og:image:alt" content="ResQNet — local-first emergency messaging" />',
    `<meta name="twitter:image" content="${siteUrl}/og-resqnet.png" />`,
  ].join('\n    ');
  await writeFile(
    resolve(dist, 'index.html'),
    index.replace('<!-- site-meta -->', tags),
    'utf8',
  );

  const today = new Date().toISOString().slice(0, 10);
  const urls = routes
    .map(
      (route) =>
        `  <url><loc>${siteUrl}${route}</loc><lastmod>${today}</lastmod><changefreq>${route === '/' ? 'weekly' : 'monthly'}</changefreq></url>`,
    )
    .join('\n');
  await writeFile(
    resolve(dist, 'sitemap.xml'),
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`,
    'utf8',
  );
  await writeFile(
    resolve(dist, 'robots.txt'),
    `User-agent: *\nAllow: /\n\nSitemap: ${siteUrl}/sitemap.xml\n`,
    'utf8',
  );
} else {
  await writeFile(resolve(dist, 'robots.txt'), 'User-agent: *\nAllow: /\n', 'utf8');
  console.warn('SITE_URL is not set; skipped canonical URLs and sitemap generation.');
}

function normaliseUrl(value) {
  const trimmed = value.trim();
  if (!trimmed) return '';
  const withScheme = /^https?:\/\//.test(trimmed) ? trimmed : `https://${trimmed}`;
  try {
    const url = new URL(withScheme);
    return url.origin.replace(/\/$/, '');
  } catch {
    return '';
  }
}
