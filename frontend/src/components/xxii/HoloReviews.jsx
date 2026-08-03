import { useMemo, useState } from 'react';
import GeoIcon from './GeoIcon';
import NeonBadge from './NeonBadge';

/**
 * XXII — TASK 3 (animated reviews: fluid expand, neon stars).
 *
 * The component renders **only real data**. The backend does not yet expose a
 * reviews endpoint, so this reads whatever the product payload actually carries
 * (`reviews`, `averageRating`/`rating`, `reviewCount`/`ratingCount`) and, when
 * there is nothing, renders an honest empty state instead of fabricated
 * testimonials. A shop that invents five-star reviews to fill a panel is worse
 * than a shop with no reviews panel.
 *
 * Two interaction details:
 *
 *   - A review body longer than `CLAMP_CHARS` collapses to a clamped block with
 *     an expand control. The expand is a height transition on a measured
 *     max-height rather than a display toggle, so it reads as the review
 *     unfolding rather than the page jumping.
 *   - The star row is duplicated as text (`4.6 / 5`) and as an `aria-label`, so
 *     the rating never depends on counting glowing glyphs.
 */

const CLAMP_CHARS = 220;

/** Neon star row. `value` is 0..5 and may be fractional. */
export function NeonStars({ value = 0, size = 'md', className = '' }) {
  const box = size === 'sm' ? 'h-3.5 w-3.5' : size === 'lg' ? 'h-6 w-6' : 'h-4 w-4';
  const rounded = Math.round(value * 2) / 2;

  return (
    <span
      className={`inline-flex items-center gap-0.5 ${className}`}
      role="img"
      aria-label={`Evaluare ${value.toFixed(1)} din 5`}
    >
      {[1, 2, 3, 4, 5].map((position) => {
        const filled = rounded >= position;
        const half = !filled && rounded >= position - 0.5;
        return (
          <span
            key={position}
            aria-hidden="true"
            className="relative inline-flex"
            style={{
              filter: filled || half ? 'drop-shadow(0 0 6px rgba(255,194,75,0.55))' : 'none',
            }}
          >
            <GeoIcon
              name="star"
              className={box}
              accent={filled ? '#ffc24b' : half ? '#ffc24b' : 'rgba(255,255,255,0.22)'}
            />
          </span>
        );
      })}
    </span>
  );
}

function ReviewCard({ review, index }) {
  const body = String(review.comment || review.body || review.text || '');
  const long = body.length > CLAMP_CHARS;
  const [open, setOpen] = useState(false);

  const author = review.authorName || review.author || review.userName || 'Client ElectroShop';
  const rating = Number(review.rating ?? review.stars ?? 0);
  const date = review.createdAt || review.date || null;

  return (
    <article
      className="card card-static p-4 transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.35)]"
      style={{ animationDelay: `${index * 60}ms` }}
    >
      <header className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2.5">
          <span
            aria-hidden="true"
            className="grid h-9 w-9 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.05)] font-display text-sm font-bold text-[color:var(--xx-ink)]"
          >
            {author.trim().charAt(0).toUpperCase()}
          </span>
          <div>
            <p className="text-sm font-semibold text-[color:var(--xx-ink)]">{author}</p>
            {date ? (
              <p className="text-xs xx-ink-dim">{new Date(date).toLocaleDateString('ro-RO')}</p>
            ) : null}
          </div>
        </div>
        <NeonStars value={rating} size="sm" />
      </header>

      <div
        className="relative mt-3 overflow-hidden transition-[max-height] duration-500 ease-xx"
        style={{ maxHeight: !long || open ? '60rem' : '5.25rem' }}
      >
        <p className="whitespace-pre-line text-sm leading-relaxed xx-ink-muted">{body}</p>
        {long && !open ? (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 bottom-0 h-10 bg-gradient-to-t from-[rgba(7,8,24,0.95)] to-transparent"
          />
        ) : null}
      </div>

      {long ? (
        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
          className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-[color:var(--xx-cyan)] transition-opacity duration-xx hover:opacity-80"
        >
          {open ? 'Restrânge' : 'Citește tot'}
          <GeoIcon
            name="chevron"
            className={`h-3.5 w-3.5 transition-transform duration-xx ease-xx ${
              open ? '-rotate-90' : 'rotate-90'
            }`}
            accent="currentColor"
          />
        </button>
      ) : null}
    </article>
  );
}

export default function HoloReviews({ product, className = '' }) {
  const reviews = useMemo(
    () => (Array.isArray(product?.reviews) ? product.reviews.filter(Boolean) : []),
    [product],
  );

  // The average comes from the payload when the backend supplies one; otherwise
  // it is computed from the reviews actually present, so the two never disagree.
  const average = useMemo(() => {
    const supplied = Number(product?.averageRating ?? product?.rating ?? 0);
    if (supplied > 0) return supplied;
    if (reviews.length === 0) return 0;
    const sum = reviews.reduce((total, review) => total + Number(review.rating ?? review.stars ?? 0), 0);
    return sum / reviews.length;
  }, [product, reviews]);

  const count = Number(product?.reviewCount ?? product?.ratingCount ?? reviews.length ?? 0);

  // Distribution bars, computed only from reviews we can actually see.
  const distribution = useMemo(() => {
    const buckets = [0, 0, 0, 0, 0];
    reviews.forEach((review) => {
      const stars = Math.round(Number(review.rating ?? review.stars ?? 0));
      if (stars >= 1 && stars <= 5) buckets[stars - 1] += 1;
    });
    return buckets;
  }, [reviews]);

  const hasData = count > 0 || reviews.length > 0;

  return (
    <section className={className} aria-label="Recenzii">
      <div className="grid gap-6 lg:grid-cols-[minmax(0,18rem)_minmax(0,1fr)]">
        {/* Summary module */}
        <div className="card p-5">
          <p className="xx-eyebrow">Feedback</p>
          <h3 className="xx-title text-xl">Recenzii clienți</h3>

          {hasData ? (
            <>
              <div className="mt-4 flex items-end gap-3">
                <span className="font-display text-4xl font-bold text-[color:var(--xx-ink)]">
                  {average.toFixed(1)}
                </span>
                <span className="pb-1.5 text-sm xx-ink-dim">/ 5</span>
              </div>
              <NeonStars value={average} size="lg" className="mt-2" />
              <p className="mt-2 text-sm xx-ink-muted">
                {count} {count === 1 ? 'recenzie' : 'recenzii'}
              </p>

              {reviews.length > 0 ? (
                <div className="mt-4 space-y-1.5">
                  {[5, 4, 3, 2, 1].map((stars) => {
                    const value = distribution[stars - 1];
                    const percent = reviews.length ? (value / reviews.length) * 100 : 0;
                    return (
                      <div key={stars} className="flex items-center gap-2">
                        <span className="w-3 text-right text-xs xx-ink-dim">{stars}</span>
                        <GeoIcon name="star" className="h-3 w-3" accent="#ffc24b" />
                        <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-[rgba(255,255,255,0.08)]">
                          <span
                            className="block h-full rounded-full bg-gradient-to-r from-[#ffc24b] to-[#ff8a3d] transition-[width] duration-700 ease-xx"
                            style={{ width: `${percent}%` }}
                          />
                        </span>
                        <span className="w-6 text-right text-xs xx-ink-dim">{value}</span>
                      </div>
                    );
                  })}
                </div>
              ) : null}
            </>
          ) : (
            <div className="mt-4">
              <NeonBadge tone="neutral" icon={<GeoIcon name="document" className="h-3 w-3" accent="currentColor" />}>
                Fără recenzii
              </NeonBadge>
              <p className="mt-3 text-sm xx-ink-muted">
                Acest produs nu are încă recenzii. Fii primul care îl evaluează după livrare.
              </p>
            </div>
          )}
        </div>

        {/* Review list */}
        <div className="space-y-4">
          {reviews.length > 0 ? (
            reviews.map((review, index) => (
              <ReviewCard key={review.id ?? index} review={review} index={index} />
            ))
          ) : (
            <div className="card card-static grid place-items-center p-8 text-center">
              <span
                aria-hidden="true"
                className="grid h-12 w-12 place-items-center rounded-xl border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.05)]"
              >
                <GeoIcon name="star" className="h-6 w-6" accent="var(--xx-ink-dim)" />
              </span>
              <p className="mt-3 text-sm xx-ink-muted">
                Recenziile apar aici imediat ce primii clienți evaluează produsul.
              </p>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
