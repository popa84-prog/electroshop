import { useState } from 'react';

/**
 * XXII — downloads a server-generated file. Tasks 5 and 19.
 *
 * ## The object URL is always revoked
 *
 * A blob URL holds its blob in memory until revoked. A panel where an operator
 * exports the log a dozen times over an afternoon would accumulate a dozen
 * copies of it, and the `finally` here is what stops that being a slow leak in
 * the one place people go when something is already wrong.
 *
 * ## The anchor is created, clicked and removed
 *
 * `window.open` on a blob is blocked as a popup in several browsers, and
 * assigning `location.href` navigates away from the dashboard when the response
 * carries no attachment disposition. A temporary anchor with `download` works
 * everywhere and leaves the page where it was.
 *
 * ## Failure is reported on the button
 *
 * Not in a toast that has already vanished by the time the operator looks back
 * at the screen, and not silently — a download that quietly does nothing is
 * indistinguishable from one the browser saved somewhere unexpected, and people
 * spend real time looking for the file.
 *
 * @param {() => Promise<Blob>} onExport returns the file
 * @param {string} filename what to call it
 */
export default function ExportButton({
  onExport,
  filename = 'export.csv',
  label = 'Export CSV',
  compact = false,
  className = '',
}) {
  const [state, setState] = useState('idle');

  const run = async () => {
    setState('working');
    let url = null;
    try {
      const blob = await onExport();
      url = URL.createObjectURL(blob);

      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();

      setState('done');
      setTimeout(() => setState('idle'), 2000);
    } catch {
      setState('error');
      setTimeout(() => setState('idle'), 4000);
    } finally {
      if (url) {
        // Deferred one tick: revoking immediately can cancel the download in
        // Safari, which reads the blob after the click handler returns.
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      }
    }
  };

  const text =
    state === 'working' ? 'Se pregătește…'
    : state === 'done' ? 'Descărcat'
    : state === 'error' ? 'Export eșuat'
    : label;

  return (
    <button
      type="button"
      onClick={run}
      disabled={state === 'working'}
      className={`inline-flex items-center gap-1.5 rounded-lg border transition-all duration-xx ease-xx
        disabled:cursor-wait disabled:opacity-60
        ${state === 'error'
          ? 'border-[rgba(184,47,60,0.5)] text-[#ff8a97]'
          : state === 'done'
          ? 'border-[rgba(31,172,121,0.5)] text-[#4fd3a0]'
          : 'border-[rgba(255,255,255,0.14)] text-[color:var(--xx-ink-dim)] hover:border-[color:var(--xx-cyan)] hover:text-[color:var(--xx-cyan)]'}
        ${compact ? 'px-2 py-1 text-[11px]' : 'px-3 py-1.5 text-xs'} ${className}`}
    >
      <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
           strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 3v12" />
        <path d="M8 11l4 4 4-4" />
        <path d="M4 19h16" />
      </svg>
      {text}
    </button>
  );
}
