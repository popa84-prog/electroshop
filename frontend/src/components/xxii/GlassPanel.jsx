import { forwardRef } from 'react';

/**
 * XXII — TASK 1 / TASK 9 (glassmorphic container, atom level).
 *
 * The single surface primitive of the design system. Everything that needs a
 * background in XXII is a GlassPanel: cards, side panels, toolbars, modals.
 *
 * Variants:
 *   `panel`  — the standard floating module (blur 22px, 5.5% fill, soft edge)
 *   `soft`   — a nested surface; less blur, no top hairline, so panels inside
 *              panels do not stack into an opaque block
 *   `neon`   — a gradient-border module for content that must claim attention
 *   `hot`    — the aqua→magenta border, reserved for promotions and alerts
 *
 * `glow` adds the 40–60px edge spread from the brief. It is a prop rather than
 * a default because a screen where every panel glows has no hierarchy left.
 */

const VARIANTS = {
  panel: 'xx-panel',
  soft: 'glass-soft',
  neon: 'xx-border-neon rounded-[1.25rem] shadow-glass',
  hot: 'xx-border-neon-hot rounded-[1.25rem] shadow-glass',
};

const GLOWS = {
  none: '',
  blue: 'xx-glow-blue',
  purple: 'xx-glow-purple',
  aqua: 'xx-glow-aqua',
  magenta: 'xx-glow-magenta',
};

const GlassPanel = forwardRef(function GlassPanel(
  {
    as: Tag = 'div',
    variant = 'panel',
    glow = 'none',
    float = false,
    scanning = false,
    padded = false,
    interactive = false,
    className = '',
    children,
    ...rest
  },
  ref
) {
  const classes = [
    VARIANTS[variant] || VARIANTS.panel,
    GLOWS[glow] || '',
    float ? 'xx-float' : '',
    scanning ? 'xx-scanning' : '',
    padded ? 'p-5 sm:p-6' : '',
    // `card-static` disables the built-in hover lift for panels that are pure
    // containers — a table wrapper that rises on hover is distracting.
    interactive ? '' : 'card-static',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <Tag ref={ref} className={classes} {...rest}>
      {children}
    </Tag>
  );
});

export default GlassPanel;
