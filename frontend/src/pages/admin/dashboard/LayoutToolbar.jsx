import { useState } from 'react';
import { availablePanels } from './PanelRegistry';

/**
 * The controls that configure the dashboard. Tasks 1 and 4.
 *
 * Density (compact / expanded), an edit mode that turns on dragging and hiding,
 * a visibility menu, and a reset.
 *
 * ## Edit mode is explicit
 *
 * Dragging is off until the operator turns it on. A dashboard where cards move
 * on an accidental drag is one people stop scrolling confidently, and the cost
 * of one extra click before rearranging is far smaller than the cost of a layout
 * that shifts when somebody meant to select a number.
 *
 * ## Reset asks first
 *
 * It is the one irreversible action here: an arrangement somebody spent time on
 * cannot be recovered afterwards. The confirmation is inline rather than a
 * browser dialog, because a native confirm is unstyled, blocks the page, and is
 * the one control on the screen an operator cannot read in context.
 *
 * ## Save state is shown, not assumed
 *
 * Layout changes save in the background, so without an indicator the operator
 * has no way to know whether their arrangement will survive a reload. Three
 * states — saving, saved, failed — and the failed one persists rather than
 * fading, because it is the only one that needs a response.
 */
export default function LayoutToolbar({
  editing,
  onEditingChange,
  compact,
  onToggleDensity,
  layout,
  hasPermission,
  onToggleHidden,
  onReset,
  saveState,
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirmingReset, setConfirmingReset] = useState(false);

  const panels = availablePanels(layout, hasPermission);
  const hiddenCount = panels.filter((panel) => panel.hidden).length;

  return (
    <div className="flex flex-wrap items-center gap-2">
      <SaveIndicator state={saveState} />

      <button
        type="button"
        onClick={onToggleDensity}
        aria-pressed={compact}
        className={buttonClass(compact)}
        title={compact ? 'Comută la vizualizare extinsă' : 'Comută la vizualizare compactă'}
      >
        {compact ? <ExpandIcon /> : <CompactIcon />}
        {compact ? 'Extins' : 'Compact'}
      </button>

      <div className="relative">
        <button
          type="button"
          onClick={() => setMenuOpen((open) => !open)}
          aria-expanded={menuOpen}
          aria-haspopup="true"
          className={buttonClass(hiddenCount > 0)}
        >
          <EyeIcon />
          Carduri
          {hiddenCount > 0 ? (
            <span className="rounded-full bg-[rgba(34,232,245,0.2)] px-1.5 text-[10px]
              text-[color:var(--xx-cyan)]">
              {hiddenCount} ascunse
            </span>
          ) : null}
        </button>

        {menuOpen ? (
          <>
            {/* A transparent backdrop closes the menu on any outside click,
                including on another control, which a document listener would
                race against. */}
            <button
              type="button"
              aria-label="Închide meniul"
              onClick={() => setMenuOpen(false)}
              className="fixed inset-0 z-30 cursor-default"
            />
            <div
              className="xx-no-scrollbar absolute right-0 z-40 mt-1 max-h-80 w-64 overflow-y-auto
                rounded-xl border border-[rgba(255,255,255,0.14)] bg-[rgba(9,10,26,0.97)] p-1.5
                shadow-[0_28px_70px_-32px_rgba(0,0,0,0.95)] backdrop-blur-glass-lg"
            >
              <p className="px-2 py-1 text-[10px] font-semibold uppercase tracking-[0.14em]
                text-[color:var(--xx-ink-dim)]">
                Afișare carduri
              </p>
              {panels.map((panel) => (
                <label
                  key={panel.id}
                  className={`flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm
                    transition-colors duration-xx hover:bg-[rgba(255,255,255,0.05)]
                    ${panel.pinned ? 'opacity-50' : 'cursor-pointer'}`}
                >
                  <input
                    type="checkbox"
                    checked={!panel.hidden}
                    disabled={panel.pinned}
                    onChange={() => onToggleHidden(panel.id)}
                    className="h-3.5 w-3.5 rounded border-[rgba(255,255,255,0.3)]
                      bg-transparent accent-[color:var(--xx-cyan)]"
                  />
                  <span className="min-w-0 truncate text-[color:var(--xx-ink)]">{panel.title}</span>
                  {panel.pinned ? (
                    <span className="ml-auto shrink-0 text-[10px] text-[color:var(--xx-ink-dim)]">
                      fix
                    </span>
                  ) : null}
                </label>
              ))}
            </div>
          </>
        ) : null}
      </div>

      <button
        type="button"
        onClick={() => onEditingChange(!editing)}
        aria-pressed={editing}
        className={buttonClass(editing)}
      >
        <MoveIcon />
        {editing ? 'Gata' : 'Rearanjează'}
      </button>

      {confirmingReset ? (
        <span className="inline-flex items-center gap-1.5 rounded-lg border
          border-[rgba(184,47,60,0.45)] bg-[rgba(184,47,60,0.1)] px-2 py-1.5 text-xs">
          <span className="text-[color:var(--xx-ink)]">Resetezi aranjamentul?</span>
          <button
            type="button"
            onClick={() => {
              onReset();
              setConfirmingReset(false);
            }}
            className="font-semibold text-[#ff8a97] underline underline-offset-2"
          >
            Da
          </button>
          <button
            type="button"
            onClick={() => setConfirmingReset(false)}
            className="text-[color:var(--xx-ink-dim)] underline underline-offset-2"
          >
            Nu
          </button>
        </span>
      ) : (
        <button
          type="button"
          onClick={() => setConfirmingReset(true)}
          className={buttonClass(false)}
          title="Revino la aranjamentul implicit"
        >
          <ResetIcon />
          Reset
        </button>
      )}
    </div>
  );
}

function buttonClass(active) {
  return `inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs font-medium
    transition-all duration-xx ease-xx focus:outline-none
    focus-visible:ring-2 focus-visible:ring-[color:var(--xx-cyan)] ${
      active
        ? 'border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.12)] text-[color:var(--xx-cyan)]'
        : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-dim)] hover:border-[rgba(255,255,255,0.28)] hover:text-[color:var(--xx-ink)]'
    }`;
}

/**
 * Whether the arrangement has been saved.
 *
 * Nothing is shown while idle. A permanent "saved" badge is noise, and an
 * indicator that is always present stops being read.
 */
function SaveIndicator({ state }) {
  if (state === 'idle') return null;

  const config = {
    saving: { text: 'Se salvează…', tone: 'text-[color:var(--xx-ink-dim)]' },
    saved: { text: 'Salvat', tone: 'text-[#4fd3a0]' },
    error: { text: 'Nesalvat', tone: 'text-[#ff8a97]' },
  }[state];

  if (!config) return null;

  return (
    <span className={`text-[11px] ${config.tone}`} role="status" aria-live="polite">
      {config.text}
    </span>
  );
}

function CompactIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
         strokeWidth="1.9" strokeLinecap="round" aria-hidden="true">
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  );
}

function ExpandIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
         strokeWidth="1.9" strokeLinecap="round" aria-hidden="true">
      <path d="M4 6h16M4 12h16M4 18h16" />
      <path d="M4 9h16M4 15h16" opacity="0.3" />
    </svg>
  );
}

function EyeIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
      <circle cx="12" cy="12" r="2.6" />
    </svg>
  );
}

function MoveIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
         strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3v18M3 12h18" />
      <path d="M9 6l3-3 3 3M9 18l3 3 3-3M6 9l-3 3 3 3M18 9l3 3-3 3" />
    </svg>
  );
}

function ResetIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
         strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3.5 12a8.5 8.5 0 1 0 2.6-6.1" />
      <path d="M3 4v5h5" />
    </svg>
  );
}
