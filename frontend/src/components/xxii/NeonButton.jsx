import { forwardRef, useCallback, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

/**
 * XXII — TASK 1 / TASK 8 (futuristic buttons: rounded, glow on hover, pulse,
 * futuristic ripple).
 *
 * One component covers the three things a button can be in this app — a real
 * `<button>`, a router `<Link>`, or an external `<a>` — because the ripple and
 * pulse behaviour must be identical regardless of which element is rendered.
 * The element is chosen from the props: `to` → Link, `href` → anchor, neither
 * → button.
 *
 * The ripple is a DOM-free effect: each click pushes a short-lived entry into
 * state, keyed by an incrementing id, and the entry removes itself when its
 * animation ends. That avoids the classic leak of appending spans that are
 * never cleaned up when the component unmounts mid-animation.
 */

const VARIANTS = {
  primary: 'btn-primary',
  secondary: 'btn-secondary',
  ghost: 'btn-ghost',
  hot: 'btn-gold',
  danger: 'btn-danger',
};

const SIZES = {
  sm: 'px-3.5 py-1.5 text-xs',
  md: '',
  lg: 'px-7 py-3 text-base',
  xl: 'px-8 py-3.5 text-lg',
};

const RIPPLE_MS = 520;

const NeonButton = forwardRef(function NeonButton(
  {
    variant = 'primary',
    size = 'md',
    pulse = false,
    charging = false,
    block = false,
    icon = null,
    iconRight = null,
    to,
    href,
    className = '',
    children,
    onClick,
    disabled = false,
    type = 'button',
    ...rest
  },
  ref
) {
  const [ripples, setRipples] = useState([]);
  const nextId = useRef(0);

  const handleClick = useCallback(
    (event) => {
      if (disabled) return;

      // Position the ripple at the click point relative to the button so it
      // expands from the finger/cursor rather than always from the centre.
      const rect = event.currentTarget.getBoundingClientRect();
      const id = nextId.current++;
      const ripple = {
        id,
        x: event.clientX - rect.left,
        y: event.clientY - rect.top,
        // Cover the furthest corner so the wave always reaches every edge.
        size: Math.max(rect.width, rect.height) * 1.1,
      };
      setRipples((current) => [...current, ripple]);
      window.setTimeout(() => {
        setRipples((current) => current.filter((r) => r.id !== id));
      }, RIPPLE_MS);

      if (onClick) onClick(event);
    },
    [disabled, onClick]
  );

  const classes = [
    VARIANTS[variant] || VARIANTS.primary,
    SIZES[size] || '',
    pulse && !disabled ? 'btn-pulse' : '',
    charging ? 'btn-charging' : '',
    block ? 'w-full' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const inner = (
    <>
      {icon ? <span className="shrink-0">{icon}</span> : null}
      {children ? <span className="relative z-10">{children}</span> : null}
      {iconRight ? <span className="shrink-0">{iconRight}</span> : null}
      {ripples.map((r) => (
        <span
          key={r.id}
          aria-hidden="true"
          className="pointer-events-none absolute rounded-full bg-white/35 animate-xx-ripple"
          style={{
            left: r.x - r.size / 2,
            top: r.y - r.size / 2,
            width: r.size,
            height: r.size,
          }}
        />
      ))}
    </>
  );

  if (to && !disabled) {
    return (
      <Link ref={ref} to={to} className={classes} onClick={handleClick} {...rest}>
        {inner}
      </Link>
    );
  }

  if (href && !disabled) {
    return (
      <a ref={ref} href={href} className={classes} onClick={handleClick} {...rest}>
        {inner}
      </a>
    );
  }

  return (
    <button
      ref={ref}
      type={type}
      className={classes}
      onClick={handleClick}
      disabled={disabled}
      aria-disabled={disabled || undefined}
      {...rest}
    >
      {inner}
    </button>
  );
});

export default NeonButton;
