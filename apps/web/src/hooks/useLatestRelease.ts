import { useEffect, useState } from 'react';

import {
  fetchLatestAndroidRelease,
  type AndroidRelease,
} from '@/lib/releases';

type ReleaseState =
  | { status: 'loading'; release: null }
  | { status: 'ready'; release: AndroidRelease | null }
  | { status: 'error'; release: null };

export function useLatestRelease(): ReleaseState {
  const [state, setState] = useState<ReleaseState>({
    status: 'loading',
    release: null,
  });

  useEffect(() => {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 8000);
    let active = true;

    fetchLatestAndroidRelease(controller.signal)
      .then((release) => {
        if (active) setState({ status: 'ready', release });
      })
      .catch(() => {
        if (active) setState({ status: 'error', release: null });
      })
      .finally(() => window.clearTimeout(timeout));

    return () => {
      active = false;
      controller.abort();
      window.clearTimeout(timeout);
    };
  }, []);

  return state;
}
