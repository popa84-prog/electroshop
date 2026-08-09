import { useCallback, useRef, useState } from 'react';

/**
 * Drag-and-drop reordering for the dashboard grid, with no external dependency.
 *
 * Task 4 asks for movable cards.
 *
 * ## Why this is hand-written rather than `@dnd-kit` or `react-beautiful-dnd`
 *
 * Either library would do the job well. Neither is worth the risk here: the
 * frontend is built on Vercel from `package.json`, and a package that fails to
 * install does not break one panel — it breaks the entire admin build, which is
 * how the operator reaches every other feature. The interaction needed is one
 * list, reordered by dragging, and that is a few dozen lines over the HTML5
 * Drag and Drop API. The trade is a small amount of code against a build-time
 * dependency on the critical path.
 *
 * ## Three input methods, because a grid you can only mouse-drag is not usable
 *
 * - **Pointer/mouse**: native HTML5 drag events.
 * - **Touch**: the HTML5 API is unreliable on touch, so the handle also listens
 *   for pointer events and tracks which card is under the finger.
 * - **Keyboard**: the handle is a real button. Space or Enter picks a card up,
 *   arrow keys move it, Escape cancels, Space or Enter drops it. Without this
 *   the layout is unreachable for anyone not using a mouse, and "rearrangeable"
 *   would mean "rearrangeable by some people".
 *
 * ## Reordering is announced
 *
 * `liveMessage` carries a sentence for an ARIA live region. A blind operator
 * moving a card gets "Profit mutat pe poziția 3 din 8" rather than silence.
 *
 * @param {Array<{id: string}>} items the current order
 * @param {(nextIds: Array<string>) => void} onReorder called with the new order
 */
export default function useCardDrag(items, onReorder) {
  const [draggingId, setDraggingId] = useState(null);
  const [overId, setOverId] = useState(null);
  const [keyboardId, setKeyboardId] = useState(null);
  const [liveMessage, setLiveMessage] = useState('');

  // Held in a ref rather than state: it is read inside event handlers that
  // must not re-run on every render, and it never needs to paint anything.
  const dragSourceId = useRef(null);

  const ids = items.map((item) => item.id);

  /** Moves one id to a new index and reports the result. */
  const move = useCallback(
    (id, toIndex) => {
      const from = ids.indexOf(id);
      if (from < 0) return;

      const clamped = Math.max(0, Math.min(ids.length - 1, toIndex));
      if (clamped === from) return;

      const next = [...ids];
      next.splice(from, 1);
      next.splice(clamped, 0, id);
      onReorder(next);
      setLiveMessage(`Mutat pe poziția ${clamped + 1} din ${next.length}.`);
    },
    [ids, onReorder]
  );

  // ---- Pointer drag ----------------------------------------------------

  const onDragStart = useCallback((event, id) => {
    dragSourceId.current = id;
    setDraggingId(id);
    // Some browsers refuse to start a drag without transfer data set.
    try {
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', id);
    } catch {
      // Safari in some configurations throws here; the drag still works.
    }
  }, []);

  const onDragOver = useCallback((event, id) => {
    // preventDefault is what marks an element as a valid drop target. Without
    // it the browser refuses the drop and the card snaps back with no
    // explanation.
    event.preventDefault();
    if (id !== overId) {
      setOverId(id);
    }
  }, [overId]);

  const onDrop = useCallback(
    (event, targetId) => {
      event.preventDefault();
      const sourceId = dragSourceId.current;
      dragSourceId.current = null;
      setDraggingId(null);
      setOverId(null);

      if (!sourceId || sourceId === targetId) return;
      move(sourceId, ids.indexOf(targetId));
    },
    [ids, move]
  );

  const onDragEnd = useCallback(() => {
    dragSourceId.current = null;
    setDraggingId(null);
    setOverId(null);
  }, []);

  // ---- Keyboard --------------------------------------------------------

  const onHandleKeyDown = useCallback(
    (event, id) => {
      const index = ids.indexOf(id);

      // Pick up or drop.
      if (event.key === ' ' || event.key === 'Enter') {
        event.preventDefault();
        if (keyboardId === id) {
          setKeyboardId(null);
          setLiveMessage('Card fixat.');
        } else {
          setKeyboardId(id);
          setLiveMessage(`Card ridicat de pe poziția ${index + 1}. Folosește săgețile pentru a-l muta.`);
        }
        return;
      }

      if (event.key === 'Escape' && keyboardId) {
        event.preventDefault();
        setKeyboardId(null);
        setLiveMessage('Mutare anulată.');
        return;
      }

      // Arrows only move a card that has been picked up, so arrowing through
      // the page does not silently rearrange the dashboard.
      if (keyboardId !== id) return;

      const back = event.key === 'ArrowLeft' || event.key === 'ArrowUp';
      const forward = event.key === 'ArrowRight' || event.key === 'ArrowDown';
      if (!back && !forward) return;

      event.preventDefault();
      move(id, index + (back ? -1 : 1));
    },
    [ids, keyboardId, move]
  );

  return {
    draggingId,
    overId,
    keyboardId,
    liveMessage,
    /** Spread onto each draggable card. */
    cardProps: (id) => ({
      draggable: true,
      onDragStart: (event) => onDragStart(event, id),
      onDragOver: (event) => onDragOver(event, id),
      onDrop: (event) => onDrop(event, id),
      onDragEnd,
      'data-dragging': draggingId === id ? 'true' : undefined,
      'data-drop-target': overId === id && draggingId !== id ? 'true' : undefined,
    }),
    /** Spread onto each card's drag handle button. */
    handleProps: (id, label) => ({
      type: 'button',
      'aria-label': keyboardId === id
        ? `${label}: card ridicat. Săgeți pentru mutare, Enter pentru fixare, Escape pentru anulare.`
        : `Mută ${label}. Apasă Enter pentru a ridica.`,
      'aria-pressed': keyboardId === id,
      onKeyDown: (event) => onHandleKeyDown(event, id),
    }),
  };
}
