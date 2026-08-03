import { useTilt } from '../../hooks/useTilt';

/**
 * XXII — TASK 2 / TASK 3 / TASK 6 (3D hover: rotate 3–5°, glow, parallax glare).
 *
 * A surface that leans toward the cursor and carries a specular highlight that
 * follows it. Two layers are involved:
 *
 *   - the outer element owns the perspective and never moves, so the element's
 *     hit area stays exactly where the browser laid it out (a rotating hit area
 *     causes the cursor to fall off the card mid-hover and flicker);
 *   - the inner element carries the rotation and the content.
 *
 * The glare is a radial gradient positioned from the normalised pointer
 * coordinates the hook returns, so a single pointer read drives both effects.
 */
export default function TiltCard({
  as: Tag = 'div',
  max = 5,
  scale = 1.02,
  glare = true,
  glow = true,
  className = '',
  innerClassName = '',
  children,
  ...rest
}) {
  const { ref, style, active, pointer, handlers } = useTilt({ max, scale, glare });

  return (
    <Tag ref={ref} className={`xx-tilt-deep ${className}`} {...handlers} {...rest}>
      <div
        className={`xx-tilt relative h-full ${innerClassName}`}
        style={{
          ...style,
          boxShadow:
            glow && active
              ? '0 26px 64px -26px rgba(0,0,0,0.95), 0 0 52px -10px rgba(122,60,255,0.55)'
              : undefined,
        }}
      >
        {children}

        {glare && active ? (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 rounded-[inherit]"
            style={{
              background: `radial-gradient(340px circle at ${(pointer.x * 100).toFixed(1)}% ${(
                pointer.y * 100
              ).toFixed(1)}%, rgba(255,255,255,0.13), transparent 62%)`,
              transition: 'opacity 200ms cubic-bezier(0.22, 1, 0.36, 1)',
            }}
          />
        ) : null}
      </div>
    </Tag>
  );
}
