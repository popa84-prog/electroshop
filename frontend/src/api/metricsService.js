import api from './axios';

/**
 * Business metrics: the banner, stock value, potential profit, profit breakdown
 * and the sales forecast.
 *
 * Tasks 9–12 and the forecast half of task 2.
 *
 * Every call unwraps `r.data.data` because the backend returns an `ApiResponse`
 * envelope. Doing it here means a panel component never touches the envelope,
 * and the day the envelope changes there is one place to fix rather than
 * seventeen.
 *
 * `signal` is threaded through on every method. Panels fetch on mount and again
 * whenever their range changes, so a slow response for "30 days" can land after
 * a fast one for "7 days" and overwrite it. Passing an AbortSignal lets the
 * caller cancel the stale request instead of guarding against it afterwards.
 */
const metricsService = {
  /**
   * TASK 9 — the four banner figures in one call.
   *
   * One request rather than four: the banner renders as a unit, and separate
   * calls would let its cards be computed against a catalogue that changed
   * between them.
   */
  banner: (signal) =>
    api.get('/metrics/banner', { signal }).then((r) => r.data.data),

  /** TASK 10 — capital tied up in inventory, at cost. */
  stockValue: (signal) =>
    api.get('/metrics/stock-value', { signal }).then((r) => r.data.data),

  /** TASK 11 — margin the current inventory would yield at list price. */
  profitPotential: (signal) =>
    api.get('/metrics/profit-potential', { signal }).then((r) => r.data.data),

  /**
   * TASK 12 — profit by category, brand and product.
   *
   * @param {{range?: string, category?: string, brand?: string}} params
   */
  profitBreakdown: (params = {}, signal) =>
    api.get('/metrics/profit-breakdown', { params, signal }).then((r) => r.data.data),

  /**
   * TASK 2 — the sales forecast.
   *
   * @param {number} horizon how many days ahead; the server clamps it
   */
  predictiveSales: (horizon = 14, signal) =>
    api.get('/metrics/predictive-sales', { params: { horizon }, signal }).then((r) => r.data.data),

  /** TASK 14 — revenue, profit and cost of goods sold over 3, 6 or 12 months. */
  financialOverview: (range = '12m', signal) =>
    api.get('/financial/overview', { params: { range }, signal }).then((r) => r.data.data),
};

export default metricsService;
