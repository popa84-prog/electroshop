/**
 * XXII — TASK 1 (badges).
 *
 * Status is never carried by colour alone: every tone ships with an icon slot
 * and a text label, so the meaning survives colour-blindness, greyscale print
 * and forced-colors mode.
 */

const TONES = {
  neutral: 'badge',
  neon: 'badge badge-neon',
  aqua: 'badge badge-aqua',
  magenta: 'badge badge-magenta',
  good: 'badge border-[rgba(110,247,168,0.55)] bg-[rgba(110,247,168,0.14)] text-[var(--xx-good-1)]',
  warning: 'badge border-[rgba(255,194,75,0.55)] bg-[rgba(255,194,75,0.14)] text-[var(--xx-warn-1)]',
  critical: 'badge border-[rgba(255,84,112,0.55)] bg-[rgba(255,84,112,0.14)] text-[var(--xx-danger-1)]',
};

export default function NeonBadge({ tone = 'neutral', icon = null, pulse = false, className = '', children, ...rest }) {
  const classes = [TONES[tone] || TONES.neutral, pulse ? 'animate-xx-pulse-glow' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <span className={classes} {...rest}>
      {icon ? (
        <span aria-hidden="true" className="shrink-0">
          {icon}
        </span>
      ) : null}
      {children}
    </span>
  );
}
