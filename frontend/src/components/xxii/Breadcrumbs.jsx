import { Link } from 'react-router-dom';

/**
 * XXII — the trail at the top of each admin page. Task 3.
 *
 * ## The last crumb is not a link
 *
 * It is the page the operator is already on. Rendering it as a link that
 * navigates to where they already are is the small kind of wrong that makes an
 * interface feel careless, and for a keyboard user it is a tab stop that does
 * nothing. It carries `aria-current="page"` instead.
 *
 * ## The separator is decorative
 *
 * A screen reader announcing "Administrare slash Produse slash Editare" is
 * reading punctuation aloud. The chevron is `aria-hidden`, and the list markup
 * carries the structure that the separator only draws.
 */
export default function Breadcrumbs({ items = [], className = '' }) {
  if (!items.length) return null;

  return (
    <nav aria-label="Firul Ariadnei" className={className}>
      <ol className="flex flex-wrap items-center gap-1 text-xs text-[color:var(--xx-ink-dim)]">
        {items.map((item, index) => {
          const last = index === items.length - 1;
          return (
            <li key={item.to || item.label} className="flex items-center gap-1">
              {index > 0 ? (
                <svg viewBox="0 0 24 24" className="h-3 w-3 opacity-50" fill="none"
                     stroke="currentColor" strokeWidth="2" strokeLinecap="round"
                     aria-hidden="true">
                  <path d="M9 6l6 6-6 6" />
                </svg>
              ) : null}

              {last || !item.to ? (
                <span
                  aria-current={last ? 'page' : undefined}
                  className={last ? 'font-medium text-[color:var(--xx-ink)]' : ''}
                >
                  {item.label}
                </span>
              ) : (
                <Link
                  to={item.to}
                  className="rounded transition-colors duration-xx hover:text-[color:var(--xx-ink)]
                    focus:outline-none focus-visible:ring-1 focus-visible:ring-[color:var(--xx-cyan)]"
                >
                  {item.label}
                </Link>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
