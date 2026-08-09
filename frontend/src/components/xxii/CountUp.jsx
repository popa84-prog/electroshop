import useCountUp from '../../hooks/useCountUp';

/**
 * XXII — an animated number. Task 9.
 *
 * ## The animated value is hidden from assistive technology
 *
 * A screen reader following a live count-up announces forty intermediate
 * numbers, none of which is the answer. The animated text is therefore
 * `aria-hidden`, and the final value is exposed once in a visually hidden span.
 * A sighted user sees the count; a screen-reader user hears the figure. Both get
 * the information, neither gets the other's noise.
 *
 * ## Formatting happens per frame, on the animated value
 *
 * The number is formatted with the same `Intl` formatter at every frame, so the
 * thousands separators appear as the value grows rather than snapping in at the
 * end. Formatting only the final value and interpolating the digits would drift
 * out of alignment as the number gains a digit.
 *
 * @param {number} value the target
 * @param {'currency'|'percent'|'number'} format how to render it
 * @param {string} currency ISO code, when format is currency
 * @param {number} decimals decimal places
 * @param {number} duration animation length in milliseconds
 */
export default function CountUp({
  value,
  format = 'number',
  currency = 'RON',
  decimals = null,
  duration = 900,
  className = '',
}) {
  const numeric = Number(value);
  const safe = Number.isFinite(numeric) ? numeric : 0;

  // Currency and percentage default to two and one decimal respectively, which
  // is how each is read aloud in Romanian retail. A caller can override, but the
  // default should never be "whatever the raw number happens to carry".
  const places = decimals ?? (format === 'currency' ? 2 : format === 'percent' ? 1 : 0);

  const animated = useCountUp(safe, { duration, decimals: places });

  const formatter = new Intl.NumberFormat('ro-RO', {
    minimumFractionDigits: places,
    maximumFractionDigits: places,
  });

  const suffix = format === 'currency' ? ` ${currency}` : format === 'percent' ? '%' : '';
  const rendered = `${formatter.format(animated)}${suffix}`;
  const finalText = `${formatter.format(safe)}${suffix}`;

  return (
    <span className={className}>
      <span aria-hidden="true">{rendered}</span>
      <span className="sr-only">{finalText}</span>
    </span>
  );
}
