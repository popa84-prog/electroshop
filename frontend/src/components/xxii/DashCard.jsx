import { forwardRef } from 'react';
import GlassPanel from './GlassPanel';

/**
 * XXII — the modular dashboard card. Tasks 1 and 4.
 *
 * Every panel on the dashboard is one of these: a glass surface with a header,
 * an optional toolbar, an optional drag handle, and a body. Standardising the
 * shell is what makes seventeen panels read as one system rather than as
 * seventeen screens that happen to share a page.
 *
 * ## The header is a grid, not a flex row
 *
 * A flex row with a title and a toolbar collapses badly: a long title pushes the
 * range switch off the edge, or the switch squeezes the title to an ellipsis at
 * three characters. A two-column grid with a fixed-content second column keeps
 * the controls at their natural width and gives the rest to the title, which is
 * the behaviour a person expects and a flex row only approximates.
 *
 * ## Compact mode changes spacing, never content
 *
 * Task 1 asks for compact and expanded views. Compact tightens padding and drops
 * the subtitle; it does not hide data. A density toggle that removes information
 * is a second layout to reason about, and an operator who compacts the dashboard
 * to fit more on screen has not asked to see less.
 *
 * ## Loading and error are states of the card, not replacements for it
 *
 * The header stays visible while the body loads or fails, so the card keeps its
 * position in the grid and the operator does not watch the layout reflow every
 * time a slow panel resolves.
 */
const DashCard = forwardRef(function DashCard(
  {
    title,
    subtitle,
    icon = null,
    toolbar = null,
    footer = null,
    compact = false,
    loading = false,
    error = null,
    onRetry = null,
    dragHandle = null,
    onHide = null,
    accent = 'var(--xx-cyan)',
    className = '',
    bodyClassName = '',
    children,
    ...rest
  },
  ref
) {
  const pad = compact ? 'p-3.5 sm:p-4' : 'p-5 sm:p-6';

  return (
    <GlassPanel
      ref={ref}
      as="section"
      variant="panel"
      className={`flex h-full flex-col ${pad} transition-shadow duration-xx ease-xx
        data-[dragging=true]:opacity-50
        data-[drop-target=true]:ring-2 data-[drop-target=true]:ring-[color:var(--xx-cyan)]
        ${className}`}
      aria-busy={loading || undefined}
      {...rest}
    >
      <header
        className={`grid grid-cols-[minmax(0,1fr)_auto] items-start gap-3 ${
          compact ? 'mb-3' : 'mb-4'
        }`}
      >
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            {dragHandle}
            {icon ? (
              <span
                className="grid h-7 w-7 shrink-0 place-items-center rounded-lg
                  border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.04)]"
                style={{ color: accent }}
                aria-hidden="true"
              >
                {icon}
              </span>
            ) : null}
            <h2
              className={`truncate font-display font-semibold text-[color:var(--xx-ink)] ${
                compact ? 'text-sm' : 'text-base sm:text-lg'
              }`}
            >
              {title}
            </h2>
          </div>
          {/* The subtitle is the first thing compact mode gives up: it is
              context rather than data, and it is the only element on the card
              that is purely explanatory. */}
          {subtitle && !compact ? (
            <p className="mt-1 text-xs leading-relaxed text-[color:var(--xx-ink-dim)]">
              {subtitle}
            </p>
          ) : null}
        </div>

        <div className="flex shrink-0 items-center gap-1.5">
          {toolbar}
          {onHide ? (
            <button
              type="button"
              onClick={onHide}
              title="Ascunde cardul"
              aria-label={`Ascunde ${title}`}
              className="grid h-7 w-7 place-items-center rounded-lg border
                border-[rgba(255,255,255,0.1)] text-[color:var(--xx-ink-dim)]
                transition-colors duration-xx hover:border-[rgba(255,255,255,0.25)]
                hover:text-[color:var(--xx-ink)]"
            >
              <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
                   strokeWidth="1.9" strokeLinecap="round" aria-hidden="true">
                <path d="M3 3l18 18M10.6 10.7a2 2 0 002.8 2.8" />
                <path d="M9.4 5.3A9.5 9.5 0 0112 5c5 0 9 4.5 9 7 0 .9-.5 2-1.4 3.1" />
                <path d="M6.2 6.8C3.9 8.3 3 10.3 3 12c0 2.5 4 7 9 7 1.2 0 2.3-.2 3.3-.6" />
              </svg>
            </button>
          ) : null}
        </div>
      </header>

      <div className={`min-w-0 flex-1 ${bodyClassName}`}>
        {error ? (
          <CardError error={error} onRetry={onRetry} />
        ) : loading && !children ? (
          <CardSkeleton compact={compact} />
        ) : (
          children
        )}
      </div>

      {footer && !error ? (
        <footer className={`${compact ? 'mt-3' : 'mt-4'} border-t border-[rgba(255,255,255,0.08)] pt-3`}>
          {footer}
        </footer>
      ) : null}
    </GlassPanel>
  );
});

/**
 * What a card shows when its request failed.
 *
 * States the failure and offers exactly one action. A card that fails silently
 * and shows an empty chart is indistinguishable from a card reporting that
 * nothing happened, and the second reading is the dangerous one.
 */
function CardError({ error, onRetry }) {
  const status = error?.response?.status;
  const message =
    status === 403
      ? 'Nu ai permisiunea necesară pentru aceste date.'
      : status === 404
      ? 'Datele nu sunt disponibile pe acest server.'
      : 'Datele nu au putut fi încărcate.';

  return (
    <div className="flex flex-col items-start gap-3 rounded-xl border border-[rgba(184,47,60,0.35)]
      bg-[rgba(184,47,60,0.08)] p-4">
      <p className="text-sm text-[color:var(--xx-ink)]">{message}</p>
      {/* A permission failure has no retry: trying again produces the same 403,
          and a button that cannot work teaches people to distrust buttons. */}
      {onRetry && status !== 403 ? (
        <button
          type="button"
          onClick={onRetry}
          className="rounded-lg border border-[rgba(255,255,255,0.18)] px-3 py-1.5 text-xs
            font-medium text-[color:var(--xx-ink)] transition-colors duration-xx
            hover:border-[color:var(--xx-cyan)] hover:text-[color:var(--xx-cyan)]"
        >
          Încearcă din nou
        </button>
      ) : null}
    </div>
  );
}

/** Placeholder bars while a card's first request is in flight. */
function CardSkeleton({ compact }) {
  return (
    <div className="space-y-2.5" aria-hidden="true">
      {Array.from({ length: compact ? 3 : 5 }).map((_, i) => (
        <div
          key={i}
          className="xx-shimmer h-3 rounded-full bg-[rgba(255,255,255,0.06)]"
          style={{ width: `${100 - i * 12}%` }}
        />
      ))}
    </div>
  );
}

export default DashCard;
