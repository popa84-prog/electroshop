import HoloLoader from './xxii/HoloLoader';

/**
 * XXII — TASK 8 (load animations).
 *
 * Kept as its own module rather than replaced by `HoloLoader` at every call
 * site: roughly thirty screens import `Spinner`, and re-pointing all of them
 * would be thirty edits for zero behavioural gain. The component now renders
 * the XXII loader plus its label, so the whole app upgrades from one file.
 */
export default function Spinner({ label = 'Se încarcă...' }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16">
      <HoloLoader size="md" label={label} />
      <span className="text-sm xx-ink-muted">{label}</span>
    </div>
  );
}
