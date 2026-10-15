import { useEffect, useState } from 'react';

/**
 * Tracks the user's reduced-motion preference and stays in sync with it.
 *
 * Checking the media query once at mount is the usual mistake: a user can turn
 * reduced motion on while the app is open, and an interface that ignores that
 * is not honouring the setting.
 */
export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  });

  useEffect(() => {
    if (!window.matchMedia) return undefined;
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = (event: MediaQueryListEvent) => setReduced(event.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  return reduced;
}
