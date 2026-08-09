import api from './axios';

/**
 * Operational analytics: inventory health, order efficiency, customer insights,
 * product performance and the top-products card.
 *
 * Tasks 6, 13, 15, 16 and 18.
 */
const analyticsService = {
  /**
   * TASK 13 — critical stock, overstock, out-of-stock and restock suggestions.
   *
   * No range parameter, deliberately. Inventory health is a statement about
   * right now — what is about to run out, what is sitting unsold — and a
   * historical window would answer a question nobody asked.
   */
  inventoryHealth: (signal) =>
    api.get('/inventory/health', { signal }).then((r) => r.data.data),

  /** TASK 15 — processing time, delivery time, return rate, cancellation rate. */
  orderEfficiency: (range = '30d', signal) =>
    api.get('/orders/efficiency', { params: { range }, signal }).then((r) => r.data.data),

  /**
   * TASK 16 — new against returning, frequency, basket value, segments.
   *
   * @param {{range?: string, type?: string}} params `type` accepts ALL, NEW,
   *   RETURNING or a segment key
   */
  customerInsights: (params = {}, signal) =>
    api.get('/customers/insights', { params, signal }).then((r) => r.data.data),

  /** TASK 18 — rising, declining and stagnant products, with recommendations. */
  productPerformance: (range = '30d', signal) =>
    api.get('/products/performance', { params: { range }, signal }).then((r) => r.data.data),

  /**
   * TASK 6 — three top-product rankings and the promotion candidates.
   *
   * All three rankings arrive in one response so switching between revenue,
   * units and profit is a local state change rather than a round trip — and so
   * the three are computed against the same moment.
   *
   * @param {{range?: string, category?: string, brand?: string}} params
   */
  topProducts: (params = {}, signal) =>
    api.get('/products/top-insights', { params, signal }).then((r) => r.data.data),

  /** TASK 17 — campaign impressions, clicks, conversions and cost per acquisition. */
  marketingPerformance: (range = '30d', signal) =>
    api.get('/marketing/performance', { params: { range }, signal }).then((r) => r.data.data),
};

export default analyticsService;
