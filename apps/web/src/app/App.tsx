import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

import { LandingPage } from '@/pages/LandingPage';

/**
 * Route table.
 *
 * <p>The landing page is in the main bundle because it is the entry point for
 * every visitor. The command centre is split out and lazy-loaded, so someone
 * reading about the technology never downloads the dashboard, its state stores
 * or its topology renderer.
 */
const CommandPage = lazy(() =>
  import('@/pages/CommandPage').then((module) => ({ default: module.CommandPage })),
);
const SignInPage = lazy(() =>
  import('@/pages/SignInPage').then((module) => ({ default: module.SignInPage })),
);

export function App() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/command" element={<CommandPage />} />
        <Route path="/signin" element={<SignInPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}

function RouteFallback() {
  return (
    <div className="grid min-h-screen place-items-center bg-base-900">
      <div className="flex flex-col items-center gap-3">
        <span className="h-6 w-6 animate-signal-pulse rounded-full bg-network" />
        <span className="eyebrow">Loading</span>
      </div>
    </div>
  );
}
