import { describe, expect, it } from 'vitest';

import { formatReleaseSize, parseLatestAndroidRelease } from './releases';

const BASE = 'https://github.com/Gautam-aman/ResQNet/releases';

function release(overrides: Record<string, unknown> = {}) {
  return {
    tag_name: 'v0.2.0-beta',
    name: 'v0.2.0-beta — Android Experimental Preview',
    published_at: '2026-10-20T08:08:19Z',
    html_url: `${BASE}/tag/v0.2.0-beta`,
    draft: false,
    prerelease: true,
    assets: [
      {
        name: 'resqnet-v0.2.0-beta-release.apk',
        browser_download_url: `${BASE}/download/v0.2.0-beta/resqnet-v0.2.0-beta-release.apk`,
        size: 8_600_000,
        digest: 'sha256:abc123',
      },
      {
        name: 'SHA256SUMS',
        browser_download_url: `${BASE}/download/v0.2.0-beta/SHA256SUMS`,
        size: 500,
      },
    ],
    ...overrides,
  };
}

describe('release lookup', () => {
  it('selects the newest signed release APK', () => {
    const parsed = parseLatestAndroidRelease([
      release({
        tag_name: 'v0.1.0-alpha',
        published_at: '2026-10-18T19:27:16Z',
        html_url: `${BASE}/tag/v0.1.0-alpha`,
        assets: [
          {
            name: 'resqnet-v0.1.0-alpha-release.apk',
            browser_download_url: `${BASE}/download/v0.1.0-alpha/resqnet-v0.1.0-alpha-release.apk`,
            size: 7_000_000,
          },
        ],
      }),
      release(),
    ]);

    expect(parsed?.tag).toBe('v0.2.0-beta');
    expect(parsed?.apk.url).toContain('/releases/download/v0.2.0-beta/');
    expect(parsed?.apk.sha256).toBe('abc123');
    expect(parsed?.checksumsUrl).toContain('SHA256SUMS');
  });

  it('ignores drafts and releases without a release APK', () => {
    expect(
      parseLatestAndroidRelease([
        release({ draft: true }),
        release({
          tag_name: 'v9.0.0',
          assets: [
            {
              name: 'resqnet-v9.0.0-debug.apk',
              browser_download_url: `${BASE}/download/v9.0.0/resqnet-v9.0.0-debug.apk`,
            },
          ],
        }),
      ]),
    ).toBeNull();
  });

  it('rejects an asset link outside the project release path', () => {
    expect(
      parseLatestAndroidRelease([
        release({
          assets: [
            {
              name: 'resqnet-v0.2.0-beta-release.apk',
              browser_download_url: 'https://example.com/malware.apk',
            },
          ],
        }),
      ]),
    ).toBeNull();
  });

  it('rejects malformed payloads', () => {
    expect(parseLatestAndroidRelease(null)).toBeNull();
    expect(parseLatestAndroidRelease({})).toBeNull();
  });

  it('formats release sizes without inventing precision', () => {
    expect(formatReleaseSize(8_600_000)).toBe('8.2 MB');
    expect(formatReleaseSize(0)).toBe('Size listed on GitHub');
  });
});
