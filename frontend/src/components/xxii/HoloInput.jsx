import { forwardRef, useId, useState } from 'react';

/**
 * XXII — TASK 1 (holographic inputs: neon border) and
 * TASK 5 (instant validation: red/blue glow, micro-shake on error).
 *
 * A field is two things stacked:
 *   1. the control itself (input / textarea / select),
 *   2. a status line that reserves its own height so the layout never jumps
 *      between the valid and invalid states.
 *
 * `status` is deliberately a three-value prop (`null` | 'valid' | 'invalid')
 * rather than a boolean: an untouched field is neither valid nor invalid, and
 * painting every empty required field red on first render is hostile.
 *
 * The micro-shake runs on the *transition* into the invalid state, not for as
 * long as the state lasts, so a field that stays invalid while the user keeps
 * typing shakes exactly once.
 *
 * An earlier version drew a cyan "scan" bar meant to sweep once on focus, but
 * it was built on an animation utility that loops forever for as long as the
 * field holds focus — a continuous, distracting sweep on every field the
 * operator types into, not the one-shot cue it was meant to be. Removed
 * outright rather than re-timed: a validation-status border is signal enough.
 */

const HoloInput = forwardRef(function HoloInput(
  {
    as = 'input',
    label,
    hint,
    status = null,
    message,
    icon = null,
    suffix = null,
    className = '',
    containerClassName = '',
    id: providedId,
    onFocus,
    onBlur,
    ...rest
  },
  ref
) {
  const generatedId = useId();
  const id = providedId || `xx-field-${generatedId}`;
  const messageId = `${id}-msg`;
  const [shakeKey, setShakeKey] = useState(0);
  const [lastStatus, setLastStatus] = useState(status);

  // Fire the shake only on the null/valid → invalid edge.
  if (status !== lastStatus) {
    setLastStatus(status);
    if (status === 'invalid') setShakeKey((k) => k + 1);
  }

  const Tag = as;

  const controlClasses = [
    'input',
    status === 'valid' ? 'input-valid' : '',
    status === 'invalid' ? 'input-invalid' : '',
    icon ? 'pl-10' : '',
    suffix ? 'pr-10' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={containerClassName}>
      {label ? (
        <label htmlFor={id} className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">
          {label}
        </label>
      ) : null}

      <div
        key={shakeKey}
        className={`relative overflow-hidden rounded-[0.85rem] ${
          status === 'invalid' ? 'animate-xx-shake' : ''
        }`}
      >
        {icon ? (
          <span className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 xx-ink-dim">
            {icon}
          </span>
        ) : null}

        <Tag
          ref={ref}
          id={id}
          className={controlClasses}
          aria-invalid={status === 'invalid' || undefined}
          aria-describedby={message || hint ? messageId : undefined}
          onFocus={onFocus}
          onBlur={onBlur}
          {...rest}
        />

        {suffix ? (
          <span className="absolute right-3 top-1/2 z-10 -translate-y-1/2 xx-ink-dim">{suffix}</span>
        ) : null}
      </div>

      {/* Reserved status line — always rendered so nothing below shifts. */}
      <p
        id={messageId}
        className={`mt-1 min-h-[1.1rem] text-xs ${
          status === 'invalid' ? 'text-[color:var(--xx-red)]' : 'xx-ink-dim'
        }`}
      >
        {message || hint || ' '}
      </p>
    </div>
  );
});

export default HoloInput;
