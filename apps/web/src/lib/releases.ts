import { GITHUB_RELEASES, RELEASE_DOWNLOAD_PREFIX, RELEASES_API } from './links';

export interface ReleaseAsset {
  name: string;
  url: string;
  size: number;
  sha256?: string;
}

export interface AndroidRelease {
  tag: string;
  name: string;
  publishedAt: string;
  pageUrl: string;
  apk: ReleaseAsset;
  checksumsUrl?: string;
}

interface GitHubAsset {
  name?: unknown;
  browser_download_url?: unknown;
  size?: unknown;
  digest?: unknown;
}

interface GitHubRelease {
  tag_name?: unknown;
  name?: unknown;
  published_at?: unknown;
  html_url?: unknown;
  draft?: unknown;
  assets?: unknown;
}

export function parseLatestAndroidRelease(payload: unknown): AndroidRelease | null {
  if (!Array.isArray(payload)) return null;

  const releases = payload
    .filter((entry): entry is GitHubRelease => isRecord(entry))
    .filter((entry) => entry.draft !== true && typeof entry.tag_name === 'string')
    .sort((a, b) => dateValue(b.published_at) - dateValue(a.published_at));

  for (const release of releases) {
    if (!Array.isArray(release.assets)) continue;

    const assets = release.assets.filter((entry): entry is GitHubAsset =>
      isRecord(entry),
    );
    const apk = assets.find(
      (asset) =>
        typeof asset.name === 'string' &&
        /^resqnet-v.+-release\.apk$/.test(asset.name) &&
        isReleaseDownload(asset.browser_download_url),
    );

    if (!apk || typeof apk.name !== 'string' || typeof apk.browser_download_url !== 'string') {
      continue;
    }

    const tag = release.tag_name;
    if (typeof tag !== 'string') continue;

    const pageUrl = safeReleasePage(release.html_url);
    if (!pageUrl) continue;

    const checksums = assets.find(
      (asset) =>
        asset.name === 'SHA256SUMS' &&
        isReleaseDownload(asset.browser_download_url),
    );
    const digest =
      typeof apk.digest === 'string' && apk.digest.startsWith('sha256:')
        ? apk.digest.slice('sha256:'.length)
        : undefined;

    return {
      tag,
      name: typeof release.name === 'string' ? release.name : tag,
      publishedAt:
        typeof release.published_at === 'string' ? release.published_at : '',
      pageUrl,
      apk: {
        name: apk.name,
        url: apk.browser_download_url,
        size: typeof apk.size === 'number' ? apk.size : 0,
        ...(digest ? { sha256: digest } : {}),
      },
      ...(checksums && typeof checksums.browser_download_url === 'string'
        ? { checksumsUrl: checksums.browser_download_url }
        : {}),
    };
  }

  return null;
}

export async function fetchLatestAndroidRelease(
  signal?: AbortSignal,
): Promise<AndroidRelease | null> {
  const response = await fetch(RELEASES_API, {
    headers: {
      Accept: 'application/vnd.github+json',
    },
    ...(signal ? { signal } : {}),
  });

  if (!response.ok) {
    throw new Error(`GitHub release lookup failed with status ${response.status}`);
  }

  return parseLatestAndroidRelease(await response.json());
}

export function formatReleaseSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return 'Size listed on GitHub';
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatReleaseDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Date listed on GitHub';
  return new Intl.DateTimeFormat('en', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date);
}

export function releasesPage(): string {
  return GITHUB_RELEASES;
}

function isReleaseDownload(value: unknown): value is string {
  if (typeof value !== 'string' || !value.startsWith(RELEASE_DOWNLOAD_PREFIX)) {
    return false;
  }
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname === 'github.com';
  } catch {
    return false;
  }
}

function safeReleasePage(value: unknown): string | null {
  if (typeof value !== 'string' || !value.startsWith(`${GITHUB_RELEASES}/`)) {
    return null;
  }
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname === 'github.com'
      ? value
      : null;
  } catch {
    return null;
  }
}

function dateValue(value: unknown): number {
  if (typeof value !== 'string') return 0;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? 0 : time;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
