import { ArrowUpRight, Check, CircleAlert, Download, PackageCheck, Smartphone } from 'lucide-react';

import { PageHero } from '@/components/public/PageHero';
import { ReleaseSummary } from '@/components/public/ReleaseSummary';
import { SafetyNote } from '@/components/public/SafetyNote';
import { Reveal } from '@/components/primitives/Reveal';
import {
  GITHUB_DEVICE_TESTING,
  GITHUB_RELEASES,
  GITHUB_SECURITY,
} from '@/lib/links';
import { usePageMeta } from '@/lib/seo';

const STEPS = [
  {
    title: 'Download from GitHub',
    body: 'Use the release panel on this page. It resolves the newest published release with a signed release APK. Never install an APK from a fork or a mirror.',
  },
  {
    title: 'Verify the checksum',
    body: 'Download SHA256SUMS and compare it before installing. On macOS use shasum -a 256 -c SHA256SUMS; on Linux use sha256sum -c SHA256SUMS.',
  },
  {
    title: 'Allow this source only',
    body: 'Android will warn because the app is not from Play Store. That warning is correct. Allow installation only for the file you just verified from this repository.',
  },
  {
    title: 'Grant nearby devices',
    body: 'The app cannot discover or connect without the nearby-devices permission. If it is denied, the app says so rather than pretending to work.',
  },
  {
    title: 'Opt in to the mesh',
    body: 'Mesh participation is an explicit choice. A phone does not silently become a relay. Test with a second device before relying on it.',
  },
];

const VERIFIED = [
  'Signed release APK published by the project release workflow',
  'Automated Android unit and instrumented tests in CI',
  'Developer report of two physical phones working together',
];

const NOT_VERIFIED = [
  'Multi-hop delivery across three or more physical devices',
  'Acknowledgement over real radios',
  'Physical range, penetration or battery behaviour',
  'Operation during a real emergency or under safety certification',
];

export function DownloadPage() {
  usePageMeta({
    title: 'Android preview — ResQNet',
    description:
      'Install the latest signed ResQNet Android experimental preview, verify its checksum and understand exactly what has and has not been tested.',
    path: '/download',
  });

  return (
    <>
      <PageHero
        eyebrow="Android preview"
        title={
          <>
            Put the mesh in your{' '}
            <span className="font-display font-normal italic">pocket.</span>
          </>
        }
        body="A signed experimental Android build. No account, no mobile data and no cloud dependency in the SOS path. Verify the artifact before you install it."
        aside={
          <div className="rounded-2xl border border-base-600 bg-base-900/70 p-5">
            <p className="eyebrow">Device floor</p>
            <p className="mt-4 flex items-baseline gap-2 text-3xl font-semibold tracking-tight text-ink-primary">
              Android 8.0
              <span className="text-xs font-normal text-ink-tertiary">API 26+</span>
            </p>
            <p className="mt-3 text-xs leading-relaxed text-ink-secondary">
              Package <span className="font-mono text-ink-tertiary">dev.resqnet.android</span>.
              Nearby-devices permission required.
            </p>
          </div>
        }
      />

      <section className="section-space border-b border-base-600">
        <div className="container-page grid gap-10 lg:grid-cols-[minmax(0,0.9fr)_minmax(340px,1.1fr)] lg:gap-16">
          <div>
            <ReleaseSummary compact />
            <SafetyNote className="mt-5" title="Experimental pre-release">
              This is not a consumer messaging app and not a replacement for
              emergency services. If someone is in immediate danger, call your
              local emergency number.
            </SafetyNote>
          </div>

          <div>
            <span className="eyebrow">Install in order</span>
            <ol className="mt-5 divide-y divide-base-600 border-y border-base-600">
              {STEPS.map((step, index) => (
                <Reveal
                  key={step.title}
                  delay={index * 0.03}
                  className="grid grid-cols-[34px_1fr] gap-4 py-5"
                >
                  <span className="tabular pt-0.5 text-[11px] font-semibold text-network">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <div>
                    <h2 className="text-sm font-semibold text-ink-primary">{step.title}</h2>
                    <p className="mt-1.5 text-[13px] leading-relaxed text-ink-secondary">
                      {step.body}
                    </p>
                  </div>
                </Reveal>
              ))}
            </ol>
          </div>
        </div>
      </section>

      <section className="section-space border-b border-base-600 bg-base-900">
        <div className="container-page grid gap-11 lg:grid-cols-2 lg:gap-16">
          <div>
            <div className="flex items-center gap-2.5">
              <PackageCheck size={17} className="text-delivered" aria-hidden="true" />
              <h2 className="text-xl font-semibold tracking-tight text-ink-primary">
                What is verified
              </h2>
            </div>
            <ul className="mt-5 space-y-3">
              {VERIFIED.map((item) => (
                <li key={item} className="flex gap-3 text-[13px] leading-relaxed text-ink-secondary">
                  <Check size={14} className="mt-0.5 shrink-0 text-delivered" aria-hidden="true" />
                  {item}
                </li>
              ))}
            </ul>
          </div>

          <div>
            <div className="flex items-center gap-2.5">
              <CircleAlert size={17} className="text-pending" aria-hidden="true" />
              <h2 className="text-xl font-semibold tracking-tight text-ink-primary">
                What is not verified
              </h2>
            </div>
            <ul className="mt-5 space-y-3">
              {NOT_VERIFIED.map((item) => (
                <li key={item} className="flex gap-3 text-[13px] leading-relaxed text-ink-secondary">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-pending" aria-hidden="true" />
                  {item}
                </li>
              ))}
            </ul>
            <a
              href={GITHUB_DEVICE_TESTING}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-6 inline-flex min-h-[28px] items-center gap-1.5 text-xs font-medium text-network transition-colors hover:text-ink-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-network"
            >
              Read the device testing procedure
              <ArrowUpRight size={12} aria-hidden="true" />
            </a>
          </div>
        </div>
      </section>

      <section className="section-space">
        <div className="container-page grid gap-8 lg:grid-cols-[1fr_1fr]">
          <div className="rounded-2xl border border-base-600 bg-base-850 p-6 transition-[transform,border-color] duration-500 ease-cinematic hover:-translate-y-1 hover:border-network/35 sm:p-8">
            <Smartphone size={18} className="text-network" aria-hidden="true" />
            <h2 className="mt-5 text-lg font-semibold text-ink-primary">Prefer the simulator?</h2>
            <p className="mt-2 text-[13px] leading-relaxed text-ink-secondary">
              The release page also carries the self-contained simulator and
              coordination gateway jars. The simulator needs only a JDK — no
              database, no network and no Android device.
            </p>
            <a
              href={GITHUB_RELEASES}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-5 inline-flex min-h-[28px] items-center gap-1.5 text-xs font-medium text-network transition-colors hover:text-ink-primary"
            >
              Browse release artifacts
              <ArrowUpRight size={12} aria-hidden="true" />
            </a>
          </div>

          <div className="rounded-2xl border border-pending/25 bg-pending-soft p-6 transition-[transform,border-color] duration-500 ease-cinematic hover:-translate-y-1 hover:border-pending/50 sm:p-8">
            <Download size={18} className="text-pending" aria-hidden="true" />
            <h2 className="mt-5 text-lg font-semibold text-ink-primary">Found a problem?</h2>
            <p className="mt-2 text-[13px] leading-relaxed text-ink-secondary">
              Say whether you reproduced, read or inferred it. Security issues
              must be reported privately, never in a public issue.
            </p>
            <a
              href={GITHUB_SECURITY}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-5 inline-flex min-h-[28px] items-center gap-1.5 text-xs font-medium text-pending transition-colors hover:text-ink-primary"
            >
              Read the security policy
              <ArrowUpRight size={12} aria-hidden="true" />
            </a>
          </div>
        </div>
      </section>
    </>
  );
}
