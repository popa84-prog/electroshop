import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import orderService from '../api/orderService';
import Pagination from '../components/Pagination';
import { formatPrice, formatDate, statusColor, statusGlyph, statusLabel } from '../utils/format';
import {
  GeoIcon,
  HoloLoader,
  NeonButton,
  Reveal,
  SectionHeader,
  TiltCard,
} from '../components/xxii';

/**
 * XXII — TASK 1 / TASK 8 / TASK 9 (Modul: istoricul comenzilor clientului).
 *
 * Fiecare comandă este un card de sticlă care se înclină ușor sub cursor
 * (`TiltCard`), pentru că întregul card este o zonă de click către detalii —
 * mișcarea confirmă asta înainte de apăsare, fără să adauge text.
 *
 * Cardurile intră pe rând, decalat cu 50ms fiecare. Decalajul este mic
 * intenționat: lista se citește de sus în jos, iar un decalaj mai mare ar
 * transforma o simplă apariție într-o așteptare.
 *
 * Statusul nu este purtat niciodată doar de culoare — insigna conține un
 * glif, eticheta scrisă în română și abia apoi nuanța. Vechea variantă afișa
 * codul brut din baza de date („SHIPPED”) colorat; acum afișează „➤ Expediată”.
 */
export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    orderService
      .myOrders({ page, size: 10 })
      .then((data) => {
        setOrders(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => setOrders([]))
      .finally(() => setLoading(false));
  }, [page]);

  if (loading) return <HoloLoader label="Se încarcă comenzile" />;

  return (
    <div>
      <SectionHeader
        eyebrow="Contul meu"
        title="Comenzile mele"
        as="h1"
        subtitle="Istoricul complet al comenzilor plasate, cu status live și total."
      />

      {orders.length === 0 ? (
        <Reveal direction="scale">
          <div className="card card-static flex flex-col items-center gap-4 px-6 py-16 text-center">
            <span className="flex h-14 w-14 items-center justify-center rounded-full border border-[rgba(122,60,255,0.4)] bg-[rgba(122,60,255,0.12)]">
              <GeoIcon name="box" className="h-6 w-6" />
            </span>
            <div>
              <p className="text-base font-semibold text-[#e8ecff]">
                Nu ai plasat încă nicio comandă.
              </p>
              <p className="mt-1 text-sm xx-ink-muted">
                Când plasezi prima comandă, o vei găsi aici împreună cu statusul livrării.
              </p>
            </div>
            <NeonButton
              to="/products"
              pulse
              icon={<GeoIcon name="grid" className="h-4 w-4" accent="currentColor" />}
            >
              Vezi produse
            </NeonButton>
          </div>
        </Reveal>
      ) : (
        <>
          <div className="space-y-4">
            {orders.map((o, i) => (
              <Reveal key={o.id} delay={i * 50}>
                <TiltCard
                  as={Link}
                  to={`/orders/${o.id}`}
                  max={3}
                  scale={1.008}
                  className="block"
                  innerClassName="card flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0">
                    <p className="flex items-center gap-2 font-semibold text-[#e8ecff]">
                      <GeoIcon name="document" className="h-4 w-4 shrink-0" />
                      Comanda <span className="font-mono">#{o.id}</span>
                    </p>
                    <p className="mt-1 flex items-center gap-1.5 text-sm xx-ink-muted">
                      <GeoIcon name="clock" className="h-3.5 w-3.5 shrink-0" />
                      <span className="font-mono">{formatDate(o.createdAt)}</span>
                    </p>
                    <p className="mt-0.5 flex items-center gap-1.5 text-sm xx-ink-muted">
                      <GeoIcon name="box" className="h-3.5 w-3.5 shrink-0" />
                      {o.items.length} {o.items.length === 1 ? 'produs' : 'produse'}
                    </p>
                  </div>

                  <div className="flex shrink-0 items-center gap-4">
                    <span
                      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 py-1 text-xs font-semibold ${statusColor(
                        o.status
                      )}`}
                    >
                      <span aria-hidden="true">{statusGlyph(o.status)}</span>
                      {statusLabel(o.status)}
                    </span>
                    <span
                      className="text-lg font-bold text-[#e8ecff]"
                      style={{ textShadow: '0 0 24px rgba(34,232,245,0.45)' }}
                    >
                      {formatPrice(o.totalAmount)}
                    </span>
                  </div>
                </TiltCard>
              </Reveal>
            ))}
          </div>

          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
