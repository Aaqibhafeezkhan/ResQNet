import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

import { PUBLIC_SITE_ONLY } from './buildMode';
import { PublicLayout } from '@/components/public/PublicLayout';
import { AboutPage } from '@/pages/AboutPage';
import { ContributePage } from '@/pages/ContributePage';
import { DemoPage } from '@/pages/DemoPage';
import { DownloadPage } from '@/pages/DownloadPage';
import { LandingPage } from '@/pages/LandingPage';

const LabPage = lazy(() =>
  import('@/pages/LabPage').then((module) => ({ default: module.LabPage })),
);

const CommandPage = PUBLIC_SITE_ONLY
  ? null
  : lazy(() =>
      import('@/pages/CommandPage').then((module) => ({ default: module.CommandPage })),
    );
const SignInPage = PUBLIC_SITE_ONLY
  ? null
  : lazy(() =>
      import('@/pages/SignInPage').then((module) => ({ default: module.SignInPage })),
    );

export function App() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route path="/" element={<LandingPage />} />
          <Route path="/demo" element={<DemoPage />} />
          <Route path="/lab" element={<LabPage />} />
          <Route path="/download" element={<DownloadPage />} />
          <Route path="/contribute" element={<ContributePage />} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
        {!PUBLIC_SITE_ONLY && CommandPage ? (
          <Route path="/command" element={<CommandPage />} />
        ) : null}
        {!PUBLIC_SITE_ONLY && SignInPage ? (
          <Route path="/signin" element={<SignInPage />} />
        ) : null}
      </Routes>
    </Suspense>
  );
}

function RouteFallback() {
  return (
    <div className="grid min-h-screen place-items-center bg-base-950">
      <div className="flex flex-col items-center gap-3">
        <span className="h-6 w-6 animate-signal-pulse rounded-full bg-network" />
        <span className="eyebrow">Loading</span>
      </div>
    </div>
  );
}
