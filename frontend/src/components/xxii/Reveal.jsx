import { useReveal } from '../../hooks/useReveal';

/**
 * XXII — TASK 8 (scroll animations: fade + float, staggered).
 *
 * Wraps children in an element that animates from a displaced, transparent
 * state to its resting state the first time it scrolls into view.
 *
 * `delay` staggers a list without needing a keyframe per item — passing the
 * array index multiplied by a small step is the intended usage. The delay is an
 * inline style rather than a Tailwind class because arbitrary per-item values
 * cannot be statically extracted by the compiler.
 *
 * The duration deliberately exceeds the 150–250ms interaction budget from the
 * brief: that budget governs *response to input*, where latency is felt.
 * A scroll reveal is ambient, and 460ms reads as elegant rather than sluggish.
 */

const DIRECTIONS = {
  up: 'translate3d(0, 26px, 0)',
  down: 'translate3d(0, -26px, 0)',
  left: 'translate3d(28px, 0, 0)',
  right: 'translate3d(-28px, 0, 0)',
  scale: 'translate3d(0, 14px, 0) scale(0.965)',
  none: 'none',
};

export default function Reveal({
  as: Tag = 'div',
  direction = 'up',
  delay = 0,
  duration = 460,
  blur = true,
  className = '',
  children,
  ...rest
}) {
  const [ref, shown] = useReveal();

  return (
    <Tag
      ref={ref}
      className={className}
      style={{
        opacity: shown ? 1 : 0,
        transform: shown ? 'none' : DIRECTIONS[direction] || DIRECTIONS.up,
        filter: blur && !shown ? 'blur(7px)' : 'blur(0px)',
        transition: `opacity ${duration}ms cubic-bezier(0.22, 1, 0.36, 1) ${delay}ms, transform ${duration}ms cubic-bezier(0.22, 1, 0.36, 1) ${delay}ms, filter ${duration}ms cubic-bezier(0.22, 1, 0.36, 1) ${delay}ms`,
        willChange: shown ? 'auto' : 'opacity, transform',
      }}
      {...rest}
    >
      {children}
    </Tag>
  );
}
