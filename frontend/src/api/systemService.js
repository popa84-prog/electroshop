import api from './axios';

/**
 * Infrastructure monitoring: health, operational logs and their CSV export.
 *
 * Tasks 2, 8 and 19.
 */
const systemService = {
  /** TASK 2 — live availability, latency and error rate of the running instance. */
  healthStatus: (signal) =>
    api.get('/system/health-status', { signal }).then((r) => r.data.data),

  /**
   * TASK 19 — the operational log, filtered and paged.
   *
   * @param {{range?: string, source?: string, level?: string, q?: string,
   *          page?: number, size?: number}} params
   */
  logs: (params = {}, signal) =>
    api.get('/system/logs', { params, signal }).then((r) => r.data.data),

  /**
   * TASK 19 — the same filtered rows as a CSV file.
   *
   * Returns a Blob rather than text so the caller hands it straight to an
   * object URL. Reading it as a string first would corrupt the byte-order mark
   * the backend writes for Excel, and the diacritics with it.
   */
  exportLogs: (params = {}) =>
    api.get('/system/logs/export', { params, responseType: 'blob' }).then((r) => r.data),
};

export default systemService;
