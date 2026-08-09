import { useCallback, useEffect, useRef, useState } from 'react';
import dashboardConfigService from '../api/dashboardConfigService';

/**
 * The sidebar's global search. Task 3.
 *
 * ## Debounced, because the alternative is a request per keystroke
 *
 * Typing "samsung" would fire seven searches, six of which are already obsolete
 * when they return. The delay is short enough that the results feel immediate
 * and long enough that a normal typing speed produces one request.
 *
 * ## Late responses are discarded, not just aborted
 *
 * Each request carries a sequence number and only the newest one may write to
 * state. Abort alone is not enough: a response that has already been parsed when
 * the abort fires still resolves, and would replace results for a longer query
 * with results for a shorter one — the search box showing matches for "sam"
 * while the field reads "samsung".
 *
 * ## Below two characters it searches nothing
 *
 * A one-character query matches most of the catalogue, which is slower to
 * produce and useless to read. The hook clears the results and waits.
 */
export default function useGlobalSearch(delay = 250) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  const sequence = useRef(0);
  const timer = useRef(null);
  const controller = useRef(null);

  useEffect(() => {
    clearTimeout(timer.current);
    if (controller.current) controller.current.abort();

    const term = query.trim();
    if (term.length < 2) {
      setResults(null);
      setLoading(false);
      return undefined;
    }

    setLoading(true);
    timer.current = setTimeout(() => {
      sequence.current += 1;
      const id = sequence.current;
      controller.current = new AbortController();

      dashboardConfigService
        .search(term, controller.current.signal)
        .then((data) => {
          if (id !== sequence.current) return;
          setResults(data);
        })
        .catch(() => {
          if (id !== sequence.current) return;
          // A failed search shows no results rather than an error banner. The
          // box is a convenience; interrupting the operator's typing with a
          // dismissable message costs more than the failure does.
          setResults(null);
        })
        .finally(() => {
          if (id !== sequence.current) return;
          setLoading(false);
        });
    }, delay);

    return () => clearTimeout(timer.current);
  }, [query, delay]);

  useEffect(
    () => () => {
      clearTimeout(timer.current);
      if (controller.current) controller.current.abort();
    },
    []
  );

  const clear = useCallback(() => {
    setQuery('');
    setResults(null);
    setOpen(false);
  }, []);

  const total = results?.totalHits ?? 0;

  return { query, setQuery, results, loading, open, setOpen, clear, total };
}
