import { ArrowUpRight, Download, FileCheck2, GitBranch } from 'lucide-react';

import { ButtonAnchor, ButtonLink } from '@/components/primitives/Button';
import { useLatestRelease } from '@/hooks/useLatestRelease';
import { GITHUB_RELEASES, GITHUB_REPO } from '@/lib/links';
import {
  formatReleaseDate,
  formatReleaseSize,
} from '@/lib/releases';

interface ReleaseSummaryProps {
  compact?: boolean;
}

export function ReleaseSummary({ compact = false }: ReleaseSummaryProps) {
  const { status, release } = useLatestRelease();

  return (
    <div className="surface-raised overflow-hidden">
      <div className="border-b border-base-600 px-5 py-4 sm:px-6">
        <div className="flex items-center justify-between gap-3">
          <span className="eyebrow">Latest Android build</span>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-pending-soft px-2.5 py-1 text-[9px] font-semibold uppercase tracking-widest text-pending">
            Experimental
          </span>
        </div>
      </div>

      <div className="p-5 sm:p-6">
        {status === 'loading' ? (
          <div>
            <p className="text-lg font-semibold text-ink-primary">Checking the release feed…</p>
            <p className="mt-2 text-[13px] leading-relaxed text-ink-secondary">
              Resolving the newest published Android artifact from GitHub.
            </p>
          </div>
        ) : release ? (
          <div>
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-2xl font-semibold tracking-tight text-ink-primary">
                  {release.tag}
                </p>
                <p className="mt-1 text-[13px] text-ink-secondary">
                  {release.name}
                </p>
              </div>
              <Download size={20} className="mt-1 text-network" aria-hidden="true" />
            </div>

            <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-3 border-t border-base-700 pt-4 text-xs">
              <div>
                <dt className="eyebrow">Published</dt>
                <dd className="mt-1 text-ink-secondary">{formatReleaseDate(release.publishedAt)}</dd>
              </div>
              <div>
                <dt className="eyebrow">APK size</dt>
                <dd className="mt-1 tabular text-ink-secondary">
                  {formatReleaseSize(release.apk.size)}
                </dd>
              </div>
              <div className="col-span-2">
                <dt className="eyebrow">Artifact</dt>
                <dd className="mt-1 break-all font-mono text-[11px] text-ink-tertiary">
                  {release.apk.name}
                </dd>
              </div>
            </dl>

            <div className="mt-5 flex flex-col gap-2 sm:flex-row">
              <ButtonAnchor
                href={release.apk.url}
                size={compact ? 'default' : 'large'}
                className="flex-1"
              >
                <Download size={16} aria-hidden="true" />
                Download signed APK
              </ButtonAnchor>
              <ButtonAnchor
                href={release.checksumsUrl ?? release.pageUrl}
                variant="secondary"
                target="_blank"
                rel="noreferrer noopener"
                className="flex-1"
              >
                <FileCheck2 size={15} aria-hidden="true" />
                Verify checksum
              </ButtonAnchor>
            </div>
          </div>
        ) : (
          <div>
            <p className="text-lg font-semibold text-ink-primary">Release feed unavailable</p>
            <p className="mt-2 text-[13px] leading-relaxed text-ink-secondary">
              GitHub is the source of truth for signed artifacts. Open the
              releases page to choose the newest Android preview.
            </p>
          </div>
        )}

        <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-base-700 pt-4 text-[11px] text-ink-tertiary">
          <a
            href={GITHUB_RELEASES}
            target="_blank"
            rel="noreferrer noopener"
            className="inline-flex min-h-[28px] items-center gap-1.5 transition-colors hover:text-ink-primary"
          >
            All releases
            <ArrowUpRight size={11} aria-hidden="true" />
          </a>
          <a
            href={GITHUB_REPO}
            target="_blank"
            rel="noreferrer noopener"
            className="inline-flex min-h-[28px] items-center gap-1.5 transition-colors hover:text-ink-primary"
          >
            <GitBranch size={11} aria-hidden="true" />
            Build from source
          </a>
        </div>

        {!compact ? (
          <ButtonLink to="/download" variant="ghost" className="mt-3 w-full">
            Read the install notes
            <ArrowUpRight size={14} aria-hidden="true" />
          </ButtonLink>
        ) : null}
      </div>
    </div>
  );
}
