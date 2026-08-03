// Feature #7 (performance) — "cache pentru liste mari (produse, comenzi)".
//
// A tiny in-memory, short-TTL cache for paginated admin list requests. It is
// intentionally NOT a full data-fetching library (no SWR/react-query in this
// project) — just enough to avoid re-fetching the exact same page+filters
// combination within a few seconds, which is the common case when an
// operator pages back and forth or re-opens a page they just left. Mutations
// (create/update/delete) call `invalidateListCache()` for the affected list
// so the next read is always fresh, never stale.

const store = new Map(); // key -> { data, expiresAt }
const inFlight = new Map(); // key -> Promise (de-dupes concurrent identical requests)

const DEFAULT_TTL_MS = 15_000;

function buildKey(namespace, params) {
  return `${namespace}::${JSON.stringify(params, Object.keys(params).sort())}`;
}

/**
 * Returns a cached value for (namespace, params) if still fresh; otherwise
 * calls `fetchFn()`, caches the result, and returns it. Concurrent calls for
 * the same key share one in-flight request instead of firing duplicates.
 */
export function cachedList(namespace, params, fetchFn, ttlMs = DEFAULT_TTL_MS) {
  const key = buildKey(namespace, params || {});
  const cached = store.get(key);
  if (cached && cached.expiresAt > Date.now()) {
    return Promise.resolve(cached.data);
  }

  const existing = inFlight.get(key);
  if (existing) return existing;

  const promise = fetchFn()
    .then((data) => {
      store.set(key, { data, expiresAt: Date.now() + ttlMs });
      inFlight.delete(key);
      return data;
    })
    .catch((err) => {
      inFlight.delete(key);
      throw err;
    });

  inFlight.set(key, promise);
  return promise;
}

/** Drops every cached entry for a namespace (e.g. after creating/editing/deleting a row). */
export function invalidateListCache(namespace) {
  for (const key of store.keys()) {
    if (key.startsWith(`${namespace}::`)) store.delete(key);
  }
}
