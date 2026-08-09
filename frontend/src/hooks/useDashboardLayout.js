import { useCallback, useEffect, useRef, useState } from 'react';
import dashboardConfigService from '../api/dashboardConfigService';

/**
 * The dashboard arrangement: order, widths, hidden cards and density.
 *
 * Task 4.
 *
 * ## Local first, server second
 *
 * Every change applies to local state immediately and is saved in the
 * background. Dragging a card that waits for a round trip before it moves feels
 * broken, and the operation is not one that can meaningfully fail — the worst
 * case is that an arrangement is not remembered, which the interface reports
 * without undoing the move. Reverting a card the operator just dragged, because
 * a network blip lost the save, would be a worse answer than a stale row in a
 * settings table.
 *
 * ## Saves are debounced
 *
 * Dragging a card across eight positions fires eight reorders. Sending eight
 * PUTs would be pointless and would let them arrive out of order, so the last
 * state wins after a short pause.
 *
 * ## The server owns the panel list
 *
 * The layout that comes back has already been reconciled against the registry:
 * unknown ids dropped, new panels appended, spans clamped. The hook does not
 * repeat that logic, so there is one definition of a valid layout rather than
 * two that drift.
 */
export default function useDashboardLayout() {
  const [panels, setPanels] = useState([]);
  const [density, setDensity] = useState('COMFORTABLE');
  const [customised, setCustomised] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saveState, setSaveState] = useState('idle');

  const saveTimer = useRef(null);
  const pending = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    dashboardConfigService
      .getLayout(controller.signal)
      .then((layout) => {
        setPanels(layout.panels || []);
        setDensity(layout.density || 'COMFORTABLE');
        setCustomised(Boolean(layout.customised));
      })
      .catch(() => {
        // The dashboard must render even if preferences are unreachable. An
        // empty list makes the grid fall back to its own default order, which
        // is the same order the server would have sent.
        setPanels([]);
      })
      .finally(() => setLoading(false));

    return () => {
      controller.abort();
      clearTimeout(saveTimer.current);
    };
  }, []);

  /** Queues a save. The last call inside the debounce window is the one sent. */
  const scheduleSave = useCallback((nextPanels, nextDensity) => {
    pending.current = { panels: nextPanels, density: nextDensity };
    setSaveState('saving');
    clearTimeout(saveTimer.current);

    saveTimer.current = setTimeout(() => {
      const payload = pending.current;
      if (!payload) return;
      dashboardConfigService
        .saveLayout(payload.panels, payload.density)
        .then(() => {
          setCustomised(true);
          setSaveState('saved');
          // The "saved" badge clears itself; a permanent one is noise.
          setTimeout(() => setSaveState('idle'), 2000);
        })
        .catch(() => setSaveState('error'));
    }, 700);
  }, []);

  /** Applies a change locally and queues the save. */
  const apply = useCallback(
    (nextPanels, nextDensity = density) => {
      setPanels(nextPanels);
      setDensity(nextDensity);
      scheduleSave(nextPanels, nextDensity);
    },
    [density, scheduleSave]
  );

  /** Reorders by id list, preserving each panel's span and hidden flag. */
  const reorder = useCallback(
    (nextIds) => {
      const byId = new Map(panels.map((p) => [p.id, p]));
      const next = nextIds
        .map((id, index) => {
          const panel = byId.get(id);
          return panel ? { ...panel, order: index } : null;
        })
        .filter(Boolean);
      apply(next);
    },
    [panels, apply]
  );

  /** Shows or hides one card. */
  const toggleHidden = useCallback(
    (id) => {
      apply(panels.map((p) => (p.id === id ? { ...p, hidden: !p.hidden } : p)));
    },
    [panels, apply]
  );

  /** Changes one card's width in grid columns. */
  const setSpan = useCallback(
    (id, span) => {
      apply(panels.map((p) => (p.id === id ? { ...p, span } : p)));
    },
    [panels, apply]
  );

  /** Switches between compact and comfortable. */
  const toggleDensity = useCallback(() => {
    apply(panels, density === 'COMPACT' ? 'COMFORTABLE' : 'COMPACT');
  }, [panels, density, apply]);

  /**
   * Restores the default arrangement.
   *
   * Goes straight to the server rather than reconstructing the default here:
   * the registry order lives on the backend, and rebuilding it in the browser
   * would be a second copy to keep in step.
   */
  const reset = useCallback(() => {
    setSaveState('saving');
    clearTimeout(saveTimer.current);
    pending.current = null;

    dashboardConfigService
      .resetLayout()
      .then((layout) => {
        setPanels(layout.panels || []);
        setDensity(layout.density || 'COMFORTABLE');
        setCustomised(false);
        setSaveState('saved');
        setTimeout(() => setSaveState('idle'), 2000);
      })
      .catch(() => setSaveState('error'));
  }, []);

  return {
    panels,
    density,
    customised,
    loading,
    saveState,
    reorder,
    toggleHidden,
    setSpan,
    toggleDensity,
    reset,
  };
}
