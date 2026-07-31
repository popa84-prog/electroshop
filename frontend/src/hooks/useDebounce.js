import { useEffect, useState } from 'react';

/**
 * Feature #7 (performance): returns `value`, but only after it has stopped
 * changing for `delayMs`. Used on search inputs so every keystroke doesn't
 * fire its own API request — only the value the user settles on does.
 */
export function useDebounce(value, delayMs = 350) {
    const [debounced, setDebounced] = useState(value);

  useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delayMs);
        return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}

export default useDebounce;
