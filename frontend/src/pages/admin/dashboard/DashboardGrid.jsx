import { useMemo } from 'react';
import useCardDrag from '../../../hooks/useCardDrag';
import { resolvePanels } from './PanelRegistry';

/**
 * The twelve-column grid that lays out the dashboard panels. Tasks 1 and 4.
 *
 * ## Twelve columns, and spans that collapse rather than shrink
 *
 * A card declared as six columns is half-width on a desktop, full-width on a
 * tablet, and full-width on a phone. It does not become a six-column card in a
 * narrower grid, because six columns of a 360-pixel screen is 180 pixels, and a
 * chart in 180 pixels is a smear. Below the large breakpoint every card is full
 * width; between medium and large, cards of six or fewer share a row.
 *
 * ## Reordering is only possible while editing
 *
 * Cards are draggable when `editing` is true and inert otherwise. A dashboard
 * where every card moves on an accidental drag is a dashboard people are afraid
 * to touch, and the operator who wants to read a number should not be one
 * mis-click from rearranging the page.
 *
 * ## Entrance animation is staggered and bounded
 *
 * Each card fades up shortly after the one before it. The delay is capped, so a
 * layout with seventeen panels does not have its last card arrive a second and a
 * half after the first — past a certain point a stagger stops reading as one
 * motion and starts reading as a slow page.
 */
export default function DashboardGrid({
  layout,
  hasPermission,
  editing = false,
  compact = false,
  onReorder,
  onToggleHidden,
}) {
  const panels = useMemo(
    () => resolvePanels(layout, hasPermission, editing),
    [layout, hasPermission, editing]
  );

  const { cardProps, handleProps, liveMessage, draggingId, keyboardId } =
    useCardDrag(panels, onReorder);

  return (
    <>
      {/* Every reorder is announced. Without this a keyboard or screen-reader
          user moves a card and receives no confirmation that anything happened. */}
      <span className="sr-only" role="status" aria-live="polite">
        {liveMessage}
      </span>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
        {panels.map((panel, index) => {
          const Component = panel.component;
          const span = panel.span || 6;

          return (
            <div
              key={panel.id}
              className={`xx-rise min-w-0 ${spanClass(span)} ${
                draggingId === panel.id ? 'opacity-50' : ''
              } ${keyboardId === panel.id ? 'ring-2 ring-[color:var(--xx-cyan)] rounded-[1.3rem]' : ''}`}
              style={{ animationDelay: `${Math.min(index * 60, 480)}ms` }}
              {...(editing && !panel.pinned ? cardProps(panel.id) : {})}
            >
              <Component
                compact={compact}
                panelId={panel.id}
                title={panel.title}
                editing={editing}
                onHide={editing && !panel.pinned ? () => onToggleHidden(panel.id) : null}
                hidden={panel.hidden}
                dragHandle={
                  editing && !panel.pinned ? (
                    <DragHandle {...handleProps(panel.id, panel.title)} />
                  ) : null
                }
              />
            </div>
          );
        })}
      </div>
    </>
  );
}

/**
 * Maps a span to Tailwind classes.
 *
 * Written out rather than interpolated: Tailwind scans source text for class
 * names at build time, and a template string like `xl:col-span-${n}` produces no
 * CSS at all. This is the single most common way a Tailwind layout silently
 * stops working.
 */
function spanClass(span) {
  switch (span) {
    case 3:
      return 'md:col-span-1 xl:col-span-3';
    case 4:
      return 'md:col-span-1 xl:col-span-4';
    case 5:
      return 'md:col-span-1 xl:col-span-5';
    case 6:
      return 'md:col-span-1 xl:col-span-6';
    case 7:
      return 'md:col-span-2 xl:col-span-7';
    case 8:
      return 'md:col-span-2 xl:col-span-8';
    case 9:
      return 'md:col-span-2 xl:col-span-9';
    case 10:
      return 'md:col-span-2 xl:col-span-10';
    case 11:
      return 'md:col-span-2 xl:col-span-11';
    case 12:
    default:
      return 'md:col-span-2 xl:col-span-12';
  }
}

/** The grip a card is dragged by. A real button, so the keyboard path works. */
function DragHandle(props) {
  return (
    <button
      {...props}
      className="grid h-6 w-6 shrink-0 cursor-grab place-items-center rounded
        text-[color:var(--xx-ink-dim)] transition-colors duration-xx
        hover:text-[color:var(--xx-cyan)] active:cursor-grabbing
        focus:outline-none focus-visible:ring-1 focus-visible:ring-[color:var(--xx-cyan)]"
    >
      <svg viewBox="0 0 24 24" className="h-4 w-4" fill="currentColor" aria-hidden="true">
        <circle cx="9" cy="6" r="1.4" />
        <circle cx="15" cy="6" r="1.4" />
        <circle cx="9" cy="12" r="1.4" />
        <circle cx="15" cy="12" r="1.4" />
        <circle cx="9" cy="18" r="1.4" />
        <circle cx="15" cy="18" r="1.4" />
      </svg>
    </button>
  );
}
