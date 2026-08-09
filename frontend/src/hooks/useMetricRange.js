import { useCallback, useState } from 'react';

/**
 * The selected time window for one panel, remembered between visits.
 *
 * Task 2 asks for switchable intervals. Each panel keeps its own selection
 * rather than sharing one global range, because the questions differ: an
 * operator looking at twelve months of finance very often wants seven days of
 * order efficiency on the same screen. One shared range would force them to
 * choose, and they would choose by switching back and forth.
 *
 * The choice is stored in localStorage keyed by panel, so it survives a reload.
 * A dashboard that resets every filter on every visit makes an operator redo
 * the same four clicks every morning.
 *
 * @param {string} panelId which panel this range belongs to
 * @param {string} initial the default when nothing is stored
 * @param {Array<string>} allowed the codes this panel accepts
 */
export default function useMetricRange(panelId, initial = '30d', allowed = null) {
  const storageKey = `es_range_${panelId}`;

  const [range, setRangeState] = useState(() => {
    if (typeof window === 'undefined') return initial;
    try {
      const stored = window.localStorage.getItem(storageKey);
      // A stored value that this panel does not offer is discarded rather than
      // used. Panels have different option sets, and a "24h" left over from a
      // panel that offers it would put a chart into a range with no button to
      // leave it.
      if (stored && (!allowed || allowed.includes(stored))) {
        return stored;
      }
    } catch {
      // Private browsing or a full quota. The default is still correct.
    }
    return initial;
  });

  const setRange = useCallback(
    (next) => {
      setRangeState(next);
      try {
        window.localStorage.setItem(storageKey, next);
      } catch {
        // The selection still applies this visit; it just will not be
        // remembered next time.
      }
    },
    [storageKey]
  );

  return [range, setRange];
}
