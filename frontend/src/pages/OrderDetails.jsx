import { useEffect, useState } from 'react';
import { useParams, useLocation, Link } from 'react-router-dom';
import orderService from '../api/orderService';
import {
  formatPrice,
  formatDate,
  resolveImage,
  statusColor,
  statusGlyph,
  statusLabel,
} from '../utils/format';
import {
  GeoIcon,
  HoloLoader,
  NeonButton,
  Reveal,
  SectionHeader,
} from '../components/xxii';

/**
 * XXII — TASK 1 / TASK 5 / TASK 8 / TASK 9 (Modul: detaliul unei comenzi).
 *
 * Ecranul are două regimuri de citire care nu trebuie confundate. Când sosești
 * imediat după plasarea comenzii (`justPlaced`), primul lucru pe care trebuie
 * să îl vezi este confirmarea; când revii peste o săptămână ca să verifici
 * livrarea, primul lucru trebuie să fie statusul. De aceea confirmarea este un
 * panou separat, cu `role="status"` și cu pictogramă proprie, iar statusul stă
 * permanent lângă titlu.
 *
 * Panoul de confirmare folosea `bg-green-50 text-green-700` și un emoji „✅”
 * randat de fontul sistemului. Acum poartă jetoanele de sticlă verde validate
 * și un `GeoIcon`, deci arată identic pe orice platformă.
 *
 * Layoutul rămâne 2 + 1 coloane pe desktop: lista de produse este conținutul,
 * rezumatul este referința, iar pe mobil rezumatul coboară sub listă.
 */
export default function OrderDetails() {
  const { id } = useParams();
  const location = useLocation();
  const justPlaced = location.state?.justPlaced;
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    orderService
      .getOne(id)
      .then(setOrder)
      .catch(() => setError('Comanda nu a fost găsită.'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <HoloLoader label="Se încarcă comanda" />;

  if (error) {
    return (
      <Reveal direction="scale">
        <div className="card card-static flex flex-col items-center gap-4 px-6 py-16 text-center">
          <span className="flex h-14 w-14 items-center justify-center rounded-full border border-[rgba(255,90,122,0.5)] bg-[rgba(255,90,122,0.14)]">
            <GeoIcon name="alert" className="h-6 w-6" accent="#ff8fa8" />
          </span>
          <p role="alert" className="text-base font-semibold text-[#ff8fa8]">
            {error}
          </p>
          <NeonButton
            to="/orders"
            variant="secondary"
            icon={<GeoIcon name="arrow" className="h-4 w-4" accent="currentColor" />}
          >
            Înapoi la comenzi
          </NeonButton>
        </div>
      </Reveal>
    );
  }

  return (
    <div>
      <Link
        to="/orders"
        className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#22e8f5] transition-colors duration-200 hover:text-[#7ee9ff]"
      >
        <GeoIcon name="arrow" className="h-3.5 w-3.5 rotate-180" accent="currentColor" />
        Înapoi la comenzi
      </Link>

      {justPlaced && (
        <Reveal direction="down" className="mt-4">
          <div
            role="status"
            className="flex items-center gap-3 rounded-[1rem] border border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.12)] px-4 py-3 backdrop-blur-xl"
            style={{ boxShadow: '0 0 48px -18px rgba(31,172,121,0.65)' }}
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-[rgba(31,172,121,0.5)] bg-[rgba(31,172,121,0.16)]">
              <GeoIcon name="check" className="h-4 w-4" accent="#7ee9bd" />
            </span>
            <div>
              <p className="text-sm font-semibold text-[#7ee9bd]">
                Comanda ta a fost plasată cu succes.
              </p>
              <p className="text-xs xx-ink-muted">
                Vei primi actualizări de status pe măsură ce comanda este procesată.
              </p>
            </div>
          </div>
        </Reveal>
      )}

      <SectionHeader
        eyebrow="Comanda mea"
        title={`Comanda #${order.id}`}
        as="h1"
        className="mt-4"
        subtitle={`Plasată la ${formatDate(order.createdAt)}`}
        action={
          <span
            className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 py-1 text-xs font-semibold ${statusColor(
              order.status
            )}`}
          >
            <span aria-hidden="true">{statusGlyph(order.status)}</span>
            {statusLabel(order.status)}
          </span>
        }
      />

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-3 lg:col-span-2">
          {order.items.map((item, i) => (
            <Reveal key={item.id} delay={i * 50}>
              <div className="card card-static flex items-center gap-4 p-4">
                <img
                  src={resolveImage(item.imageUrl)}
                  alt={item.productName}
                  loading="lazy"
                  className="h-16 w-16 shrink-0 rounded-[0.8rem] border border-[rgba(255,255,255,0.1)] object-cover"
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium text-[#e8ecff]">{item.productName}</p>
                  <p className="mt-0.5 text-sm xx-ink-muted">
                    <span className="font-mono">{item.quantity}</span> ×{' '}
                    <span className="font-mono">{formatPrice(item.unitPrice)}</span>
                  </p>
                </div>
                <span className="shrink-0 font-semibold text-[#e8ecff]">
                  {formatPrice(item.subtotal)}
                </span>
              </div>
            </Reveal>
          ))}
        </div>

        <Reveal direction="left" delay={120} className="lg:sticky lg:top-24 lg:h-fit">
          <div className="card card-static p-6">
            <h2 className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] xx-ink-muted">
              <GeoIcon name="document" className="h-4 w-4" />
              Detalii livrare
            </h2>

            <div className="mt-4 space-y-1.5 text-sm">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] xx-ink-dim">
                Adresă livrare
              </p>
              <p className="text-[#e8ecff]">{order.shippingAddress || '—'}</p>
            </div>

            <div className="mt-5 flex items-baseline justify-between border-t border-[rgba(255,255,255,0.1)] pt-4">
              <span className="text-sm font-semibold uppercase tracking-[0.14em] xx-ink-muted">
                Total
              </span>
              <span
                className="text-2xl font-bold text-[#e8ecff]"
                style={{ textShadow: '0 0 26px rgba(34,232,245,0.5)' }}
              >
                {formatPrice(order.totalAmount)}
              </span>
            </div>

            <NeonButton
              to="/products"
              variant="secondary"
              block
              className="mt-5"
              icon={<GeoIcon name="grid" className="h-4 w-4" accent="currentColor" />}
            >
              Continuă cumpărăturile
            </NeonButton>
          </div>
        </Reveal>
      </div>
    </div>
  );
}
