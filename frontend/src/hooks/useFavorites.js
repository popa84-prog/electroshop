import { useCallback, useEffect, useState } from 'react';
import dashboardConfigService from '../api/dashboardConfigService';

/**
 * The admin routes pinned to the top of the navigation rail. Task 3.
 *
 * ## Optimistic, with a real rollback
 *
 * Pinning applies immediately and saves in the background. Unlike the dashboard
 * layout — where a failed save leaves a card in a position that is still
 * perfectly usable — a favourite that appears and then does not persist is
 * actively confusing: the operator sees it in the rail, reloads tomorrow, and it
 * is gone with no explanation. So this one does roll back on failure, and says
 * so through `error`.
 *
 * ## The cap is enforced here as well as on the server
 *
 * The server is the authority and rejects an over-long list. Checking here too
 * means the pin control can be disabled at the limit rather than accepting a
 * click that will fail — a button that looks available and then does nothing is
 * worse than one that is visibly unavailable.
 */
export default function useFavorites() {
  const [items, setItems] = useState([]);
  const [max, setMax] = useState(12);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    dashboardConfigService
      .getFavorites(controller.signal)
      .then((data) => {
        setItems(data.items || []);
        setMax(data.max ?? 12);
      })
      .catch(() => {
        // The rail must render without favourites if they are unreachable.
        setItems([]);
      })
      .finally(() => setLoading(false));

    return () => controller.abort();
  }, []);

  /** Saves a list, rolling back to the previous one if the server refuses. */
  const persist = useCallback(
    (next, previous) => {
      setItems(next);
      setError(null);
      dashboardConfigService
        .saveFavorites(next)
        .then((data) => {
          // The server sanitises: it drops unsafe routes and caps the list, so
          // what comes back is the truth. Keeping the local copy instead would
          // show the operator a favourite that was silently discarded.
          setItems(data.items || []);
          setMax(data.max ?? max);
        })
        .catch(() => {
          setItems(previous);
          setError('Favoritele nu au putut fi salvate.');
        });
    },
    [max]
  );

  const isFavorite = useCallback(
    (route) => items.some((item) => item.route === route),
    [items]
  );

  /** Pins or unpins one route. */
  const toggle = useCallback(
    (route, label, icon) => {
      const previous = items;
      if (isFavorite(route)) {
        persist(
          items.filter((item) => item.route !== route).map((item, index) => ({ ...item, order: index })),
          previous
        );
        return;
      }
      if (items.length >= max) {
        setError(`Poți fixa cel mult ${max} pagini.`);
        return;
      }
      persist([...items, { route, label, icon, order: items.length }], previous);
    },
    [items, isFavorite, max, persist]
  );

  return { items, max, loading, error, isFavorite, toggle, full: items.length >= max };
}
