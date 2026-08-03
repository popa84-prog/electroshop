import { Link } from 'react-router-dom';

/**
 * XXII — TASK 9 (each section is a self-contained module).
 *
 * Every module on the site opens with the same three-part header: an eyebrow
 * that names the module in the system's voice, a display title, and an optional
 * action anchored to the far right. Consistency here is what makes the modular
 * grid read as one system rather than a pile of unrelated panels.
 *
 * The eyebrow is not decoration — it is the module's label. Keeping it in a
 * separate, dimmer, letter-spaced line lets the title stay short and large,
 * which is what makes the layout legible at TV distance.
 */
export default function SectionHeader({
  eyebrow,
  title,
  subtitle,
  action = null,
  actionTo,
  actionLabel,
  align = 'left',
  className = '',
  titleClassName = '',
  as: Tag = 'h2',
}) {
  const centered = align === 'center';

  return (
    <div
      className={`mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between ${
        centered ? 'sm:flex-col sm:items-center' : ''
      } ${className}`}
    >
      <div className={centered ? 'text-center' : ''}>
        {eyebrow ? <p className="xx-eyebrow">{eyebrow}</p> : null}
        <Tag className={`xx-title text-2xl sm:text-3xl tv:text-4xl ${titleClassName}`}>{title}</Tag>
        {subtitle ? <p className="mt-2 max-w-2xl text-sm xx-ink-muted tv:text-base">{subtitle}</p> : null}
      </div>

      {action ||
      (actionTo && actionLabel) ? (
        <div className="shrink-0">
          {action || (
            <Link
              to={actionTo}
              className="group inline-flex items-center gap-2 rounded-full border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.05)] px-4 py-2 text-sm font-semibold text-[#cdd4f2] transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.5)] hover:text-white hover:shadow-glow-aqua"
            >
              {actionLabel}
              <span aria-hidden="true" className="transition-transform duration-xx ease-xx group-hover:translate-x-1">
                →
              </span>
            </Link>
          )}
        </div>
      ) : null}
    </div>
  );
}
