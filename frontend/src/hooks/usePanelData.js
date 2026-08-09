import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Fetches one panel's data, with cancellation and a stable reload handle.
 *
 * Seventeen panels each need the same four things: a loading flag, an error,
 * the data, and a way to refetch. Writing that in each of them would be
 * seventeen copies of the same `useEffect`, and the interesting part — getting
 * cancellation right — would be wrong in at least one.
 *
 * ## Why cancellation is not optional here
 *
 * A panel refetches whenever its range changes. Switching from "12 months" to
 * "7 days" fires a second request while the first is still running, and the
 * 12-month query is the slower of the two, so it very often lands *after* the
 * 7-day one. Without cancellation the panel would show twelve months of data
 * under a "7 days" label — a wrong answer that looks entirely correct.
 *
 * Two mechanisms guard against it. The AbortSignal cancels the in-flight
 * request, and a monotonic request id makes any late response that still
 * arrives get dropped rather than applied. The second exists because abort is
 * cooperative: a response already parsed by the time abort fires will still
 * resolve.
 *
 * ## Why the previous data is kept while reloading
 *
 * `data` is not cleared when a refetch starts. A panel that blanks to a
 * skeleton every time the operator nudges a filter flickers, and the flicker is
 * worse than a moment of slightly stale numbers — especially since the stale
 * numbers are about to be replaced by very similar ones.
 *
 * @param {(signal: AbortSignal) => Promise<any>} fetcher receives the signal
 * @param {Array<any>} deps re-fetch when any of these change
 * @param {{enabled?: boolean}} options set `enabled: false` to skip fetching,
 *   for a panel the current operator has no permission to load
 */
export default function usePanelData(fetcher, deps = [], options = {}) {
  const { enabled = true } = options;

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState(null);

  // Bumped on every load so a late response can tell whether it is still wanted.
  const requestId = useRef(0);
  // Incremented by reload() to force the effect to run again.
  const [reloadToken, setReloadToken] = useState(0);

  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      return undefined;
    }

    const controller = new AbortController();
    requestId.current += 1;
    const id = requestId.current;

    setLoading(true);
    setError(null);

    fetcherRef
      .current(controller.signal)
      .then((result) => {
        // A response from a superseded request is discarded rather than shown.
        if (id !== requestId.current) return;
        setData(result);
        setError(null);
      })
      .catch((err) => {
        if (id !== requestId.current) return;
        // An aborted request is not a failure — it is a request the panel no
        // longer wants — so it must not paint an error the operator has to
        // dismiss.
        if (controller.signal.aborted || err?.name === 'CanceledError'
            || err?.code === 'ERR_CANCELED') {
          return;
        }
        setError(err);
      })
      .finally(() => {
        if (id !== requestId.current) return;
        setLoading(false);
      });

    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, enabled, reloadToken]);

  /** Refetches without changing any dependency. Stable across renders. */
  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  return { data, loading, error, reload };
}
