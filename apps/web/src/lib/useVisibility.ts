import { useEffect, useRef } from 'react';

/**
 * Pauses non-essential animation work while the page is hidden.
 *
 * A dashboard left open on a second monitor burns a core animating a mesh that
 * nobody is looking at, and a phone in a pocket spends battery on it. Anything
 * purely decorative subscribes here.
 */
export function useDocumentVisible(onChange: (visible: boolean) => void): void {
  const handler = useRef(onChange);
  handler.current = onChange;

  useEffect(() => {
    const listener = () => handler.current(!document.hidden);
    document.addEventListener('visibilitychange', listener);
    // Report the current state immediately so a caller mounting while hidden
    // does not start animating for one frame.
    listener();
    return () => document.removeEventListener('visibilitychange', listener);
  }, []);
}
